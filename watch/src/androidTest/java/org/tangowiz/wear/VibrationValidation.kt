package org.tangowiz.wear

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.util.UUID

object VibrationValidation {
 fun run(i:Instrumentation){
  i.runOnMainSync{WatchCache.init(i.targetContext)};i.waitForIdleSync()
  val original=JSONObject(WatchCache.state.toString())
  var revision=original.optLong("revision")+1000;val report=JSONObject();var failure:Throwable?=null;var activity:android.app.Activity?=null
  fun state(enabled:Boolean,listening:Boolean=true){WatchCache.accept(JSONObject().put("revision",++revision).put("updatedAt",System.currentTimeMillis()).put("watchVibration",enabled).put("watchEndVibration",enabled).put("phoneVibration",!enabled).put("listening",listening).put("microphone","phone"))}
  fun event(pulses:Int)=JSONObject().put("id",UUID.randomUUID().toString()).put("pulses",pulses).put("sentAt",System.currentTimeMillis())
  try{
   state(false);activity=i.startActivitySync(android.content.Intent(i.targetContext,WatchActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync();check(!WatchCache.state.optBoolean("watchVibration")){"Fixture state rejected: ${WatchCache.state}"};check(!WatchVibration.receive(event(1)))
   state(true);val single=event(1);check(WatchVibration.receive(single))
   check(!WatchVibration.receive(single));Thread.sleep(500)
   check(WatchVibration.receive(event(2)));Thread.sleep(800)
   check(!WatchVibration.receive(event(2).put("sentAt",System.currentTimeMillis()-10000)))
   check(!WatchVibration.receive(event(3)))
   state(true,false);check(!WatchVibration.receive(event(1)))
   state(false);check(!WatchVibration.receive(event(2)))
   check(i.targetContext.getSystemService(android.app.NotificationManager::class.java).activeNotifications.none{it.id in 301..302})
   report.put("status","passed").put("independent_watch_toggle",true).put("single_and_double_android_output",true)
    .put("duplicate_stale_invalid_and_paused_rejected",true).put("paired_delivery_tested",false)
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString())}
  finally{
   activity?.let{a->i.runOnMainSync{a.finish()}}
   WatchCache.accept(JSONObject(original.toString()).put("revision",++revision).put("updatedAt",System.currentTimeMillis()))
   File(i.targetContext.filesDir,"vibration-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("vibration",report.toString())})
  }
 }
}
