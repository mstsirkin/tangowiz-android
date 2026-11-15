package org.tangowiz.wear
import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.tangowiz.link.HapticVisibility

object WatchHapticSession:Application.ActivityLifecycleCallbacks {
 private val visibility=HapticVisibility()
 fun visible()=visibility.visible()
 override fun onActivityStarted(activity:Activity){if(activity is WatchActivity||activity is AmbientDisplayActivity)visibility.started(activity)}
 override fun onActivityStopped(activity:Activity){visibility.stopped(activity)}
 override fun onActivityDestroyed(activity:Activity){visibility.stopped(activity)}
 override fun onActivityCreated(activity:Activity,state:Bundle?){}
 override fun onActivityResumed(activity:Activity){}
 override fun onActivityPaused(activity:Activity){}
 override fun onActivitySaveInstanceState(activity:Activity,state:Bundle){}
}
