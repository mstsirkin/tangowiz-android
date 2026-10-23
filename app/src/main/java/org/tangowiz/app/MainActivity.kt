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
 private lateinit var resourceLine:TextView
 private lateinit var resourceButton:Button
 private var openedResources=false
 private var promptedUpdate=false
 private val resourceChanged:()->Unit={runOnUiThread{renderResources()}}
 private val changed:()->Unit={runOnUiThread{if(LiveState.current.exited)finishAndRemoveTask() else render()}}
 private val exitReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){finishAndRemoveTask()}}
 private val bg=Color.rgb(17,20,29)
 private val white=Color.rgb(239,240,246)
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun box(color:Int,radius:Float=24f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.toInt()).toFloat()}
 private fun text(s:String,size:Float=18f)=TextView(this).apply{text=s;textSize=size;setTextColor(white);setPadding(dp(16),dp(5),dp(16),dp(5))}
 private fun icon(s:String,hint:String,action:()->Unit)=Button(this).apply{text=s;textSize=25f;contentDescription=hint;tooltipText=hint;minWidth=dp(60);minimumWidth=dp(60);minHeight=dp(60);background=box(Color.rgb(43,47,64));setTextColor(white);setOnClickListener{action()}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(LiveState.current.exited)LiveState.publish(RecognitionState());if(Build.VERSION.SDK_INT>=33)registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY),Context.RECEIVER_NOT_EXPORTED) else registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY));store=Store(this);window.statusBarColor=bg;window.navigationBarColor=bg
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(20),dp(16),dp(12));setBackgroundColor(bg)};setContentView(root)
  root.setOnApplyWindowInsetsListener{v,insets-> if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom)}else{v.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(12)+insets.systemWindowInsetTop,dp(16)+insets.systemWindowInsetRight,dp(12)+insets.systemWindowInsetBottom)};insets}
  val toolbar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};root.addView(toolbar)
  toolbar.addView(ImageView(this).apply{setImageResource(org.tangowiz.app.R.drawable.app_icon);background=box(Color.rgb(245,236,220),12f);setPadding(dp(3),dp(3),dp(3),dp(3));contentDescription="TangoWiz"},LinearLayout.LayoutParams(dp(48),dp(48)))
  toolbar.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
  val audio=icon("●","Save audio"){store.audio=!store.audio;render()};audio.id=100;toolbar.addView(audio)
  val debug=icon("🐞","Timing debug"){store.timing=!store.timing;render()};debug.id=101;toolbar.addView(debug)
  toolbar.addView(icon("↗","Export recordings and diagnostics"){Thread{runCatching{store.export()}.onSuccess{file->runOnUiThread{startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,Uri.parse("content://org.tangowiz.app.exports/${file.name}"));addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},""))}}.onFailure{runOnUiThread{Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()}}}.start()})
  toolbar.addView(icon("⋮","More options"){vMenu(toolbar)})
  val resources=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};resourceLine=text("",14f);resources.addView(resourceLine,LinearLayout.LayoutParams(0,-2,1f));resourceButton=icon("↓","Download or retry resources"){resourceAction()};resources.addView(resourceButton);root.addView(resources)
  phase=text("",14f);root.addView(phase)
  best=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(14),dp(8),dp(14));background=box(Color.rgb(34,39,54))};root.addView(best,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  runner=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(8),dp(8),dp(8));background=box(Color.rgb(28,32,45))};root.addView(runner,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  listen=icon("▶","Start listening"){toggle()};root.addView(listen,LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16);bottomMargin=dp(16)})
  historyAdapter=object:BaseAdapter(){override fun getCount()=historyRows.size;override fun getItem(p:Int)=historyRows[p];override fun getItemId(p:Int)=p.toLong();override fun getView(p:Int,old:View?,parent:android.view.ViewGroup?):View {val r=historyRows[p];val item=(old as? LinearLayout)?:LinearLayout(this@MainActivity).apply{orientation=1;setPadding(0,dp(9),0,dp(9));addView(text("",16f));addView(text("",17f))};val time=runCatching{DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(r.getString("time")))}.getOrDefault("");(item.getChildAt(0) as TextView).text="$time   ${r.optString("orchestra").ifEmpty{"?"}}   ${r.optInt("confidence_score")}%";(item.getChildAt(1) as TextView).text=r.optString("title");item.alpha=if(r.optString("state")=="strong")1f else .65f;return item}}
  history=ListView(this).apply{adapter=historyAdapter;divider=null;setBackgroundColor(bg)};root.addView(history,LinearLayout.LayoutParams(-1,0,1f));render()
 }
 override fun onStart(){super.onStart();LiveState.listeners.add(changed);ResourceProgress.listeners.add(resourceChanged);render();renderResources();if(!openedResources){openedResources=true;window.decorView.post{bootstrapResources()}}else if(ResourceStore(this).dueAutomaticCheck())window.decorView.post{runResources(download=false)}}
 override fun onStop(){LiveState.listeners.remove(changed);ResourceProgress.listeners.remove(resourceChanged);super.onStop()}
 override fun onDestroy(){unregisterReceiver(exitReceiver);super.onDestroy()}
 private fun runResources(action:String="CHECK",download:Boolean=true){startForegroundService(Intent(this,ResourceDownloadService::class.java).setAction(action).putExtra("download",download))}
 private fun bootstrapResources(){val r=ResourceStore(this);when{r.hasPending()->runResources("RESUME");r.current()==null&&r.offered()!=null->ResourceProgress.publish(ResourceState("available","Download resources",total=r.offered()!!.archive.bytes));r.current()==null->runResources(download=false);r.dueAutomaticCheck()->runResources(download=false)}}
 private fun resourceAction(){val r=ResourceStore(this);when(ResourceProgress.current.kind){"update_app"->startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(APP_UPDATE_URL)));"checking","downloading","verifying"->Unit;else->when{r.hasPending()->runResources("RESUME");r.offered()!=null->runResources("ACCEPT");else->runResources(download=false)}}}
 private fun vMenu(anchor:View){val r=ResourceStore(this);val menu=PopupMenu(this,anchor);menu.menu.add(0,1,0,"Update");menu.menu.add(0,2,1,"Automatic update checks").apply{isCheckable=true;isChecked=r.automaticChecks};menu.setOnMenuItemClickListener{item->when(item.itemId){1->runResources(download=false);2->{r.automaticChecks=!r.automaticChecks;item.isChecked=r.automaticChecks}};true};menu.show()}
 private fun renderResources(){if(!::resourceLine.isInitialized)return;val s=ResourceProgress.current;val have=ResourceStore(this).current()!=null;val visible=s.kind !in setOf("idle","ready")||!have;resourceLine.visibility=if(visible)View.VISIBLE else View.GONE;resourceButton.visibility=if(visible)View.VISIBLE else View.GONE;resourceButton.isEnabled=s.kind !in setOf("checking","downloading","verifying");resourceButton.text=if(s.kind=="update_app")"↗" else "↓";resourceLine.text=when{ s.kind=="available"&&s.total>0->"Download %.2f GB".format(s.total/1e9);s.kind=="verifying"&&s.total>0->"Verifying ${s.received*100/s.total}%";s.kind=="downloading"&&s.total>0->"${s.received*100/s.total}% · %.2f / %.2f GB".format(s.received/1e9,s.total/1e9);s.message.isNotEmpty()->s.message;!have->"Download resources to begin";else->""};if(s.kind=="update_app"&&!promptedUpdate){promptedUpdate=true;android.app.AlertDialog.Builder(this).setMessage(s.message+". Your installed resources are kept.").setPositiveButton("Update app"){_,_->resourceAction()}.setNegativeButton("Later",null).show()}}
 private fun notificationSettings(){Toast.makeText(this,"Enable notifications for Pause, Resume and Exit controls",Toast.LENGTH_LONG).show();startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
 private fun toggle(){if(LiveState.current.listening){startService(Intent(this,RecognitionService::class.java).setAction(RecognitionService.PAUSE));return}
  if(ResourceStore(this).current()==null){resourceAction();return}
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),42);return}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),43);return}
  if(!getSystemService(NotificationManager::class.java).areNotificationsEnabled()){notificationSettings();return}
  startForegroundService(Intent(this,RecognitionService::class.java).setAction(RecognitionService.RESUME))
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==42&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)toggle();if(requestCode==43){if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)toggle() else notificationSettings()}}
 private fun candidate(panel:LinearLayout,c:Candidate?,primary:Boolean){panel.removeAllViews();if(c==null){panel.addView(text(if(primary) "♪" else "…",if(primary)32f else 20f));return}
  val row=LinearLayout(this);row.addView(text(c.orchestra.ifEmpty{"?"},if(primary)25f else 18f).apply{setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));row.addView(text("${c.confidence}%",if(primary)25f else 18f).apply{setTextColor(Color.rgb((220*(1-c.confidence/100f)).toInt().coerceIn(0,220),170,95));contentDescription="Confidence score ${c.confidence} percent, uncalibrated"});panel.addView(row);panel.addView(text(c.title,if(primary)22f else 17f));val detail=listOf(c.singer,c.year).filter{it.isNotEmpty()}.joinToString(" · ");if(detail.isNotEmpty())panel.addView(text(detail,14f))
 }
 private fun render(){if(!::store.isInitialized)return;val s=LiveState.current;candidate(best,s.best,true);candidate(runner,s.second,false);listen.text=if(s.listening)"Ⅱ" else "▶";listen.contentDescription=if(s.listening)"Pause identification" else if(s.resident)"Resume identification" else "Start listening";phase.text=if(s.error.isNotEmpty())s.error else if(s.listening)"${s.phase}  ${s.seconds.toInt()}s" else if(s.resident)"Ⅱ" else "";findViewById<Button>(100).apply{alpha=if(store.audio)1f else .45f;setTextColor(if(store.audio)Color.rgb(255,85,100) else white)};findViewById<Button>(101).apply{alpha=if(store.timing)1f else .45f;background=box(if(store.timing)Color.rgb(43,100,80) else Color.rgb(43,47,64))}
  val version=LiveState.historyVersion.get();if(version!=historyVersion&&!loadingHistory){loadingHistory=true;Thread{val rows=store.history();val grouped=mutableListOf<org.json.JSONObject>();for(r in rows){if(grouped.lastOrNull()?.optString("id")==r.optString("id")&&grouped.lastOrNull()?.optString("session")==r.optString("session"))grouped[grouped.lastIndex]=r else grouped.add(r)};runOnUiThread{historyRows=grouped.asReversed().take(200);historyVersion=version;loadingHistory=false;historyAdapter.notifyDataSetChanged()}}.start()}
 }
}
