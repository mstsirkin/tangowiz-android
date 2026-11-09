package org.tangowiz.app
import android.app.Instrumentation
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioRecord
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import org.json.JSONArray

object CaptureModeValidation {
 fun run(i:Instrumentation):String {
  val store=Store(i.targetContext);val original=store.phoneCaptureMode
  check(original=="default"){"Fresh install must use Default capture"}
  val activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  val rows=JSONArray()
  fun onMain(action:()->Unit){var failure:Throwable?=null;i.runOnMainSync{try{action()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
  fun spinner(v:View):Spinner? {if(v is Spinner&&v.contentDescription=="Phone capture mode")return v;if(v is ViewGroup)for(n in 0 until v.childCount)spinner(v.getChildAt(n))?.let{return it};return null}
  try{
   for(mode in listOf("camcorder","default")){
    lateinit var dialog:AlertDialog
    onMain{dialog=MainActivity::class.java.getDeclaredMethod("advanced").apply{isAccessible=true}.invoke(activity) as AlertDialog}
    // onShow installs Save's listener through the main looper; emulate a user click after it runs.
    i.waitForIdleSync()
    onMain{
     val capture=spinner(dialog.window!!.decorView)?:error("Capture setting missing")
     check(capture.selectedItemPosition==if(store.phoneCaptureMode=="camcorder")1 else 0)
     capture.setSelection(if(mode=="camcorder")1 else 0);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
     check(Store(i.targetContext).phoneCaptureMode==mode){"Capture mode was not saved"}
    }
    val ar=PhoneCapture.create(store.phoneCaptureMode)
    try{ar.startRecording();check(ar.recordingState==AudioRecord.RECORDSTATE_RECORDING);val pcm=ShortArray(4000);var n=0;while(n<pcm.size){val got=ar.read(pcm,n,pcm.size-n,AudioRecord.READ_BLOCKING);check(got>0);n+=got};check(ar.audioSource==PhoneCapture.source(mode));check(ar.sampleRate==8000&&ar.channelCount==1);rows.put(PhoneCapture.describe(ar).put("mode",mode));ar.stop()}finally{ar.release()}
   }
  }finally{store.phoneCaptureMode=original;i.runOnMainSync{activity.finish()}}
  return rows.toString()
 }
}
