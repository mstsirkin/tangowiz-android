package org.tangowiz.app
import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.*
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean

class ExportService:Service(){
 companion object{private val active=AtomicBoolean(false);fun isBusy()=active.get()}
 private val wakeGuard=Any();@Volatile private var lastStart=0
 private fun releaseWake(){synchronized(wakeGuard){val held=wake;wake=null;if(held?.isHeld==true)held.release()}}
 private val cancelled=AtomicBoolean(false);private var worker:Thread?=null;private var wake:PowerManager.WakeLock?=null
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){super.onCreate();ExportJobs.restore(this);getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("exports","Diagnostic exports",NotificationManager.IMPORTANCE_LOW))}
 private fun notification(s:ExportState,ongoing:Boolean):Notification{val open=PendingIntent.getActivity(this,30,Intent(this,ExportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);val n=Notification.Builder(this,"exports").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("TangoWiz export").setContentText(if(s.busy)if(s.kind=="scanning")"Scanning files…" else "${if(s.total>0)s.copied*100/s.total else 0}%" else if(s.kind=="ready")"ZIP ready — tap to share or save" else s.error).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing);if(ongoing)n.setProgress(100,if(s.total>0)(s.copied*100/s.total).toInt() else 0,s.total<=0);return n.build()}
 private fun update(s:ExportState){ExportJobs.publish(this,s);if(active.get())getSystemService(NotificationManager::class.java).notify(3,notification(s,true))}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  lastStart=startId
  if(intent?.action=="CANCEL"){cancelled.set(true);worker?.interrupt();if(worker==null)stopSelf();return START_NOT_STICKY}
  if(!active.compareAndSet(false,true))return START_NOT_STICKY
  cancelled.set(false);startForeground(3,notification(ExportJobs.state,true));wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TangoWiz:Export").apply{acquire(6*60*60*1000L)}
  worker=Thread({android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
   try{if(intent?.action=="SAVE"){
     val name=intent.getStringExtra("file")?:error("Missing ZIP");val source=ExportJobs.file(this,name)?:error("ZIP is no longer available; export again");val destination=Uri.parse(intent.getStringExtra("destination")?:error("Missing destination"));val total=source.length();var copied=0L;var at=0L
     contentResolver.openOutputStream(destination,"wt").use{raw->checkNotNull(raw){"Destination could not be opened"};val out=BufferedOutputStream(raw,262144);source.inputStream().buffered().use{input->val b=ByteArray(262144);while(true){if(cancelled.get()||Thread.currentThread().isInterrupted)throw InterruptedIOException("Saving cancelled");val n=input.read(b);if(n<0)break;out.write(b,0,n);copied+=n;val now=SystemClock.elapsedRealtime();if(now-at>=250){update(ExportState("saving",copied,total,fileName=name));at=now}}};out.flush()}
     update(ExportState("ready",total,total,fileName=name,error="ZIP saved"))
    }else{
     update(ExportState("scanning"));val file=Store(this).export(progress={p->update(ExportState("preparing",p.copiedBytes,p.totalBytes,p.filesDone,p.totalFiles))},cancelled={cancelled.get()||Thread.currentThread().isInterrupted})
     update(ExportState("ready",file.length(),file.length(),fileName=file.name))
    }
   }catch(e:Exception){val interrupted=cancelled.get()||e is InterruptedIOException||e is InterruptedException;update(ExportJobs.state.copy(kind=if(interrupted)"interrupted" else "error",error=if(interrupted)"Export interrupted. Your recordings are unchanged; retry when ready." else "Export failed: ${e.message?:e.javaClass.simpleName}"))}
   finally{releaseWake();stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager::class.java).notify(3,notification(ExportJobs.state,false));stopSelf(lastStart);active.set(false)}
  },"tango-export").apply{start()};return START_NOT_STICKY
 }
 override fun onTimeout(startId:Int,fgsType:Int){cancelled.set(true);worker?.interrupt();releaseWake();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){cancelled.set(true);worker?.interrupt();releaseWake();super.onDestroy()}
}
