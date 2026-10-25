package org.tangowiz.wear

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/** User-selected display session. Microphone capture remains owned by WatchMicService. */
object DisplaySession {
 const val STOP="org.tangowiz.wear.CLOSE_DISPLAY"
 const val NOTIFICATION=13
 fun enabled(ctx:Context)=ctx.getSharedPreferences("display-mode",0).getBoolean("enabled",false)
 fun enable(ctx:Context){ctx.getSharedPreferences("display-mode",0).edit().putBoolean("enabled",true).apply()}
 fun close(ctx:Context){ctx.getSharedPreferences("display-mode",0).edit().putBoolean("enabled",false).apply();ctx.stopService(Intent(ctx,WatchDisplayService::class.java));WatchCache.changed()}
}

class WatchDisplayService:Service(){
 private val main=Handler(Looper.getMainLooper())
 private var lastText=""
 private var started=false
 private val changed:()->Unit={main.post{if(started)refresh()}}
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){super.onCreate();WatchCache.init(this);getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("watch-display","Recognition display",NotificationManager.IMPORTANCE_LOW));WatchCache.listeners.add(changed)}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==DisplaySession.STOP){DisplaySession.close(this);return START_NOT_STICKY}
  if(!DisplaySession.enabled(this)){stopSelf();return START_NOT_STICKY}
  started=true
  val notification=notification()
  if(Build.VERSION.SDK_INT>=29)startForeground(DisplaySession.NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(DisplaySession.NOTIFICATION,notification)
  return START_NOT_STICKY
 }
 private fun description():String=when{
  !WatchCache.connected->"Phone disconnected · cached"
  !WatchCache.state.optBoolean("listening")->"Identification paused"
  WatchCache.stale()->"Cached result"
  else->listOf(WatchCache.orchestra(),WatchCache.song()).filter{it.isNotBlank()&&it!="—"}.joinToString(" · ").ifBlank{"Listening…"}.take(200)
 }
 private fun notification():Notification{
  lastText=description()
  val open=PendingIntent.getActivity(this,13,Intent(this,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val close=PendingIntent.getService(this,14,Intent(this,WatchDisplayService::class.java).setAction(DisplaySession.STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val builder=NotificationCompat.Builder(this,"watch-display").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TangoWiz").setContentText(lastText).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(open).addAction(0,"Close display",close)
  val status=Status.Builder().addTemplate("#result#").addPart("result",Status.TextPart(lastText)).build()
  OngoingActivity.Builder(this,DisplaySession.NOTIFICATION,builder).setStaticIcon(android.R.drawable.ic_btn_speak_now).setTouchIntent(open).setStatus(status).build().apply(this)
  return builder.build()
 }
 private fun refresh(){if(!DisplaySession.enabled(this)){stopSelf();return};if(description()!=lastText)getSystemService(NotificationManager::class.java).notify(DisplaySession.NOTIFICATION,notification())}
 override fun onDestroy(){started=false;WatchCache.listeners.remove(changed);main.removeCallbacksAndMessages(null);stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager::class.java).cancel(DisplaySession.NOTIFICATION);super.onDestroy()}
}
