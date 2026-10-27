package org.tangowiz.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.wear.ambient.AmbientLifecycleObserver
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen session: no screen-on flag, polling wake lock, or complication data path. */
class AmbientDisplayActivity:ComponentActivity(){
 private lateinit var content:LinearLayout
 private lateinit var clock:TextView
 private lateinit var orchestra:OrchestraNameView
 private lateinit var song:TextView
 private lateinit var status:TextView
 private lateinit var percent:TextView
 private lateinit var footer:LinearLayout
 private var ambient=false
 private var lowBit=false
 private var burnIn=false
 private var permissionAsked=false
 private var started=false
 private val handler=Handler(Looper.getMainLooper())
 private val changed:()->Unit={runOnUiThread{if(started){if(!DisplaySession.enabled(this))finish() else if(!ambient)render()}}}
 private val minute=object:Runnable{override fun run(){if(started&&!ambient){render();scheduleMinute()}}}
 private val callbacks=object:AmbientLifecycleObserver.AmbientLifecycleCallback{
  override fun onEnterAmbient(ambientDetails:AmbientLifecycleObserver.AmbientDetails){ambient=true;lowBit=ambientDetails.deviceHasLowBitAmbient;burnIn=ambientDetails.burnInProtectionRequired;handler.removeCallbacks(minute);render()}
  override fun onUpdateAmbient(){render()}
  override fun onExitAmbient(){ambient=false;lowBit=false;burnIn=false;render();scheduleMinute();WatchLink.refresh()}
 }
 private val observer by lazy{AmbientLifecycleObserver(this,callbacks)}
 private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
 private fun label(size:Float)=TextView(this).apply{textSize=size;gravity=Gravity.CENTER;setTextColor(Color.WHITE);includeFontPadding=false}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);WatchCache.init(this)
  if(savedInstanceState==null)DisplaySession.enable(this)
  permissionAsked=savedInstanceState?.getBoolean("permission_asked")?:false
  window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
  content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(22),dp(18),dp(22),dp(16));setBackgroundColor(Color.BLACK)}
  clock=label(12f).apply{contentDescription="Time"};content.addView(clock,LinearLayout.LayoutParams(-1,dp(18)))
  orchestra=OrchestraNameView(this).apply{setTextColor(Color.WHITE);setOnClickListener{if(!ambient)WatchLink.reset()};contentDescription="Orchestra; tap to reset recognition, keeping two seconds";maxLines=2;setTypeface(null,Typeface.BOLD);setAutoSizeTextTypeUniformWithConfiguration(18,40,1,TypedValue.COMPLEX_UNIT_SP)};content.addView(orchestra,LinearLayout.LayoutParams(-1,0,2f))
  song=label(18f).apply{maxLines=2;setAutoSizeTextTypeUniformWithConfiguration(12,20,1,TypedValue.COMPLEX_UNIT_SP)};content.addView(song,LinearLayout.LayoutParams(-1,0,1f))
  status=label(10f).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};content.addView(status,LinearLayout.LayoutParams(-1,dp(16)))
  footer=LinearLayout(this).apply{gravity=Gravity.CENTER}
  percent=label(14f);footer.addView(percent,LinearLayout.LayoutParams(0,-1,1f))
  footer.addView(Button(this).apply{text="⚙";textSize=22f;contentDescription="Recognition controls";setTextColor(Color.LTGRAY);setBackgroundColor(Color.TRANSPARENT);setOnClickListener{startActivity(Intent(this@AmbientDisplayActivity,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))}},LinearLayout.LayoutParams(dp(44),dp(40)))
  content.addView(footer,LinearLayout.LayoutParams(-1,dp(40)));setContentView(content)
  onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){DisplaySession.close(this@AmbientDisplayActivity);finish()}})
  lifecycle.addObserver(observer)
 }
 override fun onStart(){super.onStart();started=true;WatchCache.listeners.add(changed);if(!DisplaySession.enabled(this)){finish();return};ensureSession();render();WatchLink.refresh();scheduleMinute()}
 override fun onStop(){started=false;WatchCache.listeners.remove(changed);handler.removeCallbacks(minute);super.onStop()}
 override fun onSaveInstanceState(out:Bundle){out.putBoolean("permission_asked",permissionAsked);super.onSaveInstanceState(out)}
 private fun ensureSession(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){if(!permissionAsked){permissionAsked=true;requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),61)};return}
  runCatching{startForegroundService(Intent(this,WatchDisplayService::class.java))}.onFailure{WatchCache.error=it.message?:"Cannot keep display active"}
 }
 override fun onRequestPermissionsResult(code:Int,permissions:Array<String>,results:IntArray){super.onRequestPermissionsResult(code,permissions,results);if(code==61){if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED)ensureSession();render()}}
 private fun scheduleMinute(){handler.removeCallbacks(minute);if(started&&!ambient)handler.postDelayed(minute,60000-System.currentTimeMillis()%60000)}
 private fun uncertain(raw:String)=if(raw.contains(" ?"))"? "+raw.removeSuffix(" ?") else raw
 private fun render(){
  if(!::content.isInitialized)return
  clock.text=LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
  val stale=WatchCache.stale();val name=uncertain(WatchCache.orchestra())
  orchestra.showName(if(stale&&name!="—"&&!name.startsWith("?"))"? $name" else name,WatchCache.keyword())
  song.text=uncertain(WatchCache.song())
  val best=WatchCache.state.optJSONObject("best");val confidence=best?.optInt("percent")?.coerceIn(0,100)
  percent.text=confidence?.let{"$it%"}?:""
  status.text=when{
   Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED->"Enable notifications to keep display"
   !WatchCache.protocolCompatible->WatchCache.error
   !WatchCache.connected->"Disconnected · cached"
   WatchCache.state.optString("error").isNotBlank()->WatchCache.state.optString("error")
   !WatchCache.state.optBoolean("listening")->"Ⅱ"
   stale->"Cached"
   else->""
  }
  footer.visibility=if(ambient)View.INVISIBLE else View.VISIBLE
  val color=if(ambient||stale)Color.LTGRAY else Color.rgb(160,225,190)
  orchestra.setTextColor(color);percent.setTextColor(color)
  for(view in listOf(clock,orchestra,song,status,percent))view.paint.isAntiAlias=!(ambient&&lowBit)
  if(ambient&&burnIn){val tick=System.currentTimeMillis()/60000;content.translationX=dp((tick%5).toInt()-2).toFloat();content.translationY=dp(((tick/5)%5).toInt()-2).toFloat()}else{content.translationX=0f;content.translationY=0f}
 }
}
