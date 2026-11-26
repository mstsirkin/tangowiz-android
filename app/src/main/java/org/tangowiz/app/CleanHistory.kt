package org.tangowiz.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Builds a display timeline from stored observations without running recognition. */
internal object CleanHistory {
 private const val maxGap = 30.0

 data class Observation(val session:String,val at:Double,val score:Double,val origin:Double?,
                        val confirmed:Boolean,val title:String,val orchestra:String,val singer:String,
                        val date:String,val ids:Set<String>)

 private class Play(private val rows:List<Observation>) {
  val members = mutableListOf<Int>()
  val ids = mutableSetOf<String>()
  val origins = mutableListOf<Double>()
  var confirmed = false
   private set
  val first get() = rows[members.first()]
  val last get() = rows[members.last()]
  val span get() = last.at-first.at
  val origin:Double? get() {
   if(origins.isEmpty()) return null
   return (origins[(origins.size-1)/2]+origins[origins.size/2])/2
  }
  fun add(index:Int) {
   val row=rows[index]
   members.add(index)
   ids.addAll(row.ids)
   row.origin?.let {
    val position=origins.binarySearch(it)
    origins.add(if(position>=0)position else -position-1,it)
   }
   confirmed=confirmed||row.confirmed
  }
 }

 private fun sameWork(a:Observation,b:Observation) =
  a.title.isNotEmpty() && a.title==b.title && a.orchestra.isNotEmpty() && a.orchestra==b.orchestra

 private fun compatible(a:String,b:String)=a.isEmpty()||b.isEmpty()||a==b
 private val dateSeparator = Regex("[^0-9a-z]+")
 private fun compatibleDate(a:String,b:String):Boolean {
  val left=a.trim().lowercase(java.util.Locale.ROOT).split(dateSeparator)
  val right=b.trim().lowercase(java.util.Locale.ROOT).split(dateSeparator)
  return a.isBlank()||b.isBlank()||left.zip(right).all{(x,y)->x==y}
 }

 private fun continues(play:Play,row:Observation):Boolean {
  val origin=play.origin
  val distance=if(origin!=null&&row.origin!=null)abs(origin-row.origin) else null
  if(row.ids.any{it in play.ids})return distance==null||distance<=12
  return distance!=null&&distance<=2&&sameWork(play.last,row)&&
   compatible(play.last.singer,row.singer)&&compatibleDate(play.last.date,row.date)
 }

 private fun timeline(rows:List<Observation>,indices:List<Int>,weak:Boolean):List<Play> {
  val active=mutableListOf<Play>()
  val all=mutableListOf<Play>()
  for(index in indices) {
   val row=rows[index]
   val eligible=if(weak)row.score in .55..<.65&&row.origin!=null else row.score>=.65||row.confirmed
   if(row.ids.isEmpty()||!eligible)continue
   active.removeAll{row.at-it.last.at>maxGap}
   val play=active.lastOrNull{continues(it,row)} ?: Play(rows).also{active.add(it);all.add(it)}
   play.add(index)
  }
  val ranked=all.filter{play->
   if(weak) {
    val origin=play.origin ?: return@filter false
    val supporting=play.origins.count{abs(it-origin)<=2}
    play.span>=25&&supporting>=6&&supporting*5>=play.members.size*4
   } else {
    play.confirmed||(play.members.size>=3&&play.span>=10)||
     (play.members.size==2&&play.span>=9&&play.origins.size==2&&abs(play.origins[0]-play.origins[1])<=2)
   }
  }.sortedWith(compareByDescending<Play>{it.confirmed}.thenByDescending{it.members.size})
  val accepted=mutableListOf<Play>()
  for(play in ranked)if(accepted.none{it.first.at<=play.first.at&&it.last.at>=play.last.at})accepted.add(play)
  return accepted.sortedBy{it.first.at}
 }

 fun plays(rows:List<Observation>):List<List<Int>> {
  val sessions=rows.indices.groupBy{rows[it].session}
  return sessions.values.flatMap{session->
   val indices=session.filter{rows[it].at.isFinite()&&rows[it].score.isFinite()}.sortedBy{rows[it].at}
   val strong=timeline(rows,indices,false)
   val weak=timeline(rows,indices,true).filter{play->strong.none{other->
    play.first.at<=other.last.at&&other.first.at<=play.last.at ||
     sameWork(play.first,other.first)&&play.first.at<=other.last.at+maxGap&&other.first.at<=play.last.at+maxGap&&
     abs(play.origin!!-(other.origin?:Double.POSITIVE_INFINITY))<=5&&
     compatible(play.first.singer,other.first.singer)&&compatibleDate(play.first.date,other.first.date)
   }}
   val joined=mutableListOf<Play>()
   for(play in (strong+weak).sortedBy{it.first.at}) {
    val last=joined.lastOrNull()
    if(last!=null&&sameWork(last.first,play.first)) {
     play.members.forEach(last::add)
     last.members.sortBy{rows[it].at}
    } else joined.add(play)
   }
   joined.map{it.members.toList()}
  }.sortedBy{it.first()}
 }

 fun displayDate(dates:List<String>,fallback:String):String {
  val known=dates.filter{it.isNotBlank()}
  val longest=known.maxByOrNull{it.length}?:return fallback
  return if(known.all{compatibleDate(longest,it)})longest else ""
 }

 /** Capture the host algorithm's exact acoustic evidence on the history row. */
 fun evidence(result:JSONObject?,candidate:Candidate,at:Double,confirmed:Boolean):JSONObject? {
  val hits=OrchestraPresentation.candidates(result)
  val hit=hits.firstOrNull{candidate.sameRecording(it)} ?: return null
  val raw=JSONObject(hit.rawJson)
  val latest=result?.optDouble("queryOffsetSeconds",at-1)?:at-1
  val frames=result?.optInt("frames",1)?:1
  val origin=latest-(frames-1).coerceAtLeast(0)*.5*hit.speed-raw.optDouble("referenceSeconds")*hit.speed
  return JSONObject().put("score",hit.score).put("origin",if(origin.isFinite())origin else JSONObject.NULL)
   .put("confirmed",confirmed&&hit.sameRecording(hits.first()))
   .put("group",raw.optString("groupId"))
 }

 fun rows(input:List<JSONObject>,enabled:Boolean):List<JSONObject> {
  val visible=input.filter{ConfidenceVisibility.visible(it.optInt("confidence_score",0))}
  if(!enabled)return RecordingHistory.consecutive(visible)
  // Old histories lack a speed-aligned origin. Their stored confidence stream
  // still supports sustained plays. Older rows without an audio clock use the legacy view.
  return visible.groupBy{it.optString("session")}.values.flatMap{session->
   if(session.any{!it.optDouble("audio_seconds").isFinite()})RecordingHistory.consecutive(session)
   else cleanSession(session)
  }.sortedBy{it.optString("time")}
 }

 private fun cleanSession(input:List<JSONObject>):List<JSONObject> {
  val rows=input.map{row->
   val candidate=Candidate.fromJson(row)
   val acoustic=row.optJSONObject("clean_history")
   Observation(row.optString("session"),row.optDouble("audio_seconds"),
    acoustic?.optDouble("score",candidate.score)?:candidate.score,
    acoustic?.optDouble("origin")?.takeIf{it.isFinite()},acoustic?.optBoolean("confirmed")?:false,
    DisplayLabelKey.normalizeWorkTitle(candidate.title),MetadataLabels.orchestraKey(candidate.copy(metadataAlternatives=emptyMap())).orEmpty(),
    DisplayLabelKey.normalize(candidate.singer),candidate.year,candidate.aliases+listOfNotNull(acoustic?.optString("group")?.takeIf{it.isNotBlank()}))
  }
  return plays(rows).map{members->
   val first=input[members.first()]
   val best=input[members.maxBy{rows[it].score}]
   val last=input[members.last()]
   val dates=members.flatMap{Candidate.fromJson(input[it]).labels("year")}.distinct()
   val date=displayDate(dates,best.optString("year"))
   JSONObject(best.toString()).apply {
    put("time",first.optString("time"))
    put("last_time",last.optString("time"))
    put("audio_seconds",first.optDouble("audio_seconds"))
    put("sourceIds",JSONArray(members.flatMap{Candidate.fromJson(input[it]).aliases}.distinct().sorted()))
    put("year",date)
    optJSONObject("metadataAlternatives")?.remove("year")
    if(date.isEmpty()&&dates.isNotEmpty())put("conflicting_recording_dates",JSONArray(dates))
   }
  }
 }
}
