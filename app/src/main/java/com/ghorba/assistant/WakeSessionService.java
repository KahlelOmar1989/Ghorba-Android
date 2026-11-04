package com.ghorba.assistant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Voluntary microphone foreground session without a built-in timer.
 * Not a speaker-identity verifier and not a reliable always-on wake word engine.
 * Recognized words are never written to storage or sent by this app to a server.
 * Android's SpeechRecognizer provider may use the network (user is warned beforehand).
 */
public final class WakeSessionService extends Service {
    public static final String ACTION_START="com.ghorba.assistant.START_WAKE_SESSION";
    public static final String ACTION_STOP="com.ghorba.assistant.STOP_WAKE_SESSION";
    public static final String CHANNEL="ghorba_active_listening";
    public static final int NOTIFICATION_ID=3002;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognition;
    private TextToSpeech speaker;
    private boolean enabled=false;
    private boolean speaking=false;
    private int consecutiveErrors=0;

    private final Runnable retry=()->listen();

    @Override public IBinder onBind(Intent i){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null || ACTION_STOP.equals(intent.getAction())){
            stopSession();return START_NOT_STICKY;
        }
        if(!ACTION_START.equals(intent.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(enabled)return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED ||
                !SpeechRecognizer.isRecognitionAvailable(this)){
            stopSelf();return START_NOT_STICKY;
        }
        try {
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "جلسة استماع غربة",NotificationManager.IMPORTANCE_LOW));
            Notification n=notification("الاستماع مفعّل حتى توقفه أو يوقفه النظام");
            if(Build.VERSION.SDK_INT>=29)
                startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION_ID,n);
        }catch(Exception e){stopSelf();return START_NOT_STICKY;}
        enabled=true;
        try {
            speaker=new TextToSpeech(this,status->{
                if(status==TextToSpeech.SUCCESS && speaker!=null){
                    int r=speaker.setLanguage(Locale.forLanguageTag("ar-SY"));
                    if(r==TextToSpeech.LANG_NOT_SUPPORTED || r==TextToSpeech.LANG_MISSING_DATA)
                        speaker.setLanguage(new Locale("ar"));
                    speaker.setPitch(1.09f);speaker.setSpeechRate(.94f);
                    speaker.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                        @Override public void onStart(String id){}
                        @Override public void onDone(String id){handler.post(()->{
                            speaking=false;scheduleRetry(600);
                        });}
                        @Override public void onError(String id){handler.post(()->{
                            speaking=false;scheduleRetry(600);
                        });}
                    });
                }
            });
        }catch(Exception ignored){}
        scheduleRetry(250);
        return START_NOT_STICKY; // Explicitly started only: never covertly restart from boot or process death.
    }

    private Notification notification(String status){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,71,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,WakeSessionService.class).setAction(ACTION_STOP);
        PendingIntent stopPi=PendingIntent.getService(this,72,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("غربة تسمع عبارة «ها غربة»")
                .setContentText(status)
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_media_pause,"أوقفي الاستماع",stopPi)
                .build();
    }

    private void listen(){
        if(!enabled || speaking)return;
        cleanupRecognizer();
        try {
            recognition=SpeechRecognizer.createSpeechRecognizer(this);
            recognition.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle b){}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rms){}
                @Override public void onBufferReceived(byte[] b){}
                @Override public void onEndOfSpeech(){}
                @Override public void onPartialResults(Bundle b){}
                @Override public void onEvent(int t,Bundle b){}
                @Override public void onError(int error){
                    consecutiveErrors++;
                    if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                       error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY && consecutiveErrors>8){
                        stopSession();return;
                    }
                    scheduleRetry(consecutiveErrors>=5?5000:1600);
                }
                @Override public void onResults(Bundle b){
                    consecutiveErrors=0;
                    ArrayList<String> alternatives=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    boolean wake=false;
                    if(alternatives!=null){
                        for(String heard:alternatives){
                            String cleaned=heard.replace('أ','ا').replace('إ','ا').replace('آ','ا')
                               .replace('ة','ه').replaceAll("[\\u064B-\\u065F]","").trim();
                            if(cleaned.contains("ها غربه")||cleaned.contains("يا غربه")){
                                wake=true;break;
                            }
                        }
                    }
                    if(wake)respond();else scheduleRetry(700);
                }
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-SY");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            recognition.startListening(i);
        }catch(Exception e){scheduleRetry(4000);}
    }

    private void respond(){
        speaking=true;
        cleanupRecognizer();
        String text="هلا يا عيوني! أنا هون. افتح المحادثة واحكيلي شو بدك.";
        try {
            if(speaker!=null && speaker.speak(text,TextToSpeech.QUEUE_FLUSH,null,"wake_reply")>=0){
                handler.postDelayed(()->{if(speaking){speaking=false;scheduleRetry(400);}},4500);
            }else {speaking=false;scheduleRetry(1800);}
        }catch(Exception e){speaking=false;scheduleRetry(1800);}
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(
            NOTIFICATION_ID,notification("سمعت «ها غربة» — اضغط لفتح المحادثة"));
    }

    private void scheduleRetry(long delay){
        handler.removeCallbacks(retry);
        if(enabled && !speaking)handler.postDelayed(retry,delay);
    }
    private void cleanupRecognizer(){
        if(recognition!=null){
            try{recognition.cancel();recognition.destroy();}catch(Exception ignored){}
            recognition=null;
        }
    }
    private void stopSession(){
        enabled=false; speaking=false;
        handler.removeCallbacksAndMessages(null);
        cleanupRecognizer();
        if(speaker!=null){try{speaker.stop();speaker.shutdown();}catch(Exception ignored){}speaker=null;}
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy(){
        enabled=false;
        handler.removeCallbacksAndMessages(null);
        cleanupRecognizer();
        if(speaker!=null){try{speaker.shutdown();}catch(Exception ignored){}speaker=null;}
        super.onDestroy();
    }
}
