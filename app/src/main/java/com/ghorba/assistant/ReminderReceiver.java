package com.ghorba.assistant;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL="ghorba_reminders";
    public static void ensureChannel(Context c){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"تذكيرات غربة",NotificationManager.IMPORTANCE_HIGH));
    }
    public static void schedule(Context c,long id,long when){
        Intent i=new Intent(c,ReminderReceiver.class).putExtra("rid",id);
        PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        // Inexact by design; no intrusive exact-alarm permission required.
        a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }
    public static void cancel(Context c,long id){
        Intent i=new Intent(c,ReminderReceiver.class).putExtra("rid",id);
        PendingIntent pi=PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();}
    }
    @Override public void onReceive(Context c,Intent intent){
        long id=intent.getLongExtra("rid",-1);
        LocalStore store=new LocalStore(c);
        LocalStore.Reminder item=store.getReminder(id);
        if(item==null)return;
        ensureChannel(c);
        Intent open=new Intent(c,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(c,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL)
          .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
          .setContentTitle("غربة تذكّرك 💜")
          .setContentText(item.body)
          .setStyle(new Notification.BigTextStyle().bigText(item.body))
          .setAutoCancel(true)
          .setContentIntent(pi).build();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)id+10000,n);
    }
}
