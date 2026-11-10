package org.tangowiz.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.Instant

/** Explicit diagnostic input. Recognition, presentation and watch scheduling remain production code. */
object AudioReplay {
 const val ACTION="org.tangowiz.app.REPLAY_AUDIO"
 @Volatile var current:Session?=null;private set
 @Synchronized fun prepare(context:Context,intent:Intent):Session {
  check(current==null&&!LiveState.current.listening){"Pause recognition before starting audio replay"}
  val file=File(requireNotNull(intent.getStringExtra("pcm")){"Supply pcm: a PCM16 little-endian mono 8000 Hz file"})
  require(file.isFile&&file.canRead()&&file.length()>=32000&&file.length()%8000L==0L){"Replay requires complete half-second PCM16 mono 8000 Hz blocks"}
  require(file.canonicalPath.startsWith("/data/local/tmp/tangowiz-replay-")&&file.name.endsWith(".pcm16")){"Use /data/local/tmp/tangowiz-replay-NAME.pcm16"}
  val directory=File(requireNotNull(context.getExternalFilesDir("replays")),"replay-"+Instant.now().toString().replace(":","-")).apply{check(mkdirs())}
  return Session(file,directory,intent.getBooleanExtra("dry_run_watch",true)).also{current=it}
 }
 @Synchronized fun release(session:Session){if(current===session)current=null}
 class Session(val input:File,val directory:File,val dryRunWatch:Boolean) {
  val totalSeconds=input.length()/16000.0
  @Volatile var exhausted=false;private set
  @Volatile var processedSeconds=0.0
  private var stream:FileInputStream?=null
  private var began=0L
  private var fed=0L
  private val trace=File(directory,"watch-trace.jsonl").bufferedWriter()
  private var closed=false
  @Synchronized fun log(event:String,extra:JSONObject=JSONObject()) {
   if(closed)return
   trace.write(extra.put("event",event).put("time",Instant.now().toString()).put("elapsed_ms",if(began==0L)0L else SystemClock.elapsedRealtime()-began).put("processed_audio_seconds",processedSeconds).toString());trace.newLine();trace.flush()
  }
  fun next():ShortArray? {
   if(stream==null){stream=FileInputStream(input);began=SystemClock.elapsedRealtime();log("replay_started",JSONObject().put("total_seconds",totalSeconds).put("dry_run_watch",dryRunWatch))}
   if(fed*2>=input.length()){exhausted=true;log("input_finished");return null}
   val bytes=ByteArray(8000);var n=0
   while(n<bytes.size){val got=stream!!.read(bytes,n,bytes.size-n);check(got>0){"Truncated replay PCM"};n+=got}
   val due=began+(fed+4000)*1000/8000
   val wait=due-SystemClock.elapsedRealtime();if(wait>0)Thread.sleep(wait)
   fed+=4000
   return ShortArray(4000){i->((bytes[i*2].toInt() and 255) or (bytes[i*2+1].toInt() shl 8)).toShort()}
  }
  @Synchronized fun progress(state:RecognitionState){processedSeconds=state.seconds;log("recognition_state",JSONObject().put("state",JSONObject().put("listening",state.listening).put("error",state.error).put("best",state.best?.let{JSONObject().put("id",it.id).put("title",it.display("title")).put("orchestra",it.display("orchestra")).put("score",it.score).put("rankingScore",it.rankingScore).put("speed",it.speed)}?:JSONObject.NULL)))
   val row=JSONObject().put("state","running").put("processed_seconds",processedSeconds).put("total_seconds",totalSeconds).put("updated_at",System.currentTimeMillis()/1000.0)
   File(directory,"status.json.tmp").writeText(row.toString());check(File(directory,"status.json.tmp").renameTo(File(directory,"status.json")))
  }
  @Synchronized fun finish(success:Boolean,error:String="") {
   if(closed)return
   log("replay_finished",JSONObject().put("success",success).put("error",error));stream?.close()
   File(directory,"status.json.tmp").writeText(JSONObject().put("state",if(success)"complete" else "failed").put("processed_seconds",processedSeconds).put("total_seconds",totalSeconds).put("updated_at",System.currentTimeMillis()/1000.0).put("error",error).toString());check(File(directory,"status.json.tmp").renameTo(File(directory,"status.json")))
   trace.close();closed=true
  }
 }
}
