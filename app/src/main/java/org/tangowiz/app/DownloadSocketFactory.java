package org.tangowiz.app;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;
import javax.net.SocketFactory;

/** Requests receive memory before TCP connects; never replaces TLS verification. */
public final class DownloadSocketFactory extends SocketFactory {
    public static final int REQUESTED_RECEIVE_BYTES = 4 * 1024 * 1024;
    private final SocketFactory delegate;
    private final int requested;
    private final Consumer<String> report;

    public DownloadSocketFactory(SocketFactory delegate, int requested, Consumer<String> report) {
        if (requested < 0) throw new IllegalArgumentException("Negative receive buffer");
        this.delegate = delegate;
        this.requested = requested;
        this.report = report;
    }

    @Override public Socket createSocket() throws IOException {
        Socket socket = delegate.createSocket();
        try {
            if (socket.isConnected()) throw new IOException("Expected an unconnected socket");
            int initial = socket.getReceiveBufferSize();
            if (requested == 0 || initial >= requested) {
                report.accept("receive-buffer auto initial=" + initial + " requested=" + requested);
                return socket;
            }
            try {
                socket.setReceiveBufferSize(requested);
            } catch (java.net.SocketException unsupported) {
                socket.close();
                socket = delegate.createSocket();
                report.accept("receive-buffer unsupported requested=" + requested + " using-auto=" + socket.getReceiveBufferSize());
                return socket;
            }
            int actual = socket.getReceiveBufferSize();
            if (actual >= requested) {
                report.accept("receive-buffer before-connect requested=" + requested + " effective=" + actual);
                return socket;
            }
            // Setting SO_RCVBUF can disable Linux receive autotuning. A clamped
            // request must not leave the connection pinned to a small window.
            socket.close();
            socket = delegate.createSocket();
            report.accept("receive-buffer capped requested=" + requested + " capped=" + actual
                    + " using-auto=" + socket.getReceiveBufferSize());
            return socket;
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote, 20000);
            return socket;
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException ignored) { }
            throw e;
        }
    }
    @Override public Socket createSocket(String host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
}
