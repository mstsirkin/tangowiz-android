package org.tangowiz.app

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Optional, catalog-bound offline evidence. No title inference or live silence trimming. */
object EndingReferenceData {
 const val FILE_NAME="musical-endpoints.json"
 const val MAX_BYTES=16*1024*1024
 data class Endpoint(val seconds:Double,val indexedSeconds:Double)
 data class Data(val endpoints:Map<String,Endpoint> = emptyMap(),val families:Map<String,String> = emptyMap()) {
  fun end(source:String,indexed:Double):Double=endpoints[source]?.let{MusicalEndpoint.resolve(indexed,it.seconds,it.indexedSeconds)}?:indexed
  fun candidate(raw:JSONObject):EndingConsensus.Candidate {
   val source=raw.optString("id")
   // Catalog acoustic groups are the fallback. Extra aliases require offline verification.
   val family=families[source]?:raw.optString("groupId",source)
   return EndingConsensus.Candidate(family,source,raw.optDouble("score"),raw.optDouble("referenceSeconds"),end(source,raw.optDouble("referenceEndSeconds")),raw.optDouble("speed",1.0))
  }
 }
 fun decode(bytes:ByteArray,catalogHash:String):Data {
  require(bytes.size in 1..MAX_BYTES)
  val j=JSONObject(String(bytes,Charsets.UTF_8));require(j.getInt("version")==1&&j.getString("catalog_sha256")==catalogHash)
  val endpoints=mutableMapOf<String,Endpoint>();val families=mutableMapOf<String,String>()
  val rows=j.getJSONObject("recordings")
  for(source in rows.keys()){
   require(source.isNotBlank()&&source.length<=1024)
   val row=rows.getJSONObject(source)
   if(row.has("musical_end_seconds")){
    val end=row.getDouble("musical_end_seconds");val indexed=row.getDouble("indexed_end_seconds")
    require(end.isFinite()&&indexed.isFinite()&&end>0&&indexed>=end&&indexed<=86400)
    require(row.getString("audio_sha256").matches(Regex("[0-9a-f]{64}")))
    endpoints[source]=Endpoint(end,indexed)
   }
   if(row.has("family")){val family=row.getString("family");require(family.isNotBlank()&&family.length<=1024);families[source]=family}
  }
  return Data(endpoints.toMap(),families.toMap())
 }
 fun load(bundle:BundleSnapshot):Data=runCatching {
  val files=JSONObject(File(bundle.directory,"verified.json").readText()).getJSONObject("files")
  val spec=files.optJSONObject(FILE_NAME)?:return Data()
  val file=File(bundle.directory,FILE_NAME);val size=spec.getLong("bytes");require(size in 1..MAX_BYTES.toLong()&&file.length()==size)
  val bytes=file.readBytes();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
  require(hash==spec.getString("sha256"));decode(bytes,files.getJSONObject("catalog.bin").getString("sha256"))
 }.getOrDefault(Data())
}
