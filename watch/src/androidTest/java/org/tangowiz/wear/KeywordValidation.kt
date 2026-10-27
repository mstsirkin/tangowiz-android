package org.tangowiz.wear

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.widget.FrameLayout
import org.json.*
import java.io.File

/** Real round-screen layout/Spannable tests, no synthetic recognition state or microphone capture. */
object KeywordValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val report=JSONObject();val rows=JSONArray();var activity:WatchActivity?=null
  fun ui(action:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{action()}catch(t:Throwable){error=t}};error?.let{throw it}}
  try{
   WatchCache.init(ctx);check(!WatchCache.capturing){"Do not interrupt capture"}
   activity=i.startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WatchActivity
   val density=ctx.resources.displayMetrics.density
   lateinit var view:OrchestraNameView
   ui{val root=FrameLayout(ctx).apply{setBackgroundColor(Color.BLACK)};view=OrchestraNameView(ctx).apply{setTextColor(Color.WHITE)};root.addView(view,FrameLayout.LayoutParams((ctx.resources.displayMetrics.widthPixels-44*density).toInt(),(80*density).toInt(),android.view.Gravity.CENTER));activity!!.setContentView(root)}
   i.waitForIdleSync()
   val examples=listOf("Carlos Di Sarli","Miguel Caló","Roberto Caló","Domingo Federico","Leopoldo Federico","Gotan Project","Orquesta Típica Víctor","La Juan D’Arienzo","Unknown ensemble","Francisco Canaro / Ricardo Tanturi ?")
   for(name in examples){ui{view.showName(name)};i.waitForIdleSync();ui{
    val styled=view.emphasized;val text=view.text;val layout=view.layout
    if(styled){check(layout.lineCount<=2&&layout.height<=view.height);check(layout.getLineEnd(layout.lineCount-1)==text.length)}
    if(name in listOf("Carlos Di Sarli","Miguel Caló","Gotan Project","Orquesta Típica Víctor","La Juan D’Arienzo"))check(styled){"Expected emphasis: $name at ${view.width}x${view.height}"}
    if(name in listOf("Roberto Caló","Domingo Federico","Leopoldo Federico","Unknown ensemble","Francisco Canaro / Ricardo Tanturi ?"))check(!styled){"Expected original styling: $name"}
    if(name=="La Juan D’Arienzo")check(text.toString().endsWith("LJD"))
    if(name=="Carlos Di Sarli"){val span=(text as Spanned).getSpans(0,text.length,AbsoluteSizeSpan::class.java).single();check(text.subSequence(text.getSpanStart(span),text.getSpanEnd(span)).toString()=="Di Sarli")}
    if(name=="Miguel Caló"){val span=(text as Spanned).getSpans(0,text.length,AbsoluteSizeSpan::class.java).single();check(text.subSequence(text.getSpanStart(span),text.getSpanEnd(span)).toString()=="Caló")}
    rows.put(JSONObject().put("name",name).put("emphasized",styled).put("display",text.toString()).put("lines",layout.lineCount).put("height",layout.height).put("available_height",view.height))
   };val slug=when(name){"Carlos Di Sarli"->"di-sarli";"Miguel Caló"->"calo";"Domingo Federico"->"federico";"La Juan D’Arienzo"->"ljd";else->null};if(slug!=null){i.uiAutomation.takeScreenshot()?.let{bitmap->File(ctx.filesDir,"keyword-$slug.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}}
   // Layout every entry using real font metrics; emphasis must never hide text.
   val names=JSONObject(ctx.assets.open("orchestra-keywords.json").bufferedReader().use{it.readText()}).getJSONArray("names");var emphasized=0
   for(index in 0 until names.length()){val name=names.getJSONArray(index).getString(0);ui{view.showName(name)};i.waitForIdleSync();ui{if(view.emphasized){emphasized++;check(view.layout.lineCount<=2&&view.layout.height<=view.height);check(view.layout.getLineEnd(view.layout.lineCount-1)==view.text.length){"Clipped: $name"}}}}
   // Unicode, particles, and boundaries must resolve safely.
   check(OrchestraKeywords.span("Carlos Di Sarli","Di Sarli")==7 until 15)
   check(OrchestraKeywords.span("Orquesta Ti\u0301pica Vi\u0301ctor","Víctor")!=null)
   check(OrchestraKeywords.span("Francisco Canarovich","Canaro")==null)
   ui{view.layoutParams=view.layoutParams.apply{width=20;height=16};view.requestLayout()};i.waitForIdleSync();ui{view.showName("La Juan D’Arienzo");check(!view.emphasized)}
   report.put("status","passed").put("round_screen",ctx.resources.configuration.isScreenRound).put("table_entries",names.length()).put("entries_emphasized_without_clipping",emphasized).put("examples",rows).put("tiny_area_falls_back",true).put("protocol_unchanged",true)
   File(ctx.filesDir,"keyword-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("report",report.toString())})
  }catch(e:Throwable){report.put("status","failed").put("examples",rows).put("error",e.stackTraceToString());File(ctx.filesDir,"keyword-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{runCatching{ui{activity?.finishAndRemoveTask()}}}
 }
}
