package org.tangowiz.wear

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.*
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView
import org.tangowiz.link.DisplayNames

/** A separate, secondary one-line credit; the full name stays available by tapping. */
class SingerNameView(context:Context):TextView(context){
 private var full=""
 private var includeFirst=false
 init{
  gravity=Gravity.CENTER;includeFontPadding=false;maxLines=1;ellipsize=TextUtils.TruncateAt.END
  setOnClickListener{
   val credit=TextView(context).apply{text=full;textSize=18f;setPadding(20,16,20,16)}
   AlertDialog.Builder(context).setTitle("Singer").setView(ScrollView(context).apply{addView(credit)}).setPositiveButton("Close",null).show()
  }
 }
 fun showCredit(value:String,firstName:Boolean){full=value;includeFirst=firstName;fitCredit()}
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);fitCredit()}
 private fun fitCredit(){
  val display=DisplayNames.singer(full,includeFirst);val surname=if(includeFirst)DisplayNames.surname(full) else null
  val width=width-compoundPaddingLeft-compoundPaddingRight;val height=height-compoundPaddingTop-compoundPaddingBottom
  var styled:SpannableString?=null
  if(surname!=null&&width>0&&height>0){
   val at=display.lastIndexOf(surname)
   for(size in 20 downTo 14){
    val candidate=SpannableString(display)
    candidate.setSpan(AbsoluteSizeSpan(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size.toFloat(),resources.displayMetrics).toInt()),at,at+surname.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val metrics=TextPaint(paint).apply{textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,12f,resources.displayMetrics)}
    val layout=StaticLayout.Builder.obtain(candidate,0,candidate.length,metrics,width).setIncludePad(false).build()
    if(layout.lineCount==1&&layout.height<=height&&layout.getLineWidth(0)<=width+.5f){styled=candidate;break}
   }
  }
  setTypeface(null,Typeface.NORMAL)
  if(styled!=null){setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE);setTextSize(TypedValue.COMPLEX_UNIT_SP,12f);text=styled}
  else{setAutoSizeTextTypeUniformWithConfiguration(10,20,1,TypedValue.COMPLEX_UNIT_SP);text=display}
  contentDescription="Singer: $full; tap for full credit"
 }
}
