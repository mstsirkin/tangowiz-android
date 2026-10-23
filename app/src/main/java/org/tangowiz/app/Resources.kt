package org.tangowiz.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipInputStream
import org.tensorflow.lite.Interpreter
import java.util.UUID

const val RESOURCE_FORMAT=2
const val RESOURCE_MANIFEST_URL="https://github.com/mstsirkin/tangowiz-android/releases/download/resources-v2/manifest.json"
const val APP_UPDATE_URL="https://github.com/mstsirkin/tangowiz-android/releases/latest"
class AppUpdateRequired(message:String):IOException(message)
fun validateCatalogFormat(bundle:BundleSnapshot){val expected="TWIDX00${bundle.formatVersion}".toByteArray(Charsets.US_ASCII);val actual=ByteArray(8);FileInputStream(bundle.catalog).use{input->var count=0;while(count<8){val n=input.read(actual,count,8-count);require(n>0){"Truncated catalog header"};count+=n}};require(actual.contentEquals(expected)){"Manifest format does not match catalog format"}}
data class ResourceState(val kind:String="idle",val message:String="",val received:Long=0,val total:Long=0)
object ResourceProgress {
 @Volatile var current=ResourceState()
 val listeners=CopyOnWriteArrayList<()->Unit>()
 fun publish(value:ResourceState){current=value;listeners.forEach{it()}}
}
data class BundleSnapshot(val directory:File,val version:String,val archiveHash:String,val formatVersion:Int=1){val catalog get()=File(directory,"catalog.bin");val model get()=File(directory,"nmfp.tflite")}
data class ResourceFile(val bytes:Long,val sha256:String)
data class ResourceManifest(val version:String,val archiveUrl:String,val archive:ResourceFile,val files:Map<String,ResourceFile>,val json:JSONObject,val formatVersion:Int=1){
 companion object {
  fun parse(json:JSONObject):ResourceManifest {
   try{
    val format=json.getInt("formatVersion");if(format !in 1..RESOURCE_FORMAT)throw AppUpdateRequired("This resource format needs a newer app")
    val version=json.getString("bundleVersion");require(version.isNotBlank()&&version.length<=128)
    fun file(j:JSONObject,max:Long):ResourceFile{val size=j.getLong("bytes");val hash=j.getString("sha256").lowercase();require(size in 1..max&&hash.matches(Regex("[0-9a-f]{64}")));return ResourceFile(size,hash)}
    val a=json.getJSONObject("archive");val url=a.getString("url");require(URL(url).protocol=="https"||URL(url).host in setOf("127.0.0.1","localhost"))
    val f=json.getJSONObject("files");require(f.keys().asSequence().toSet()==setOf("catalog.bin","nmfp.tflite"))
    return ResourceManifest(version,url,file(a,9L*1024*1024*1024),mapOf("catalog.bin" to file(f.getJSONObject("catalog.bin"),8L*1024*1024*1024),"nmfp.tflite" to file(f.getJSONObject("nmfp.tflite"),512L*1024*1024)),json,format)
   }catch(e:AppUpdateRequired){throw e}catch(e:Exception){throw AppUpdateRequired("The resource manifest is incompatible; update the app")}
  }
 }
}
class RemoteResponse(val code:Int,val headers:Map<String,String>,val body:InputStream,private val done:()->Unit={}):Closeable{fun header(name:String)=headers.entries.firstOrNull{it.key.equals(name,true)}?.value;override fun close(){try{body.close()}finally{done()}}}
fun interface ResourceTransport {fun open(url:String,headers:Map<String,String>):RemoteResponse;fun cancel(){}}
class HttpResourceTransport:ResourceTransport {
 private val connections=java.util.concurrent.ConcurrentHashMap<HttpURLConnection,Boolean>()
 override fun cancel(){connections.keys.forEach{it.disconnect()}}
 override fun open(url:String,headers:Map<String,String>):RemoteResponse {
  val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=20000;c.readTimeout=30000;c.instanceFollowRedirects=true;c.setRequestProperty("Accept-Encoding","identity");headers.forEach{(k,v)->c.setRequestProperty(k,v)}
  connections[c]=true;try{val code=c.responseCode;val h=c.headerFields.filterKeys{it!=null}.mapKeys{it.key!!}.mapValues{it.value.joinToString(",")};return RemoteResponse(code,h,if(code in 200..299)c.inputStream else c.errorStream?:ByteArrayInputStream(byteArrayOf())){connections.remove(c);c.disconnect()}}catch(e:Exception){connections.remove(c);c.disconnect();throw e}
 }
}
object ResourcePins {
 val lock=Any()
 val held=mutableMapOf<String,Int>()
 fun release(bundle:BundleSnapshot){synchronized(lock){val key=bundle.directory.absolutePath;val n=(held[key]?:1)-1;if(n<=0)held.remove(key) else held[key]=n}}
}
/** Immutable generations plus an atomic pointer; network and ZIP work stay off the UI thread. */
class ResourceStore(context:Context,private val transport:ResourceTransport=HttpResourceTransport(),private val manifestUrl:String=RESOURCE_MANIFEST_URL,private val progress:(ResourceState)->Unit={ResourceProgress.publish(it)},private val validate:(BundleSnapshot)->Unit={bundle->
 var native=0L;try{validateCatalogFormat(bundle);native=NativeEngine.initFile(bundle.catalog.absolutePath);FileInputStream(bundle.model).channel.use{ch->Interpreter(ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,ch.size()),Interpreter.Options().setNumThreads(1)).use{model->require(model.getInputTensor(0).shape().contentEquals(intArrayOf(1,256,33,1))&&model.getOutputTensor(0).shape().contentEquals(intArrayOf(1,128))){"Unsupported recognition model shape"}}}}finally{if(native!=0L)NativeEngine.close(native)}
},private val beforeActivationCommit:()->Unit={}){
 val root=File(context.filesDir,"resources-v1").apply{mkdirs()}
 private val pointer=AtomicFile(File(root,"active.json"))
 private val pending=File(root,"pending.json")
 private val offer=File(root,"offer.json")
 private val prefs=context.getSharedPreferences("settings",0)
 var automaticChecks:Boolean get()=prefs.getBoolean("automatic_update_checks",false);set(value){prefs.edit().putBoolean("automatic_update_checks",value).apply()}
 fun dueAutomaticCheck()=automaticChecks&&System.currentTimeMillis()-prefs.getLong("last_resource_check",0)>24*60*60*1000L
 fun hasPending()=pending.isFile
 fun cancel(){transport.cancel()}
 private fun write(file:File,j:JSONObject){val a=AtomicFile(file);val out=a.startWrite();try{out.write(j.toString().toByteArray());out.fd.sync();a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e}}
 fun current():BundleSnapshot?=synchronized(ResourcePins.lock){runCatching{
  val j=pointer.openRead().use{JSONObject(it.bufferedReader().readText())};val m=ResourceManifest.parse(j);val directory=j.getString("directory");require(directory.matches(Regex("bundle-[a-f0-9]{64}-[a-f0-9-]{36}")));val dir=File(root,directory)
  require(File(dir,"verified.json").isFile&&m.files.all{(n,v)->File(dir,n).isFile&&File(dir,n).length()==v.bytes});BundleSnapshot(dir,m.version,m.archive.sha256,m.formatVersion)
 }.getOrNull()}
 fun pinCurrent():BundleSnapshot?=synchronized(ResourcePins.lock){current()?.also{val key=it.directory.absolutePath;ResourcePins.held[key]=(ResourcePins.held[key]?:0)+1}}
 fun cleanupUnused(){synchronized(ResourcePins.lock){val active=current()?.directory?.absolutePath;root.listFiles()?.filter{it.isDirectory&&it.name.startsWith("bundle-")&&it.absolutePath!=active&&it.absolutePath !in ResourcePins.held}?.forEach{it.deleteRecursively()}}}
 fun offered():ResourceManifest?=runCatching{ResourceManifest.parse(JSONObject(offer.readText()))}.getOrNull()?.takeIf{it.archive.sha256!=current()?.archiveHash}
 fun check(download:Boolean):Boolean {
  progress(ResourceState("checking","Checking updates…"))
  val m=transport.open(manifestUrl,emptyMap()).use{r->
   if(r.code==404||r.code==410)throw AppUpdateRequired("Resources for this app format are no longer available")
   if(r.code!=200)throw IOException("Update check failed (HTTP ${r.code}); retry later")
   val text=r.body.readBytesLimited(128*1024).toString(Charsets.UTF_8);ResourceManifest.parse(runCatching{JSONObject(text)}.getOrElse{throw AppUpdateRequired("The resource manifest needs a newer app")})
  }
  prefs.edit().putLong("last_resource_check",System.currentTimeMillis()).apply()
  if(current()?.archiveHash==m.archive.sha256){progress(ResourceState("ready","Up to date"));return false}
  write(offer,m.json)
  if(download){write(pending,m.json);install(m);return true}
  progress(ResourceState("available","New resources available",total=m.archive.bytes));return false
 }
 fun resume():Boolean{val m=ResourceManifest.parse(JSONObject(pending.readText()));install(m);return true}
 fun acceptOffer():Boolean{val m=offered()?:throw IOException("Check for updates first");write(pending,m.json);install(m);return true}
 private fun sha(file:File,onBytes:(Long)->Unit={}):String{var count=0L;val digest=MessageDigest.getInstance("SHA-256");file.inputStream().buffered().use{input->val b=ByteArray(262144);while(true){if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue");val n=input.read(b);if(n<0)break;digest.update(b,0,n);count+=n;onBytes(count)}};return digest.digest().joinToString(""){"%02x".format(it)}}
 private fun install(m:ResourceManifest){
  val part=File(root,"${m.archive.sha256}.part");if(part.length()>m.archive.bytes)part.delete()
  val single=File(root,"${m.archive.sha256}.single")
  if(!single.exists()&&!ParallelArchive(transport,progress).fetch(m,part))single.writeText("range fallback")
  if(single.exists())downloadSingle(m,part)
  val verificationTotal=m.archive.bytes+m.files.values.sumOf{it.bytes};var lastVerification=0L;var lastBytes=0L;var rawVerified=0L
  fun verification(received:Long){if(received-lastBytes<262144&&received!=verificationTotal)return;lastBytes=received;val now=System.currentTimeMillis();if(now-lastVerification>=250||received==verificationTotal){progress(ResourceState("verifying","Verifying resources…",received,verificationTotal));lastVerification=now}}
  progress(ResourceState("verifying","Verifying resources…",0,verificationTotal));if(sha(part){verification(it)}!=m.archive.sha256){part.delete();File(root,"${m.archive.sha256}.ranges.json").delete();throw IOException("Downloaded archive failed verification; retry")}
  val stage=File(root,"staging-${m.archive.sha256}");stage.deleteRecursively();stage.mkdirs()
  try{
   val seen=mutableSetOf<String>();ZipInputStream(part.inputStream().buffered()).use{zip->while(true){val entry=zip.nextEntry?:break;val expected=m.files[entry.name]?:throw IOException("Unexpected resource ZIP entry");requireDownload(!entry.isDirectory&&seen.add(entry.name),"Duplicate or invalid ZIP entry");val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
    FileOutputStream(File(stage,entry.name)).use{fileOut->val out=BufferedOutputStream(fileOut,262144);val b=ByteArray(262144);while(true){if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue");val n=zip.read(b);if(n<0)break;bytes+=n;requireDownload(bytes<=expected.bytes,"Resource ZIP expands beyond expected size");digest.update(b,0,n);out.write(b,0,n);rawVerified+=n;verification(m.archive.bytes+rawVerified)};out.flush();fileOut.fd.sync()}
    requireDownload(bytes==expected.bytes&&digest.digest().joinToString(""){"%02x".format(it)}==expected.sha256,"Resource file failed verification");zip.closeEntry()
   }};requireDownload(seen==m.files.keys,"Incomplete resource ZIP")
   try{validate(BundleSnapshot(stage,m.version,m.archive.sha256,m.formatVersion))}catch(e:Exception){throw AppUpdateRequired("These recognition resources need a newer app")}
   write(File(stage,"verified.json"),m.json);val target=File(root,"bundle-${m.archive.sha256}-${UUID.randomUUID()}")
   synchronized(ResourcePins.lock){
   requireDownload(stage.renameTo(target),"Cannot activate resource directory");beforeActivationCommit()
   if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue")
   val out=pointer.startWrite();try{out.write(JSONObject(m.json.toString()).put("directory",target.name).toString().toByteArray());out.fd.sync();pointer.finishWrite(out)}catch(e:Exception){pointer.failWrite(out);throw e}
   }
   pending.delete();offer.delete();part.delete();File(root,"${m.archive.sha256}.ranges.json").delete();single.delete();progress(ResourceState("ready","Resources ready"))
  }catch(e:Exception){stage.deleteRecursively();throw e}
 }
 private fun downloadSingle(m:ResourceManifest,part:File){
  var offset=part.length()
  if(offset<m.archive.bytes){
   val headers=if(offset>0)mapOf("Range" to "bytes=$offset-") else emptyMap()
   transport.open(m.archiveUrl,headers).use{r->
    if(r.code==416){if(part.length()!=m.archive.bytes){part.delete();throw IOException("Download range expired; retry to restart")}}
    else{
     if(r.code==200){offset=0}
     else if(r.code==206){val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(r.header("Content-Range")?:"")?:throw IOException("Invalid download range response");requireDownload(range.groupValues[1].toLong()==offset&&range.groupValues[3].toLong()==m.archive.bytes&&range.groupValues[2].toLong() in offset until m.archive.bytes,"Download range does not match the checkpoint")}
     else throw IOException("Download failed (HTTP ${r.code}); retry to continue")
     FileOutputStream(part,offset>0).use{out->var received=offset;var syncAt=offset;var updateAt=0L;val b=ByteArray(262144)
      while(true){if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue");val n=r.body.read(b);if(n<0)break;requireDownload(received+n<=m.archive.bytes,"Download exceeds expected size");out.write(b,0,n);received+=n
       if(received-syncAt>=4*1024*1024){out.fd.sync();syncAt=received};val now=System.currentTimeMillis();if(now-updateAt>250){progress(ResourceState("downloading","Downloading resources",received,m.archive.bytes));updateAt=now}
      };out.fd.sync();requireDownload(received==m.archive.bytes,"Download interrupted; retry to continue")
     }
    }
   }
  }
 }
 private fun requireDownload(value:Boolean,message:String){if(!value)throw IOException(message)}
}
private fun InputStream.readBytesLimited(max:Int):ByteArray {val out=ByteArrayOutputStream();val b=ByteArray(4096);while(true){val n=read(b);if(n<0)break;if(out.size()+n>max)throw IOException("Manifest is too large");out.write(b,0,n)};return out.toByteArray()}
