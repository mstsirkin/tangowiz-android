package org.tangowiz.app

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Runs the production watch command handler on an idle emulator, without paired transport. */
object WatchUpdateControlValidation {
 fun run(i:Instrumentation) {
  val ctx=i.targetContext;val store=Store(ctx);PhoneWatch.init(ctx)
  val interval=store.watchUpdateInterval;val threshold=store.watchImmediatePercent
  val prefs=ctx.getSharedPreferences("watch-link",0)
  val report=JSONObject().put("production_command_handler",true).put("paired_transport",false)
  var failure:Throwable?=null
  fun handled()=JSONObject(prefs.getString("handled","{}")!!)
  fun await(id:String):JSONObject {
   val end=SystemClock.elapsedRealtime()+10000
   while(!handled().has(id)){check(SystemClock.elapsedRealtime()<end){"Watch command was not acknowledged"};SystemClock.sleep(50)}
   return handled().getJSONObject(id).getJSONObject("ack")
  }
  fun request(percent:Int,ms:Long,revision:Long=PhoneWatch.state().getLong("revision")):JSONObject =
   JSONObject().put("requestId",UUID.randomUUID().toString()).put("protocol",1).put("kind","set")
    .put("expectedRevision",revision).put("immediateUpdatePercent",percent).put("updateIntervalMs",ms)
  fun send(r:JSONObject):JSONObject {PhoneWatch.command(ctx,"emulator-validation",r.toString().toByteArray());return await(r.getString("requestId"))}
  try {
   check(!LiveState.current.listening){"Refusing to change settings while listening"}
   val before=PhoneWatch.state().getLong("revision")
   val first=request(25,2000,before);val ack=send(first)
   check(ack.getBoolean("ok"))
   check(Store(ctx).watchImmediatePercent==25&&Store(ctx).watchUpdateInterval==2000L)
   check(ack.getJSONObject("state").getInt("immediateUpdatePercent")==25)
   val current=PhoneWatch.state().getLong("revision")
   check(current>before)
   send(first);SystemClock.sleep(200)
   check(PhoneWatch.state().getLong("revision")==current){"Duplicate command changed settings revision"}
   check(!send(request(90,60000,before)).getBoolean("ok"))
   check(store.watchImmediatePercent==25&&store.watchUpdateInterval==2000L)
   check(!send(request(101,60000)).getBoolean("ok"))
   check(store.watchImmediatePercent==25&&store.watchUpdateInterval==2000L){"Invalid command partially changed settings"}
   check(send(request(0,10000)).getBoolean("ok"))
   check(PhoneWatch.state().getInt("immediateUpdatePercent")==0)
   check(Store(ctx).watchImmediatePercent==0&&Store(ctx).watchUpdateInterval==10000L)
   report.put("persistence_and_state_roundtrip",true).put("duplicate_and_stale_commands",true)
    .put("invalid_command_atomicity",true).put("any_result_threshold",true).put("status","passed")
  }catch(e:Throwable){failure=e;report.put("status","failed").put("error",e.stackTraceToString())}
  finally {
   store.watchUpdateInterval=interval;store.watchImmediatePercent=threshold
   File(ctx.filesDir,"watch-update-phone-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("watch_update_report",report.toString())})
  }
 }
}
