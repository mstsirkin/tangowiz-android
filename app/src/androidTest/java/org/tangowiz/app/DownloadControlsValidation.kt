package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import org.json.*
import java.io.File
import java.util.UUID

object DownloadControlsValidation{
 fun run(i:Instrumentation){val ctx=i.targetContext;val root=ResourceStore(ctx).root;val backup=File(ctx.filesDir,"resources-controls-backup");var activity:SetupActivity?=null;var moved=false;val checks=JSONArray();var resultCode=0;var result=Bundle()
  fun await(test:()->Boolean){val limit=SystemClock.elapsedRealtime()+45000;while(!test()){check(SystemClock.elapsedRealtime()<limit){"Timed out: ${ResourceProgress.current}"};SystemClock.sleep(50)}}
  fun button(name:String)=SetupActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(activity) as Button
  try{
   check(!ResourceDownloadService.isBusy()&&!LiveState.current.resident);check(!backup.exists());check(root.renameTo(backup));moved=true;check(root.mkdirs())
   val oldDir=File(root,"bundle-${"a".repeat(64)}-${UUID.randomUUID()}");oldDir.mkdirs();File(oldDir,"catalog.bin").writeText("KEEP");File(oldDir,"nmfp.tflite").writeText("KEEP");File(oldDir,"verified.json").writeText("{}")
   val old=JSONObject().put("formatVersion",1).put("bundleVersion","control-test-old").put("directory",oldDir.name).put("archive",JSONObject().put("url","https://test.invalid/old").put("bytes",1).put("sha256","a".repeat(64))).put("files",JSONObject().put("catalog.bin",JSONObject().put("bytes",4).put("sha256","a".repeat(64))).put("nmfp.tflite",JSONObject().put("bytes",4).put("sha256","a".repeat(64))))
   File(root,"active.json").writeText(old.toString());val activeBefore=File(root,"active.json").readBytes()
   val manifest=HttpResourceTransport().open(RESOURCE_MANIFEST_URL,emptyMap()).use{check(it.code==200);ResourceManifest.parse(JSONObject(it.body.bufferedReader().readText()))};File(root,"pending.json").writeText(manifest.json.toString())
   ResourceProgress.publish(ResourceState());activity=i.startActivitySync(Intent(ctx,SetupActivity::class.java).putExtra(SetupActivity.UPDATE,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SetupActivity
   await{ResourceProgress.current.kind=="downloading"&&ResourceProgress.current.received>=131072}
   i.runOnMainSync{check(button("pauseButton").isShown&&button("pauseButton").isEnabled);button("pauseButton").performClick()}
   await{ResourceProgress.current.kind=="paused"&&!ResourceDownloadService.isBusy()};i.waitForIdleSync()
   val part=File(root,"${manifest.archive.sha256}.part");val cp=File(root,"${manifest.archive.sha256}.ranges.json");check(part.exists()&&cp.exists()&&File(root,"pending.json").exists())
   val done=JSONObject(cp.readText()).getJSONArray("done");val saved=(0..3).sumOf{done.getLong(it)};check(saved>0);check(File(root,"active.json").readBytes().contentEquals(activeBefore));checks.put("Pause safely saves received bytes and retains the installed library")
   i.runOnMainSync{button("appUpdateButton").performClick()};await{button("appUpdateButton").isEnabled};check(button("appUpdateButton").text.toString().let{it.contains("up to date")||it.startsWith("Download APK")});checks.put("App update check completes independently while the library is paused")
   val shot=i.uiAutomation.takeScreenshot();if(shot!=null){val image=File(ctx.getExternalFilesDir(null),"download-controls-paused.png");image.outputStream().use{shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};shot.recycle()}
   val unused="b".repeat(64);File(root,"$unused.part").writeText("partial");File(root,"$unused.ranges.json.bak").writeText("checkpoint");File(root,"$unused.single").writeText("fallback");File(root,"offer.json.new").writeText("offer");File(root,"staging-$unused").mkdirs();File(root,"staging-$unused/catalog.bin").writeText("partial extraction")
   i.runOnMainSync{check(button("resourceButton").text.toString().startsWith("Resume"));button("resourceButton").performClick()}
   await{ResourceProgress.current.kind=="downloading"&&ResourceProgress.current.received>saved};checks.put("Resume continues beyond the saved checkpoint")
   i.runOnMainSync{check(button("cancelButton").isShown&&button("cancelButton").isEnabled);button("cancelButton").performClick()}
   await{ResourceProgress.current.kind=="cancelled"&&!ResourceDownloadService.isBusy()};i.waitForIdleSync()
   check(!part.exists()&&!cp.exists()&&!File(root,"pending.json").exists());check(root.listFiles()!!.map{it.name}.toSet()==setOf("active.json",oldDir.name));check(File(oldDir,"catalog.bin").readText()=="KEEP");check(File(root,"active.json").readBytes().contentEquals(activeBefore));checks.put("Cancel removes all partials, checkpoints and staging only after writers stop; installed library survives")
   val report=JSONObject().put("status","passed").put("checks",checks).put("bytes_checkpointed_on_pause",saved);File(ctx.filesDir,"download-controls-validation.json").writeText(report.toString(2));result=Bundle().apply{putString("report",report.toString())}
  }catch(e:Throwable){resultCode=1;result=Bundle().apply{putString("error",e.stackTraceToString())}}
  finally{if(ResourceDownloadService.isBusy()){ctx.startForegroundService(Intent(ctx,ResourceDownloadService::class.java).setAction("CANCEL"));runCatching{await{!ResourceDownloadService.isBusy()}}};runCatching{i.runOnMainSync{activity?.finishAndRemoveTask()}};if(moved&&!ResourceDownloadService.isBusy()){root.deleteRecursively();check(backup.renameTo(root))};ResourceProgress.publish(ResourceState())}
  i.finish(resultCode,result)
 }
}
