package org.tangowiz.wear

import android.app.AlertDialog
import android.content.Context
import org.json.JSONObject

/** The phone persists the values and acknowledges them back into the watch cache. */
object WatchUpdatePreferences {
 private val intervals=longArrayOf(2000,5000,10000,30000,60000)
 private val thresholds=intArrayOf(0,25,50,70,90,100)
 fun showInterval(context:Context,send:(JSONObject)->Unit={WatchLink.set(it)}):AlertDialog {
  val current=WatchCache.state.optLong("updateIntervalMs",5000)
  return AlertDialog.Builder(context).setTitle("Result update interval")
   .setSingleChoiceItems(intervals.map{"${it/1000} seconds"}.toTypedArray(),intervals.indexOf(current)){dialog,index->
    send(JSONObject().put("updateIntervalMs",intervals[index]));dialog.dismiss()
   }.setNegativeButton("Cancel",null).show()
 }
 fun showConfidence(context:Context,send:(JSONObject)->Unit={WatchLink.set(it)}):AlertDialog {
  val current=WatchCache.state.optInt("immediateUpdatePercent",70)
  return AlertDialog.Builder(context).setTitle("Immediate update confidence")
   .setSingleChoiceItems(thresholds.map{if(it==0)"Any result" else "$it%"}.toTypedArray(),thresholds.indexOf(current)){dialog,index->
    send(JSONObject().put("immediateUpdatePercent",thresholds[index]));dialog.dismiss()
   }.setNegativeButton("Cancel",null).show()
 }
}
