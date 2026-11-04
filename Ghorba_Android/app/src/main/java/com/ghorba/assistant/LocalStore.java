package com.ghorba.assistant;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local AES-GCM protected values. No automatic upload or backup. */
public class LocalStore extends SQLiteOpenHelper {
    private static final String KEY_ALIAS = "ghorba_local_key_v1";
    private SecretKey secret;
    public static final class Memo {
        public final long id;
        public final String key, value;
        Memo(long i, String k, String v){ id=i; key=k; value=v; }
    }
    public static final class Reminder {
        public final long id, at;
        public final String body;
        Reminder(long i, String b, long t){ id=i; body=b; at=t; }
    }
    public LocalStore(Context c) {
        super(c.getApplicationContext(), "ghorba_local_v1.db", null, 1);
        try { secret = keyForApp(); }
        catch(Exception ex) { throw new IllegalStateException("تعذر حماية البيانات على هذا الجهاز", ex); }
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, k TEXT NOT NULL UNIQUE, v BLOB NOT NULL)");
        db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, v BLOB NOT NULL, at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) { }
    private SecretKey keyForApp() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }
    private byte[] encrypt(String value) {
        try {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secret);
            byte[] iv=c.getIV(), encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] out=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,out,0,iv.length);
            System.arraycopy(encrypted,0,out,iv.length,encrypted.length);
            return out;
        }catch(Exception ex){throw new IllegalStateException("تعذر تشفير المعلومات",ex);}
    }
    private String decrypt(byte[] value) {
        try {
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,secret,new GCMParameterSpec(128,Arrays.copyOfRange(value,0,12)));
            return new String(c.doFinal(Arrays.copyOfRange(value,12,value.length)),StandardCharsets.UTF_8);
        }catch(Exception ex){return "[معلومة غير قابلة للقراءة]";}
    }
    public void putMemo(String key,String value) {
        SQLiteDatabase db=getWritableDatabase();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("k",key);v.put("v",encrypt(value));
        db.insertWithOnConflict("memos",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public String getMemo(String key) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT v FROM memos WHERE k=?",new String[]{key})) {
            return c.moveToFirst()?decrypt(c.getBlob(0)):null;
        }
    }
    public List<Memo> allMemos(){
        List<Memo> data=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,k,v FROM memos ORDER BY id DESC",null)){
            while(c.moveToNext())data.add(new Memo(c.getLong(0),c.getString(1),decrypt(c.getBlob(2))));
        }
        return data;
    }
    public void removeMemo(long id){getWritableDatabase().delete("memos","id=?",new String[]{String.valueOf(id)});}
    public void clearMemos(){getWritableDatabase().delete("memos",null,null);}
    public long addReminder(String description,long timestamp){
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("v",encrypt(description));v.put("at",timestamp);
        return getWritableDatabase().insertOrThrow("reminders",null,v);
    }
    public Reminder getReminder(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,v,at FROM reminders WHERE id=?",new String[]{String.valueOf(id)})){
            if(!c.moveToFirst())return null;
            return new Reminder(c.getLong(0),decrypt(c.getBlob(1)),c.getLong(2));
        }
    }
    public List<Reminder> allReminders(){
        List<Reminder> data=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,v,at FROM reminders ORDER BY at",null)){
            while(c.moveToNext())data.add(new Reminder(c.getLong(0),decrypt(c.getBlob(1)),c.getLong(2)));
        }
        return data;
    }
    public void removeReminder(long id){getWritableDatabase().delete("reminders","id=?",new String[]{String.valueOf(id)});}
}
