package com.ghorba.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTPS-only, asynchronous calls to a user-configured AI relay. Never embeds an OpenAI API key. */
public final class AiClient {
    public interface Callback { void done(String answer, String error); }
    private static final ExecutorService QUEUE=Executors.newSingleThreadExecutor();
    public static void ask(String endpoint, String accessToken, String message,
                           String memory, String trade, JSONArray history, Callback callback) {
        QUEUE.execute(()->{
            HttpURLConnection c=null;
            try {
                URL url=new URL(endpoint);
                if(!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty() ||
                   url.getUserInfo()!=null || url.getRef()!=null)
                    throw new Exception("رابط الخدمة لازم يكون HTTPS آمن.");
                if(!url.getHost().endsWith(".workers.dev") && !url.getHost().contains("."))
                    throw new Exception("عنوان الخدمة غير صحيح.");
                c=(HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);c.setReadTimeout(60000);
                c.setDoOutput(true);c.setInstanceFollowRedirects(false);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                c.setRequestProperty("Accept","application/json");
                c.setRequestProperty("Authorization","Bearer "+accessToken);
                JSONObject body=new JSONObject();
                body.put("message",message);
                body.put("memory",memory);
                body.put("trade",trade);
                body.put("history",history);
                byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
                if(payload.length>32000)throw new Exception("المحتوى طويل؛ جرّب رسالة أقصر.");
                try(OutputStream o=c.getOutputStream()){o.write(payload);}
                int code=c.getResponseCode();
                if(code<200||code>299){
                    if(code==401)throw new Exception("رمز الوصول للخدمة غير صحيح.");
                    if(code==429)throw new Exception("الخدمة مزدحمة أو تجاوزت الحد المسموح. جرّب لاحقًا.");
                    throw new Exception("الخدمة رجّعت خطأ: "+code);
                }
                try(InputStream in=c.getInputStream()){
                    String raw=readLimited(in,64000);
                    JSONObject json=new JSONObject(raw);
                    String reply=json.optString("reply","").trim();
                    if(reply.isEmpty())throw new Exception("الخدمة لم ترجع إجابة واضحة.");
                    callback.done(reply.substring(0,Math.min(5000,reply.length())),null);
                }
            }catch(Exception e){callback.done(null,e.getMessage()==null?"تعذّر الاتصال":e.getMessage());}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private static String readLimited(InputStream in,int limit) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n,total=0;
        while((n=in.read(buf))!=-1){total+=n;if(total>limit)throw new Exception("حجم الرد كبير جدًا");out.write(buf,0,n);}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private AiClient(){}
}
