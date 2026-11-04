package com.ghorba.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Store the relay access token encrypted with a Keystore key; never include it in memory export. */
public final class SecureSettings {
    private static final String ALIAS="ghorba_ai_connection_key_v1";
    private final SharedPreferences preferences;
    private final SecretKey key;
    public SecureSettings(Context context)throws Exception {
        preferences=context.getSharedPreferences("ghorba_secure_ai",Context.MODE_PRIVATE);
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))key=((KeyStore.SecretKeyEntry)store.getEntry(ALIAS,null)).getSecretKey();
        else {
            KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            key=kg.generateKey();
        }
    }
    public void setToken(String token)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] encrypted=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] iv=cipher.getIV(),all=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);
        preferences.edit().putString("token",Base64.encodeToString(all,Base64.NO_WRAP)).apply();
    }
    public String getToken()throws Exception {
        String stored=preferences.getString("token","");if(stored.isEmpty())return "";
        byte[] all=Base64.decode(stored,Base64.DEFAULT);if(all.length<29)throw new Exception("معلومات الاتصال تالفة");
        byte[] iv=new byte[12],data=new byte[all.length-12];
        System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(data),StandardCharsets.UTF_8);
    }
    public void clearToken(){preferences.edit().remove("token").apply();}
}
