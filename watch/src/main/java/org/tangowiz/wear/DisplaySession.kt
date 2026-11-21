package org.tangowiz.wear

import android.content.Context

/** User-selected display session. Microphone capture remains owned by WatchMicService. */
object DisplaySession {
 fun enabled(ctx:Context)=ctx.getSharedPreferences("display-mode",0).getBoolean("enabled",false)
 fun enable(ctx:Context){ctx.getSharedPreferences("display-mode",0).edit().putBoolean("enabled",true).apply()}
 fun close(ctx:Context){ctx.getSharedPreferences("display-mode",0).edit().putBoolean("enabled",false).apply();WatchCache.changed()}
}
