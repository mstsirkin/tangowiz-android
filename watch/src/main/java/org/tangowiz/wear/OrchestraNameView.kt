package org.tangowiz.wear

import android.content.Context
import android.graphics.Typeface
import android.text.*
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Display-only preferences. Unknown/ambiguous labels retain the original uniform presentation. */
object OrchestraKeywords {
 data class Hint(val keyword:String,val append:Boolean)
 private fun key(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
 fun fromMessage(value:JSONObject?):Hint?{value?:return null;val text=value.optString("text");val placement=value.optString("placement");if(text.isBlank()||text.length>100||placement !in setOf("in_name","append"))return null;return Hint(text,placement=="append")}
 /** UTF-16 offsets for Android spans, including decomposed accent marks. */
 fun span(name:String,keyword:String):IntRange?{
  val normalized=StringBuilder();val starts=mutableListOf<Int>();val ends=mutableListOf<Int>();var pos=0
  while(pos<name.length){val end=pos+Character.charCount(name.codePointAt(pos));val part=Normalizer.normalize(name.substring(pos,end),Normalizer.Form.NFD).lowercase(Locale.ROOT);var added=false
   for(c in part){if(Character.getType(c)==Character.NON_SPACING_MARK.toInt())continue;normalized.append(if(c.isLetterOrDigit())c else ' ');starts.add(pos);ends.add(end);added=true}
   if(!added&&ends.isNotEmpty())ends[ends.lastIndex]=end
   pos=end
  }
  val pattern=key(keyword).split(' ').joinToString("\\s*"){Regex.escape(it)}
  val match=Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])").find(normalized)?:return null
  return starts[match.range.first] until ends[match.range.last]
 }
 fun sameText(a:String,b:String)=key(a)==key(b)
}

/** Fits context and emphasized name into the existing two-line rectangle; never clips to force emphasis. */
class OrchestraNameView(ctx:Context):TextView(ctx){
 var normalMaxSp=40
 var keywordMaxSp=46
 var contextMaxSp=26
 private var name=""
 private var hint:OrchestraKeywords.Hint?=null
 private var renderedKey=""
 private var rendering=false
 var emphasized=false;private set
 init{gravity=Gravity.CENTER;includeFontPadding=false;maxLines=2;setTypeface(null,Typeface.BOLD)}
 fun showName(value:String,keyword:OrchestraKeywords.Hint?=null){name=value;hint=keyword;fitName()}
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);fitName()}
 private data class Choice(val text:SpannableString,val size:Int)
 private fun fitName(){
  if(rendering)return
  val width=width-compoundPaddingLeft-compoundPaddingRight;val height=height-compoundPaddingTop-compoundPaddingBottom
  val cache="$name|$hint|$width|$height|$normalMaxSp|$keywordMaxSp|$contextMaxSp|${resources.configuration.fontScale}"
  if(cache==renderedKey)return
  rendering=true
  try{
   val plain=if(name.startsWith("? "))name.substring(2) else name
   val hint=hint.takeUnless{plain.contains('/')||plain.contains('?')||plain.contains('|')}
   val span=if(hint!=null&&!hint.append)OrchestraKeywords.span(name,hint.keyword) else null
   var choice:Choice?=null
   if(width>0&&height>0&&hint!=null&&(hint.append||!OrchestraKeywords.sameText(plain,hint.keyword))){
    val variants=mutableListOf<Pair<String,IntRange>>()
    if(hint.append){val display=name+"\n"+hint.keyword;variants.add(display to (name.length+1 until display.length))}
    else if(span!=null){
     val before=name.substring(0,span.first).trimEnd();val word=name.substring(span);val after=name.substring(span.last+1).trimStart()
     if(before.isNotEmpty()&&after.isEmpty()){val display=before+"\n"+word;variants.add(display to (before.length+1 until display.length))}
     if(before.isEmpty()&&after.isNotEmpty()){variants.add((word+"\n"+after) to (0 until word.length))}
     variants.add(name to span)
    }
    fun measure(size:Int,keySize:Int):Choice?{
     for((display,range) in variants){val text=SpannableString(display);text.setSpan(AbsoluteSizeSpan(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,keySize.toFloat(),resources.displayMetrics).toInt()),range.first,range.last+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      val metrics=TextPaint(paint).apply{textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size.toFloat(),resources.displayMetrics)}
      val layout=StaticLayout.Builder.obtain(text,0,text.length,metrics,width).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
      if(layout.lineCount<=2&&layout.height<=height&&layout.getLineEnd(layout.lineCount-1)==text.length&&(0 until layout.lineCount).all{layout.getLineWidth(it)<=width+.5f})return Choice(text,size)
     };return null
    }
    for(keySize in keywordMaxSp downTo maxOf(24,contextMaxSp) step 2){
     val minSize=minOf(18,contextMaxSp);if(measure(minSize,keySize)==null)continue
     var low=minSize;var high=minOf(contextMaxSp,keySize-2)
     while(low<high){val mid=(low+high+1)/2;if(measure(mid,keySize)!=null)low=mid else high=mid-1}
     choice=measure(low,keySize);break
    }
   }
   emphasized=choice!=null
   if(choice!=null){setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE);setTextSize(TypedValue.COMPLEX_UNIT_SP,choice.size.toFloat());text=choice.text}
   else{setAutoSizeTextTypeUniformWithConfiguration(18,normalMaxSp,1,TypedValue.COMPLEX_UNIT_SP);text=name}
   contentDescription=name+(if(hint?.append==true&&emphasized)", ${hint.keyword}" else "")+"; tap to reset recognition, keeping two seconds"
   renderedKey=cache
  }finally{rendering=false}
 }
}
