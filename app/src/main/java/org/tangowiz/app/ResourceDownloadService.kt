package org.tangowiz.app
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ResourceDownloadService:Service(){
 companion object{private val jobBusy=AtomicBoolean(false);fun isBusy()=jobBusy.get()}
 private val running=AtomicBoolean(false)
 private val control=AtomicReference("")
 private val main=Handler(Looper.getMainLooper())
 private var lastStartId=0
 @Volatile private var resourceStore:ResourceStore?=null
 @Volatile private var worker:Thread?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("resources","Resource downloads",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(s:ResourceState):Notification {val open=PendingIntent.getActivity(this,20,Intent(this,SetupActivity::class.java).putExtra(SetupActivity.UPDATE,true).putExtra(SetupActivity.PROGRESS,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);val builder=Notification.Builder(this,"resources").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("TangoWiz resources").setContentText(s.message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);if(s.total>0)builder.setProgress(100,(s.received*100/s.total).toInt(),false) else builder.setProgress(0,0,true);return builder.build()}
 private fun requestControl(action:String){
  control.updateAndGet{if(it=="CANCEL"||action=="CANCEL")"CANCEL" else "PAUSE"}
  val state=ResourceProgress.current
  ResourceProgress.publish(state.copy(kind=if(control.get()=="CANCEL")"cancelling" else "pausing",message=if(control.get()=="CANCEL")"Cancelling download…" else "Pausing download…"))
  resourceStore?.cancel()
 }
 override fun onTimeout(startId:Int,fgsType:Int){requestControl("PAUSE");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){if(running.get()){control.compareAndSet("","PAUSE");resourceStore?.cancel()};super.onDestroy()}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  lastStartId=startId
  val action=intent?.action?:"CHECK"
  if(running.get()){
   if(action=="PAUSE"||action=="CANCEL")requestControl(action)
   return START_NOT_STICKY
  }
  startForeground(2,notification(ResourceProgress.current))
  if(!jobBusy.compareAndSet(false,true)){ResourceProgress.publish(ResourceState("retry","Previous download is stopping; retry shortly"));stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return START_NOT_STICKY}
  running.set(true);control.set(if(action=="CANCEL"||action=="PAUSE")action else "")
  worker=Thread({
   var store:ResourceStore?=null;var activated=false;var terminal:ResourceState?=null
   try{
    store=ResourceStore(this,progress={s->if(control.get().isEmpty()){ResourceProgress.publish(s);if(running.get())getSystemService(NotificationManager::class.java).notify(2,notification(s))}})
    resourceStore=store
    if(control.get().isNotEmpty())store.cancel()
    activated=when(action){
     "CANCEL","PAUSE"->false
     "RESUME"->if(store.hasPending())store.resume() else store.check(true)
     "ACCEPT"->store.acceptOffer()
     else->store.check(intent?.getBooleanExtra("download",true)?:true)
    }
    if(activated&&LiveState.current.resident)startService(Intent(this,RecognitionService::class.java).setAction("RELOAD"))
    if(activated)store.cleanupUnused()
   }catch(e:AppUpdateRequired){terminal=ResourceState("update_app",e.message?:"Update the app")}
   catch(e:Exception){terminal=ResourceState("retry",e.message?:"Download interrupted; resume to retry")}
   finally{
    try{
     if(control.get()=="CANCEL"){store?.discardPartialDownloads();terminal=if(activated)ResourceState("ready","Resources ready") else ResourceState("cancelled","Download cancelled; partial files removed")}
     else if(control.get()=="PAUSE")terminal=if(activated)ResourceState("ready","Resources ready") else ResourceProgress.current.copy(kind="paused",message="Download paused")
    }catch(e:Exception){terminal=ResourceState("retry",e.message?:"Could not remove partial download")}
    val ending=terminal
    main.post{resourceStore=null;worker=null;running.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(lastStartId);jobBusy.set(false);if(ending!=null)ResourceProgress.publish(ending)}
   }
  },"tango-resource-download").apply{start()}
  return START_NOT_STICKY
 }
}
