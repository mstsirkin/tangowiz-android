package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button

object ConfidenceUiValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;val store=Store(ctx);val before=LiveState.current
  check(store.adaptiveRecognition){"Fresh install must default to Adaptive"}
  check(store.adaptiveInterval==2.0){"Default retry interval must be 2 seconds"}
  ResourceStore(ctx).automaticChecks=false
  val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
  try {
   i.waitForIdleSync()
   i.runOnMainSync {
    val dj=activity.findViewById<Button>(104);val row=dj.parent as ViewGroup
    check((0 until row.childCount).map{row.getChildAt(it)}.any{it.contentDescription=="TangoWiz"}){"DJ is not in logo row"}
    check((0 until row.childCount).all{val v=row.getChildAt(it);v.left>=0&&v.right<=row.width}){"Toolbar clips"}
    val historySize=store.history().size
    store.result(Candidate("weak","","LOW GUESS",score=.28,confidence=4),null,0.0,"ui-validation")
    check(store.history().size==historySize){"Sub-five candidate entered history"}
    val best=MainActivity::class.java.getDeclaredField("best").apply{isAccessible=true}.get(activity) as View
    val runner=MainActivity::class.java.getDeclaredField("runner").apply{isAccessible=true}.get(activity) as View
    LiveState.publish(RecognitionState(best=Candidate("weak","","LOW GUESS",score=.28,confidence=4),second=Candidate("weak2","","LOW GUESS 2",score=.28,confidence=4)))
    check(best.visibility==View.GONE&&runner.visibility==View.GONE){"Low confidence visible"}
    LiveState.publish(RecognitionState(best=Candidate("boundary","","FIVE PERCENT",score=.29,confidence=5)))
    check(best.visibility==View.VISIBLE){"Five percent incorrectly hidden"}
   }
  } finally { i.runOnMainSync{LiveState.publish(before);activity.finish()} }
 }
}
