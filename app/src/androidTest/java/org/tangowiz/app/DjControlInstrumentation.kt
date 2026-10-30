package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
/** Isolated fake resources for controls only; no inference or microphone. */
object DjControlValidation { fun run(instrumentation:Instrumentation) = with(instrumentation) { val ctx=targetContext;val root=File(ctx.filesDir,"resources-v1");root.mkdirs();val pointer=File(root,"active.json");val backup=pointer.takeIf{it.exists()}?.readBytes();val dir=File(root,"bundle-${"0".repeat(64)}-00000000-0000-0000-0000-000000000001");var activity:MainActivity?=null
 try{
  dir.mkdirs();fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
  val catalog="TWIDX002fixture".toByteArray();val model=byteArrayOf(1);val b=ByteArrayOutputStream();DataOutputStream(b).use{d->d.writeBytes("TWDJPR01");d.writeInt(1);d.writeInt(0);d.writeFloat(.025f);d.writeFloat(.025f);d.writeFloat(.02f);d.write(MessageDigest.getInstance("SHA-256").digest(catalog));d.writeShort(4);d.writeBytes("test")};val data=b.toByteArray()
  val files=JSONObject();for((name,bytes) in mapOf("catalog.bin" to catalog,"nmfp.tflite" to model,DjPriorTable.FILE_NAME to data)){File(dir,name).writeBytes(bytes);files.put(name,JSONObject().put("bytes",bytes.size).put("sha256",sha(bytes)))}
  val manifest=JSONObject().put("formatVersion",2).put("bundleVersion","dj-controls-test").put("archive",JSONObject().put("url","https://test.invalid/library.zip").put("bytes",1).put("sha256","0".repeat(64))).put("files",files);File(dir,"verified.json").writeText(manifest.toString());pointer.writeText(JSONObject(manifest.toString()).put("directory",dir.name).toString());val store=Store(ctx);val oldDj=store.dj;store.dj=false;PhoneWatch.init(ctx)
  activity=startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity;waitForIdleSync();val button=activity!!.findViewById<Button>(104);check(button.text=="DJ"&&button.isEnabled&&!button.isSelected)
  runOnMainSync{button.performClick()};val end=System.currentTimeMillis()+5000;while(!store.dj&&System.currentTimeMillis()<end)Thread.sleep(50);waitForIdleSync();check(store.dj&&PhoneWatch.state().optBoolean("djEnabled"));check(button.isSelected)
  // Exercise the same revisioned command handler used by a watch, without hardware pairing.
  fun request(rev:Long,enabled:Boolean):String{val id=UUID.randomUUID().toString();val req=JSONObject().put("protocol",1).put("supportedProtocols",JSONArray(listOf(1))).put("capabilities",JSONArray(listOf("dj-popularity-v1"))).put("requestId",id).put("kind","set").put("expectedRevision",rev).put("djEnabled",enabled);PhoneWatch.command(ctx,"validation-peer",req.toString().toByteArray());return id}
  val stale=PhoneWatch.state().getLong("revision");val id=request(stale,false);val stop=System.currentTimeMillis()+5000;while(store.dj&&System.currentTimeMillis()<stop)Thread.sleep(50);check(!store.dj);val reject=request(stale,true);Thread.sleep(500);check(!store.dj);val handled=JSONObject(ctx.getSharedPreferences("watch-link",0).getString("handled","{}")!!);check(handled.getJSONObject(id).getJSONObject("ack").getBoolean("ok"));check(!handled.getJSONObject(reject).getJSONObject("ack").getBoolean("ok"))
  val settings=File(store.root,"settings.jsonl").readText();check(settings.contains("dj_setting_changed"));check(settings.contains("\"djRequested\":true")&&settings.contains("\"djRequested\":false"))
  File(dir,DjPriorTable.FILE_NAME).delete();runOnMainSync{LiveState.publish(LiveState.current)};waitForIdleSync();check(!button.isEnabled);check(button.contentDescription.toString().contains("unavailable"));check(!PhoneWatch.state().getBoolean("djAvailable"));store.dj=oldDj
  runOnMainSync{activity!!.finish()};waitForIdleSync();if(backup==null)pointer.delete() else pointer.writeBytes(backup);dir.deleteRecursively();finish(0,Bundle().apply{putString("checks","phone DJ on/off, watch revisioned command, stale command rejection, setting logs, absent data disabled; fixture restored")})
 }catch(e:Throwable){runOnMainSync{activity?.finish()};if(backup==null)pointer.delete() else pointer.writeBytes(backup);dir.deleteRecursively();finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}}
