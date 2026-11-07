package org.tangowiz.wear

import android.view.View

/** A completed long press consumes the gesture, so it cannot also refresh. */
object ResultTouchControls {
 fun bind(view:View,update:()->Unit={WatchLink.refresh()},reset:()->Unit={WatchLink.reset()}) {
  view.setOnClickListener{update()}
  view.setOnLongClickListener{reset();true}
 }
}
