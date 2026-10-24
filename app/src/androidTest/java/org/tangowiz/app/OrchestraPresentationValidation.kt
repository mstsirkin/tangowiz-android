package org.tangowiz.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

object OrchestraPresentationValidation {
 fun run(i:Instrumentation){val checks=JSONArray();var activity:MainActivity?=null
  try{
   fun row(id:String,orchestra:String,title:String="Tú",singer:String="",year:String="",score:Double=.8,options:JSONObject=JSONObject())=JSONObject().put("id",id).put("groupId","acoustic:$id").put("sourceIds",JSONArray(listOf(id))).put("orchestra",orchestra).put("title",title).put("singer",singer).put("year",year).put("score",score).put("confidence",score).put("metadataAlternatives",options)
   fun c(j:JSONObject)=Candidate.fromJson(j)
   // Public source IDs and scores from the observed Jamás retornarás pair; no private audio is embedded.
   val a=row("youtube-X4E4_s6HBas","Miguel Caló","JAMAS RETORNARAS","Raúl Berón","1942",.804814)
   val b=row("youtube-loJb46V_Egg","Miguel Caló","JAMAS RETORNARAS","Raúl Berón","1942",.758135)
   val other=row("canaro-control","Francisco Canaro","El internado",year="1935",score=.60)
   val raw=JSONObject().put("candidates",JSONArray().put(a).put(b).put(other));val before=raw.toString()
   val merged=OrchestraPresentation.merge(OrchestraPresentation.candidates(raw));check(merged.size==2)
   val best=merged[0];check(best.id==a.getString("id")&&best.groupId==a.getString("groupId")&&best.sourceIds==listOf(a.getString("id")))
   check(best.score==.804814&&best.confidence==80&&merged[1].id=="canaro-control");check(best.display("title")=="JAMAS RETORNARAS"&&best.display("year")=="1942");check(best.presentationContributors.size==2);check(raw.toString()==before)
   checks.put("real Jamás pair becomes one Caló presentation row, runner is another orchestra, max score and native anchor identity/raw JSON unchanged")
   val complementary=OrchestraPresentation.merge(listOf(c(row("tu-a","MIGUEL CALO",year="1949",score=.9)),c(row("tu-b","Orquesta Miguel Caló",singer="Raúl Berón",year="20-7-1949",score=.7)))).single()
   check(complementary.display("singer")=="Raúl Berón"&&complementary.display("year")=="1949-07-20"&&!complementary.display("orchestra").contains('?'))
   checks.put("accent/case/generic orchestra prefix normalize and complementary singer/date precision fill without false conflicts")
   val conflict=OrchestraPresentation.merge(listOf(c(row("conflict-a","Miguel Caló","Un crimen","Raúl Berón","1942",.8)),c(row("conflict-b","Miguel Caló","4 Compases","Alberto Podestá","1943",.7)))).single()
   check(listOf("title","singer","year").all{conflict.display(it).contains(" / ")&&conflict.display(it).endsWith(" ?")});check(conflict.score==.8)
   checks.put("genuinely different titles/singers/dates remain alternatives marked ? without score addition")
   val ambiguous=c(row("ambiguous","Miguel Caló",options=JSONObject().put("orchestra",JSONArray(listOf("Miguel Caló","Francisco Canaro")))))
   val unknown=listOf(c(row("blank-1","")),c(row("blank-2","")),c(row("question","?")),c(row("unknown","Unknown")),ambiguous,c(a))
   check(OrchestraPresentation.merge(unknown).size==unknown.size)
   checks.put("blank, unknown, question-mark and conflicting multi-orchestra candidates do not merge with one another or known orchestras")
   check(OrchestraPresentation.merge(listOf(unknown[0],unknown[0].copy(score=.5))).size==1);check(OrchestraPresentation.merge(listOf(ambiguous,ambiguous.copy(score=.5))).size==1)
   checks.put("same native unknown or multi-orchestra recording is deduplicated across baseline/fallback, while different unknown recording identities stay separate")
   val formations=listOf(c(row("old","Juan D’Arienzo")),c(row("prefix","Orquesta Típica Juan D'Arienzo",score=.7)),c(row("modern","La Juan D’Arienzo",score=.6)))
   check(OrchestraPresentation.merge(formations).size==2)
   checks.put("generic historical orchestra names combine but La Juan D'Arienzo remains distinct")
   val ctx=object:ContextWrapper(i.targetContext){override fun getFilesDir()=File(super.getFilesDir(),"orchestra-presentation-test").apply{mkdirs()};override fun getCacheDir()=File(super.getCacheDir(),"orchestra-presentation-test").apply{mkdirs()}}
   val store=Store(ctx);store.root.deleteRecursively();store.root.mkdirs()
   store.result(best,merged[1],20.0,"session",raw)
   val nextSong=c(row("next-calo-song","Miguel Caló","Que te importa que te llore","Raúl Berón","1942",.9))
   val next=OrchestraPresentation.merge(listOf(nextSong,c(a))).single();store.result(next,merged[1],30.0,"session",raw)
   val history=store.history();check(RecordingHistory.consecutive(history).size==2);check(history[0].getJSONArray("sourceIds").length()==1&&history[1].getJSONArray("sourceIds").length()==1)
   check(history.all{r->!r.toString().contains("groupId")&&!r.toString().contains("group_id")&&!r.toString().contains("runner_up_group")});check(Candidate.fromJson(history[0]).presentationContributors.size==2);val storedContributors=history[0].getJSONArray("presentationContributors");for(n in 0 until storedContributors.length()){val item=storedContributors.getJSONObject(n);check(item.keys().asSequence().toSet()==setOf("id","sourceIds","score","speed"));check(item.getJSONArray("sourceIds").length()==1)};check(storedContributors.getJSONObject(0).getDouble("score")==.804814)
   check(File(store.root,"session/matches.jsonl").readLines().all{JSONObject(it).getJSONObject("result").toString()==raw.toString()})
   val archive=store.export();ZipFile(archive).use{z->val text=z.getInputStream(z.getEntry("history.jsonl")).bufferedReader().readText();check(text.contains("presentationContributors")&&text.contains("youtube-loJb46V_Egg"))}
   checks.put("saved combined metadata/contributor provenance export correctly; different Caló song anchors stay separate and no internal group IDs enter history")
   activity=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
   fun texts(v:View):List<String> =if(v is TextView)listOf(v.text.toString()) else if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else emptyList()
   i.runOnMainSync{LiveState.publish(RecognitionState(best=conflict,second=merged[1]))};i.waitForIdleSync()
   val bestView=MainActivity::class.java.getDeclaredField("best").apply{isAccessible=true}.get(activity) as View
   val runnerView=MainActivity::class.java.getDeclaredField("runner").apply{isAccessible=true}.get(activity) as View
   check(texts(bestView).count{it=="Miguel Caló"}==1);check(texts(bestView).any{it.contains("Un crimen")&&it.contains("4 Compases")&&it.startsWith("? ")});check(texts(runnerView).any{it=="Francisco Canaro"});check(texts(runnerView).none{it=="Miguel Caló"})
   checks.put("actual Main displays one Caló row with uncertain metadata and a different-orchestra runner-up")
   val longOptions=JSONObject().put("title",JSONArray((0..9).map{"Long alternative song title $it"}));val long=c(row("long","Miguel Caló",title="Long alternative song title 0",options=longOptions))
   i.runOnMainSync{LiveState.publish(RecognitionState(best=long,second=merged[1]))};i.waitForIdleSync();val titleView=(bestView as ViewGroup).getChildAt(1) as TextView
   check(titleView.maxLines==3&&titleView.ellipsize==android.text.TextUtils.TruncateAt.END&&titleView.text.startsWith("? ")&&titleView.contentDescription.contains("Long alternative song title 9"))
   i.runOnMainSync{bestView.performClick()};i.waitForIdleSync();val until=android.os.SystemClock.elapsedRealtime()+10000;var expanded=false;while(!expanded&&android.os.SystemClock.elapsedRealtime()<until){expanded=i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Long alternative song title 9")?.isNotEmpty()==true;if(!expanded)android.os.SystemClock.sleep(50)};check(expanded);i.uiAutomation.executeShellCommand("input keyevent 4").close()
   checks.put("long card labels are bounded with visible leading uncertainty, full accessibility text and a tappable scrollable details dialog")
   i.runOnMainSync{activity!!.finishAndRemoveTask()};val report=JSONObject().put("status","passed").put("count",checks.length()).put("checks",checks)
   File(i.targetContext.filesDir,"orchestra-presentation-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){runCatching{i.runOnMainSync{activity?.finishAndRemoveTask()}};File(i.targetContext.filesDir,"orchestra-presentation-validation.json").writeText(JSONObject().put("status","failed").put("checks",checks).put("error",e.stackTraceToString()).toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
 fun replay(i:Instrumentation){try{val source=File("/data/local/tmp/milagro-matches.jsonl");var count=0;var combined=0;var largest=0
  source.bufferedReader().useLines{lines->lines.filter{it.isNotBlank()}.forEach{line->val raw=JSONObject(line).getJSONObject("result");val before=raw.toString();val input=OrchestraPresentation.candidates(raw);val rows=OrchestraPresentation.merge(input);check(rows.isNotEmpty());val original=input.maxBy{it.score};val first=rows.first();check(first.id==original.id&&first.sourceIds==original.sourceIds&&first.groupId==original.groupId&&first.score==original.score)
   val keys=rows.mapNotNull{MetadataLabels.orchestraKey(it)};check(keys.size==keys.distinct().size)
   for(row in rows){val key=MetadataLabels.orchestraKey(row);if(key!=null)check(row.score==input.filter{MetadataLabels.orchestraKey(it)==key}.maxOf{it.score});check(input.any{it.id==row.id&&it.sourceIds==row.sourceIds});if(row.presentationContributors.size>1)combined++};check(raw.toString()==before);largest=maxOf(largest,rows.maxOf{it.display("title").length});count++}}
  check(count==3548);val report=JSONObject().put("status","passed").put("rows",count).put("combined_rows",combined).put("largest_full_title_chars",largest).put("scores_and_anchor_identities_unchanged",true).put("no_duplicate_unambiguous_orchestra_rows",true).put("raw_json_unchanged",true);File(i.targetContext.filesDir,"orchestra-replay-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
 }catch(e:Throwable){i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}
}
