package org.tangowiz.wear

import org.json.JSONObject
import org.tangowiz.link.VibrationPattern
import java.io.File

/** Ephemeral requests only; receipts say requested, never claim physical sensation. */
object WatchVibration {
 private val seen=LinkedHashSet<String>()
 private fun result(value:JSONObject,reason:String,requested:Boolean=false):Boolean{
  val row=JSONObject().put("id",value.optString("id")).put("pulses",value.optInt("pulses")).put("time_ms",System.currentTimeMillis()).put("visible",WatchHapticSession.visible()).put("requested",requested).put("reason",reason)
  runCatching{File(WatchCache.context.filesDir,"vibration-delivery.jsonl").appendText(row.toString()+"\n")}
  return requested
 }
 @Synchronized fun receive(value:JSONObject):Boolean{
  if(!WatchHapticSession.visible())return result(value,"not_visible")
  if(!WatchCache.protocolCompatible||!org.tangowiz.link.VibrationSettings.enabled(value.optInt("pulses"),WatchCache.state.optBoolean("watchVibration"),WatchCache.state.optBoolean("watchEndVibration"))||!WatchCache.state.optBoolean("listening"))return result(value,"disabled_or_paused")
  val id=value.optString("id");val pulses=value.optInt("pulses")
  val age=System.currentTimeMillis()-value.optLong("sentAt")
  if(id.length !in 1..100||pulses !in 1..2||age !in -3000L..3000L)return result(value,"invalid_or_stale")
  if(!seen.add(id))return result(value,"duplicate")
  while(seen.size>64)seen.remove(seen.first())
  val requested=VibrationPattern.play(WatchCache.context,pulses)
  return result(value,if(requested)"tick_requested" else "weak_haptic_unsupported",requested)
 }
}
