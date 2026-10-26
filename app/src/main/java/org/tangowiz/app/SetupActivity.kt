package org.tangowiz.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

/** Resource-only entry screen. A committed, validated bundle is the gate to recognition. */
class SetupActivity:Activity(){
 companion object {const val UPDATE="update";const val PROGRESS="progress"}
 private lateinit var resourceLine:TextView
 private lateinit var resourceButton:Button
 private lateinit var pauseButton:Button
 private lateinit var cancelButton:Button
 private lateinit var appUpdateButton:Button
 private var apkOffer:ApkOffer?=null
 private var checkingApk=false
 private lateinit var progress:ProgressBar
 private var initialDirectory:String?=null
 private var initialized=false
 private var leaving=false
 private var foreground=false
 private val changed:()->Unit={runOnUiThread{renderResources()}}
 private val bg=Color.rgb(17,20,29)
 private val white=Color.rgb(239,240,246)
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun box(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(24).toFloat()}
 private fun updateMode()=intent.getBooleanExtra(UPDATE,false)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val current=ResourceStore(this).current();initialDirectory=if(savedInstanceState?.containsKey("initial_directory")==true)savedInstanceState.getString("initial_directory") else current?.directory?.absolutePath
  initialized=savedInstanceState?.getBoolean("initialized")?:false
  if(current!=null&&!updateMode()){openMain();return}
  window.statusBarColor=bg;window.navigationBarColor=bg
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(28),dp(40),dp(28),dp(40));setBackgroundColor(bg)};setContentView(root)
  root.addView(ImageView(this).apply{setImageResource(R.drawable.app_icon);background=box(Color.rgb(245,236,220));setPadding(dp(10),dp(10),dp(10),dp(10));contentDescription="TangoWiz"},LinearLayout.LayoutParams(dp(108),dp(108)).apply{bottomMargin=dp(30)})
  root.addView(TextView(this).apply{text=if(current==null)"Set up music recognition" else "Resource update";textSize=25f;setTextColor(white);gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2))
  root.addView(TextView(this).apply{text=if(current==null)"Download once, then recognize music offline." else "Your current music library stays available.";textSize=16f;setTextColor(white);gravity=Gravity.CENTER;setPadding(0,dp(14),0,dp(22))},LinearLayout.LayoutParams(-1,-2))
  resourceLine=TextView(this).apply{textSize=18f;setTextColor(white);gravity=Gravity.CENTER;setPadding(0,dp(12),0,dp(12))};root.addView(resourceLine,LinearLayout.LayoutParams(-1,-2))
  progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100};root.addView(progress,LinearLayout.LayoutParams(-1,dp(12)).apply{bottomMargin=dp(20)})
  resourceButton=Button(this).apply{textSize=20f;setTextColor(white);background=box(Color.rgb(43,100,80));minHeight=dp(68);setOnClickListener{resourceAction()}};root.addView(resourceButton,LinearLayout.LayoutParams(-1,dp(72)))
  val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};root.addView(controls,LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(12)})
  pauseButton=Button(this).apply{text="Pause";setOnClickListener{runResources("PAUSE")}};controls.addView(pauseButton,LinearLayout.LayoutParams(0,-1,1f))
  cancelButton=Button(this).apply{text="Cancel download";setOnClickListener{runResources("CANCEL")}};controls.addView(cancelButton,LinearLayout.LayoutParams(0,-1,1f))
  appUpdateButton=Button(this).apply{text="Check app update";setOnClickListener{val offer=apkOffer;if(offer==null)checkAppUpdate() else openApk(offer)}};root.addView(appUpdateButton,LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(12)})
 }
 override fun onStart(){super.onStart();foreground=true;if(leaving||!::resourceLine.isInitialized)return;ResourceProgress.listeners.add(changed);renderResources();window.decorView.post{if(foreground&&!leaving&&(!initialized||ResourceProgress.current.kind=="idle")){initialized=true;begin()}}}
 override fun onStop(){foreground=false;ResourceProgress.listeners.remove(changed);super.onStop()}
 override fun onSaveInstanceState(out:Bundle){out.putString("initial_directory",initialDirectory);out.putBoolean("initialized",initialized);super.onSaveInstanceState(out)}
 override fun onNewIntent(value:Intent){super.onNewIntent(value);intent=value;initialized=false;if(ResourceStore(this).current()!=null&&!updateMode())openMain() else if(::resourceLine.isInitialized)window.decorView.post{begin()}}
 private fun runResources(action:String="CHECK"){startForegroundService(Intent(this,ResourceDownloadService::class.java).setAction(action).putExtra("download",false))}
 private fun begin(){if(!foreground||leaving||ResourceDownloadService.isBusy())return;val r=ResourceStore(this);when{
  r.hasPending()->runResources("RESUME")
  !updateMode()&&r.current()!=null->openMain()
  !updateMode()&&r.offered()!=null->ResourceProgress.publish(ResourceState("available","Download resources",total=r.offered()!!.archive.bytes))
  else->runResources()
 }}
 private fun resourceAction(){val r=ResourceStore(this);when(ResourceProgress.current.kind){
  "update_app"->checkAppUpdate(true)
  "checking","downloading","verifying","pausing","cancelling"->Unit
  "ready"->if(r.current()!=null)openMain() else runResources()
  else->when{r.hasPending()->runResources("RESUME");r.offered()!=null->runResources("ACCEPT");else->runResources()}
 }}
 private fun renderResources(){if(!foreground||leaving||isFinishing||isDestroyed||!::resourceLine.isInitialized)return;val current=ResourceStore(this).current()
  if(current!=null&&(initialDirectory==null||current.directory.absolutePath!=initialDirectory)){openMain();return}
  val s=ResourceProgress.current;val busy=s.kind in setOf("checking","downloading","verifying","pausing","cancelling");resourceButton.isEnabled=!busy
  resourceLine.text=when{ s.kind=="available"&&s.total>0->"Download %.2f GB".format(s.total/1e9);s.kind=="verifying"&&s.total>0->"Verifying ${s.received*100/s.total}%";s.kind in setOf("downloading","paused")&&s.total>0->"%d%% · %.2f / %.2f GB".format(s.received*100/s.total,s.received/1e9,s.total/1e9)+(if(s.kind=="paused")"\nPaused" else if(s.message.startsWith("Reconnecting"))"\n"+s.message else "");s.kind=="ready"&&current==null->"Resources are not ready. Retry.";s.message.isNotEmpty()->s.message;else->"Checking resources…"}
  resourceButton.text=when{s.kind=="update_app"->"Update app ↗";busy->if(s.kind=="checking")"Checking…" else if(s.kind=="verifying")"Verifying…" else if(s.kind in setOf("pausing","cancelling"))"Stopping…" else "Downloading…";s.kind=="ready"&&current!=null->"Done";s.kind=="paused"->"Resume ↓";s.kind=="retry"->"Retry ↻";else->"Download ↓"}
  pauseButton.visibility=if(s.kind in setOf("checking","downloading","verifying","pausing"))View.VISIBLE else View.GONE
  pauseButton.isEnabled=s.kind in setOf("checking","downloading","verifying")
  cancelButton.visibility=if(busy||s.kind=="paused"||ResourceStore(this).hasPending())View.VISIBLE else View.GONE
  cancelButton.isEnabled=s.kind!="cancelling"
  progress.visibility=if(busy)View.VISIBLE else View.INVISIBLE;progress.isIndeterminate=s.total<=0;if(s.total>0)progress.progress=(s.received*100/s.total).toInt().coerceIn(0,100)
 }
 private fun openApk(offer:ApkOffer){try{startActivity(LatestApk.downloadIntent(offer))}catch(e:android.content.ActivityNotFoundException){Toast.makeText(this,"No browser is available",Toast.LENGTH_LONG).show()}}
 private fun checkAppUpdate(openWhenFound:Boolean=false){
  if(checkingApk)return;checkingApk=true;appUpdateButton.isEnabled=false;appUpdateButton.text="Checking app update…"
  val current=packageManager.getPackageInfo(packageName,0).versionName?:"0.0.0"
  Thread({try{val offer=LatestApk.check(current);runOnUiThread{if(!isFinishing&&!isDestroyed){checkingApk=false;apkOffer=offer;appUpdateButton.isEnabled=true;appUpdateButton.text=if(offer==null)"App $current is up to date · Check again" else "Download APK ${offer.version}";if(openWhenFound&&offer!=null)openApk(offer)}}}catch(e:Exception){runOnUiThread{if(!isFinishing&&!isDestroyed){checkingApk=false;appUpdateButton.isEnabled=true;appUpdateButton.text="Retry app update check";Toast.makeText(this,e.message?:"Could not check app update",Toast.LENGTH_LONG).show()}}}},"tango-apk-check").start()
 }
 private fun openMain(){if(leaving)return;leaving=true;startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()}
 @Deprecated("Platform back callback") override fun onBackPressed(){if(ResourceStore(this).current()!=null)openMain() else finishAndRemoveTask()}
}
