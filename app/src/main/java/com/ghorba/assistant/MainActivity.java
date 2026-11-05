package com.ghorba.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Offline-first companion UI. It does not pretend to be an always-on voice assistant.
 * Explicit permission + confirmation for every personal-data collection action.
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO_REQUEST=112, NOTIFY_REQUEST=113, WAKE_AUDIO_REQUEST=114, CONTACT_PICK=211, FILE_PICK=212, FILE_EXPORT=213, FILE_MULTI_PICK=214;
    private static final String STANDBY_CHANNEL="ghorba_quick";
    private static final int STANDBY_NOTIFICATION=1011;
    private static final int BG=0xff0B1026, PANEL=0xff242253, LIGHT=0xffDCD7FA, PINK=0xffC986FA;
    private LinearLayout root;
    private TextView answer, micStatus;
    private EditText input;
    private OrbView orb;
    private LocalStore store;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private boolean voiceReady=false;
    private String pendingExport="";
    private String lastQuestion="";
    // Conversations stay only in process memory until the user explicitly approves saving.
    // The temporary buffer is cleared on rejection, or on application process termination.
    // A large session may still be lost if Android kills the process before the user saves.
    private final ArrayList<String> unsavedConversation = new ArrayList<>();
    private boolean proposingMemory=false;
    // Only short, unsaved dialogue context is used for AI, never stored automatically.
    private final ArrayList<String[]> recentAiHistory=new ArrayList<>();
    private boolean aiBusy=false;
    private SecureSettings secureSettings;

    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff080d22);
        getWindow().setNavigationBarColor(0xff080d22);
        try {store=new LocalStore(this);secureSettings=new SecureSettings(this);}
        catch(Exception ex){showError("تعذر فتح الذاكرة أو الإعدادات المشفرة: "+ex.getMessage());return;}
        ReminderReceiver.ensureChannel(this);
        setupUi();
        tts=new TextToSpeech(this,this);
    }
    private GradientDrawable rounded(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);
        return d;
    }
    private TextView label(String text,int size,boolean strong){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(size);
        t.setGravity(Gravity.CENTER);if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button btn(String txt){
        Button b=new Button(this);b.setText(txt);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setTextSize(13);b.setPadding(dp(9),dp(9),dp(9),dp(9));
        b.setBackground(rounded(0xff382B73,0xff635C9E,15));return b;
    }
    private void addButton(String txt,View.OnClickListener l){
        Button b=btn(txt);b.setOnClickListener(l);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(10);root.addView(b,p);
    }
    private LinearLayout makeRow(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);
        root.addView(row,p);return row;
    }
    private void rowButton(LinearLayout row,String text,View.OnClickListener listener){
        Button b=btn(text);b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),0,dp(3),0);
        row.addView(b,p);
    }
    private void setupUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff080d24,0xff191743,0xff080d20}));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18),dp(22),dp(18),dp(28));scroll.addView(root);
        if(Build.VERSION.SDK_INT>=35){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(18),bars.top+dp(14),dp(18),bars.bottom+dp(25));return insets;
            });
        }
        setContentView(scroll);
        TextView title=label("غربة ✦",29,true);root.addView(title);
        TextView subtitle=label("صديقتك السورية • مساعدتك الشخصية",13,false);
        subtitle.setTextColor(LIGHT);root.addView(subtitle);
        orb=new OrbView(this);root.addView(orb,new LinearLayout.LayoutParams(-1,dp(285)));
        answer=label("أهلين يا عيوني 💜 أنا غربة! شو بدك نعمل اليوم؟",16,false);
        answer.setPadding(dp(14),dp(16),dp(14),dp(16));answer.setBackground(rounded(PANEL,0xff6662A0,17));
        root.addView(answer,new LinearLayout.LayoutParams(-1,-2));
        micStatus=label("الميكروفون متوقف • اضغط للتحدث",12,false);
        micStatus.setTextColor(0xffC7C2E8);micStatus.setPadding(0,dp(10),0,0);
        root.addView(micStatus);
        LinearLayout row=makeRow();
        rowButton(row,"🎙️ احكي مع غربة",v->microphoneButton());
        rowButton(row,"🔊 جرّب صوتها",v->say("هلا والله يا عيوني! ها أنا هون، شو أخبارك اليوم؟ هههه!"));
        addButton("🎚️ اختار صوت غربة من أصوات هاتفك",v->chooseVoice());
        addButton("✨ فعّلي محادثة حرة بالذكاء الاصطناعي",v->configureAi());
        LinearLayout messageRow=makeRow();
        input=new EditText(this);input.setSingleLine(false);input.setMaxLines(3);
        input.setTextColor(Color.WHITE);input.setHintTextColor(0xffB2ADD4);input.setTextSize(15);
        input.setHint("اكتب: اسمي ...، احفظي ...، ذكّريني ...");
        input.setBackground(rounded(0xff111A39,0xff655D9D,12));input.setPadding(dp(12),dp(10),dp(12),dp(10));
        messageRow.addView(input,new LinearLayout.LayoutParams(0,dp(55),3));
        Button send=btn("إرسال");
        LinearLayout.LayoutParams ps=new LinearLayout.LayoutParams(0,dp(55),1);ps.setMargins(dp(6),0,0,0);
        messageRow.addView(send,ps);send.setOnClickListener(v->{String q=input.getText().toString();input.setText("");handle(q);});
        LinearLayout tools=makeRow();
        rowButton(tools,"🧠 ذاكرتي",v->showMemories());
        rowButton(tools,"🔔 تذكيراتي",v->showReminders());
        LinearLayout tools2=makeRow();
        rowButton(tools2,"📱 جهة اتصال",v->askContact());
        rowButton(tools2,"📄 ملف أختاره",v->askImport());
        LinearLayout tools3=makeRow();
        rowButton(tools3,"⬇️ تصدير معلوماتي",v->exportData());
        rowButton(tools3,"🛡️ الخصوصية",v->showPrivacy());
        LinearLayout tools4=makeRow();
        rowButton(tools4,"💜 شو بتعرفي عني؟",v->showPersonalProfile());
        rowButton(tools4,"💬 خلصنا — أحفظ الحديث؟",v->offerSaveConversation());
        LinearLayout tools5=makeRow();
        rowButton(tools5,"🔎 فتّشي بذكرياتي",v->promptMemorySearch());
        rowButton(tools5,"📚 استوردي ملفات نصية",v->askImportMany());
        LinearLayout tools6=makeRow();
        rowButton(tools6,"📔 اكتبي يومياتي",v->promptDiary());
        addButton("🧰 قاموس المهن • صور توضيحية وفيديو ومصطلحات ألمانية",v->showTradeIndex());
        rowButton(tools6,"⚙️ أوامر التطبيقات",v->configureQuickAppOpen());
        addButton("🎙️ خلّي غربة تسمع «ها غربة» حتى أوقفها",v->startWakeSession());
        addButton("⛔ أوقفي الاستماع المستمر الآن",v->stopWakeSession());
        addButton("⭐ اختصار غربة في الإشعارات (بدون مراقبة صوتية)",v->enableStandbyShortcut());
        TextView footer=label("الحديث واليوميات لا يُحفظان إلا بموافقتك. عند قول «خلصنا» تختار حفظ المحادثة أو عدم حفظها.\nالاستماع يتطلب تشغيلًا صريحًا، ويمكن إيقافه من التطبيق أو الإشعار. قد يوقفه أندرويد، وقد تستخدم خدمة التعرف الشبكة والبطارية.",11,false);
        footer.setTextColor(0xffB1ADD0);footer.setPadding(dp(7),dp(20),dp(7),dp(10));root.addView(footer);
    }
    private void reply(String text){
        answer.setText(text);say(text);
    }
    private void say(String message){
        answer.setText(message);
        if(!voiceReady||tts==null){Toast.makeText(this,"النص جاهز، لكن صوت الجهاز غير متاح بعد",Toast.LENGTH_SHORT).show();return;}
        try {tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"ghorba_utterance");}
        catch(Exception ex){micStatus.setText("الصوت غير متاح: راجع إعدادات تحويل النص إلى كلام");}
    }
    @Override public void onInit(int status){
        if(status!=TextToSpeech.SUCCESS){micStatus.setText("لا يوجد محرك نطق متاح على الهاتف");return;}
        Locale sy=Locale.forLanguageTag("ar-SY");
        int result=tts.setLanguage(sy);
        if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED){
            tts.setLanguage(new Locale("ar"));
        }
        // Android TTS engines do not guarantee gender. Prefer named female Arabic voices when installed.
        try {
            if(tts.getVoices()!=null){
                for(android.speech.tts.Voice voice:tts.getVoices()){
                    if(!"ar".equals(voice.getLocale().getLanguage()))continue;
                    String n=voice.getName().toLowerCase(Locale.ROOT);
                    if(n.contains("female")||n.contains("sara")||n.contains("layla")||n.contains("hoda")||n.contains("salma")){
                        tts.setVoice(voice);break;
                    }
                }
            }
        }catch(Exception ignored){}
        String selectedVoice=getPreferences(MODE_PRIVATE).getString("voice_name","");
        if(!selectedVoice.isEmpty() && tts.getVoices()!=null){
            for(android.speech.tts.Voice v:tts.getVoices()){
                if(v.getName().equals(selectedVoice)){tts.setVoice(v);break;}
            }
        }
        tts.setPitch(1.09f);tts.setSpeechRate(0.94f);voiceReady=true;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){runOnUiThread(()->{orb.setSpeaking(true);micStatus.setText("غربة عم تحكي…");});}
            @Override public void onDone(String id){runOnUiThread(()->{orb.setSpeaking(false);micStatus.setText("الميكروفون متوقف • اضغط للتحدث");});}
            @Override public void onError(String id){runOnUiThread(()->{orb.setSpeaking(false);micStatus.setText("تعذر تشغيل الصوت");});}
        });
    }
    private void chooseVoice(){
        if(tts==null || !voiceReady || tts.getVoices()==null){
            showError("محرك الصوت غير جاهز. تأكد من تثبيت بيانات العربية في إعدادات تحويل النص إلى كلام على هاتفك.");return;
        }
        List<android.speech.tts.Voice> arabic=new ArrayList<>();
        for(android.speech.tts.Voice v:tts.getVoices()){
            if("ar".equals(v.getLocale().getLanguage()))arabic.add(v);
        }
        if(arabic.isEmpty()){showError("ما في أصوات عربية مثبّتة عندك. افتح إعدادات سامسونج وثبّت أصوات العربية لتحويل النص إلى كلام.");return;}
        String[] names=new String[arabic.size()];
        for(int i=0;i<arabic.size();i++){
            android.speech.tts.Voice v=arabic.get(i);
            names[i]=v.getName()+" • "+v.getLocale().toLanguageTag()
                +(v.isNetworkConnectionRequired()?" • يحتاج شبكة":" • يمكن تشغيله محليًا");
        }
        new AlertDialog.Builder(this).setTitle("اختاري صوت غربة")
          .setItems(names,(d,which)->{
              android.speech.tts.Voice v=arabic.get(which);
              try {
                  int result=tts.setVoice(v);
                  if(result==TextToSpeech.SUCCESS){
                      getPreferences(MODE_PRIVATE).edit().putString("voice_name",v.getName()).apply();
                      say("هلا يا عيوني، أنا غربة! شو رأيك بصوتي الجديد؟");
                  }else showError("لم يقبل محرّك الهاتف هالصوت. جرّب صوت ثاني.");
              }catch(Exception e){showError("تعذر تغيير الصوت: "+e.getMessage());}
          }).setNegativeButton("إغلاق",null).show();
    }
    private void microphoneButton(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQUEST);return;
        }
        boolean consent=getPreferences(MODE_PRIVATE).getBoolean("voice_notice",false);
        if(!consent){
            new AlertDialog.Builder(this).setTitle("خصوصية الصوت")
              .setMessage("الاستماع يبدأ فقط عندما تضغط الميكروفون. قد ترسل خدمة التعرف على الكلام في هاتفك الصوت لمعالجته عبر الإنترنت. هل توافق على بدء جلسة استماع واحدة؟")
              .setNegativeButton("لا",null)
              .setPositiveButton("أوافق",(d,w)->{
                  getPreferences(MODE_PRIVATE).edit().putBoolean("voice_notice",true).apply();listenOnce();
              }).show();
        }else listenOnce();
    }
    private void listenOnce(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){
            reply("ما لقيت خدمة التعرف على الكلام بجهازك، فيك تكتبلي بدل الصوت.");return;
        }
        if(recognizer!=null){recognizer.destroy();recognizer=null;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            @Override public void onReadyForSpeech(Bundle p){micStatus.setText("عم اسمعك هلأ…");}
            @Override public void onBeginningOfSpeech(){}
            @Override public void onRmsChanged(float rms){}
            @Override public void onBufferReceived(byte[] buffer){}
            @Override public void onEndOfSpeech(){micStatus.setText("عم أفهم كلامك…");}
            @Override public void onPartialResults(Bundle b){}
            @Override public void onEvent(int event,Bundle b){}
            @Override public void onError(int error){micStatus.setText("تعذر سماع الكلام ("+error+"). جرّب مرة تانية أو اكتب.");}
            @Override public void onResults(Bundle b){
                ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(results!=null&&!results.isEmpty())handle(results.get(0));
                else micStatus.setText("ما فهمت الصوت، جرّب من جديد.");
            }
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-SY");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
        try{recognizer.startListening(intent);}catch(Exception e){micStatus.setText("الميكروفون لم يبدأ: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] r){
        super.onRequestPermissionsResult(request,p,r);
        if(request==AUDIO_REQUEST && r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)microphoneButton();
        else if(request==AUDIO_REQUEST)micStatus.setText("لم تسمح بالميكروفون؛ تقدر تكتبلي دائمًا.");
        else if(request==WAKE_AUDIO_REQUEST && r.length>0 && r[0]==PackageManager.PERMISSION_GRANTED)
            micStatus.setText("منحت الإذن. اضغط زر جلسة «ها غربة» مرة ثانية لتأكيد التشغيل.");
        else if(request==WAKE_AUDIO_REQUEST)micStatus.setText("ما وافقت على الميكروفون؛ ما في استماع.");
    }
    private String cleaned(String s){
        return s.replace('أ','ا').replace('إ','ا').replace('آ','ا')
          .replace('ة','ه').replaceAll("[\\u064B-\\u065F]","").trim();
    }
    private void handle(String raw){
        String q=raw==null?"":raw.trim();if(q.isEmpty())return;
        lastQuestion=q;
        String norm=cleaned(q);
        if(isHighRiskPrivateText(q)){
            reply("لأمانك ما رح أخزّن كلمات السر أو رموز التحقق أو بيانات البطاقة هون. استخدم مدير كلمات مرور آمن، يا عيوني.");
            return;
        }
        // Never store chat without explicit approval. Avoid retaining high-risk strings.
        if(!isHighRiskPrivateText(q) && unsavedConversation.size()<500)
            unsavedConversation.add(q.substring(0,Math.min(q.length(),1000)));
        if(norm.equals("خلصنا") || norm.equals("خلصنا الحديث") || norm.contains("انتهينا من الحديث") || norm.equals("مع السلامه")){
            offerSaveConversation();return;
        }
        if((norm.contains("صور ") || norm.contains("صوره ") || norm.contains("فيديو ") || norm.contains("فيديوهات "))
           && TradeGuide.best(this,q)!=null){
            TradeGuide.Entry guide=TradeGuide.best(this,q);
            String query=Uri.encode(guide.query);
            String link=(norm.contains("فيديو") ? "https://www.youtube.com/results?search_query=" : "https://www.google.com/search?tbm=isch&q=")+query;
            new AlertDialog.Builder(this).setTitle("أمثلة عن "+guide.name)
              .setMessage("رح أفتح نتائج "+(norm.contains("فيديو")?"الفيديوهات":"الصور")+" على الإنترنت. الصور والفيديوهات الخارجية ما بيتم التحقق من كل محتواها تلقائيًا.")
              .setPositiveButton("افتحي",(d,w)->openSafeTradeUrl(link))
              .setNegativeButton("إلغاء",null).show();return;
        }
        if(norm.equals("قاموس المهن") || norm.equals("قاموس حرف") || norm.equals("المهن") || norm.equals("علميني مهن")){
            showTradeIndex();return;
        }
        if(norm.contains("شو بتعرفي عني") || norm.contains("تعرفيني") || norm.contains("شخصيتي") || norm.contains("طبعي")){
            showPersonalProfile();return;
        }
        if(norm.startsWith("بحب ") || norm.startsWith("انا بحب ") || norm.startsWith("ما بحب ") || norm.startsWith("بكره ") || norm.startsWith("طبيعتي ") || norm.startsWith("بفضل ")){
            final String fact=q;
            confirm("تعلّم عن شخصيتك","بدك أتذكر هالمعلومة عنك لحتى نرجع نحكي عنها بالمستقبل؟\n\n"+fact,()->{
                store.putMemo("عن شخصيتي "+System.currentTimeMillis(),fact);
                reply("حفظتها بملف شخصيتك بعد موافقتك 💜");
            });return;
        }
        if(norm.startsWith("اليوم صار معي ") || norm.startsWith("امبارح صار معي ") ||
           norm.startsWith("اكتبي بمذكراتي ") || norm.startsWith("ضيفي ليومياتي ") ||
           norm.startsWith("بمذكراتي ")){
            offerDiarySave(q);return;
        }
        if(norm.contains("ذكريات اليوم") || norm.equals("يومياتي") || norm.contains("شو كتبت بمذكراتي")){
            searchPersonalMemory("يوميات");return;
        }
        if(norm.startsWith("بتتذكري ") || norm.startsWith("تتذكري ") ||
           norm.startsWith("فتشي بذكرياتي") || norm.startsWith("فتشي بذاكرتك") ||
           norm.startsWith("دوري بذكرياتي") || norm.startsWith("شو صار معي") ||
           norm.startsWith("متى صار") || norm.startsWith("شو بتعرفي عن")){
            searchPersonalMemory(q);return;
        }
        if(norm.contains("افتح") && (norm.contains("يوتيوب") || norm.contains("youtube") || norm.contains("تيك توك") || norm.contains("tiktok") || norm.contains("انستغرام") || norm.contains("انستقرام") || norm.contains("instagram"))){
            offerAppOpen(norm);return;
        }
        if(norm.equals("ها غربه")||norm.equals("يا غربه")||norm.equals("غربه")||norm.equals("اهلا غربه")){
            reply("هلا والله يا عيوني! هههه، أنا هون. شو بدّك مني؟");return;
        }
        if(norm.startsWith("اسمي ")||norm.startsWith("انا اسمي ")){
            String n=norm.startsWith("انا اسمي ") ? q.substring(Math.min(q.length(),8)).trim() :
                q.substring(Math.min(q.length(),5)).trim();
            final String name=n;
            confirm("حفظ اسمك","أحفظ اسمك: "+name+"؟",()->{store.putMemo("اسمك",name);reply("تشرفت فيك يا "+name+"! صار اسمك محفوظ عندي بهالجهاز.");});return;
        }
        if(norm.contains("شو اسمي")||norm.contains("ما اسمي")){
            String n=store.getMemo("اسمك");reply(n==null?"لسا ما حفظنا اسمك. قوللي: اسمي ...":"اسمك "+n+"! أكيد متذكرتك 💜");return;
        }
        if(norm.startsWith("احفظي ")||norm.startsWith("تذكري ")||norm.startsWith("احفظ ")){
            String data=q.substring(q.indexOf(' ')+1).trim();
            confirm("حفظ معلومة","أحفظ هالمعلومة عندي على جهازك؟\n\n"+data,()->{
                store.putMemo("معلومة "+System.currentTimeMillis(),data);
                reply("حفظتها بعد موافقتك، يا عيوني. بتلاقيها بذاكرتي.");
            });return;
        }
        if(norm.contains("ذكريني")||norm.contains("ذكرني")||norm.contains("تذكير")){
            showReminderPicker(q);return;
        }
        if(norm.contains("شو بتعرفي")||norm.contains("ذاكرتك")||norm.contains("المعلومات المحفوظه")){
            showMemories();return;
        }
        if(norm.contains("ابحثي ")||norm.contains("دوري على ")||norm.contains("ابحث عن ")){
            final String question=q;
            confirm("بحث عبر الإنترنت","هل توافق أن أرسل نص سؤالك إلى متصفح البحث؟\n\n"+question,()->webSearch(question));return;
        }
        if(!aiEnabled() && seemsTradeQuestion(q)){
            TradeGuide.Entry e=TradeGuide.best(this,q);
            if(e!=null){showTradeEntry(e);return;}
        }
        if(aiEnabled()){askAi(q);return;}
        String n=store.getMemo("اسمك");
        answer.setText((n==null?"":"يا "+n+"، ")+"سمعتك 💜 لتصير إجاباتي حرة، افتح زر «فعّلي محادثة حرة» واربطني بخدمة ذكاء اصطناعي آمنة.");
        new AlertDialog.Builder(this).setTitle("سؤال جديد")
           .setMessage("بدك أفتح بحث على الإنترنت عن هالسؤال؟\n\n"+q)
           .setNegativeButton("لا",null).setPositiveButton("ابحثي",(d,w)->webSearch(q)).show();
    }

    /** Browse introductory guides offline, with original schematic image and online examples on request. */
    private void showTradeIndex(){
        java.util.List<TradeGuide.Entry> entries=TradeGuide.all(this);
        if(entries.isEmpty()){showError("ما قدرت أفتح قاموس المهن. تحقق من ملفات التطبيق.");return;}
        String[] options=new String[entries.size()+1];
        options[0]="🔎 ابحث باسم المهنة أو الأداة بالعربي أو الألماني";
        for(int i=0;i<entries.size();i++){
            TradeGuide.Entry e=entries.get(i);options[i+1]=e.name+" — "+e.de;
        }
        new AlertDialog.Builder(this).setTitle("🧰 قاموس المهن • "+entries.size()+" موضوع")
          .setItems(options,(d,which)->{
              if(which==0)promptTradeSearch();
              else showTradeEntry(entries.get(which-1));
          }).setNegativeButton("إغلاق",null).show();
    }
    private void promptTradeSearch(){
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setHint("مثلاً: ربط الحديد / WDVS / Fliesen");
        new AlertDialog.Builder(this).setTitle("ابحثي بقاموس المهن")
          .setView(field).setNegativeButton("إلغاء",null)
          .setPositiveButton("بحث",(d,w)->{
              String q=field.getText().toString().trim();
              if(q.isEmpty())return;
              TradeGuide.Entry e=TradeGuide.best(this,q);
              if(e!=null)showTradeEntry(e);
              else new AlertDialog.Builder(this).setTitle("ما لقيت الموضوع محليًا")
                  .setMessage("الموسوعة قابلة للتوسع. بتحب أبحث لك عن هالمهنة عبر الإنترنت؟")
                  .setNegativeButton("إلغاء",null)
                  .setPositiveButton("ابحثي",(d2,w2)->openSafeTradeUrl("https://www.google.com/search?q="+Uri.encode(q+" Handwerk Anleitung")))
                  .show();
          }).show();
    }
    private boolean seemsTradeQuestion(String q){
        String a=TradeGuide.normalize(q);
        for(String term:new String[]{"كيف اشتغل","كيف اعمل","كيف اركب","كيف اسوي","كيف انجز","طريقه","شرح","مواد","ادوات","المهن","الحرف","بالموقع","بالورشه","تعلمني","علميني"})
            if(a.contains(term))return true;
        // A question about a recognized trade may be handled with the offline guide if AI is off.
        return a.contains("الحديد") || a.contains("الطينه") || a.contains("لياسه") || a.contains("العزل") ||
               a.contains("الجبس") || a.contains("البلاط") || a.contains("دهان") || a.contains("wdvs");
    }
    private void showTradeEntry(TradeGuide.Entry e){
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(12),dp(18),dp(12));body.setBackgroundColor(BG);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(body);
        TextView heading=label(e.name+"\n"+e.de,19,true);heading.setPadding(0,0,0,dp(10));body.addView(heading);
        try(InputStream image=getAssets().open("guides/"+e.id+".png")){
            ImageView iv=new ImageView(this);
            iv.setImageBitmap(BitmapFactory.decodeStream(image));
            iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            body.addView(iv,new LinearLayout.LayoutParams(-1,dp(197)));
            TextView note=label("رسم توضيحي من إعداد غربة — مو صورة موقع ولا مخطط تنفيذ",11,false);
            note.setTextColor(0xffBAB5DB);body.addView(note);
        }catch(Exception ignored){}
        tradeSection(body,"شو هي المهنة؟",e.summary);
        tradeSection(body,"طريقة العمل خطوة بخطوة",e.numberedSteps());
        tradeSection(body,"المواد والأدوات",e.toolsText());
        tradeSection(body,"قاموس عربي / ألماني",e.vocabulary());
        tradeSection(body,"⚠️ السلامة والأخطاء",e.safety);
        Button photos=btn("🖼️ شوف صور شغل وأمثلة حقيقية");body.addView(photos);
        photos.setOnClickListener(v->openSafeTradeUrl("https://www.google.com/search?tbm=isch&q="+Uri.encode(e.query)));
        Button videos=btn("▶️ ابحث عن فيديوهات تعليمية");body.addView(videos);
        videos.setOnClickListener(v->openSafeTradeUrl("https://www.youtube.com/results?search_query="+Uri.encode(e.query)));
        Button source=btn("📖 افتح المرجع المهني • "+e.sourceLabel);body.addView(source);
        source.setOnClickListener(v->openSafeTradeUrl(e.sourceUrl));
        Button discuss=btn("💬 ناقشيني بالتفصيل عن "+e.name);body.addView(discuss);
        discuss.setOnClickListener(v->{
            if(aiEnabled())askAi("اشرح لي بالتفصيل وبالعامية السورية طريقة العمل والمواد والخطوات والأخطاء الشائعة في "+e.name+" ("+e.de+")، واسألني عن تفاصيل موقع العمل عند الحاجة.");
            else new AlertDialog.Builder(this).setTitle("المناقشة الحرة")
                .setMessage("قاموس المهن بيعمل بدون إنترنت. للمناقشة الحرة بأي سؤال، لازم تربط خدمة الذكاء الاصطناعي من زر تفعيلها في الشاشة الرئيسية.")
                .setPositiveButton("إعداد الذكاء",(x,y)->configureAi())
                .setNegativeButton("لاحقًا",null).show();
        });
        new AlertDialog.Builder(this).setTitle("دليل المهن — "+e.name)
            .setView(scroll).setPositiveButton("تمام",null)
            .setNeutralButton("مهنة ثانية",(d,w)->showTradeIndex()).show();
    }
    private void tradeSection(LinearLayout body,String title,String text){
        TextView h=label(title,16,true);h.setGravity(Gravity.RIGHT);
        h.setTextColor(0xffDBB4FF);h.setPadding(0,dp(12),0,dp(4));body.addView(h);
        TextView content=label(text,14,false);content.setGravity(Gravity.RIGHT);
        content.setTextColor(0xffE1E2F6);content.setPadding(0,0,0,dp(5));body.addView(content);
    }
    private void openSafeTradeUrl(String link){
        try{
            Uri uri=Uri.parse(link);
            if(!"https".equals(uri.getScheme()))throw new Exception("رابط غير آمن");
            Intent i=new Intent(Intent.ACTION_VIEW,uri);
            startActivity(i);
        }catch(Exception e){showError("ما قدرت أفتح الرابط. تأكد من وجود متصفح وإنترنت.");}
    }

    private boolean aiEnabled(){
        return getPreferences(MODE_PRIVATE).getBoolean("ai_enabled",false) &&
               getPreferences(MODE_PRIVATE).getString("ai_url","").startsWith("https://");
    }
    private void configureAi(){
        android.content.SharedPreferences p=getPreferences(MODE_PRIVATE);
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(16),dp(5),dp(16),dp(5));
        EditText endpoint=new EditText(this);endpoint.setSingleLine(true);
        endpoint.setHint("https://my-ghorba.workers.dev/chat");
        endpoint.setText(p.getString("ai_url",""));fields.addView(endpoint);
        EditText token=new EditText(this);token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("رمز الوصول الخاص بخدمتك (ليس مفتاح OpenAI)");
        fields.addView(token);
        android.widget.CheckBox includeMemory=new android.widget.CheckBox(this);
        includeMemory.setText("اسمحي لغربة باستخدام مقتطفات من ذكرياتي المحفوظة عند السؤال");
        includeMemory.setTextColor(Color.WHITE);
        includeMemory.setChecked(p.getBoolean("ai_memories",false));fields.addView(includeMemory);
        TextView warning=label("لما تفعّل الخدمة، نص أسئلتك وآخر الحوار رح يروح للخادم اللي بتحط عنوانه. وإذا اخترت الذكريات، رح تُرسل مقتطفات مناسبة من معلوماتك المحفوظة، مو كل الهاتف. ما رح أرسل كلمات مرور أو رموز تحقق. ممكن تكون الخدمة مدفوعة.",12,false);
        warning.setPadding(dp(2),dp(8),dp(2),dp(5));fields.addView(warning);
        new AlertDialog.Builder(this).setTitle("إعداد الإجابات الحرة لغربة")
          .setView(fields)
          .setPositiveButton("أوافق وأفعّل",(d,w)->{
              String u=endpoint.getText().toString().trim();
              String auth=token.getText().toString().trim();
              if(!u.startsWith("https://")||!u.endsWith("/chat")||u.length()<18){showError("أدخل رابط الخدمة HTTPS المنتهي بـ /chat");return;}
              try {
                  if(!auth.isEmpty())secureSettings.setToken(auth);
                  if(secureSettings.getToken().isEmpty()){showError("أدخل رمز الوصول للربط أول مرة");return;}
                  p.edit().putString("ai_url",u).putBoolean("ai_memories",includeMemory.isChecked())
                    .putBoolean("ai_enabled",true).apply();
                  reply("تمام! جهّزت الاتصال بالخدمة. اسألني سؤال مفتوح حتى نجرب. إذا ما كانت الخدمة منشورة بعد رح يظهر خطأ اتصال.");
              }catch(Exception ex){showError("تعذر حماية رمز الوصول: "+ex.getMessage());}
          })
          .setNeutralButton("أوقفي الذكاء الشبكي",(d,w)->{
              p.edit().putBoolean("ai_enabled",false).remove("ai_url").putBoolean("ai_memories",false).apply();
              secureSettings.clearToken();recentAiHistory.clear();
              reply("أوقفت اتصال الإجابات الحرة ومسحت معلومات ربط الخدمة من التطبيق.");
          })
          .setNegativeButton("إلغاء",null).show();
    }
    /** Select only relevant approved memos, never send the entire personal archive. */
    private String relevantMemories(String question){
        if(!getPreferences(MODE_PRIVATE).getBoolean("ai_memories",false))return "";
        String norm=cleaned(question).toLowerCase(Locale.ROOT);
        String[] terms=norm.split("[\\s،.؟!]+",-1);
        class Match { LocalStore.Memo m;int score;Match(LocalStore.Memo m,int score){this.m=m;this.score=score;} }
        ArrayList<Match> found=new ArrayList<>();
        for(LocalStore.Memo m:store.allMemos()){
            String raw=cleaned(m.key+" "+m.value).toLowerCase(Locale.ROOT);
            int score=m.key.equals("اسمك")?3:0;
            for(String term:terms)if(term.length()>=4&&raw.contains(term))score+=2;
            if(score>0)found.add(new Match(m,score));
        }
        found.sort((a,b)->Integer.compare(b.score,a.score));
        StringBuilder out=new StringBuilder();
        for(Match entry:found){
            String data=entry.m.key+": "+entry.m.value;
            if(isHighRiskPrivateText(data))continue;
            if(out.length()+data.length()>2600)break;
            out.append(data).append("\n");
            if(out.length()>2200)break;
        }
        return out.toString();
    }
    private void askAi(String question){
        if(aiBusy){answer.setText("ثانية يا عيوني، عم حضّر إجابتي السابقة 💜");return;}
        String url=getPreferences(MODE_PRIVATE).getString("ai_url","");
        String token;
        try{token=secureSettings.getToken();}
        catch(Exception e){showError("تعذر قراءة إعدادات الاتصال. افتح الإعدادات من جديد.");return;}
        if(token.isEmpty()){configureAi();return;}
        JSONArray history=new JSONArray();
        // Send most recent user/assistant exchanges; no hidden file or phone access.
        int start=Math.max(0,recentAiHistory.size()-10);
        for(int i=start;i<recentAiHistory.size();i++){
            try{String[] pair=recentAiHistory.get(i);
                JSONObject j=new JSONObject();j.put("role",pair[0]);j.put("content",pair[1]);history.put(j);
            }catch(Exception ignored){}
        }
        aiBusy=true;answer.setText("غربة عم تفكّر بجوابك… ✨");micStatus.setText("اتصال بالخدمة…");
        String memories=relevantMemories(question);
        String tradeContext=TradeGuide.context(this,question);
        AiClient.ask(url,token,question,memories,tradeContext,history,(result,error)->runOnUiThread(()->{
            aiBusy=false;
            if(isFinishing()||isDestroyed())return;
            if(error!=null){
                answer.setText("ما قدرت أتصل بالذكاء الاصطناعي: "+error+"\nتأكد من إعدادات خدمة غربة واتصال الإنترنت.");
                micStatus.setText("تعذر الاتصال بالخدمة");return;
            }
            addRecent("user",question);addRecent("assistant",result);
            unsavedConversation.add("غربة: "+result.substring(0,Math.min(result.length(),1000)));
            reply(result);
        }));
    }
    private void addRecent(String role,String value){
        recentAiHistory.add(new String[]{role,value.substring(0,Math.min(value.length(),1800))});
        while(recentAiHistory.size()>14)recentAiHistory.remove(0);
    }

    /** A small safeguard against accidentally asking to save a password or one-time code. */
    private boolean isHighRiskPrivateText(String q){
        String x=cleaned(q).toLowerCase(Locale.ROOT);
        return x.contains("كلمه السر") || x.contains("كلمه المرور") || x.contains("الباسورد") ||
            x.contains("رمز التحقق") || x.contains("كود التحقق") || x.contains("password") || x.contains("otp") ||
            x.contains("رقم البطاقه") || x.contains("رقم حسابي البنكي");
    }

    private void showPersonalProfile(){
        List<LocalStore.Memo> data=store.allMemos();
        StringBuilder profile=new StringBuilder("هي المعلومات اللي طلبت مني أحفظها عندي على هالجهاز:\n\n");
        int count=0;
        for(LocalStore.Memo item:data){
            if(item.key.equals("اسمك") || item.key.startsWith("عن شخصيتي") || item.key.startsWith("ملخص حديث") ||
               item.key.startsWith("أرشيف حديث") || item.key.startsWith("يوميات")){
                profile.append("• ").append(item.value,0,Math.min(item.value.length(),400)).append("\n\n");
                count++;
            }
            if(count>=15){profile.append("… في معلومات تانية بذاكرتي.");break;}
        }
        if(count==0)profile.append("لسا ما عندي ملف شخصية محفوظ. خبرني شو بتحب، وشو ما بتحب، وبدك أحفظه ولا لأ.");
        profile.append("\nغربة ما بتعرف عنك شي ما حكيتلها أو ما سمحتلها تحفظه.");
        new AlertDialog.Builder(this).setTitle("ملف شخصيتك 💜")
            .setMessage(profile.toString()).setPositiveButton("تمام",null)
            .setNeutralButton("إدارة الذاكرة",(d,w)->showMemories()).show();
    }

    private String dateStamp(){
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.GERMANY)
            .format(new java.util.Date());
    }
    /** Text diary/poems/events, approved one at a time and encrypted like other memos. */
    private void offerDiarySave(String text){
        if(isHighRiskPrivateText(text)){
            showError("لأمانك ما رح أحفظ كلمات سر أو رموز تحقق في اليوميات.");return;
        }
        String stamp=dateStamp();
        confirm("إضافة إلى يومياتك", "بدك أحفظ هالحدث بتاريخ " + stamp + "؟\n\n" + text,
                ()->{
                    store.putMemo("يوميات " + stamp + " / " + System.nanoTime(), text);
                    reply("حفظت هالموقف بدفتر يومياتك بتاريخ " + stamp + "، بعد موافقتك 💜");
                });
    }
    private void promptDiary(){
        EditText writing=new EditText(this);
        writing.setMinLines(4);writing.setSingleLine(false);
        writing.setHint("اكتب موقف، تاريخ، قصيدة، جملة، أو تفاصيل عن يومك...");
        new AlertDialog.Builder(this).setTitle("اكتبي بدفتر يومياتي")
            .setView(writing).setNegativeButton("إلغاء",null)
            .setPositiveButton("راجعي قبل الحفظ",(d,w)->{
                String note=writing.getText().toString().trim();
                if(note.isEmpty()){showError("ما في نص للحفظ.");return;}
                offerDiarySave(note);
            }).show();
    }
    /** Only app opening can be allowed with one-time consent. No message sending or data access. */
    private void configureQuickAppOpen(){
        boolean enabled=getPreferences(MODE_PRIVATE).getBoolean("quick_app_open",false);
        new AlertDialog.Builder(this).setTitle("فتح التطبيقات بدون تأكيد متكرر")
            .setMessage("الحالة: "+(enabled?"مفعّل":"متوقف")+"\n\n"+
                "إذا فعّلت الخيار، غربة تقدر تفتح يوتيوب أو إنستغرام أو تيك توك بأمر منك بدون ما تسألك كل مرة. " +
                "لكن لا يسمح الخيار بقراءة الرسائل، أو إرسالها، أو إجراء مشتريات، أو حفظ بيانات، أو حذف معلومات.")
            .setPositiveButton(enabled?"أوقف الخيار":"فعّلي فتح التطبيقات",(d,w)->{
                getPreferences(MODE_PRIVATE).edit().putBoolean("quick_app_open",!enabled).apply();
                reply(!enabled?"تمام، صار فتح التطبيقات الثلاثة بأمرك مباشر. أي إجراء حساس لسا بدي موافقتك.":
                        "تمام، رجّعت التأكيد قبل فتح التطبيقات.");
            }).setNegativeButton("إلغاء",null).show();
    }

    private static final String[] MEMORY_STOP_WORDS={"انا","عني","وين","شو","صار","معي","بخصوص","بهذا","في","من","عن","شي","شغله","شغلي","هذا","كمان","قديش","متى","ايمتى","يوم","ذكريني","اذا","انت","انتي","بدي","تذكري","بتتذكري","بتعرفي","فتشي","ذكرياتي","ذاكرتك","دوري","المحفوظ"};
    private String normalizeMemory(String s){
        return cleaned(s).toLowerCase(Locale.ROOT).replace('ى','ي').replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    private boolean memoryStop(String term){
        if(term.length()<3)return true;
        for(String w:MEMORY_STOP_WORDS)if(term.equals(w))return true;
        return false;
    }
    private void promptMemorySearch(){
        EditText search=new EditText(this);search.setHint("مثلاً: مشروع الواجهة / زيارة المستشفى / موعد المدرسة");
        new AlertDialog.Builder(this).setTitle("شو بدك أتذكّر؟")
            .setView(search).setNegativeButton("إلغاء",null)
            .setPositiveButton("فتّشي",(d,w)->searchPersonalMemory(search.getText().toString())).show();
    }
    /** Local keyword retrieval only; never invent dates or imply a language model was used. */
    private void searchPersonalMemory(String query){
        String[] words=normalizeMemory(query).split(" +");
        List<LocalStore.Memo> matches=new ArrayList<>();
        List<Integer> scores=new ArrayList<>();
        for(LocalStore.Memo m:store.allMemos()){
            String haystack=normalizeMemory(m.key+" "+m.value);
            int score=0;
            for(String word:words){
                if(memoryStop(word))continue;
                if(haystack.contains(word))score+=1;
            }
            if(score>0){
                int at=0;
                while(at<scores.size() && scores.get(at)>=score)at++;
                matches.add(at,m);scores.add(at,score);
            }
        }
        if(matches.isEmpty()){
            reply("فتّشت بذاكرتي وما لقيت معلومة محفوظة عن هالموضوع. إذا بتخبرني بالتفاصيل، فيني أسألك إذا بدك تحفظها للمستقبل.");
            return;
        }
        StringBuilder found=new StringBuilder("لقيت هالمعلومات من الأشياء اللي وافقت تحفظها عندي:\n\n");
        for(int i=0;i<Math.min(4,matches.size());i++){
            LocalStore.Memo m=matches.get(i);
            found.append("• ").append(m.key).append(": ")
                 .append(m.value,0,Math.min(m.value.length(),650)).append("\n\n");
        }
        found.append("هاي مقتطفات محفوظة، مو تأكيد إن جميع التفاصيل أو التواريخ دقيقة.");
        new AlertDialog.Builder(this).setTitle("من ذاكرتي 💜")
            .setMessage(found.toString()).setPositiveButton("تمام",null)
            .setNeutralButton("الذاكرة كاملة",(d,w)->showMemories()).show();
        answer.setText("لقيت معلومات عن هالموضوع في ذاكرتي، وفتحتلك النتائج. 💜");
    }

    private void askImportMany(){
        confirm("استيراد اختياري","اختار ملفات نصية من هاتفك بنفسك. رح أعرض أسماء الملفات والمقتطفات وأطلب موافقتك قبل أي حفظ. التطبيق ما يقرأ غير اللي بتختاره.",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            try{startActivityForResult(i,FILE_MULTI_PICK);}catch(Exception e){showError("ما قدرت افتح الملفات.");}
        });
    }

    private void processMultiImport(Intent data){
        ArrayList<Uri> selected=new ArrayList<>();
        if(data.getClipData()!=null){
            for(int i=0;i<Math.min(8,data.getClipData().getItemCount());i++)
                selected.add(data.getClipData().getItemAt(i).getUri());
        }else if(data.getData()!=null)selected.add(data.getData());
        if(selected.isEmpty())return;
        StringBuilder preview=new StringBuilder();
        ArrayList<String> excerpts=new ArrayList<>();
        for(Uri uri:selected){
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)continue;
                InputStreamReader reader=new InputStreamReader(in,StandardCharsets.UTF_8);
                char[] buf=new char[2048];int n;int used=0;
                StringBuilder sb=new StringBuilder();
                while(used<10000 && (n=reader.read(buf,0,Math.min(buf.length,10000-used)))>0){sb.append(buf,0,n);used+=n;}
                String passage=sb.toString().trim();
                if(passage.isEmpty())continue;
                excerpts.add(passage);
                preview.append("ملف ").append(excerpts.size()).append(": ")
                    .append(passage,0,Math.min(passage.length(),180)).append("…\n\n");
            }catch(Exception e){preview.append("تعذر قراءة ملف: ").append(e.getMessage()).append("\n");}
        }
        if(excerpts.isEmpty()){showError("ما في نصوص قابلة للقراءة من الملفات المختارة.");return;}
        new AlertDialog.Builder(this).setTitle("بتوافق أحفظ "+excerpts.size()+" ملف؟")
            .setMessage("سيُحفظ أول 10 آلاف حرف من كل ملف، مشفرًا ومحليًا.\n\n"+preview)
            .setPositiveButton("نعم، احفظي",(d,w)->{
                int added=0;
                for(String passage:excerpts){
                    store.putMemo("نص مستورد "+System.currentTimeMillis()+"-"+added,passage);added++;
                }
                reply("حفظت "+added+" ملفات باختيارك وموافقتك. فيك تفتشي بنصوصها من زر الذكريات.");
            }).setNegativeButton("لا",null).show();
    }

    private void offerSaveConversation(){
        if(proposingMemory)return;
        if(unsavedConversation.isEmpty()){
            reply("خلصنا يا عيوني! هالمرة ما في حديث جديد لأحفظه. 💜");return;
        }
        proposingMemory=true;
        ArrayList<String> copy=new ArrayList<>(unsavedConversation);
        StringBuilder all=new StringBuilder();
        for(String line:copy)all.append("• ").append(line).append("\n");
        String whole=all.toString();
        String preview=whole.substring(0,Math.min(4800,whole.length()));
        String stamp=dateStamp();
        new AlertDialog.Builder(this).setTitle("بدك أحفظ حديثنا؟")
          .setMessage("عدد رسائلك: " + copy.size() + "\nسأحفظ النص كما قلته، بدون استنتاجات عن شخصيتك، في أجزاء مشفّرة على جهازك.\n"+
              "معاينة أول الحديث:\n\n" + preview + (whole.length()>4800?"\n… وباقي الحديث موجود ضمن الجزء الذي سيُحفظ.":""))
          .setPositiveButton("إيه، احفظي",(d,w)->{
              try{
                  int parts=0;
                  for(int begin=0;begin<whole.length();begin+=7500){
                      String segment=whole.substring(begin,Math.min(whole.length(),begin+7500));
                      store.putMemo("أرشيف حديث " + stamp + " جزء " + (++parts) + " / " + System.nanoTime(),segment);
                  }
                  unsavedConversation.clear();
                  reply("تمام، حفظت " + copy.size() + " رسالة من حديثنا بعد موافقتك، بأجزاء مشفّرة على الجهاز 💜");
              }catch(Exception ex){showError("تعذر حفظ الحديث: "+ex.getMessage());}
              proposingMemory=false;
          })
          .setNegativeButton("لا، لا تحفظي",(d,w)->{
              unsavedConversation.clear();recentAiHistory.clear();proposingMemory=false;
              reply("ولا يهمك، ما حفظت هالمحادثة ومسحتها من قائمة الحديث المؤقتة.");
          })
          .setNeutralButton("كمّل الحديث",(d,w)->{proposingMemory=false;})
          .setOnCancelListener(d->proposingMemory=false)
          .show();
    }

    private void offerAppOpen(String normalized){
        final String app;
        final String url;
        String note="";
        if(normalized.contains("يوتيوب") || normalized.contains("youtube")){
            app="يوتيوب";url="https://www.youtube.com/";
        }else if(normalized.contains("تيك توك") || normalized.contains("tiktok")){
            app="تيك توك";url="https://www.tiktok.com/";
            note="\nمحادثات تيك توك الخاصة ما فيني أفتحها مباشرة بشكل موثوق؛ بعد الفتح روح لصندوق الوارد.";
        }else{
            app="إنستغرام";
            url=normalized.contains("محادث") || normalized.contains("رسايل") || normalized.contains("رسائل") ?
                "https://www.instagram.com/direct/inbox/" : "https://www.instagram.com/";
            note="\nرابط المحادثات قد يفتح التطبيق أو المتصفح، بحسب الجهاز وتسجيل الدخول.";
        }
        Runnable open=()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(Exception ex){showError("تعذر فتح "+app+". ربما التطبيق غير مثبت.");}
        };
        if(getPreferences(MODE_PRIVATE).getBoolean("quick_app_open",false))open.run();
        else confirm("فتح "+app,"بدك افتح "+app+" الآن؟ ما رح أقرأ محادثاتك أو أبعت رسائل."+note,open);
    }
    private void webSearch(String q){
        try {startActivity(new Intent(Intent.ACTION_VIEW,
          Uri.parse("https://www.google.com/search?q="+Uri.encode(q))));}
        catch(Exception ex){showError("ما قدرت أفتح المتصفح.");}
    }
    private void confirm(String title,String message,Runnable yes){
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("إلغاء",null)
           .setPositiveButton("أوافق",(d,w)->{
               try{yes.run();}catch(Exception ex){showError("تعذر التنفيذ: "+ex.getMessage());}
           }).show();
    }
    private void showError(String str){new AlertDialog.Builder(this).setTitle("غربة").setMessage(str).setPositiveButton("تمام",null).show();}
    private void showMemories(){
        List<LocalStore.Memo> all=store.allMemos();
        if(all.isEmpty()){showError("لسا ما حفظنا معلومات. قوللي: احفظي ...");return;}
        String[] entries=new String[all.size()];
        for(int i=0;i<all.size();i++){
            LocalStore.Memo m=all.get(i);entries[i]=m.key+"\n"+m.value;
        }
        new AlertDialog.Builder(this).setTitle("ذاكرتي — "+entries.length+" معلومات")
          .setItems(entries,(d,which)->{
              LocalStore.Memo m=all.get(which);
              confirm("حذف من الذاكرة","تحذف هالمعلومة؟\n"+m.value,()->{store.removeMemo(m.id);showMemories();});
          })
          .setNeutralButton("حذف الجميع",(d,w)->confirm("تأكيد نهائي","تمسح كل معلومات الذاكرة من هالجهاز؟",()->{
              store.clearMemos();reply("مسحت معلومات الذاكرة بعد موافقتك.");
          })).setNegativeButton("إغلاق",null).show();
    }
    private void showReminderPicker(String proposed){
        EditText desc=new EditText(this);desc.setSingleLine(false);desc.setText(proposed.equals("تذكير")?"":proposed);
        desc.setHint("ذكّريني بدفع الفاتورة، مثلًا");
        new AlertDialog.Builder(this).setTitle("تذكير جديد").setView(desc)
         .setNegativeButton("إلغاء",null).setPositiveButton("التالي: اليوم والساعة",(d,w)->{
            String body=desc.getText().toString().trim();
            if(body.isEmpty()){showError("اكتب مضمون التذكير أولًا.");return;}
            Calendar cal=Calendar.getInstance();
            new DatePickerDialog(this,(view,y,m,day)->{
                cal.set(Calendar.YEAR,y);cal.set(Calendar.MONTH,m);cal.set(Calendar.DAY_OF_MONTH,day);
                new TimePickerDialog(this,(timeView,h,min)->{
                    cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
                    long at=cal.getTimeInMillis();
                    if(at<System.currentTimeMillis()){showError("لازم تختار وقت بالمستقبل.");return;}
                    String formatted=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,new Locale("ar")).format(cal.getTime());
                    confirm("تأكيد التذكير",body+"\n\nالوقت: "+formatted+"\n\nملاحظة: التنبيه قد يتأخر دقائق حسب النظام.",()->{
                        long id=store.addReminder(body,at);ReminderReceiver.schedule(this,id,at);
                        requestNotificationsIfNeeded();reply("تمام! سجّلتلك التذكير، بس تأكد إن إشعارات غربة مسموحة.");
                    });
                },cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
            },cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();
        }).show();
    }
    private void showReminders(){
        List<LocalStore.Reminder> all=store.allReminders();
        if(all.isEmpty()){showError("ما عندك تذكيرات. اضغط واكتب: ذكّريني ...");return;}
        String[] entries=new String[all.size()];
        DateFormat df=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,new Locale("ar"));
        for(int i=0;i<all.size();i++){
            LocalStore.Reminder r=all.get(i);
            entries[i]=r.body+"\n"+df.format(r.at)+(r.at<System.currentTimeMillis()?" • منتهي":"");
        }
        new AlertDialog.Builder(this).setTitle("تذكيراتي")
          .setItems(entries,(d,which)->{
              LocalStore.Reminder r=all.get(which);
              confirm("حذف التذكير","تلغي التذكير: "+r.body+"؟",()->{
                  ReminderReceiver.cancel(this,r.id);store.removeReminder(r.id);reply("انحذف التذكير.");
              });
          }).setPositiveButton("إضافة تذكير",(d,w)->showReminderPicker("تذكير"))
          .setNegativeButton("إغلاق",null).show();
    }
    private void requestNotificationsIfNeeded(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_REQUEST);
        }
    }

    private void startWakeSession(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(this).setTitle("إذن الميكروفون")
              .setMessage("بعد منح الإذن، اضغط مجددًا على بدء الجلسة. لن يبدأ الاستماع تلقائيًا بمجرد منح الإذن.")
              .setPositiveButton("طلب الإذن",(d,w)->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},WAKE_AUDIO_REQUEST))
              .setNegativeButton("إلغاء",null).show();
            return;
        }
        confirm("استماع ممتد حسب اختيارك", "بدك أشغّل الميكروفون للاستماع لعبارة «ها غربة» دون حد زمني داخل التطبيق، إلى أن توقفه أو يوقفه أندرويد؟\n\n"+
            "• يظهر إشعار دائم أثناء الجلسة وزر لإيقافها فورًا.\n"+
            "• لا يوجد تمييز لصوتك عن أصوات الآخرين بعد.\n"+
            "• مزوّد التعرف الصوتي على الهاتف قد يعالج الصوت عبر الإنترنت.\n"+
            "• قد يتوقف الاستماع بسبب نظام أندرويد أو توفير البطارية.\n"+
            "• لا يوجد تحقق من بصمتك الصوتية، ولا حفظ تلقائي للكلام المسموع.\n"+
            "• خاصية التعرف على الكلام الحالية غير ملائمة للعمل المتواصل رسميًا وقد تستهلك بطارية وبيانات كبيرة.",()->{
                try {
                    Intent i=new Intent(this,WakeSessionService.class).setAction(WakeSessionService.ACTION_START);
                    startForegroundService(i);
                    micStatus.setText("طلبت تشغيل جلسة الاستماع؛ راجع إشعار غربة للتحكم.");
                }catch(Exception ex){showError("تعذر تشغيل جلسة الاستماع: "+ex.getMessage());}
            });
    }
    private void stopWakeSession(){
        Intent i=new Intent(this,WakeSessionService.class).setAction(WakeSessionService.ACTION_STOP);
        try{stopService(i);}catch(Exception ignored){}
        micStatus.setText("تم طلب إيقاف الاستماع. إذا بقي الإشعار، اضغط «أوقفي الاستماع» منه.");
    }

    private void enableStandbyShortcut(){
        confirm("اختصار في الإشعارات","هل تريد إظهار إشعار يسهل عليك فتح غربة والتحدث معها؟\nهذا لا يشغل الميكروفون في الخلفية ولا يراقب صوتك.",()->{
            requestNotificationsIfNeeded();
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(STANDBY_CHANNEL,"اختصار غربة",NotificationManager.IMPORTANCE_LOW));
            PendingIntent pending=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(this,STANDBY_CHANNEL)
              .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("غربة جاهزة 💜")
              .setContentText("اضغط لفتح غربة والتحدث معها")
              .setOngoing(true).setContentIntent(pending).build();
            nm.notify(STANDBY_NOTIFICATION,n);
            reply("صار عندك اختصار بغرفة الإشعارات. بس ميكروفوني مطفأ لحتى تفتحني وتضغط زر الصوت.");
        });
    }
    private void askContact(){
        confirm("اختيار جهة اتصال","رح يفتح منتقي جهات الاتصال. ما رح أشوف إلا الرقم اللي بتختاره، وما رح أحفظه بدون تأكيدك.",()->{
            Intent i=new Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            try{startActivityForResult(i,CONTACT_PICK);}catch(Exception e){showError("تطبيق جهات الاتصال غير متاح.");}
        });
    }
    private void askImport(){
        confirm("اختيار ملف","يمكنك اختيار ملف نصي واحد فقط. رح أعرضلك مقتطف منه، وما بحفظه إلا بعد موافقتك. ما في وصول تلقائي لكل ملفات الهاتف.",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
            try{startActivityForResult(i,FILE_PICK);}catch(Exception e){showError("منتقي الملفات غير متاح.");}
        });
    }
    private void exportData(){
        confirm("تصدير معلوماتك","رح أجهز نسخة نصية واضحة من المعلومات والتذكيرات المحفوظة. أي تطبيق يفتح الملف بعد التصدير قد يستطيع قراءته. هل توافق؟",()->{
            StringBuilder sb=new StringBuilder("غربة — تصدير محلي\n\nالذاكرة:\n");
            for(LocalStore.Memo m:store.allMemos())sb.append(m.key).append(": ").append(m.value).append('\n');
            sb.append("\nالتذكيرات:\n");
            for(LocalStore.Reminder r:store.allReminders())sb.append(r.at).append(" — ").append(r.body).append('\n');
            pendingExport=sb.toString();
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"ghorba_export.txt");
            try{startActivityForResult(i,FILE_EXPORT);}catch(Exception e){showError("تعذر فتح حفظ الملف.");}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==FILE_MULTI_PICK){processMultiImport(data);return;}
        if(data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(request==CONTACT_PICK){
                try(Cursor c=getContentResolver().query(uri,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                 ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,null)){
                    if(c!=null&&c.moveToFirst()){
                        String name=c.getString(0),phone=c.getString(1);
                        confirm("حفظ جهة اتصال","تحفظ محليًا؟\n"+name+"\n"+phone,()->{
                            store.putMemo("اتصال: "+name,phone);reply("حفظت جهة الاتصال اللي اخترتها، وبموافقتك.");
                        });
                    }
                }
            }else if(request==FILE_PICK){
                StringBuilder content=new StringBuilder();
                try(InputStream stream=getContentResolver().openInputStream(uri)){
                    if(stream==null)throw new Exception("لم يُفتح الملف");
                    InputStreamReader rdr=new InputStreamReader(stream,StandardCharsets.UTF_8);
                    char[] chars=new char[2048];int read;int count=0;
                    while((read=rdr.read(chars))!=-1 && count<4096){int take=Math.min(read,4096-count);content.append(chars,0,take);count+=take;}
                }
                String sample=content.toString();
                if(sample.isEmpty()){showError("الملف فارغ.");return;}
                confirm("استيراد مقتطف نصي","رح أحفظ أول 4096 حرف على الأكثر من الملف المختار.\n\n"+
                    sample.substring(0,Math.min(450,sample.length()))+"\n\nتوافق؟",()->{
                        store.putMemo("ملف مختار "+System.currentTimeMillis(),sample);
                        reply("حفظت مقتطف الملف باختيارك وبموافقتك.");
                    });
            }else if(request==FILE_EXPORT){
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new Exception("تعذر الكتابة");
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                }
                pendingExport="";reply("صدّرت نسخة من بياناتك للمكان اللي اخترته.");
            }
        }catch(Exception e){showError("تعذر قراءة/كتابة العنصر: "+e.getMessage());}
    }
    private void showPrivacy(){
        new AlertDialog.Builder(this).setTitle("خصوصيتك وتحكمك")
          .setMessage("• الذاكرة المحلية مشفرة بمفتاح أندرويد على هذا الجهاز.\n"+
             "• لا يوجد نسخ احتياطي أو رفع تلقائي لبياناتك.\n"+
             "• الميكروفون العادي: جلسة قصيرة بعد الضغط. الاستماع الممتد: بموافقة صريحة دون حد داخلي، مع إشعار وإيقاف فوري؛ يمكن للنظام إيقافه.\n"+
             "• لا نتحقق بعد من هوية المتحدث، ويمكن لأشخاص آخرين نطق العبارة.\n"+
             "• خدمة تحويل الصوت إلى نص قد تستخدم خوادم مزود الخدمة.\n"+
             "• اختيار جهة اتصال أو ملف بيدك؛ ما في سحب شامل لبيانات الهاتف.\n"+
             "• لا يتم حفظ أحاديثك أو شخصيتك تلقائيًا؛ يُعرض محتواها عليك لتوافق أو ترفض.\n"+
             "• ملفات الذاكرة: المحتوى مشفر، لكن عناوين السجلات مخزنة بدون تشفير.\n"+
             "• يمكن مراجعة الذاكرة المشفرة أو حذفها في أي وقت.\n"+
             "• أي حفظ أو حذف أو مشاركة يتطلب تأكيدك.\n"+
             "• عند تفعيل الإجابات الحرة، سترسل رسائلك ومقتطفات الذكريات التي توافق عليها إلى خدمة AI الخارجية. يمكن تعطيلها من زر الإعداد.\n"+
             "• لا يوجد وصول للرسائل أو الصور أو كلمات المرور.\n"+
             "• إشعارات التذكير قد تتأخر بحسب قيود توفير الطاقة.\n"+
             "• بعد حذف التطبيق، قد لا يمكن استعادة الذاكرة؛ استخدم التصدير إذا أردت نسخة.")
          .setPositiveButton("مفهوم",null).show();
    }
    @Override protected void onPause(){
        super.onPause();
        if(recognizer!=null){try{recognizer.cancel();}catch(Exception ignored){}micStatus.setText("الميكروفون متوقف");}
    }
    @Override protected void onDestroy(){
        if(recognizer!=null){recognizer.destroy();recognizer=null;}
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        if(store!=null)store.close();
        super.onDestroy();
    }
}
