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
import android.content.pm.ApplicationInfo
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceService : Service() {
    private lateinit var api: GuardApi
    private lateinit var locationClient: FusedLocationProviderClient
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var callback:LocationCallback
    private var polling=false

    override fun onCreate(){
        super.onCreate()
        api=GuardApi(this)
        if(api.deviceToken==null){stopSelf();return}
        locationClient=LocationServices.getFusedLocationProviderClient(this)
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("guard","GUARD",NotificationManager.IMPORTANCE_LOW))
        startForeground(7,NotificationCompat.Builder(this,"guard")
            .setContentTitle("GUARD aktif").setContentText("Perlindungan perangkat anak sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setOngoing(true).build())
        sendHeartbeat()
        collectUsage()
        handler.postDelayed(usageTask,15*60*1000L)
        requestLocations()
        pollCommands()
    }

    private val usageTask=object:Runnable{override fun run(){collectUsage();handler.postDelayed(this,15*60*1000L)}}

    private fun requestLocations(){
        callback=object:LocationCallback(){
            override fun onLocationResult(r:LocationResult){r.locations.lastOrNull()?.let{sendLocation(it)}}
        }
        try{
            val req=LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,60_000L).setMinUpdateIntervalMillis(30_000L).setWaitForAccurateLocation(false).build()
            locationClient.requestLocationUpdates(req,callback,mainLooper)
        }catch(_:SecurityException){}
    }

    private fun battery():Int{
        val b=(getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if(b in 0..100)b else 0
    }
    private fun sendHeartbeat(){Thread{try{api.heartbeat(battery())}catch(_:Exception){}}.start()}
    private fun sendLocation(l:Location){Thread{try{api.location(l.latitude,l.longitude,l.accuracy);api.heartbeat(battery())}catch(_:Exception){}}.start()}

    private fun collectUsage(){
        try{
            val usm=getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end=System.currentTimeMillis()
            val start=java.util.Calendar.getInstance().apply{
                set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)
            }.timeInMillis
            val stats=usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,end)
            val apps=JSONArray();val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
            val pm=packageManager
            stats.filter{it.totalTimeInForeground>0 && it.packageName!=packageName}.forEach{
                val label=try{pm.getApplicationInfo(it.packageName,0).loadLabel(pm).toString()}catch(_:Exception){it.packageName}
                apps.put(JSONObject().put("package_name",it.packageName).put("app_name",label).put("usage_ms",it.totalTimeInForeground).put("usage_date",day))
            }
            if(apps.length()>0)api.usage(apps)
        }catch(_:SecurityException){}catch(_:Exception){}
    }

    private fun pollCommands(){
        if(polling)return
        polling=true
        handler.post(object:Runnable{
            override fun run(){
                Thread{
                    try{
                        val arr=api.commands().optJSONArray("commands")
                        if(arr!=null)for(i in 0 until arr.length()){
                            val c=arr.getJSONObject(i);val ok=execute(c.optString("command"));api.complete(c.getString("id"),ok)
                        }
                    }catch(_:Exception){}
                }.start()
                handler.postDelayed(this,15_000L)
            }
        })
    }

    private fun execute(c:String):Boolean=when(c){
        "PLAY_SOUND"->{try{val a=getSystemService(AUDIO_SERVICE) as AudioManager;a.setStreamVolume(AudioManager.STREAM_RING,a.getStreamMaxVolume(AudioManager.STREAM_RING),0);true}catch(_:Exception){false}}
        "LOCATE","SYNC_STATUS"->{try{locationClient.lastLocation.addOnSuccessListener{it?.let(::sendLocation)};true}catch(_:Exception){false}}
        "LOCK"->{try{val d=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager;val n=ComponentName(this,GuardAdminReceiver::class.java);if(d.isAdminActive(n)){d.lockNow();true}else false}catch(_:Exception){false}}
        else->false
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        if(::callback.isInitialized)try{locationClient.removeLocationUpdates(callback)}catch(_:Exception){}
        super.onDestroy()
    }
}
