package org.tangowiz.app

import org.json.JSONArray
import org.json.JSONObject

/** A native recording group; source IDs and unmodified metadata remain available for diagnostics. */
data class Candidate(val id:String,val orchestra:String,val title:String,val singer:String="",val year:String="",val score:Double=0.0,val confidence:Int=0,val speed:Double=1.0,val groupId:String=id,val sourceIds:List<String> =listOf(id),val metadataAlternatives:Map<String,List<String>> =emptyMap(),val rawJson:String="",val presentationContributors:List<String> =emptyList(),val djRequested:Boolean=false,val djAvailable:Boolean=false,val djVersion:String="",val djSong:Double=0.0,val djOrchestra:Double=0.0,val djPair:Double=0.0){
 val djPercent:Double get()=djSong+djOrchestra+djPair
 val rankingScore:Double get()=if(djRequested&&djAvailable)DjPriorTable.adjusted(score,djPercent) else score
 val aliases: Set<String> get()=(sourceIds+id).filter{it.isNotBlank()}.toSet()
 fun sameRecording(other:Candidate)=groupId.isNotBlank()&&groupId==other.groupId||aliases.intersect(other.aliases).isNotEmpty()
 fun labels(field:String):List<String>{val scalar=when(field){"orchestra"->orchestra;"title"->title;"singer"->singer;"year"->year;else->""};return (metadataAlternatives[field].orEmpty()+scalar).map{it.trim()}.filter{it.isNotEmpty()}.distinct()}
 fun display(field:String):String{val values=MetadataLabels.resolve(this,field);return values.joinToString(" / ")+(if(values.size>1)" ?" else "")}
 fun alternativesJson()=JSONObject().also{j->for(field in MetadataLabels.fields)j.put(field,JSONArray(labels(field)))}
 companion object{
  fun fromJson(j:JSONObject):Candidate{
   val alternatives=j.optJSONObject("metadataAlternatives")?:j.optJSONObject("metadata_alternatives")
   val options=MetadataLabels.fields.filter{alternatives?.has(it)==true}.associateWith{field->val a=alternatives?.optJSONArray(field);if(a==null)emptyList() else (0 until a.length()).map{a.optString(it)}.filter{it.isNotBlank()}}
   val ids=j.optJSONArray("sourceIds")?:j.optJSONArray("source_ids");val id=j.optString("id");val sources=if(ids==null)listOf(id) else (0 until ids.length()).map{ids.optString(it)}.filter{it.isNotBlank()}
   val score=j.optDouble("score",0.0);val confidence=if(j.has("confidence_score"))j.optInt("confidence_score") else (j.optDouble("confidence",score)*100).toInt()
   val dj=j.optJSONObject("dj")
   return Candidate(id,j.optString("orchestra"),j.optString("title"),j.optString("singer"),j.optString("year"),score,confidence.coerceIn(0,100),j.optDouble("speed",1.0),j.optString("groupId",j.optString("group_id",id)).ifBlank{id},sources,options,j.toString(),j.optJSONArray("presentationContributors")?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)?.toString()}}?:emptyList(),dj?.optBoolean("requested")?:false,dj?.optBoolean("available")?:false,dj?.optString("dataVersion")?:"",(dj?.optDouble("songPercent",0.0)?:0.0)/100,(dj?.optDouble("orchestraPercent",0.0)?:0.0)/100,(dj?.optDouble("pairPercent",0.0)?:0.0)/100)
  }
  fun distinctRunner(best:Candidate?,candidates:List<Candidate>)=if(best==null)null else candidates.filter{!best.sameRecording(it)}.maxByOrNull{it.score}
 }
}
object MetadataLabels{
 val fields=listOf("orchestra","title","singer","year")
 private fun folded(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
 private fun shortOrchestra(value:String):String {
  val suffix=Regex("(?iu)\\s+y\\s+su\\s+orq(?:uesta)?\\.?\\s*(?:(?:t[ií]pica|tip)\\.?)?\\s*$")
  val prefix=Regex("(?iu)^(?:orquesta(?:\\s+t[ií]pica)?|orq\\.?(?:\\s+tip\\.?)?)\\s+")
  return value.replace(suffix, "").replace(prefix, "").trim().ifEmpty{value}
 }
 fun orchestraKey(candidate:Candidate):String?{val labels=resolve(candidate,"orchestra");if(labels.size!=1)return null;val label=labels.single();val key=folded(label).replace(Regex("^(?:orquesta(?: tipica)?|orq(?: tipica)?) +"),"");return key.takeIf{it.isNotEmpty()&&!label.contains('?')&&it !in setOf("unknown","unidentified","desconocido","desconocida","sin identificar","no identificado","not known")}}
 private fun quality(value:String)=value.count{it.code>127}*4+(if(value.any{it.isLowerCase()}&&value.any{it.isUpperCase()})2 else 0)
 private fun equivalent(values:List<String>,field:String):List<String>{val choices=linkedMapOf<String,String>();for(v in values){val key=if(field=="orchestra")folded(v).replace(Regex("^(?:orquesta(?: tipica)?|orq(?: tipica)?) +"),"") else folded(v);val old=choices[key];if(old==null||quality(v)>quality(old))choices[key]=v};return choices.values.toList()}
 fun resolve(candidate:Candidate,field:String):List<String>{var values=candidate.labels(field)
  if(field=="orchestra")values=values.map(::shortOrchestra)
  if(field=="year")return preciseDates(values)
  if(field=="title"){
   val creditWords=(candidate.labels("orchestra")+candidate.labels("singer")).flatMap{folded(it).split(' ')}.filter{it.isNotBlank()}.toSet()
   values=values.map{title->val parts=Regex("^(.+?)\\s+[-–—|]\\s+(.+)$").matchEntire(title);if(parts==null)title else{val base=parts.groupValues[1];val suffix=folded(parts.groupValues[2]).split(' ').filter{it.isNotBlank()};val corroborated=values.firstOrNull{folded(it)==folded(base)};if(corroborated!=null&&suffix.isNotEmpty()&&suffix.all{it in creditWords}&&suffix.any{it !in setOf("orquesta","tipica","orq","con","canta","y")})corroborated else title}}
  }
  return equivalent(values,field)
 }
 private data class DateLabel(val text:String,val from:java.time.LocalDate,val to:java.time.LocalDate)
 private fun dateLabel(value:String):DateLabel?=runCatching{
  val year=Regex("\\d{4}");val iso=Regex("(\\d{4})-(\\d{2})-(\\d{2})");val legacy=Regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})");val range=Regex("(\\d{4})\\s*[-–—/]\\s*(\\d{4})")
  when{year.matches(value)->{val y=value.toInt();DateLabel(value,java.time.LocalDate.of(y,1,1),java.time.LocalDate.of(y,12,31))};iso.matches(value)->{val d=java.time.LocalDate.parse(value);DateLabel(d.toString(),d,d)};legacy.matches(value)->{val p=legacy.matchEntire(value)!!.groupValues;val d=java.time.LocalDate.of(p[3].toInt(),p[2].toInt(),p[1].toInt());DateLabel(d.toString(),d,d)};range.matches(value)->{val p=range.matchEntire(value)!!.groupValues;val first=p[1].toInt();val last=p[2].toInt();require(first<=last);DateLabel("$first–$last",java.time.LocalDate.of(first,1,1),java.time.LocalDate.of(last,12,31))};else->null}
 }.getOrNull()
 /** Keep the most precise compatible claim; genuine differing dates remain alternatives. */
 fun preciseDates(values:List<String>):List<String>{val parsed=values.map{it to dateLabel(it)};return parsed.filter{(_,v)->v==null||parsed.none{(_,other)->other!=null&&other.from>=v.from&&other.to<=v.to&&(other.from>v.from||other.to<v.to)}}.map{(raw,v)->v?.text?:raw}.distinct()}
}
object RecordingHistory{
 private fun djMode(row:JSONObject):String{val dj=row.optJSONObject("dj")?:return "legacy";return "${dj.optBoolean("requested")}|${dj.optBoolean("available")}|${dj.optString("dataVersion")}"}
 fun consecutive(rows:List<JSONObject>):List<JSONObject>{val grouped=mutableListOf<JSONObject>();for(row in rows){val previous=grouped.lastOrNull();if(previous!=null&&previous.optString("session")==row.optString("session")&&djMode(previous)==djMode(row)&&Candidate.fromJson(previous).aliases.intersect(Candidate.fromJson(row).aliases).isNotEmpty()){grouped[grouped.lastIndex]=merge(previous,row)}else grouped.add(JSONObject(row.toString()))};return grouped}
 private fun merge(old:JSONObject,new:JSONObject):JSONObject{val a=Candidate.fromJson(old);val b=Candidate.fromJson(new);return JSONObject(new.toString()).apply{remove("groupId");remove("group_id");remove("runner_up_group")}.put("sourceIds",JSONArray((a.aliases+b.aliases).sorted())).put("metadataAlternatives",JSONObject().also{j->for(field in MetadataLabels.fields){val authoritative=b.metadataAlternatives.containsKey(field)&&b.aliases.containsAll(a.aliases)&&b.labels(field).isNotEmpty();j.put(field,JSONArray(if(authoritative)b.labels(field) else (a.labels(field)+b.labels(field)).distinct()))}}).put("time",old.optString("time",new.optString("time"))).put("last_time",new.optString("time"))}
}

/** Orchestra rows are presentation only: a winning recording's identity never absorbs its competitors. */
object OrchestraPresentation{
 fun candidates(result:JSONObject?):List<Candidate>{val a=result?.optJSONArray("candidates")?:return emptyList();return (0 until a.length()).mapNotNull{a.optJSONObject(it)?.let(Candidate::fromJson)}}
 fun merge(candidates:List<Candidate>):List<Candidate>{
  val rows=mutableListOf<MutableList<Candidate>>();val byOrchestra=linkedMapOf<String,MutableList<Candidate>>()
  val recordings=mutableListOf<Candidate>();for(candidate in candidates.sortedByDescending{it.rankingScore})if(recordings.none{it.sameRecording(candidate)})recordings.add(candidate)
  for(candidate in recordings){val key=MetadataLabels.orchestraKey(candidate);if(key==null){rows.add(mutableListOf(candidate));continue};val row=byOrchestra[key];if(row==null){val added=mutableListOf(candidate);byOrchestra[key]=added;rows.add(added)}else row.add(candidate)}
  return rows.map{members->val winner=members.first();if(members.size==1)winner else{
   val alternatives=MetadataLabels.fields.associateWith{field->members.flatMap{it.labels(field)}.distinct()}
   val provenance=members.flatMap{c->if(c.presentationContributors.isNotEmpty())c.presentationContributors else listOf(JSONObject().put("id",c.id).put("sourceIds",JSONArray(c.sourceIds)).put("metadataAlternatives",c.alternativesJson()).put("orchestra",c.orchestra).put("title",c.title).put("singer",c.singer).put("year",c.year).put("score",c.score).put("speed",c.speed).toString())}.distinct()
   winner.copy(metadataAlternatives=alternatives,presentationContributors=provenance)
  }}
 }
}
