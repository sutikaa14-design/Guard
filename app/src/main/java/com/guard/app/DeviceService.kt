package com.guard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.app.usage.UsageStatsManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceService : Service() {
    private lateinit var api: GuardApi
    private lateinit var locationClient: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        api = GuardApi(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("guard", "GUARD", NotificationManager.IMPORTANCE_LOW)
        )

        startForeground(
            7,
            NotificationCompat.Builder(this, "guard")
                .setContentTitle("GUARD aktif")
                .setContentText("Perlindungan perangkat anak berjalan")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .build()
        )

        collectUsage()
        handler.postDelayed(usageRunnable, 15 * 60 * 1000L)
        requestLocationUpdates()
        pollCommands()
    }

    private val usageRunnable = object : Runnable {
        override fun run() {
            collectUsage()
            handler.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun requestLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let(::sendLocation)
            }
        }
        try {
            val request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(60_000L)
                .setFastestInterval(30_000L)
            locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            // Permission is requested by the child enrollment UI.
        }
    }

    private fun sendLocation(location: Location) {
        Thread {
            try {
                api.location(location.latitude, location.longitude, location.accuracy)
                val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                api.heartbeat(battery)
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun collectUsage() {
        try {
            val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 24L * 60L * 60L * 1000L
            val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val apps = JSONArray()
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            stats.filter { it.totalTimeInForeground > 0L }.forEach {
                apps.put(
                    JSONObject()
                        .put("package_name", it.packageName)
                        .put("usage_ms", it.totalTimeInForeground)
                        .put("usage_date", day)
                )
            }
            if (apps.length() > 0) api.usage(apps)
        } catch (_: Exception) {
        }
    }

    private fun pollCommands() {
        handler.postDelayed({
            Thread {
                try {
                    val commands = api.commands().optJSONArray("commands")
                    if (commands != null) {
                        for (i in 0 until commands.length()) {
                            val command = commands.getJSONObject(i)
                            val success = executeCommand(command.optString("command"))
                            api.complete(command.getString("id"), success)
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()
            pollCommands()
        }, 15_000L)
    }

    private fun executeCommand(command: String): Boolean {
        return when (command) {
            "PLAY_SOUND" -> {
                val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(
                    AudioManager.STREAM_RING,
                    audio.getStreamMaxVolume(AudioManager.STREAM_RING),
                    0
                )
                true
            }
            "LOCATE", "SYNC_STATUS" -> {
                try {
                    locationClient.lastLocation.addOnSuccessListener { it?.let(::sendLocation) }
                } catch (_: Exception) {
                }
                true
            }
            "LOCK" -> {
                val policy = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(this, GuardAdminReceiver::class.java)
                if (policy.isAdminActive(admin)) {
                    policy.lockNow()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::locationCallback.isInitialized) {
            try {
                locationClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }
}
