package org.tangowiz.app
import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.*
import java.io.File
object BootstrapValidation {
 fun run(i:Instrumentation){val ctx=object:ContextWrapper(i.targetContext){override fun getFilesDir()=File(super.getFilesDir(),"real-bootstrap-test").apply{mkdirs()}};val began=System.currentTimeMillis();val states=JSONArray();val lock=Any();var bucket=-1;var lastKind=""
  try{val store=ResourceStore(ctx,progress={s->synchronized(lock){val b=if(s.total>0)(s.received*20/s.total).toInt() else -1;if(s.kind!=lastKind||b>bucket){lastKind=s.kind;bucket=b;states.put(JSONObject().put("kind",s.kind).put("received",s.received).put("total",s.total).put("elapsed_ms",System.currentTimeMillis()-began));File(i.targetContext.filesDir,"bootstrap-progress.json").writeText(states.toString(2))}}})
   store.check(false);check(store.current()==null);val offered=store.offered()!!;val size=offered.archive.bytes;store.acceptOffer();val bundle=store.current()!!;check(bundle.catalog.length()==offered.files.getValue("catalog.bin").bytes&&bundle.model.length()==offered.files.getValue("nmfp.tflite").bytes)
   val report=JSONObject().put("status","passed").put("published_manifest",RESOURCE_MANIFEST_URL).put("archive_bytes",size).put("elapsed_ms",System.currentTimeMillis()-began).put("bundle_version",bundle.version).put("archive_sha256",bundle.archiveHash).put("native_catalog_and_tflite_validated",true).put("states",states)
   File(i.targetContext.filesDir,"bootstrap-validation.json").writeText(report.toString(2));File(ctx.filesDir,"resources-v1").deleteRecursively();i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){val report=JSONObject().put("status","failed").put("error",e.stackTraceToString()).put("states",states);File(i.targetContext.filesDir,"bootstrap-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
