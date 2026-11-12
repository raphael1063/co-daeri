package com.lrlr.codaeri

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.lrlr.codaeri.app.Connectivity
import com.lrlr.codaeri.app.KeepAlive

/** 포그라운드 서비스로 프로세스를 살려 둔다. */
class ServiceKeepAlive(private val context: Context) : KeepAlive {
    override fun on() {
        val i = Intent(context, TripService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        } catch (_: Exception) {
        }
    }

    override fun off() {
        context.stopService(Intent(context, TripService::class.java))
    }
}

/** 지하에서 끊겼던 통신이 돌아오는 것을 듣는다. */
class AndroidConnectivity(private val context: Context) : Connectivity {
    override fun onAvailable(block: () -> Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = block()
            })
        } catch (_: Exception) {
        }
    }
}
