package org.tangowiz.app;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A deadline for each blocked body read, independent of TCP keep-alive. */
public final class DownloadReadWatchdog extends FilterInputStream {
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "tango-download-watchdog"); t.setDaemon(true); return t;
    });
    static { TIMER.setRemoveOnCancelPolicy(true); }
    private final long timeoutMillis;
    private final Runnable abort;
    public DownloadReadWatchdog(InputStream input, long timeoutMillis, Runnable abort) {
        super(input);
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Invalid idle timeout");
        this.timeoutMillis = timeoutMillis; this.abort = abort;
    }
    private int guarded(Read operation) throws IOException {
        AtomicBoolean pending = new AtomicBoolean(true);
        ScheduledFuture<?> timer = TIMER.schedule(() -> {
            if (pending.compareAndSet(true, false)) abort.run();
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        try { return operation.read(); }
        finally { pending.set(false); timer.cancel(false); }
    }
    private interface Read { int read() throws IOException; }
    @Override public int read() throws IOException { return guarded(() -> in.read()); }
    @Override public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        return guarded(() -> in.read(b, off, len));
    }
}
