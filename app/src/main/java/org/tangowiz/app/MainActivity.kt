package org.tangowiz.app
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.NotificationManager
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity:Activity(){
 private lateinit var store:Store
 private lateinit var best:LinearLayout
 private lateinit var runner:LinearLayout
 private lateinit var history:ListView
 private var historyRows=emptyList<org.json.JSONObject>()
 private var historyVersion=-1L
 private var loadingHistory=false
 private lateinit var historyAdapter:BaseAdapter
 private lateinit var listen:Button
 private lateinit var phase:TextView
 private var receiverRegistered=false
 private var foreground=false
 private var promptedOffer=""
 private val resourceChanged:()->Unit={runOnUiThread{showUpdateOffer()}}
 private val changed:()->Unit={runOnUiThread{if(LiveState.current.exited)finishAndRemoveTask() else render()}}
 private val exitReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){finishAndRemoveTask()}}
 private val bg=Color.rgb(17,20,29)
 private val white=Color.rgb(239,240,246)
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun box(color:Int,radius:Float=24f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.toInt()).toFloat()}
 private fun text(s:String,size:Float=18f)=TextView(this).apply{text=s;textSize=size;setTextColor(white);setPadding(dp(16),dp(5),dp(16),dp(5))}
 private fun icon(s:String,hint:String,action:()->Unit)=Button(this).apply{text=s;textSize=25f;contentDescription=hint;tooltipText=hint;minWidth=dp(60);minimumWidth=dp(60);minHeight=dp(60);background=box(Color.rgb(43,47,64));setTextColor(white);setOnClickListener{action()}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(!ensureResources())return;if(LiveState.current.exited)LiveState.publish(RecognitionState());if(Build.VERSION.SDK_INT>=33)registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY),Context.RECEIVER_NOT_EXPORTED) else registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY));receiverRegistered=true;store=Store(this);window.statusBarColor=bg;window.navigationBarColor=bg
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(20),dp(16),dp(12));setBackgroundColor(bg)};setContentView(root)
  root.setOnApplyWindowInsetsListener{v,insets-> if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom)}else{v.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(12)+insets.systemWindowInsetTop,dp(16)+insets.systemWindowInsetRight,dp(12)+insets.systemWindowInsetBottom)};insets}
  val toolbar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};root.addView(toolbar)
  toolbar.addView(ImageView(this).apply{setImageResource(org.tangowiz.app.R.drawable.app_icon);background=box(Color.rgb(245,236,220),12f);setPadding(dp(3),dp(3),dp(3),dp(3));contentDescription="TangoWiz"},LinearLayout.LayoutParams(dp(48),dp(48)))
  toolbar.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
  val audio=icon("●","Save audio"){store.audio=!store.audio;render()};audio.id=100;toolbar.addView(audio)
  val debug=icon("🐞","Timing debug"){store.timing=!store.timing;render()};debug.id=101;toolbar.addView(debug)
  toolbar.addView(icon("⋮","More options"){vMenu(toolbar)})
  phase=text("",14f);root.addView(phase)
  best=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(14),dp(8),dp(14));background=box(Color.rgb(34,39,54))};root.addView(best,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  runner=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(8),dp(8),dp(8));background=box(Color.rgb(28,32,45))};root.addView(runner,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  listen=icon("▶","Start listening"){toggle()};root.addView(listen,LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16);bottomMargin=dp(16)})
  historyAdapter=object:BaseAdapter(){override fun getCount()=historyRows.size;override fun getItem(p:Int)=historyRows[p];override fun getItemId(p:Int)=p.toLong();override fun getView(p:Int,old:View?,parent:android.view.ViewGroup?):View {val r=historyRows[p];val credit=Candidate.fromJson(r);val item=(old as? LinearLayout)?:LinearLayout(this@MainActivity).apply{orientation=1;setPadding(0,dp(9),0,dp(9));addView(text("",16f));addView(text("",17f))};val time=runCatching{DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(r.getString("time")))}.getOrDefault("");val header="$time   ${visibleLabel(credit.display("orchestra").ifEmpty{"?"})}   ${r.optInt("confidence_score")}%";val detail=listOf(credit.display("title"),credit.display("singer"),credit.display("year")).filter{it.isNotEmpty()}.joinToString(" · ");(item.getChildAt(0) as TextView).apply{text=header;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=header};(item.getChildAt(1) as TextView).apply{text=visibleLabel(detail);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=detail};item.setOnClickListener{details(credit)};item.alpha=if(r.optString("state")=="strong")1f else .65f;return item}}
  history=ListView(this).apply{adapter=historyAdapter;divider=null;setBackgroundColor(bg)};root.addView(history,LinearLayout.LayoutParams(-1,0,1f));render()
 }
 override fun onStart(){super.onStart();if(!::store.isInitialized||!ensureResources())return;foreground=true;LiveState.listeners.add(changed);ResourceProgress.listeners.add(resourceChanged);render();showUpdateOffer();if(ResourceStore(this).dueAutomaticCheck())window.decorView.post{if(foreground)startForegroundService(Intent(this,ResourceDownloadService::class.java).setAction("CHECK").putExtra("download",false))}}
 override fun onStop(){foreground=false;LiveState.listeners.remove(changed);ResourceProgress.listeners.remove(resourceChanged);super.onStop()}
 override fun onDestroy(){if(receiverRegistered)unregisterReceiver(exitReceiver);super.onDestroy()}
 override fun onNewIntent(value:Intent){super.onNewIntent(value);intent=value;ensureResources()}
 private fun ensureResources():Boolean{if(ResourceStore(this).current()!=null)return true;startActivity(Intent(this,SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return false}
 private fun openUpdate(){startActivity(Intent(this,SetupActivity::class.java).putExtra(SetupActivity.UPDATE,true))}
 private fun vMenu(anchor:View){val r=ResourceStore(this);val menu=PopupMenu(this,anchor);menu.menu.add(0,1,0,"Update");menu.menu.add(0,3,2,"Export debug info");menu.menu.add(0,2,1,"Automatic update checks").apply{isCheckable=true;isChecked=r.automaticChecks};menu.setOnMenuItemClickListener{item->when(item.itemId){1->openUpdate();3->startActivity(Intent(this,ExportActivity::class.java).putExtra(ExportActivity.NEW,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));2->{r.automaticChecks=!r.automaticChecks;item.isChecked=r.automaticChecks}};true};menu.show()}
 private fun showUpdateOffer(){if(!foreground||isFinishing||isDestroyed||!ResourceStore(this).automaticChecks)return;val state=ResourceProgress.current;val offered=ResourceStore(this).offered();val key=if(state.kind=="update_app")"app" else offered?.archive?.sha256?:return;if(key==promptedOffer)return
  if(state.kind=="available"||state.kind=="update_app"){promptedOffer=key;android.app.AlertDialog.Builder(this).setMessage(if(state.kind=="update_app")state.message else "New resources available · %.2f GB".format(offered!!.archive.bytes/1e9)).setPositiveButton(if(state.kind=="update_app")"Update app" else "Update"){_,_->if(state.kind=="update_app")startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(APP_UPDATE_URL))) else openUpdate()}.setNegativeButton("Later",null).show()}
 }
 private fun notificationSettings(){Toast.makeText(this,"Enable notifications for Pause, Resume and Exit controls",Toast.LENGTH_LONG).show();startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
 private fun toggle(){if(LiveState.current.listening){startService(Intent(this,RecognitionService::class.java).setAction(RecognitionService.PAUSE));return}
  if(!ensureResources())return
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),42);return}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),43);return}
  if(!getSystemService(NotificationManager::class.java).areNotificationsEnabled()){notificationSettings();return}
  startForegroundService(Intent(this,RecognitionService::class.java).setAction(RecognitionService.RESUME))
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==42&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)toggle();if(requestCode==43){if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)toggle() else notificationSettings()}}
 private fun visibleLabel(value:String)=if(value.contains(" ?"))"? "+value.removeSuffix(" ?") else value
 private fun limited(value:String,size:Float,lines:Int)=text(visibleLabel(value),size).apply{maxLines=lines;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=value}
 private fun details(c:Candidate){val full=listOf(c.display("orchestra"),c.display("title"),c.display("singer"),c.display("year")).filter{it.isNotEmpty()}.joinToString("\n\n");val scroll=ScrollView(this).apply{addView(text(full,19f))};android.app.AlertDialog.Builder(this).setView(scroll).setPositiveButton("Close",null).show()}
 private fun candidate(panel:LinearLayout,c:Candidate?,primary:Boolean){panel.removeAllViews();panel.setOnClickListener(null);if(c==null){panel.addView(text(if(primary) "♪" else "…",if(primary)32f else 20f));return}
  panel.setOnClickListener{details(c)}
  val row=LinearLayout(this);row.addView(limited(c.display("orchestra").ifEmpty{"?"},if(primary)25f else 18f,2).apply{setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));row.addView(text("${displayPercent(c)}%",if(primary)25f else 18f).apply{setTextColor(Color.rgb((220*(1-displayPercent(c)/100f)).toInt().coerceIn(0,220),170,95));contentDescription="Display score ${displayPercent(c)} percent, scaled and uncalibrated"});panel.addView(row);panel.addView(limited(c.display("title"),if(primary)22f else 17f,3));val detail=listOf(c.display("singer"),c.display("year")).filter{it.isNotEmpty()}.joinToString(" · ");if(detail.isNotEmpty())panel.addView(limited(detail,14f,2))
 }

 // Presentation scale only; raw scores, history storage and matcher thresholds stay unchanged.
 private fun displayPercent(c:Candidate)=(c.confidence*2).coerceIn(0,100)
 private fun render(){if(!::store.isInitialized)return;val s=LiveState.current;candidate(best,s.best,true);candidate(runner,s.second,false);listen.text=if(s.listening)"Ⅱ" else "▶";listen.contentDescription=if(s.listening)"Pause identification" else if(s.resident)"Resume identification" else "Start listening";phase.text=if(s.error.isNotEmpty())s.error else if(s.listening)"${s.phase}  ${s.seconds.toInt()}s" else if(s.resident)"Ⅱ" else "";findViewById<Button>(100).apply{alpha=if(store.audio)1f else .45f;setTextColor(if(store.audio)Color.rgb(255,85,100) else white)};findViewById<Button>(101).apply{alpha=if(store.timing)1f else .45f;background=box(if(store.timing)Color.rgb(43,100,80) else Color.rgb(43,47,64))}
  val version=LiveState.historyVersion.get();if(version!=historyVersion&&!loadingHistory){loadingHistory=true;Thread{val rows=store.history();val grouped=RecordingHistory.consecutive(rows);runOnUiThread{historyRows=grouped.asReversed().take(200);historyVersion=version;loadingHistory=false;historyAdapter.notifyDataSetChanged()}}.start()}
 }
}
