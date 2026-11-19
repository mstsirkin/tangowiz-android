package org.tangowiz.app
import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONObject
import java.io.File
object CalculatedEndingValidation {
 fun run(i:Instrumentation){try{
  val hash="a".repeat(64)
  val json=JSONObject().put("version",1).put("catalog_sha256",hash).put("recordings",JSONObject().put("source",JSONObject().put("musical_end_seconds",150).put("indexed_end_seconds",200).put("audio_sha256",hash)))
  val data=EndingReferenceData.decode(json.toString().toByteArray(),hash)
  check(data.end("source",200.0)==150.0);check(data.end("source",201.0)==201.0)
  val v2=JSONObject(json.toString()).put("version",2)
  v2.getJSONObject("recordings").getJSONObject("source").put("musical_end_seconds",200.8).put("decoded_end_seconds",201)
  check(EndingReferenceData.decode(v2.toString().toByteArray(),hash).end("source",200.0)==200.8)
  val p=SongEndPredictor()
  for(t in 20..90 step 5){
   val raw=JSONObject().put("id","source").put("score",.9).put("referenceSeconds",100+t-20.0).put("referenceEndSeconds",200).put("speed",1)
   data.observeMatcherReset(p,t.toDouble(),raw,60)
  }
  check(p.prediction()!=null&&p.prediction().endSeconds==90.5)
  check(p.takeReset(90.0)==null&&p.takeReset(90.5)!=null&&p.takeReset(91.0)==null)
  val consensus=EndingConsensus();var unexpectedQueries=0
  for(t in 100..130){
   val raw=JSONObject().put("id","source").put("groupId","family").put("score",.9).put("referenceSeconds",180).put("referenceEndSeconds",200).put("speed",1)
   val c=data.candidate(raw);check(c.end==150.0)
   check(consensus.observe(t.toDouble(),t.toDouble(),60,true,c,null){unexpectedQueries++;c}==null)
  }
  check(p.prediction()==null&&unexpectedQueries==0)
  val actual=File(i.targetContext.filesDir,"calculated-endpoints-under-test.json")
  var count=0
  if(actual.exists()){
   val actualJson=JSONObject(actual.readText());val loaded=EndingReferenceData.decode(actual.readBytes(),actualJson.getString("catalog_sha256"));count=loaded.endpoints.size
   check(count==60561)
  }
  val report=JSONObject().put("status","passed").put("indexed_context_reset_unchanged",true).put("retained_late_vectors_ignored_for_musical_warning",true).put("late_vectors_do_not_trigger_warning_or_recent_query",true).put("wrong_index_binding_falls_back",true).put("actual_endpoints_loaded",count)
  File(i.targetContext.filesDir,"calculated-ending-validation.json").writeText(report.toString(2))
  i.finish(0,Bundle().apply{putString("report",report.toString())})
 }catch(t:Throwable){i.finish(1,Bundle().apply{putString("error",t.stackTraceToString())})}}
}
