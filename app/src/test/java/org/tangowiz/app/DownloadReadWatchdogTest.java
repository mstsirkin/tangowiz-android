package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class DownloadReadWatchdogTest {
 @Test public void blockedReadAbortsAndReturns()throws Exception{
  CountDownLatch released=new CountDownLatch(1);AtomicBoolean aborted=new AtomicBoolean();
  InputStream blocked=new InputStream(){public int read()throws IOException{try{released.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new IOException("aborted");}};
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try{DownloadReadWatchdog guard=new DownloadReadWatchdog(blocked,100,()->{aborted.set(true);released.countDown();});
   Future<Boolean> read=pool.submit(()->{try{guard.read();return false;}catch(IOException e){return true;}});
   assertTrue(read.get(3,TimeUnit.SECONDS));assertTrue(aborted.get());
  }finally{released.countDown();pool.shutdownNow();}
 }
 @Test public void completedReadDoesNotAbortAnIdleConnection()throws Exception{
  AtomicBoolean aborted=new AtomicBoolean();DownloadReadWatchdog guard=new DownloadReadWatchdog(new ByteArrayInputStream(new byte[]{1,2}),50,()->aborted.set(true));
  assertEquals(1,guard.read());Thread.sleep(150);assertFalse(aborted.get());assertEquals(2,guard.read());assertEquals(-1,guard.read());
 }
}
