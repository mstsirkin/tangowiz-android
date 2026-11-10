package org.tangowiz.app

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Optional, verified data file in the same immutable resource generation as the index. */
object CatalogKeywords {
 data class Hint(val text:String,val append:Boolean){fun json()=JSONObject().put("text",text).put("placement",if(append)"append" else "in_name")}
 private var cachedDirectory=""
 private var cached=emptyMap<String,Hint>()
 private fun key(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
 internal fun findHint(table:Map<String,Hint>,name:String):Hint? {
  table[key(name)]?.let{return it}
  val suffix=Regex("(?iu)\\s+y\\s+su\\s+orq(?:uesta)?\\.?\\s*(?:(?:t[ií]pica|tip)\\.?)?\\s*$")
  val prefix=Regex("(?iu)^(?:orquesta(?:\\s+t[ií]pica)?|orq\\.?(?:\\s+t[ií]p(?:ica)?\\.?)?)\\s+")
  val noSuffix=name.replace(suffix, "").trim()
  table[key(noSuffix)]?.let{return it}
  val stripped=noSuffix.replace(prefix, "").trim()
  table[key(stripped)]?.let{return it}
  if(stripped!=noSuffix){val noDe=stripped.replace(Regex("(?iu)^de\\s+"), "").trim();if(noDe!=stripped)table[key(noDe)]?.let{return it}}
  return null
 }
 @Synchronized fun hint(bundle:BundleSnapshot?,name:String,displayName:String=name):Hint?{
  if(bundle==null||listOf(name,displayName).any{it.contains('/')||it.contains('?')||it.contains('|')})return null
  val file=File(bundle.directory,"orchestra-keywords.json")
  val cacheKey="${bundle.directory.absolutePath}|${file.length()}|${file.lastModified()}"
  if(cacheKey!=cachedDirectory){cached=read(bundle);cachedDirectory=cacheKey}
  return findHint(cached,name).takeIf{it!=null} ?: findHint(cached,displayName)
 }
 fun read(bundle:BundleSnapshot):Map<String,Hint> = runCatching{
  val spec=JSONObject(File(bundle.directory,"verified.json").readText()).optJSONObject("files")?.optJSONObject("orchestra-keywords.json")?:return emptyMap()
  val file=File(bundle.directory,"orchestra-keywords.json");val size=spec.getLong("bytes");require(size in 1..262144&&file.length()==size)
  val bytes=file.readBytes();val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)};require(sha==spec.getString("sha256"));decode(bytes)
 }.getOrDefault(emptyMap())
 fun decode(bytes:ByteArray):Map<String,Hint>{
  require(bytes.size in 1..262144);val table=JSONObject(String(bytes,Charsets.UTF_8));require(table.getInt("version")==1)
  val rows=table.getJSONArray("names");require(rows.length()<=5000);val result=mutableMapOf<String,Hint>()
  for(i in 0 until rows.length()){val row=rows.getJSONArray(i);val name=row.getString(0);val text=row.getString(1);require(name.length in 1..500&&text.length in 1..100);val hint=Hint(text,row.getBoolean(2));val k=key(name);val old=result[k];require(old==null||key(old.text)==key(text)&&old.append==hint.append);result[k]=hint}
  return result.toMap()
 }
}
