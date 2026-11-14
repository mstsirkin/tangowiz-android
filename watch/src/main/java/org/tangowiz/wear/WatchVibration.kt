package org.tangowiz.wear

import org.json.JSONObject
import org.tangowiz.link.VibrationPattern

/** Ephemeral messages only: reconnecting must never replay an old ending signal. */
object WatchVibration {
 private val seen=LinkedHashSet<String>()
 @Synchronized fun receive(value:JSONObject):Boolean{
  if(!WatchCache.protocolCompatible||!WatchCache.state.optBoolean("watchVibration")||!WatchCache.state.optBoolean("listening"))return false
  val id=value.optString("id");val pulses=value.optInt("pulses")
  val age=System.currentTimeMillis()-value.optLong("sentAt")
  if(id.length !in 1..100||pulses !in 1..2||age !in -3000L..3000L||!seen.add(id))return false
  while(seen.size>64)seen.remove(seen.first())
  VibrationPattern.play(WatchCache.context,pulses)
  return true
 }
}
