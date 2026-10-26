package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import javax.net.SocketFactory;

public class DownloadSocketFactoryTest {
    static class Probe extends Socket {
        int size=65536, cap=Integer.MAX_VALUE, requested=0; boolean connected=false, closed=false, reject=false;
        @Override public boolean isConnected(){return connected;}
        @Override public int getReceiveBufferSize(){return size;}
        @Override public void setReceiveBufferSize(int n)throws SocketException {
            assertFalse("Receive memory must be requested before connect",connected);
            if(reject)throw new SocketException("unsupported");
            requested=n;size=Math.min(n,cap);
        }
        @Override public void connect(SocketAddress a,int timeout){assertEquals(20000,timeout);connected=true;}
        @Override public void close(){closed=true;}
    }
    static class Source extends SocketFactory {
        final Deque<Probe> sockets=new ArrayDeque<>();
        Source(Probe... ps){sockets.addAll(Arrays.asList(ps));}
        public Socket createSocket(){return sockets.removeFirst();}
        public Socket createSocket(String a,int b){throw new AssertionError();}
        public Socket createSocket(InetAddress a,int b){throw new AssertionError();}
        public Socket createSocket(String a,int b,InetAddress c,int d){throw new AssertionError();}
        public Socket createSocket(InetAddress a,int b,InetAddress c,int d){throw new AssertionError();}
    }
    @Test public void requestsFourMiBBeforeConnecting()throws Exception {
        Probe p=new Probe();List<String> log=new ArrayList<>();
        DownloadSocketFactory f=new DownloadSocketFactory(new Source(p),4*1024*1024,log::add);
        assertSame(p,f.createSocket());assertFalse(p.connected);assertEquals(4*1024*1024,p.requested);
        assertTrue(log.get(0).contains("before-connect"));
    }
    @Test public void cappedRequestUsesFreshAutotunedSocket()throws Exception {
        Probe capped=new Probe(),fresh=new Probe();capped.cap=212992;
        Socket result=new DownloadSocketFactory(new Source(capped,fresh),4*1024*1024,s->{}).createSocket();
        assertSame(fresh,result);assertTrue(capped.closed);assertEquals(0,fresh.requested);assertFalse(fresh.connected);
    }
    @Test public void preservesLargerExistingBuffer()throws Exception {
        Probe p=new Probe();p.size=8*1024*1024;
        assertSame(p,new DownloadSocketFactory(new Source(p),4*1024*1024,s->{}).createSocket());
        assertEquals(0,p.requested);assertEquals(8*1024*1024,p.size);
    }
    @Test public void unsupportedTuningDoesNotPreventDownload()throws Exception {
        Probe p=new Probe(),fresh=new Probe();p.reject=true;
        assertSame(fresh,new DownloadSocketFactory(new Source(p,fresh),4*1024*1024,s->{}).createSocket());
        assertTrue(p.closed);assertEquals(0,fresh.requested);
    }
    @Test public void zeroKeepsSystemAutomaticSizing()throws Exception {
        Probe p=new Probe();new DownloadSocketFactory(new Source(p),0,s->{}).createSocket();assertEquals(0,p.requested);
    }
    @Test public void connectingOverloadAlsoTunesBeforeConnect()throws Exception {
        Probe p=new Probe();new DownloadSocketFactory(new Source(p),4*1024*1024,s->{}).createSocket(InetAddress.getLoopbackAddress(),443);
        assertTrue(p.connected);assertEquals(4*1024*1024,p.requested);
    }
}
