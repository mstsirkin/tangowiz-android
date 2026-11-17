package org.tangowiz.app

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject
import org.tangowiz.link.VibrationPattern
import java.io.File
import java.util.UUID

/** Emulator validation of independent settings and the actual Android vibration output. */
object VibrationValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;val store=Store(ctx);PhoneWatch.init(ctx)
  val phone=store.phoneVibration;val watch=store.watchVibration
  val phoneEnd=store.phoneEndVibration;val watchEnd=store.watchEndVibration
  val report=JSONObject();var failure:Throwable?=null;var activity:android.app.Activity?=null
  fun set(p:Boolean,w:Boolean,pe:Boolean=false,we:Boolean=false){
   val id=UUID.randomUUID().toString()
   val request=JSONObject().put("protocol",1).put("kind","set").put("requestId",id)
    .put("expectedRevision",PhoneWatch.state().getLong("revision"))
    .put("phoneVibration",p).put("watchVibration",w).put("phoneEndVibration",pe).put("watchEndVibration",we)
   PhoneWatch.command(ctx,"emulator-validation",request.toString().toByteArray())
   val deadline=android.os.SystemClock.elapsedRealtime()+10000
   while(true){
    val ack=JSONObject(ctx.getSharedPreferences("watch-link",0).getString("handled","{}")!!).optJSONObject(id)?.getJSONObject("ack")
    if(ack!=null){check(ack.getBoolean("ok"));break}
    check(android.os.SystemClock.elapsedRealtime()<deadline);Thread.sleep(50)
   }
   check(Store(ctx).phoneVibration==p&&Store(ctx).watchVibration==w)
   check(Store(ctx).phoneEndVibration==pe&&Store(ctx).watchEndVibration==we)
   check(Store(ctx).phoneVibrationEnabled(1)==p&&Store(ctx).phoneVibrationEnabled(2)==pe)
   check(Store(ctx).watchVibrationEnabled(1)==w&&Store(ctx).watchVibrationEnabled(2)==we)
   check(PhoneWatch.state().getBoolean("phoneVibration")==p&&PhoneWatch.state().getBoolean("watchVibration")==w)
   check(PhoneWatch.state().getBoolean("phoneEndVibration")==pe&&PhoneWatch.state().getBoolean("watchEndVibration")==we)
  }
  try{
   check(!LiveState.current.listening)
   store.phoneVibration=true;store.watchVibration=true
   store.prefs.edit().remove("phone_end_vibration").remove("watch_end_vibration").commit()
   check(Store(ctx).phoneVibration&&Store(ctx).watchVibration)
   check(!Store(ctx).phoneEndVibration&&!Store(ctx).watchEndVibration)
   store.phoneVibration=false;store.watchVibration=false
   check(!Store(ctx).phoneVibration&&!Store(ctx).watchVibration)
   check(!Store(ctx).phoneEndVibration&&!Store(ctx).watchEndVibration)
   check(DisplayLabelKey.equivalent("C. T. V.","ctv"))
   check(DisplayLabelKey.equivalent("María Remedios","MARIAREMEDIOS"))
   check(!DisplayLabelKey.equivalent("Anoche a las 4","Anoche a las cuatro"))
   check(!DisplayLabelKey.equivalent("A-B","A B"))
   check(MetadataLabels.resolve(Candidate("one","Orchestra","C. T. V.",metadataAlternatives=mapOf("title" to listOf("CTV"))),"title").size==1)
   report.put("legacy_setting_migration",true).put("device_display_normalization",true)
   activity=i.startActivitySync(android.content.Intent(ctx,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync()
   for(p in listOf(false,true))for(w in listOf(false,true))for(pe in listOf(false,true))for(we in listOf(false,true))set(p,w,pe,we)
   set(true,true,true,true)
   check(VibrationPattern.timings(1).contentEquals(longArrayOf(0,12)))
   check(VibrationPattern.timings(2).contentEquals(longArrayOf(0,12,100,12)))
   val policy=VibrationSignalPolicy();val end=SongEndPredictor()
   for(t in listOf(80.0,85.0,90.0))end.observe(t,"song",.8,150-(100-t)/1.03-29.5,150.0,60,1.03)
   check(policy.observe(80.0,"song",80,true,null)==VibrationSignalPolicy.Signal.NONE)
   val first=policy.observe(82.0,"song",80,true,null)
   check(first==VibrationSignalPolicy.Signal.IDENTIFIED)
   LiveState.publish(RecognitionState(listening=true,seconds=82.0));PhoneWatch.vibrationSignal(first)
   Thread.sleep(1000)
   check(policy.observe(91.5,"song",80,true,end.prediction())==VibrationSignalPolicy.Signal.NONE)
   val second=policy.observe(92.0,"song",80,true,end.prediction())
   check(second==VibrationSignalPolicy.Signal.ENDING)
   LiveState.publish(RecognitionState(listening=true,seconds=92.0));PhoneWatch.vibrationSignal(second)
   Thread.sleep(1500)
   check(policy.observe(92.5,"song",80,true,end.prediction())==VibrationSignalPolicy.Signal.NONE)
   check(end.takeReset(100.0)!=null)
   check(ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications.none{it.id in 301..302})
   val consensus=EndingConsensus()
   var warning:EndingConsensus.Warning?=null
   for(t in 70..92){
    val c=EndingConsensus.Candidate("song","source",.55,200-(100-t)-14.5,200.0,1.0)
    warning=consensus.observe(t.toDouble(),t.toDouble(),30,true,c,null){EndingConsensus.Candidate("song","recent",.6,0.0,1.0,1.0)}
   }
   check(warning?.end==100.0)
   val endpointJson=JSONObject().put("version",1).put("catalog_sha256","fixture")
    .put("recordings",JSONObject().put("source",JSONObject().put("musical_end_seconds",180).put("indexed_end_seconds",195).put("audio_sha256","a".repeat(64))))
   val endpointBytes=endpointJson.toString().toByteArray()
   check(EndingReferenceData.decode(endpointBytes,"fixture").end("source",195.0)==180.0)
   check(EndingReferenceData.decode(endpointBytes,"fixture").end("other",195.0)==195.0)
   check(runCatching{EndingReferenceData.decode(endpointBytes,"wrong")}.isFailure)
   report.put("consensus_and_catalog_bound_endpoint",true)
   report.put("notification_absent",true)
   report.put("status","passed").put("independent_setting_combinations",16)
    .put("production_android_output_called",true).put("speed_adjusted_eight_second_policy",true)
    .put("repeated_match_suppressed",true).put("physical_vibration_feel_tested",false)
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString())}
  finally{
   activity?.let{a->i.runOnMainSync{a.finish()}}
   store.phoneVibration=phone;store.watchVibration=watch;store.phoneEndVibration=phoneEnd;store.watchEndVibration=watchEnd;LiveState.publish(RecognitionState())
   File(ctx.filesDir,"vibration-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("vibration",report.toString())})
  }
 }
}
