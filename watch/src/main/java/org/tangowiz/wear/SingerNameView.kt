package org.tangowiz.wear

import android.os.SystemClock
import android.content.Context
import android.graphics.Typeface
import android.text.*
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import org.tangowiz.link.DisplayNames
import org.tangowiz.link.SingerCreditReveal
import org.tangowiz.link.SingerSizing

/** Tapping reveals the full credit inline for eight seconds without covering live recognition. */
class SingerNameView(context:Context):TextView(context){
 private var full=""
 private var includeFirst=false
 private val reveal=SingerCreditReveal()
 private val expire=Runnable{fitCredit()}
 init{
  gravity=Gravity.CENTER;includeFontPadding=false;maxLines=1;ellipsize=TextUtils.TruncateAt.END
  setOnClickListener{
   val now=SystemClock.uptimeMillis();reveal.toggle(now);removeCallbacks(expire);fitCredit()
   if(reveal.visible(now))postDelayed(expire,SingerCreditReveal.DURATION_MS)
  }
 }
 fun showCredit(value:String,firstName:Boolean){if(value!=full)removeCallbacks(expire);reveal.updateCredit(value);full=value;includeFirst=firstName;fitCredit()}
 override fun onAttachedToWindow(){super.onAttachedToWindow();fitCredit()}
 override fun onDetachedFromWindow(){removeCallbacks(expire);reveal.clear();super.onDetachedFromWindow()}
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);fitCredit()}
 private fun fitCredit(){
  val showFirst=includeFirst||reveal.visible(SystemClock.uptimeMillis())
  val display=DisplayNames.singer(full,showFirst);val surname=if(showFirst)DisplayNames.surname(full) else null
  val width=width-compoundPaddingLeft-compoundPaddingRight;val height=height-compoundPaddingTop-compoundPaddingBottom
  var styled:SpannableString?=null
  var firstNameSize=22
  if(surname!=null&&width>0&&height>0){
   val at=display.lastIndexOf(surname)
   for(size in 26 downTo 18){
    val candidate=SpannableString(display)
    candidate.setSpan(AbsoluteSizeSpan(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size.toFloat(),resources.displayMetrics).toInt()),at,at+surname.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val firstSize=SingerSizing.firstNameSize(size)
    val metrics=TextPaint(paint).apply{textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,firstSize.toFloat(),resources.displayMetrics)}
    val layout=StaticLayout.Builder.obtain(candidate,0,candidate.length,metrics,width).setIncludePad(false).build()
    if(layout.lineCount==1&&layout.height<=height&&layout.getLineWidth(0)<=width+.5f){styled=candidate;firstNameSize=firstSize;break}
   }
  }
  setTypeface(null,Typeface.NORMAL)
  if(styled!=null){setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE);setTextSize(TypedValue.COMPLEX_UNIT_SP,firstNameSize.toFloat());text=styled}
  else{setAutoSizeTextTypeUniformWithConfiguration(22,26,1,TypedValue.COMPLEX_UNIT_SP);text=display}
  contentDescription="Singer: $full; "+if(reveal.visible(SystemClock.uptimeMillis()))"tap to hide full credit" else "tap to show full credit temporarily"
 }
}
