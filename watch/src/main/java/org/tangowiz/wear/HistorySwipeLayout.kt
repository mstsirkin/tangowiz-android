package org.tangowiz.wear

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/** Only the explicit opening gesture is intercepted; Wear OS owns dismissal. */
class HistorySwipeLayout(context:Context,private val allowed:()->Boolean={true},private val open:()->Unit):FrameLayout(context){
 private var downX=0f;private var downY=0f;private var opening=false
 private fun matches(e:MotionEvent)=allowed()&&HistoryGesture.opensHistory(e.x-downX,e.y-downY,maxOf(48*resources.displayMetrics.density,width*.25f))
 override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;opening=false}
   MotionEvent.ACTION_MOVE->if(e.pointerCount==1&&allowed()&&HistoryGesture.opensHistory(e.x-downX,e.y-downY,ViewConfiguration.get(context).scaledTouchSlop*1.5f)){opening=true;return true}
   MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_UP->opening=false
  }
  return super.onInterceptTouchEvent(e)
 }
 override fun onTouchEvent(e:MotionEvent):Boolean {
  if(!opening)return super.onTouchEvent(e)
  if(e.actionMasked==MotionEvent.ACTION_UP){val navigate=matches(e);opening=false;if(navigate)open()}
  else if(e.actionMasked==MotionEvent.ACTION_CANCEL)opening=false
  return true
 }
}
