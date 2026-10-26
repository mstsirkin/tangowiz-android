package org.tangowiz.app

import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.*
import javax.net.SocketFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol

class DownloadTransportTest {
 private fun client()=OkHttpClient.Builder().socketFactory(DownloadSocketFactory(SocketFactory.getDefault(),4*1024*1024){}).protocols(listOf(Protocol.HTTP_1_1)).readTimeout(10,TimeUnit.SECONDS).build()
 private fun request(s:Socket):List<String>{val input=s.getInputStream().bufferedReader();val lines=mutableListOf<String>();while(true){val line=input.readLine()?:break;if(line.isEmpty())break;lines.add(line)};return lines}
 @Test fun followsRedirectAndPreservesExactRangeBytes(){
  val server=ServerSocket(0);val executor=Executors.newSingleThreadExecutor();val http=client();val transport=HttpResourceTransport(http)
  val worker=executor.submit<List<List<String>>>{val seen=mutableListOf<List<String>>()
   server.accept().use{s->seen.add(request(s));s.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${server.localPort}/bytes\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())}
   server.accept().use{s->seen.add(request(s));s.getOutputStream().write("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 4-7/16\r\nContent-Length: 4\r\nConnection: close\r\n\r\nEFGH".toByteArray())};seen}
  try{transport.open("http://127.0.0.1:${server.localPort}/redirect",mapOf("Range" to "bytes=4-7")).use{r->assertEquals(206,r.code);assertEquals("bytes 4-7/16",r.header("Content-Range"));assertArrayEquals("EFGH".toByteArray(),r.body.readBytes())}
   val requests=worker.get(5,TimeUnit.SECONDS);assertEquals(2,requests.size);assertTrue(requests.all{r->r.any{it.equals("Range: bytes=4-7",true)}})
  }finally{transport.cancel();server.close();executor.shutdownNow();http.connectionPool.evictAll()}
 }
 @Test fun cancellationUnblocksBodyReadAndTransportCanBeReused(){
  val server=ServerSocket(0);val pool=Executors.newFixedThreadPool(2);val release=CountDownLatch(1);val reading=CountDownLatch(1);val http=client();val transport=HttpResourceTransport(http)
  val serving=pool.submit{server.accept().use{s->request(s);s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\nConnection: close\r\n\r\n".toByteArray());s.getOutputStream().flush();release.await(10,TimeUnit.SECONDS)}}
  val fetching=pool.submit<Boolean>{try{transport.open("http://127.0.0.1:${server.localPort}/stall",emptyMap()).use{r->reading.countDown();r.body.read()};false}catch(e:IOException){true}}
  try{assertTrue(reading.await(5,TimeUnit.SECONDS));transport.cancel();assertTrue(fetching.get(5,TimeUnit.SECONDS))}
  finally{release.countDown();server.close();serving.get(5,TimeUnit.SECONDS);pool.shutdownNow();http.connectionPool.evictAll()}
  val retry=ServerSocket(0);val retryPool=Executors.newSingleThreadExecutor();val served=retryPool.submit{retry.accept().use{s->request(s);s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())}}
  try{transport.open("http://127.0.0.1:${retry.localPort}/retry",emptyMap()).use{assertEquals("OK",it.body.readBytes().toString(Charsets.UTF_8))};served.get(5,TimeUnit.SECONDS)}finally{transport.cancel();retry.close();retryPool.shutdownNow();http.connectionPool.evictAll()}
 }
}
