package com.zovex.pc.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Hosts the Windows-compatible runtime (Wine, Box64 and the processes they
 * spawn) in the dedicated `:wine` OS process declared in the manifest.
 *
 * Nothing is launched yet — stage 2 fills this in. The process boundary exists
 * from the start on purpose: when the runtime dies, only this process dies, and
 * the desktop shell survives to report it.
 */
class RuntimeService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Runtime process started (pid=${android.os.Process.myPid()})")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stage 2: start the foreground notification, lay down the prefix and
        // spawn wineserver here.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Runtime process stopping")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ZovexRuntime"
    }
}
