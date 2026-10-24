package org.tangowiz.app
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class ExportState(val kind:String="idle",val copied:Long=0,val total:Long=0,val files:Int=0,val totalFiles:Int=0,val fileName:String="",val error:String=""){
 val busy get()=kind in setOf("scanning","preparing","saving")
}
object ExportJobs{
 val listeners=CopyOnWriteArrayList<()->Unit>()
 @Volatile var state=ExportState();private set
 private var restored=false
 @Synchronized fun restore(context:Context){if(restored)return;restored=true;val text=context.getSharedPreferences("export-status",0).getString("state",null)
  state=runCatching{val j=JSONObject(text?:"{}");ExportState(j.optString("kind","idle"),j.optLong("copied"),j.optLong("total"),j.optInt("files"),j.optInt("totalFiles"),j.optString("file"),j.optString("error"))}.getOrDefault(ExportState())
  if(state.busy)publish(context,state.copy(kind="interrupted",error="The previous export was interrupted. Retry; your recordings are unchanged."))
  else if(state.fileName.isNotEmpty()&&file(context)==null)publish(context,ExportState(kind="error",error="The saved export is no longer available. Create it again."))
 }
 @Synchronized fun publish(context:Context,value:ExportState){state=value;val j=JSONObject().put("kind",value.kind).put("copied",value.copied).put("total",value.total).put("files",value.files).put("totalFiles",value.totalFiles).put("file",value.fileName).put("error",value.error);context.getSharedPreferences("export-status",0).edit().putString("state",j.toString()).commit();listeners.forEach{it()}}
 fun file(context:Context,name:String=state.fileName):File?{if(!Regex("tangowiz-debug-[0-9]+\\.zip").matches(name))return null;return File(context.cacheDir,name).takeIf{it.isFile}}
 fun uri(file:File)=Uri.parse("content://org.tangowiz.app.exports/${file.name}")
 @Synchronized fun prepare(context:Context){restore(context);if(state.busy)return;if(ExportService.isBusy()){publish(context,state.copy(error="Previous export is stopping. Retry shortly."));return};publish(context,ExportState("scanning"));try{context.startForegroundService(Intent(context,ExportService::class.java).setAction("PREPARE"))}catch(e:Exception){publish(context,ExportState("error",error="Could not start export: ${e.message}"))}}
 fun save(context:Context,uri:Uri,name:String){if(state.busy||ExportService.isBusy())return;publish(context,ExportState("saving",fileName=name));try{context.startForegroundService(Intent(context,ExportService::class.java).setAction("SAVE").putExtra("destination",uri.toString()).putExtra("file",name))}catch(e:Exception){publish(context,state.copy(kind="error",error="Could not save ZIP: ${e.message}"))}}
 fun cancel(context:Context){context.startService(Intent(context,ExportService::class.java).setAction("CANCEL"))}
 fun error(context:Context,message:String){publish(context,state.copy(kind="error",error=message))}
}
