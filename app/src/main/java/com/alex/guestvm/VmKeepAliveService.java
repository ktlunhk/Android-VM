package com.alex.guestvm;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

// Keeps the app process (and the QEMU child process) alive while the app is in the background.
public class VmKeepAliveService extends Service {
 private PowerManager.WakeLock wl;
 public static void begin(Context c){
  Intent i=new Intent(c,VmKeepAliveService.class);
  if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
 }
 public static void end(Context c){c.stopService(new Intent(c,VmKeepAliveService.class));}
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent i,int flags,int id){
  AppLog.write("SERVICE onStartCommand");
  String ch="guestvm_running";
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"Guest VM",NotificationManager.IMPORTANCE_LOW));
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,ch):new Notification.Builder(this);
  Notification n=b.setContentTitle("Guest VM running").setContentText("Android guest is booting in QEMU").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build();
  try{
   if(Build.VERSION.SDK_INT>=34)startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
   else startForeground(1,n);
   AppLog.write("SERVICE startForeground OK");
  }catch(Throwable t){AppLog.write("SERVICE startForeground FAILED: "+t);}
  if(wl==null){
   PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
   wl=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"guestvm:vm");
   wl.acquire(4L*60L*60L*1000L);
  }
  return START_NOT_STICKY;
 }
 @Override public void onTaskRemoved(Intent rootIntent){AppLog.write("SERVICE onTaskRemoved (app swiped from recents)");super.onTaskRemoved(rootIntent);}
 @Override public void onDestroy(){
  AppLog.write("SERVICE onDestroy");
  try{if(wl!=null&&wl.isHeld())wl.release();}catch(Exception e){}
  super.onDestroy();
 }
}
