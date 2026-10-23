package org.tangowiz.app
import android.app.*
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

class ResourceDownloadService:Service(){
 companion object{private val jobBusy=AtomicBoolean(false)}
 private val running=AtomicBoolean(false)
 @Volatile private var resourceStore:ResourceStore?=null
 @Volatile private var worker:Thread?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("resources","Resource downloads",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(s:ResourceState):Notification {val open=PendingIntent.getActivity(this,20,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);val builder=Notification.Builder(this,"resources").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("TangoWiz resources").setContentText(s.message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);if(s.total>0)builder.setProgress(100,(s.received*100/s.total).toInt(),false) else builder.setProgress(0,0,true);return builder.build()}
 override fun onTimeout(startId:Int,fgsType:Int){running.set(false);resourceStore?.cancel();worker?.interrupt();ResourceProgress.publish(ResourceState("retry","Download paused by Android; retry to continue"));stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){resourceStore?.cancel();worker?.interrupt();super.onDestroy()}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  startForeground(2,notification(ResourceProgress.current));if(running.get())return START_NOT_STICKY;if(!jobBusy.compareAndSet(false,true)){ResourceProgress.publish(ResourceState("retry","Previous download is stopping; retry shortly"));stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY};running.set(true)
  worker=Thread({try{val store=ResourceStore(this,progress={s->ResourceProgress.publish(s);if(running.get())getSystemService(NotificationManager::class.java).notify(2,notification(s))});resourceStore=store;val activated=when(intent?.action){"RESUME"->if(store.hasPending())store.resume() else store.check(true);"ACCEPT"->store.acceptOffer();else->store.check(intent?.getBooleanExtra("download",true)?:true)}
   if(activated&&LiveState.current.resident)startService(Intent(this,RecognitionService::class.java).setAction("RELOAD"));if(activated)store.cleanupUnused()
  }catch(e:AppUpdateRequired){ResourceProgress.publish(ResourceState("update_app",e.message?:"Update the app"))}catch(e:Exception){ResourceProgress.publish(ResourceState("retry",e.message?:"Download interrupted; retry"))}finally{running.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();jobBusy.set(false)}},"tango-resource-download").apply{start()};return START_NOT_STICKY
 }
}
