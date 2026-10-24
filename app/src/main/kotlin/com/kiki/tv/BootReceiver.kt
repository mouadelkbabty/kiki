package com.kiki.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || Prefs(context).code == null) return
        try {
            // through an activity, so the service starts from the foreground (see StartActivity)
            context.startActivity(
                Intent(context, StartActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                ),
            )
        } catch (e: Exception) {
            Log.w("kiki", "start activity refused (${e.message}), starting the service directly")
            context.startForegroundService(Intent(context, ListenerService::class.java))
        }
    }
}
