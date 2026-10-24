package org.tangowiz.app
import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*

/** Separate progress window; the application-scoped service survives navigation and recreation. */
class ExportActivity:Activity(){
 companion object{const val NEW="new_export";private const val CREATE_ZIP=80}
 private lateinit var line:TextView;private lateinit var bar:ProgressBar;private lateinit var share:ImageButton;private lateinit var save:ImageButton;private lateinit var action:Button
 private var foreground=false;private var initialized=false;private var pickerFile=""
 private val changed:()->Unit={runOnUiThread{render()}}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun button(label:String,click:()->Unit)=Button(this).apply{text=label;textSize=19f;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(43,100,80));cornerRadius=dp(24).toFloat()};setOnClickListener{click()}}
 override fun onCreate(saved:Bundle?){super.onCreate(saved);ExportJobs.restore(this);initialized=saved?.getBoolean("initialized")?:false;pickerFile=saved?.getString("picker_file")?:"";window.statusBarColor=Color.rgb(17,20,29)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(32),dp(24),dp(32));setBackgroundColor(Color.rgb(17,20,29))};setContentView(root)
  root.addView(TextView(this).apply{text="Export";textSize=27f;setTextColor(Color.WHITE);gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2))
  line=TextView(this).apply{text="Scanning files…";textSize=18f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setPadding(0,dp(22),0,dp(22))};root.addView(line,LinearLayout.LayoutParams(-1,-2))
  bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;isIndeterminate=true};root.addView(bar,LinearLayout.LayoutParams(-1,dp(14)).apply{bottomMargin=dp(22)})
  fun exportIcon(resource:Int,label:String,click:()->Unit)=ImageButton(this).apply{setImageResource(resource);contentDescription=label;tooltipText=label;background=GradientDrawable().apply{setColor(Color.rgb(43,100,80));cornerRadius=dp(24).toFloat()};setPadding(dp(18),dp(18),dp(18),dp(18));setOnClickListener{click()}}
  share=exportIcon(R.drawable.ic_export_share,"Share ZIP"){shareZip()};save=exportIcon(R.drawable.ic_export_save,"Save ZIP"){saveZip()};val ready=LinearLayout(this).apply{gravity=android.view.Gravity.CENTER};ready.addView(share,LinearLayout.LayoutParams(dp(76),dp(76)).apply{marginEnd=dp(16)});ready.addView(save,LinearLayout.LayoutParams(dp(76),dp(76)));root.addView(ready)
  action=button("Cancel export"){if(ExportJobs.state.busy)ExportJobs.cancel(this) else ExportJobs.prepare(this)};root.addView(action,LinearLayout.LayoutParams(-1,dp(64)).apply{topMargin=dp(12)})
 }
 override fun onStart(){super.onStart();foreground=true;ExportJobs.listeners.add(changed);if(!initialized){initialized=true;if(intent.getBooleanExtra(NEW,false)&&!ExportJobs.state.busy&&ExportJobs.state.kind!="interrupted"){ExportJobs.prepare(this)}};render()}
 override fun onStop(){foreground=false;ExportJobs.listeners.remove(changed);super.onStop()}
 override fun onNewIntent(value:Intent){super.onNewIntent(value);intent=value;if(value.getBooleanExtra(NEW,false)&&!ExportJobs.state.busy&&ExportJobs.state.kind!="interrupted")ExportJobs.prepare(this);render()}
 override fun onSaveInstanceState(out:Bundle){out.putBoolean("initialized",initialized);out.putString("picker_file",pickerFile);super.onSaveInstanceState(out)}
 private fun render(){if(!foreground||isFinishing||isDestroyed||!::line.isInitialized)return;val s=ExportJobs.state;val file=ExportJobs.file(this);bar.isIndeterminate=s.total<=0;bar.progress=if(s.total>0)(s.copied*100/s.total).toInt().coerceIn(0,100) else 0;bar.visibility=if(s.busy)android.view.View.VISIBLE else android.view.View.INVISIBLE
  line.text=when(s.kind){"idle","scanning"->"Scanning files…";"preparing"->"%d / %d files · %.1f / %.1f MB\n%d%%".format(s.files,s.totalFiles,s.copied/1e6,s.total/1e6,if(s.total>0)s.copied*100/s.total else 0);"saving"->"Saving ZIP… ${if(s.total>0)s.copied*100/s.total else 0}%";"ready"->if(s.error.isNotEmpty())s.error else "ZIP ready · %.1f MB".format((file?.length()?:0)/1e6);else->s.error.ifEmpty{"Export interrupted. Retry."}}
  val ready=!s.busy&&file!=null;share.visibility=if(ready)android.view.View.VISIBLE else android.view.View.GONE;save.visibility=share.visibility;share.isEnabled=ready;save.isEnabled=ready;action.visibility=if(ready)android.view.View.GONE else android.view.View.VISIBLE;action.text=if(s.busy)"Cancel export" else "Retry";action.isEnabled=true
 }
 private fun shareZip(){val file=ExportJobs.file(this)?:return ExportJobs.error(this,"ZIP is no longer available. Create it again.");try{val uri=ExportJobs.uri(file);val send=Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("TangoWiz export",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(send,"Share ZIP").apply{addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);clipData=send.clipData})}catch(e:Exception){ExportJobs.error(this,"Could not open sharing: ${e.message}. Use Save ZIP instead.")}}
 private fun saveZip(){val file=ExportJobs.file(this)?:return ExportJobs.error(this,"ZIP is no longer available. Create it again.");try{pickerFile=file.name;getSharedPreferences("export-status",0).edit().putString("picker_file",pickerFile).commit();startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/zip";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,file.name)},CREATE_ZIP)}catch(e:Exception){ExportJobs.error(this,"Could not open Save ZIP: ${e.message}. You can still share the ZIP.")}}
 @android.annotation.SuppressLint("WrongConstant") // Flags are masked to the two URI permission bits returned by DocumentsUI.
 @Deprecated("Platform result API") override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(request!=CREATE_ZIP||result!=RESULT_OK)return;val uri=data?.data?:return ExportJobs.error(this,"No save destination was returned. Try Save ZIP again.");try{require(uri.scheme=="content"){"Choose a document destination"};getSharedPreferences("export-status",0).edit().putString("last_saved_uri",uri.toString()).commit();val flags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if(data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION!=0)contentResolver.takePersistableUriPermission(uri,flags);val name=pickerFile.ifEmpty{getSharedPreferences("export-status",0).getString("picker_file","")?:""};ExportJobs.save(this,uri,name)}catch(e:Exception){ExportJobs.error(this,"Could not save ZIP: ${e.message}")}}
}
