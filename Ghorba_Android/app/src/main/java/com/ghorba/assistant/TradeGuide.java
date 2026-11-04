package com.ghorba.assistant;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline introductory trade guides. These are not specifications or engineering approvals. */
public final class TradeGuide {
    public static final class Entry {
        public final String id, name, de, summary, aliases, safety, query, sourceUrl, sourceLabel;
        public final JSONArray steps, tools, terms;
        Entry(JSONObject o){
            id=o.optString("id"); name=o.optString("name");de=o.optString("de");
            summary=o.optString("summary"); aliases=o.optString("aliases");
            safety=o.optString("safety");query=o.optString("query");
            sourceUrl=o.optString("source_url");sourceLabel=o.optString("source_label");
            steps=o.optJSONArray("steps");tools=o.optJSONArray("tools");terms=o.optJSONArray("terms");
        }
        public String numberedSteps(){return lines(steps,true);}
        public String toolsText(){return lines(tools,false);}
        public String vocabulary(){return lines(terms,false);}
    }
    private static List<Entry> CACHE;
    private TradeGuide(){}
    public static synchronized List<Entry> all(Context c){
        if(CACHE!=null)return CACHE;
        ArrayList<Entry> list=new ArrayList<>();
        try(InputStream in=c.getAssets().open("trades_ar_de.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1)bytes.write(b,0,n);
            JSONArray arr=new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for(int i=0;i<arr.length();i++)list.add(new Entry(arr.getJSONObject(i)));
        }catch(Exception ex){android.util.Log.e("GhorbaTradeGuide","Load guide failed",ex);}
        CACHE=list;return CACHE;
    }
    public static String lines(JSONArray a,boolean numbered){
        if(a==null)return "";
        StringBuilder b=new StringBuilder();
        for(int i=0;i<a.length();i++){
            if(i>0)b.append('\n');
            b.append(numbered?(i+1)+". ":"• ").append(a.optString(i));
        }
        return b.toString();
    }
    public static String normalize(String s){
        return s.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا')
            .replace('ة','ه').replace('ى','ي').replaceAll("[\\u064B-\\u065F]","")
            .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    public static Entry best(Context context,String question){
        String q=normalize(question);if(q.length()<3)return null;
        Entry result=null;int score=0;
        for(Entry e:all(context)){
            int hit=0;
            String label=normalize(e.name), de=normalize(e.de), ali=normalize(e.aliases);
            if(q.contains(label))hit+=12;
            if(q.contains(de))hit+=12;
            for(String word:label.split(" "))if(word.length()>=4 && q.contains(word))hit+=4;
            for(String word:de.split(" "))if(word.length()>=5 && q.contains(word))hit+=4;
            for(String word:ali.split(" "))if(word.length()>=5 && q.contains(word))hit+=1;
            for(String term:e.terms==null?new String[0]:toStrings(e.terms)){
                String[] parts=normalize(term).split(" ");
                for(String word:parts)if(word.length()>=5 && q.contains(word))hit+=1;
            }
            if(hit>score){result=e;score=hit;}
        }
        return score>=2?result:null;
    }
    private static String[] toStrings(JSONArray a){
        String[] s=new String[a.length()];for(int i=0;i<a.length();i++)s[i]=a.optString(i);return s;
    }
    /** The AI can use this vetted, local overview without accessing private phone files. */
    public static String context(Context context,String question){
        Entry e=best(context,question);
        if(e==null)return "";
        String result="دليل مهني تمهيدي عن: "+e.name+" ("+e.de+")\n"
            +"التعريف: "+e.summary+"\nخطوات توجيهية عامة:\n"+e.numberedSteps()
            +"\nالأدوات: "+e.toolsText()+"\nمصطلحات ألمانية: "+e.vocabulary()
            +"\nتحذير: "+e.safety+"\nمرجع عام: "+e.sourceUrl
            +"\nهذا دليل مختصر، لا يمثل مخطط تنفيذ أو أسعارًا أو متطلبات محدثة ملزمة. "
            +"اسأل عن نوع الجدار والمواد والمقاسات والموقع إن كانت مؤثرة. لا تختلق صورًا أو فيديوهات شاهَدْتها.";
        return result.substring(0,Math.min(2400,result.length()));
    }
}
