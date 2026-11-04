package com.ghorba.assistant;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;
        try {
          for(LocalStore.Reminder r:new LocalStore(c).allReminders()){
              if(r.at>System.currentTimeMillis())ReminderReceiver.schedule(c,r.id,r.at);
          }
        }catch(Exception ignored){ /* Keystore can be inaccessible until user unlocks. */ }
    }
}
