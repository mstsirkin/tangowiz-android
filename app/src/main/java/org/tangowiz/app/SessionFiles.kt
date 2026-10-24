package org.tangowiz.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

data class CleanupResult(val sessions:Int,val bytes:Long,val failed:Int,val retained:Int)

/** Protect sessions until capture, fallback work and queued writes have all closed. */
object SessionFiles {
 val lock=Any()
 private val active=mutableMapOf<String,MutableSet<String>>()
 private val exports=mutableMapOf<String,Int>()
 private val cleanup=java.util.concurrent.locks.ReentrantLock()
 private fun key(root:File)=root.absolutePath
 fun begin(root:File,session:String):File=synchronized(lock){
  val dir=File(root,session);check(dir.mkdirs()||dir.isDirectory){"Cannot create recording session"}
  active.getOrPut(key(root)){mutableSetOf()}.add(session);dir
 }
 fun end(root:File,session:String)=synchronized(lock){active[key(root)]?.remove(session);Unit}
 fun beginExport(root:File)=synchronized(lock){val k=key(root);exports[k]=(exports[k]?:0)+1}
 fun endExport(root:File)=synchronized(lock){val k=key(root);exports[k]=((exports[k]?:1)-1).coerceAtLeast(0)}

 fun clearOlder(context:Context):CleanupResult {
  check(cleanup.tryLock()){"Cleanup is already running"}
  try{return clearLocked(context)}finally{cleanup.unlock()}
 }
 private fun clearLocked(context:Context):CleanupResult {
  val store=Store(context);val root=store.root
  val trash=File(context.filesDir,"session-cleanup-${UUID.randomUUID()}")
  var kept=0;var sessions=0;var removedBytes=0L;var failed=0
  synchronized(lock){
   check(!Files.isSymbolicLink(root.toPath())){"Recording directory cannot be a symbolic link"}
   check((exports[key(root)]?:0)==0){"An export is reading the recordings. Wait for it to finish and try again."}
   val retained=active[key(root)]?.toSet()?:emptySet();kept=retained.size
   check(trash.mkdir()){"Cannot prepare cleanup"}
   val history=store.history
   if(history.exists()){
    val replacement=File(root,"history-cleanup-${UUID.randomUUID()}.tmp")
    try{
     replacement.outputStream().bufferedWriter().use{out->
      if(!Files.isSymbolicLink(history.toPath()))history.bufferedReader().useLines{lines->lines.forEach{line->
       val session=runCatching{JSONObject(line).optString("session")}.getOrDefault("")
       if(session in retained){out.write(line);out.newLine()}
      }}
     }
     Files.move(replacement.toPath(),history.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
    }finally{replacement.delete()}
   }
   for(file in root.listFiles().orEmpty()){
    if(file.name=="history.jsonl"||file.name in retained)continue
    // This file spans sessions. Keep it while a session is running.
    if(file.name=="lifecycle.jsonl"&&retained.isNotEmpty())continue
    val target=File(trash,file.name)
    if(file.renameTo(target)){if(target.isDirectory&&!Files.isSymbolicLink(target.toPath()))sessions++}else failed++
   }
   LiveState.historyVersion.incrementAndGet()
  }
  // Deleting old large files never holds the recording/history lock. Do not follow links.
  val pending=context.filesDir.listFiles().orEmpty().filter{it.name.matches(Regex("session-cleanup-[0-9a-f-]{36}"))&&!Files.isSymbolicLink(it.toPath())}
  for(directory in pending)Files.walkFileTree(directory.toPath(),object:SimpleFileVisitor<java.nio.file.Path>(){
   override fun visitFile(path:java.nio.file.Path,attrs:BasicFileAttributes):FileVisitResult{
    try{Files.delete(path);removedBytes+=attrs.size()}catch(_:Exception){failed++};return FileVisitResult.CONTINUE
   }
   override fun visitFileFailed(path:java.nio.file.Path,error:java.io.IOException):FileVisitResult{failed++;return FileVisitResult.CONTINUE}
   override fun postVisitDirectory(path:java.nio.file.Path,error:java.io.IOException?):FileVisitResult{
    try{Files.delete(path)}catch(_:Exception){failed++};return FileVisitResult.CONTINUE
   }
  })
  return CleanupResult(sessions,removedBytes,failed,kept)
 }
}
