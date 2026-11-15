package org.tangowiz.link;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
/** Started activities remain visible during Wear ambient pause; stopped activities do not. */
public final class HapticVisibility {
 private final Set<Object> started=Collections.newSetFromMap(new IdentityHashMap<>());
 public synchronized void started(Object activity){started.add(activity);}
 public synchronized void stopped(Object activity){started.remove(activity);}
 public synchronized boolean visible(){return !started.isEmpty();}
}
