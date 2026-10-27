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
 private var pendingWatchStart=false
 private val resourceChanged:()->Unit={runOnUiThread{showUpdateOffer()}}
 private val changed:()->Unit={runOnUiThread{if(LiveState.current.exited)finishAndRemoveTask() else render()}}
 private val exitReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){finishAndRemoveTask()}}
 private val bg=Color.rgb(17,20,29)
 private val white=Color.rgb(239,240,246)
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun box(color:Int,radius:Float=24f)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius.toInt()).toFloat()}
 private fun text(s:String,size:Float=18f)=TextView(this).apply{text=s;textSize=size;setTextColor(white);setPadding(dp(16),dp(5),dp(16),dp(5))}
 private fun icon(s:String,hint:String,action:()->Unit)=Button(this).apply{text=s;textSize=25f;contentDescription=hint;tooltipText=hint;minWidth=dp(60);minimumWidth=dp(60);minHeight=dp(60);background=box(Color.rgb(43,47,64));setTextColor(white);setOnClickListener{action()}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(!ensureResources())return;if(LiveState.current.exited)LiveState.publish(RecognitionState());if(Build.VERSION.SDK_INT>=33)registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY),Context.RECEIVER_NOT_EXPORTED) else registerReceiver(exitReceiver,IntentFilter(RecognitionService.EXIT_ACTIVITY));receiverRegistered=true;store=Store(this);PhoneWatch.init(this);window.statusBarColor=bg;window.navigationBarColor=bg
  val root=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(20),dp(16),dp(12));setBackgroundColor(bg)};setContentView(root)
  root.setOnApplyWindowInsetsListener{v,insets-> if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom)}else{v.setPadding(dp(16)+insets.systemWindowInsetLeft,dp(12)+insets.systemWindowInsetTop,dp(16)+insets.systemWindowInsetRight,dp(12)+insets.systemWindowInsetBottom)};insets}
  val toolbar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};root.addView(toolbar)
  toolbar.addView(ImageView(this).apply{setImageResource(org.tangowiz.app.R.drawable.app_icon);background=box(Color.rgb(245,236,220),12f);setPadding(dp(3),dp(3),dp(3),dp(3));contentDescription="TangoWiz"},LinearLayout.LayoutParams(dp(48),dp(48)))
  toolbar.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
  val audio=icon("●","Save audio"){PhoneWatch.setLocal(this,org.json.JSONObject().put("audio",!store.audio));render()};audio.id=100;toolbar.addView(audio)
  val debug=icon("🐞","Timing debug"){PhoneWatch.setLocal(this,org.json.JSONObject().put("timing",!store.timing));render()};debug.id=101;toolbar.addView(debug)
  val watch=icon("⌚","Use watch microphone"){PhoneWatch.setLocal(this,org.json.JSONObject().put("microphone",if(store.microphone=="watch")"phone" else "watch").put("listening",true))};watch.id=102;toolbar.addView(watch)
  toolbar.addView(icon("⋮","More options"){vMenu(toolbar)})
  phase=text("",14f);root.addView(phase)
  best=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(14),dp(8),dp(14));background=box(Color.rgb(34,39,54))};root.addView(best,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  runner=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(8),dp(8),dp(8));background=box(Color.rgb(28,32,45))};root.addView(runner,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
  val recognitionControls=LinearLayout(this);root.addView(recognitionControls,LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16);bottomMargin=dp(16)})
  listen=icon("▶","Start listening"){toggle()};recognitionControls.addView(listen,LinearLayout.LayoutParams(0,-1,1f))
  recognitionControls.addView(icon("↺","Reset recognition, keep last two seconds"){if(LiveState.current.listening)startService(Intent(this,RecognitionService::class.java).setAction(RecognitionService.RESET))}.apply{id=103},LinearLayout.LayoutParams(dp(76),-1).apply{leftMargin=dp(8)})
  historyAdapter=object:BaseAdapter(){override fun getCount()=historyRows.size;override fun getItem(p:Int)=historyRows[p];override fun getItemId(p:Int)=p.toLong();override fun getView(p:Int,old:View?,parent:android.view.ViewGroup?):View {val r=historyRows[p];val credit=Candidate.fromJson(r);val item=(old as? LinearLayout)?:LinearLayout(this@MainActivity).apply{orientation=1;setPadding(0,dp(9),0,dp(9));addView(text("",16f));addView(text("",17f))};val time=runCatching{DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(r.getString("time")))}.getOrDefault("");val header="$time   ${visibleLabel(credit.display("orchestra").ifEmpty{"?"})}   ${r.optInt("confidence_score")}%";val detail=listOf(credit.display("title"),credit.display("singer"),credit.display("year")).filter{it.isNotEmpty()}.joinToString(" · ");(item.getChildAt(0) as TextView).apply{text=header;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=header};(item.getChildAt(1) as TextView).apply{text=visibleLabel(detail);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=detail};item.setOnClickListener{details(credit)};item.alpha=if(r.optString("state")=="strong")1f else .65f;return item}}
  history=ListView(this).apply{adapter=historyAdapter;divider=null;setBackgroundColor(bg)};root.addView(history,LinearLayout.LayoutParams(-1,0,1f));render();handleWatchLaunch()
 }
 override fun onStart(){super.onStart();if(!::store.isInitialized||!ensureResources())return;foreground=true;LiveState.listeners.add(changed);ResourceProgress.listeners.add(resourceChanged);render();showUpdateOffer();if(ResourceStore(this).dueAutomaticCheck())window.decorView.post{if(foreground)startForegroundService(Intent(this,ResourceDownloadService::class.java).setAction("CHECK").putExtra("download",false))}}
 override fun onStop(){foreground=false;LiveState.listeners.remove(changed);ResourceProgress.listeners.remove(resourceChanged);super.onStop()}
 override fun onDestroy(){if(receiverRegistered)unregisterReceiver(exitReceiver);super.onDestroy()}
 override fun onNewIntent(value:Intent){super.onNewIntent(value);intent=value;if(ensureResources())handleWatchLaunch()}
 private fun ensureResources():Boolean{if(ResourceStore(this).current()!=null)return true;startActivity(Intent(this,SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return false}
 private fun openUpdate(){startActivity(Intent(this,SetupActivity::class.java).putExtra(SetupActivity.UPDATE,true))}
 private fun vMenu(anchor:View){val r=ResourceStore(this);val menu=PopupMenu(this,anchor);menu.menu.add(0,1,0,"Update");menu.menu.add(0,3,2,"Export debug info");menu.menu.add(0,4,3,"Clear older history and recordings");menu.menu.add(0,5,4,"Watch updates");menu.menu.add(0,2,1,"Automatic update checks").apply{isCheckable=true;isChecked=r.automaticChecks};menu.setOnMenuItemClickListener{item->when(item.itemId){1->openUpdate();5->watchUpdates();4->confirmCleanup();3->startActivity(Intent(this,ExportActivity::class.java).putExtra(ExportActivity.NEW,true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));2->{r.automaticChecks=!r.automaticChecks;item.isChecked=r.automaticChecks}};true};menu.show()}
 private fun confirmCleanup(){
  android.app.AlertDialog.Builder(this)
   .setTitle("Clear older history and recordings?")
   .setMessage("Deletes history, audio, fingerprints and debug logs from completed sessions. The active session is kept and listening continues. Exported ZIP files are kept.")
   .setNegativeButton("Cancel",null)
   .setPositiveButton("Clear"){_,_->
    val progress=android.app.AlertDialog.Builder(this).setMessage("Clearing older sessions…").setCancelable(false).create()
    progress.show()
    Thread{
     val result=runCatching{SessionFiles.clearOlder(applicationContext)}
     runOnUiThread{
      if(!isFinishing&&!isDestroyed){
       progress.dismiss()
       render()
       val message=result.fold(
        onSuccess={r->if(r.failed==0)"Older sessions cleared · %.1f MB freed".format(r.bytes/1e6) else "Cleanup incomplete: ${r.failed} items could not be removed. The active session was kept."},
        onFailure={e->e.message?:"Could not clear older sessions"}
       )
       android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show()
      }
     }
    }.start()
   }.show()
 }
 private fun showUpdateOffer(){if(!foreground||isFinishing||isDestroyed||!ResourceStore(this).automaticChecks)return;val state=ResourceProgress.current;val offered=ResourceStore(this).offered();val key=if(state.kind=="update_app")"app" else offered?.archive?.sha256?:return;if(key==promptedOffer)return
  if(state.kind=="available"||state.kind=="update_app"){promptedOffer=key;android.app.AlertDialog.Builder(this).setMessage(if(state.kind=="update_app")state.message else "New resources available · %.2f GB".format(offered!!.archive.bytes/1e9)).setPositiveButton(if(state.kind=="update_app")"Update app" else "Update"){_,_->if(state.kind=="update_app")startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(APP_UPDATE_URL))) else openUpdate()}.setNegativeButton("Later",null).show()}
 }
 private fun notificationSettings(){Toast.makeText(this,"Enable notifications for Pause, Resume and Exit controls",Toast.LENGTH_LONG).show();startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
 private fun handleWatchLaunch(){if(intent?.action=="WATCH_START"){intent.action=null;pendingWatchStart=true;if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true)};window.decorView.post{startListening()}}}
 private fun watchUpdates(){val times=longArrayOf(2000,5000,10000,30000,60000);android.app.AlertDialog.Builder(this).setTitle("Watch updates").setSingleChoiceItems(arrayOf("2 seconds","5 seconds","10 seconds","30 seconds","60 seconds"),times.indexOf(store.watchUpdateInterval)){dialog,index->PhoneWatch.setLocal(this,org.json.JSONObject().put("updateIntervalMs",times[index]));dialog.dismiss()}.setNegativeButton("Cancel",null).show()}
 private fun toggle(){if(LiveState.current.listening){PhoneWatch.setLocal(this,org.json.JSONObject().put("listening",false));return};startListening()}
 private fun startListening(){
  if(!ensureResources())return
  if(store.microphone=="phone"&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),42);return}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),43);return}
  if(!getSystemService(NotificationManager::class.java).areNotificationsEnabled()){notificationSettings();return}
  pendingWatchStart=false
  startForegroundService(Intent(this,RecognitionService::class.java).setAction(if(LiveState.current.listening)RecognitionService.RESTART else RecognitionService.RESUME))
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==42&&grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startListening();if(requestCode==43){if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startListening() else notificationSettings()}}
 private fun visibleLabel(value:String)=if(value.contains(" ?"))"? "+value.removeSuffix(" ?") else value
 private fun limited(value:String,size:Float,lines:Int)=text(visibleLabel(value),size).apply{maxLines=lines;ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=value}
 private fun details(c:Candidate){val full=listOf(c.display("orchestra"),c.display("title"),c.display("singer"),c.display("year")).filter{it.isNotEmpty()}.joinToString("\n\n");val scroll=ScrollView(this).apply{addView(text(full,19f))};android.app.AlertDialog.Builder(this).setView(scroll).setPositiveButton("Close",null).show()}
 private fun candidate(panel:LinearLayout,c:Candidate?,primary:Boolean){panel.removeAllViews();panel.setOnClickListener(null);if(c==null){panel.addView(text(if(primary) "♪" else "…",if(primary)32f else 20f));return}
  panel.setOnClickListener{details(c)}
  val percent=ConfidenceCalibration.percent(ResourceStore(this).current(),c.score)
  val row=LinearLayout(this);row.addView(limited(c.display("orchestra").ifEmpty{"?"},if(primary)25f else 18f,2).apply{setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f));row.addView(text("${percent}%",if(primary)25f else 18f).apply{setTextColor(Color.rgb((220*(1-percent/100f)).toInt().coerceIn(0,220),170,95));contentDescription="Experimental confidence estimate ${percent} percent"});panel.addView(row);panel.addView(limited(c.display("title"),if(primary)22f else 17f,3));val detail=listOf(c.display("singer"),c.display("year")).filter{it.isNotEmpty()}.joinToString(" · ");if(detail.isNotEmpty())panel.addView(limited(detail,14f,2))
 }

 private fun render(){if(!::store.isInitialized)return;findViewById<Button>(102)?.apply{alpha=if(store.microphone=="watch")1f else .45f;background=box(if(store.microphone=="watch")Color.rgb(43,100,80) else Color.rgb(43,47,64));contentDescription=if(store.microphone=="watch")"Switch to phone microphone" else "Use watch microphone"};val s=LiveState.current;findViewById<Button>(103)?.apply{isEnabled=s.listening;alpha=if(s.listening)1f else .45f};candidate(best,s.best,true);candidate(runner,s.second,false);listen.text=if(s.listening)"Ⅱ" else "▶";listen.contentDescription=if(s.listening)"Pause identification" else if(s.resident)"Resume identification" else "Start listening";phase.text=if(s.error.isNotEmpty())s.error else if(s.listening)"${s.phase}  ${s.seconds.toInt()}s" else if(s.resident)"Ⅱ" else "";findViewById<Button>(100).apply{alpha=if(store.audio)1f else .45f;setTextColor(if(store.audio)Color.rgb(255,85,100) else white)};findViewById<Button>(101).apply{alpha=if(store.timing)1f else .45f;background=box(if(store.timing)Color.rgb(43,100,80) else Color.rgb(43,47,64))}
  val version=LiveState.historyVersion.get();if(version!=historyVersion&&!loadingHistory){loadingHistory=true;Thread{val rows=store.history();val grouped=RecordingHistory.consecutive(rows);runOnUiThread{loadingHistory=false;if(LiveState.historyVersion.get()==version){historyRows=grouped.asReversed().take(200);historyVersion=version;historyAdapter.notifyDataSetChanged()}else render()}}.start()}
 }
}
