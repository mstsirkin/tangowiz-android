package org.tangowiz.app
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
/** Ranking/display only: acoustic scores, end prediction and fallback guards stay raw. */
object DjPopularity {
 private var cacheKey="";private var cached:DjPriorTable?=null
 @Synchronized fun table(bundle:BundleSnapshot?):DjPriorTable?{
  if(bundle==null)return null
  val file=File(bundle.directory,DjPriorTable.FILE_NAME);val verified=File(bundle.directory,"verified.json");val key="${bundle.directory.absolutePath}|${file.length()}|${file.lastModified()}|${verified.length()}|${verified.lastModified()}"
  if(key!=cacheKey){cached=runCatching{
   val files=JSONObject(verified.readText()).getJSONObject("files");val spec=files.optJSONObject(DjPriorTable.FILE_NAME)?:return@runCatching null;require(file.length()==spec.getLong("bytes")&&file.length() in 62..4194304);val bytes=file.readBytes();require(hash(bytes)==spec.getString("sha256"));val data=DjPriorTable.decode(bytes);require(data.catalogHash.joinToString(""){"%02x".format(it)}==files.getJSONObject("catalog.bin").getString("sha256"));data
  }.getOrNull();cacheKey=key};return cached
 }
 private fun hash(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
 fun rank(candidates:List<Candidate>,data:DjPriorTable?,requested:Boolean):List<Candidate> = candidates.map { c->
  val b=if(requested&&data!=null)data.bonuses(c.id) else doubleArrayOf(0.0,0.0,0.0)
  c.copy(djRequested=requested,djAvailable=data!=null,djVersion=data?.version?:"",djSong=b[0],djOrchestra=b[1],djPair=b[2])
 }.sortedByDescending{it.rankingScore}
 fun record(c:Candidate)=JSONObject().put("requested",c.djRequested).put("available",c.djAvailable).put("enabled",c.djRequested&&c.djAvailable).put("applied",c.djPercent>0).put("dataVersion",c.djVersion).put("method","relative_percentage").put("songPercent",c.djSong*100).put("orchestraPercent",c.djOrchestra*100).put("pairPercent",c.djPair*100).put("totalPercent",c.djPercent*100).put("acousticScore",c.score).put("adjustedScore",c.rankingScore)
}
