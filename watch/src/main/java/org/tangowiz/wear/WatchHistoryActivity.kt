package org.tangowiz.wear

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.wear.widget.SwipeDismissFrameLayout

/** A real child activity: system dismissal returns to whichever screen opened it. */
class WatchHistoryActivity:Activity(){
 private lateinit var body:LinearLayout
 private lateinit var scroll:ScrollView
 private var visible=false
 private val changed:()->Unit={runOnUiThread{if(visible)render()}}
 private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
 override fun onCreate(state:Bundle?){
  super.onCreate(state);WatchCache.init(this)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.BLACK)}
  body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(26),dp(32),dp(26),dp(12))}
  scroll=ScrollView(this).apply{addView(body)}
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val footer=LinearLayout(this).apply{gravity=Gravity.CENTER}
  val refresh=Button(this).apply{text="↻";contentDescription="Refresh history";setOnClickListener{WatchLink.history()}}
  footer.addView(refresh,LinearLayout.LayoutParams(dp(48),dp(48)))
  val close=Button(this).apply{
   text="⊗"
   textSize=16f
   setTextColor(Color.WHITE)
   setPadding(0,0,0,0)
   background=android.graphics.drawable.RippleDrawable(
    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
    null,
    android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(Color.WHITE)}
   )
   contentDescription="Close history"
   setOnClickListener{finish()}
  }
  footer.addView(close,LinearLayout.LayoutParams(dp(48),dp(48)))
  root.addView(footer,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(20)})
  setContentView(SwipeDismissFrameLayout(this).apply{
   addView(root)
   addCallback(object:SwipeDismissFrameLayout.Callback(){override fun onDismissed(layout:SwipeDismissFrameLayout){finish()}})
  })
  window.decorView.isFocusableInTouchMode=true;window.decorView.requestFocus()
  window.decorView.setOnGenericMotionListener{_,e->if(e.action==android.view.MotionEvent.ACTION_SCROLL){scroll.smoothScrollBy(0,(-e.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)*dp(36)).toInt());true}else false}
 }
 override fun onStart(){super.onStart();visible=true;WatchCache.listeners.add(changed);render();WatchLink.history()}
 override fun onStop(){visible=false;WatchCache.listeners.remove(changed);super.onStop()}
 private fun label(text:String,size:Float=15f,details:Boolean=false){
  body.addView(TextView(this).apply{
   this.text=text;textSize=size;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(5),0,dp(5));contentDescription=text
   if(details){maxLines=5;ellipsize=android.text.TextUtils.TruncateAt.END;setOnClickListener{
    val full=TextView(this@WatchHistoryActivity).apply{this.text=text;textSize=18f;setTextColor(Color.WHITE);setPadding(dp(18),dp(16),dp(18),dp(16))}
    AlertDialog.Builder(this@WatchHistoryActivity).setView(ScrollView(this@WatchHistoryActivity).apply{addView(full)}).setPositiveButton("Close",null).show().also{WatchDialog.enableSwipe(it)}
   }}
  },LinearLayout.LayoutParams(-1,-2))
 }
 private fun render(){
  val position=scroll.scrollY;body.removeAllViews()
  val history=WatchCache.history
  if(history.length()==0)label("No history yet")
  for(n in history.length()-1 downTo maxOf(0,history.length()-100)){
   val row=history.optJSONObject(n)?:continue
   val time=runCatching{java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(row.optString("time")))}.getOrDefault("")
   label(listOf((time+"  "+row.optString("orchestra")).trim(),row.optString("title"),row.optString("singer"),row.optString("year")).filter{it.isNotBlank()}.joinToString("\n"),details=true)
  }
  scroll.post{scroll.scrollTo(0,position)}
 }
}
