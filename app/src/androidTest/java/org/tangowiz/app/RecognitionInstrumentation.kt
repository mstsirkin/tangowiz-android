package org.tangowiz.app
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
class RecognitionInstrumentation:Instrumentation(){
 private var arguments=Bundle()
 override fun callActivityOnCreate(activity:android.app.Activity,state:Bundle?){super.callActivityOnCreate(activity,state);SetupProbe.created.add(activity)}
 override fun callActivityOnResume(activity:android.app.Activity){super.callActivityOnResume(activity);SetupProbe.resumed=activity}
 override fun onCreate(args:Bundle?){super.onCreate(args);arguments=args?:Bundle();start()}
 override fun onStart(){if(arguments.getString("mode")=="cleanup"){CleanupValidation.run(this);return};if(arguments.getString("mode")=="presentation_replay"){OrchestraPresentationValidation.replay(this);return};if(arguments.getString("mode")=="orchestra_presentation"){OrchestraPresentationValidation.run(this);return};if(arguments.getString("mode")=="export_store"){ExportStoreValidation.run(this);return};if(arguments.getString("mode")=="export_ui"){ExportUiValidation.run(this);return};if(arguments.getString("mode")=="export_reopen"){ExportUiValidation.reopen(this);return};if(arguments.getString("mode")=="upgrade_v2"){ResourceUpgradeValidation.run(this,arguments);return};if(arguments.getString("mode")=="formats"){ResourceCompatibilityValidation.run(this);return};if(arguments.getString("mode")=="grouping"){GroupingValidation.run(this);return};if(arguments.getString("mode")=="setup_prepare"){SetupResumeValidation.run(this,true);return};if(arguments.getString("mode")=="setup_resume"){SetupResumeValidation.run(this,false);return};if(arguments.getString("mode")=="setup"){SetupFlowValidation.run(this);return};if(arguments.getString("mode")=="download_ui"){DownloadUiValidation.run(this);return};if(arguments.getString("mode")=="updates"){UpdatePreferenceValidation.run(this);return};if(arguments.getString("mode")=="bootstrap"){BootstrapValidation.run(this);return};if(arguments.getString("mode")=="resources"){ResourceValidation.run(this);return};if(arguments.getString("mode")=="resident"){ResidentValidation.run(this);return};val report=JSONObject();var handle=0L
 try{
  check(!SignalGate.audible(FloatArray(8000))){"Silence gate failed"};check(!SignalGate.audible(FloatArray(8000){if(it%2==0)1f/32768 else -1f/32768})){"Near-silence gate failed"}
  val ctx=targetContext;val began=SystemClock.elapsedRealtimeNanos()
  val bundle=ResourceStore(ctx).current()?:error("Missing installed resources");report.put("resource_format",bundle.formatVersion);val model=FileInputStream(bundle.model).channel.use{ch->Interpreter(ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,ch.size()),Interpreter.Options().setNumThreads(2))}
  report.put("model_load_ms",(SystemClock.elapsedRealtimeNanos()-began)/1e6)
  handle=NativeEngine.initFile(bundle.catalog.absolutePath);report.put("index_load_ms",(SystemClock.elapsedRealtimeNanos()-began)/1e6)
  val bytes=File(arguments.getString("pcm","/data/local/tmp/event.pcm")).readBytes();val pcm=FloatArray(bytes.size/4);ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm)
  check(SignalGate.audible(pcm)){"Real event fixture incorrectly gated"};report.put("signal_gate_tests","silence and near-silence rejected; real Tanturi accepted")
  val input=ByteBuffer.allocateDirect(8448*4).order(ByteOrder.nativeOrder());val output=ByteBuffer.allocateDirect(128*4).order(ByteOrder.nativeOrder());val rows=JSONArray()
  for(start in 0..pcm.size-8000 step 4000){val t=SystemClock.elapsedRealtimeNanos();val window=pcm.copyOfRange(start,start+8000);check(SignalGate.audible(window)){"Real event window incorrectly gated at $start"};val features=NativeEngine.features(window);input.rewind();input.asFloatBuffer().put(features);output.rewind();model.run(input,output);val embedding=FloatArray(128);output.rewind();output.asFloatBuffer().get(embedding);val inference=SystemClock.elapsedRealtimeNanos();val raw=NativeEngine.search(handle,embedding,1,1f,start/8000f);rows.put(JSONObject().put("offset",start/8000.0).put("fp_ms",(inference-t)/1e6).put("lookup_ms",(SystemClock.elapsedRealtimeNanos()-inference)/1e6).put("result",JSONObject(raw)))}
  val last=rows.getJSONObject(rows.length()-1).getJSONObject("result").getJSONArray("candidates").getJSONObject(0)
  for(index in 0 until rows.length()){
   val result=rows.getJSONObject(index).getJSONObject("result").getJSONArray("candidates").getJSONObject(0);val expected="palazzo-f57cf688973fbd86315e1d87"
   if(bundle.formatVersion==1)check(result.getString("id")==expected){"Unexpected V1 source ${result.getString("id")}"}
   else check(result.getString("id")==expected||Candidate.fromJson(result).aliases.contains(expected)){"Expected recording absent from V2 winning group"}
   val orchestra=Candidate.fromJson(result).display("orchestra");val key=java.text.Normalizer.normalize(orchestra,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT).replace(Regex("^orquesta(?: tipica)? +"),"")
   check(key=="ricardo tanturi"){"Unexpected orchestra $orchestra"}
  }
  Store(ctx).result(Candidate.fromJson(last),null,10.0,"validation-event-95aHsFHdWY")
  report.put("history_entry_created",true).put("rows",rows).put("status","passed_execution");model.close();File(ctx.filesDir,"instrumentation.json").writeText(report.toString(2));finish(0,Bundle().apply{putString("report",report.toString())})
 }catch(e:Throwable){report.put("status","failed").put("error",e.stackTraceToString());File(targetContext.filesDir,"instrumentation.json").writeText(report.toString(2));finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{if(handle!=0L)NativeEngine.close(handle)}}
}
