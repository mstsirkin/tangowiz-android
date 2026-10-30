package org.tangowiz.app
import android.app.Instrumentation
import android.os.Bundle
import org.json.*
import java.io.*
import java.security.MessageDigest
object DjFeatureValidation { fun run(instrumentation:Instrumentation) = with(instrumentation) { try{
 val checks=JSONArray();val work=File(targetContext.cacheDir,"dj-feature-fixture");work.deleteRecursively();work.mkdirs()
 fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 val catalog="TWIDX002fixture".toByteArray();val hash=MessageDigest.getInstance("SHA-256").digest(catalog)
 fun bytes(binding:ByteArray=hash):ByteArray{val b=ByteArrayOutputStream();DataOutputStream(b).use{d->d.writeBytes("TWDJPR01");d.writeInt(1);d.writeInt(1);d.writeFloat(.025f);d.writeFloat(.025f);d.writeFloat(.02f);d.write(binding);d.writeShort(4);d.writeBytes("test");d.writeLong(DjPriorTable.hash("popular"));repeat(3){d.writeByte(255)}};return b.toByteArray()}
 val bundle=BundleSnapshot(work,"fixture","fixture",2);val f=File(work,DjPriorTable.FILE_NAME)
 fun write(b:ByteArray?){if(b==null)f.delete() else f.writeBytes(b);val files=JSONObject().put("catalog.bin",JSONObject().put("bytes",catalog.size).put("sha256",sha(catalog)));if(b!=null)files.put(DjPriorTable.FILE_NAME,JSONObject().put("bytes",b.size).put("sha256",sha(b)));File(work,"verified.json").writeText(JSONObject().put("files",files).toString())}
 write(null);check(DjPopularity.table(bundle)==null);checks.put("missing optional DJ file is unavailable")
 write(bytes());val data=DjPopularity.table(bundle)?:error("Data unavailable");checks.put("valid hash/catalog binding loads")
 val raw=Candidate(id="popular",orchestra="Known",title="Song",score=.5);val other=Candidate(id="unknown",orchestra="Other",title="Other",score=.52)
 val off=DjPopularity.rank(listOf(raw,other),data,false);check(off.first().id=="unknown"&&off.first().score==.52);val on=DjPopularity.rank(listOf(raw,other),data,true);check(on.first().id=="popular");check(on.first().score==.5&&kotlin.math.abs(on.first().rankingScore-.535)<1e-7);checks.put("off preserves acoustics; on ranks by multiplicative percentage")
 val c=on.first();val history=JSONObject().put("id",c.id).put("orchestra",c.orchestra).put("title",c.title).put("session","s").put("score",c.score).put("sourceIds",JSONArray(listOf(c.id))).put("dj",DjPopularity.record(c));val decoded=Candidate.fromJson(history);check(decoded.djRequested&&decoded.djAvailable&&decoded.djVersion=="test"&&kotlin.math.abs(decoded.rankingScore-.535)<1e-7)
 val isolated=object:android.content.ContextWrapper(targetContext){override fun getFilesDir()=work};val store=Store(isolated);store.result(c.copy(confidence=81),null,2.0,"dj-fixture");store.result(c.copy(djRequested=false,djSong=0.0,djOrchestra=0.0,djPair=0.0,confidence=78),null,3.0,"dj-fixture");val saved=store.history();check(saved.size==2&&saved[0].getJSONObject("dj").getBoolean("enabled")&&!saved[1].getJSONObject("dj").getBoolean("enabled"));check(saved[0].getDouble("score")==.5&&kotlin.math.abs(saved[0].getDouble("ranking_score")-.535)<1e-7);checks.put("actual Store history writes preserve DJ and raw score")
 val toggled=JSONObject(history.toString()).put("dj",DjPopularity.record(c.copy(djRequested=false,djSong=0.0,djOrchestra=0.0,djPair=0.0)));check(RecordingHistory.consecutive(listOf(history,toggled)).size==2);checks.put("history preserves scores, data version and on/off boundary")
 write(bytes(ByteArray(32)));check(DjPopularity.table(bundle)==null);checks.put("wrong catalog binding is unavailable")
 write(byteArrayOf(1,2,3));check(DjPopularity.table(bundle)==null);checks.put("invalid DJ data is unavailable")
 // The raw result used by safety guards remains acoustic regardless of boost.
 check(c.score<.65&&c.rankingScore<.65);checks.put("safety guards retain raw acoustic score")
 File(targetContext.filesDir,"dj-feature-validation.json").writeText(JSONObject().put("passed",true).put("checks",checks).toString(2));work.deleteRecursively();finish(0,Bundle().apply{putString("checks",checks.toString())})
 }catch(e:Throwable){finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}}
