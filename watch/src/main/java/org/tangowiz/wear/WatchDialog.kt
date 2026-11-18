package org.tangowiz.wear

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.wear.widget.SwipeDismissFrameLayout

/** Bound native modal content to the watch; keep Close visible and dismiss only this dialog. */
object WatchDialog {
 fun enableSwipe(dialog:AlertDialog){
  val window=dialog.window?:return
  val content=window.decorView.findViewById<ViewGroup>(android.R.id.content)?:return
  if(content.childCount!=1||content.getChildAt(0) is SwipeDismissFrameLayout)return
  val panel=content.getChildAt(0);content.removeView(panel)
  val density=dialog.context.resources.displayMetrics.density
  fun dp(n:Int)=(n*density).toInt()
  val close=listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL)
   .mapNotNull{dialog.getButton(it)}.singleOrNull{it.visibility==View.VISIBLE&&it.text.toString().equals("Close",true)}
  val body=LinearLayout(dialog.context).apply{orientation=LinearLayout.VERTICAL}
  if(close!=null){
   val oldButtons=close.parent as? ViewGroup;oldButtons?.removeView(close);oldButtons?.visibility=View.GONE
   body.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
   body.addView(close,LinearLayout.LayoutParams(dp(130),dp(48)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(20)})
  }else body.addView(panel,LinearLayout.LayoutParams(-1,-1))
  val swipe=SwipeDismissFrameLayout(dialog.context).apply{setSwipeable(true);addView(body,FrameLayout.LayoutParams(-1,-1))}
  swipe.addCallback(object:SwipeDismissFrameLayout.Callback(){override fun onDismissed(layout:SwipeDismissFrameLayout){dialog.dismiss()}})
  content.addView(swipe,ViewGroup.LayoutParams(-1,-1))
  window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
 }
}
