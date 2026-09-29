package com.guard.app
import android.app.*
import android.content.*
import android.location.Location
import android.os.*
import android.media.AudioManager
import android.app.usage.UsageStatsManager
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
class DeviceService:Service(){
 private lateinit var api:GuardApi;private lateinit var loc:FusedLocationProviderClient;private val h=Handler(Looper.getMainLooper())
 override fun onCreate(){super.onCreate();api=GuardApi(this);loc=LocationServices.getFusedLocationProviderClient(this);val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("guard","GUARD",NotificationManager.IMPORTANCE_LOW));usage();h.postDelayed({usage()},900000);startForeground(7,NotificationCompat.Builder(this,"guard").setContentTitle("GUARD aktif").setContentText("Perlindungan perangkat anak berjalan").setSmallIcon(android.R.drawable.ic_lock_idle_lock).build());updates();poll()}
 private fun updates(){try{loc.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,60000).setMinUpdateIntervalMillis(30000).build(),object:LocationCallback(){override fun onLocationResult(r:LocationResult){r.locations.lastOrNull()?.let{send(it)}}},mainLooper)}catch(_:SecurityException){}}
 private fun send(l:Location){Thread{try{api.location(l.latitude,l.longitude,l.accuracy);api.heartbeat((getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))}catch(_:Exception){}}.start()}
 private fun usage(){try{val us=getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager;val end=System.currentTimeMillis();val start=end-24*60*60*1000L;val stats=us.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,end);val a=JSONArray();val day=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date());for(x in stats){if(x.totalTimeInForeground>0)a.put(JSONObject().put("package_name",x.packageName).put("usage_ms",x.totalTimeInForeground).put("usage_date",day))};if(a.length()>0)api.usage(a)}catch(_:Exception){}}
 private fun poll(){h.postDelayed({Thread{try{val a=api.commands().optJSONArray("commands");if(a!=null)for(i in 0 until a.length()){val c=a.getJSONObject(i);val ok=when(c.getString("command")){"PLAY_SOUND"->{val am=getSystemService(AUDIO_SERVICE) as AudioManager;am.setStreamVolume(AudioManager.STREAM_RING,am.getStreamMaxVolume(AudioManager.STREAM_RING),0);true};"LOCATE","SYNC_STATUS"->{try{loc.lastLocation.addOnSuccessListener{it?.let{send(it)}}}catch(_:Exception){};true};"LOCK"->{val d=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager;val admin=ComponentName(this,GuardAdminReceiver::class.java);if(d.isAdminActive(admin)){d.lockNow();true}else{false}};else->false};api.complete(c.getString("id"),ok)}}catch(_:Exception){}},poll);},15000)}
 override fun onStartCommand(i:Intent?,f:Int,s:Int)=START_STICKY
 override fun onBind(i:Intent?)=null
 override fun onDestroy(){h.removeCallbacksAndMessages(null);try{loc.removeLocationUpdates(object:LocationCallback(){})}catch(_:Exception){};super.onDestroy()}
}