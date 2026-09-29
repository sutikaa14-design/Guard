package com.guard.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class GuardApi(context: Context) {
    private val secure = SecureStore(context)
    private val prefs = context.getSharedPreferences("guard_local", Context.MODE_PRIVATE)
    private val url = "https://ukavxclgnthzwewvozgl.supabase.co/functions/v1/guard-api"

    var token: String?
        get() = secure.get("session_token")
        private set(v) { secure.put("session_token", v) }

    var deviceToken: String?
        get() = secure.get("device_token")
        private set(v) { secure.put("device_token", v) }

    var deviceId: String?
        get() = secure.get("device_id")
        private set(v) { secure.put("device_id", v) }

    private fun post(body: JSONObject, device: Boolean = false): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Content-Type", "application/json")
        if (device) deviceToken?.let { body.put("device_token", it) }
        else token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw IllegalStateException(JSONObject(text).optString("error", "Server error"))
        return JSONObject(text)
    }

    fun register(name: String, email: String, password: String, phone: String = "") =
        post(JSONObject().put("action","register").put("full_name",name).put("email",email).put("password",password).put("phone",phone.ifBlank { JSONObject.NULL }))

    fun login(email: String, password: String): JSONObject {
        val result = post(JSONObject().put("action","login").put("email",email).put("password",password))
        result.optString("token").takeIf { it.isNotBlank() }?.let { token = it }
        return result
    }

    fun logout(): JSONObject = try {
        post(JSONObject().put("action","logout"))
    } finally {
        token = null
    }

    fun child(name: String, year: Int) =
        post(JSONObject().put("action","create_child").put("full_name",name).put("birth_year",year))

    fun createPairing(childId: String) =
        post(JSONObject().put("action","create_pairing").put("child_id",childId))

    fun dashboard() = post(JSONObject().put("action","parent_dashboard"))
    fun command(deviceId: String, command: String) =
        post(JSONObject().put("action","create_command").put("device_id",deviceId).put("command",command))

    fun pair(code: String): JSONObject {
        val result = post(JSONObject().put("action","pair_device")
            .put("code",code).put("device_name","Android Child")
            .put("app_version","2.0.0")
            .put("device_identifier_hash",stableDeviceIdentifier()))
        result.optString("device_token").takeIf { it.isNotBlank() }?.let { deviceToken = it }
        result.optJSONObject("device")?.optString("id")?.takeIf { it.isNotBlank() }?.let { deviceId = it }
        return result
    }

    fun stableDeviceIdentifier(): String =
        prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_id", it).apply()
        }

    fun heartbeat(battery: Int) =
        post(JSONObject().put("action","device_heartbeat").put("battery_percent",battery).put("app_version","2.0.0"), true)

    fun location(lat: Double, lon: Double, accuracy: Float) =
        post(JSONObject().put("action","device_location").put("latitude",lat).put("longitude",lon).put("accuracy_m",accuracy), true)

    fun commands() = post(JSONObject().put("action","poll_commands"), true)

    fun complete(commandId: String, ok: Boolean) =
        post(JSONObject().put("action","complete_command").put("command_id",commandId)
            .put("status",if(ok) "COMPLETED" else "FAILED"), true)

    fun usage(apps: JSONArray) = post(JSONObject().put("action","device_usage").put("apps",apps), true)

    fun deviceRules() = post(JSONObject().put("action","device_rules"), true)
    fun rules(childId: String) = post(JSONObject().put("action","get_rules").put("child_id",childId))

    fun createRule(childId: String, type: String, day: Int, start: Int?, end: Int?, limit: Int?) =
        post(JSONObject().put("action","create_rule").put("child_id",childId).put("rule_type",type)
            .put("day_of_week",day).put("start_minute",start ?: JSONObject.NULL)
            .put("end_minute",end ?: JSONObject.NULL).put("limit_minutes",limit ?: JSONObject.NULL))

    fun commandHistory(deviceId: String) = post(JSONObject().put("action","command_history").put("device_id",deviceId))

    fun locationHistory(deviceId: String) =
        post(JSONObject().put("action","location_history").put("device_id",deviceId))

    fun usageHistory(deviceId: String) =
        post(JSONObject().put("action","usage_history").put("device_id",deviceId))

    fun notifications() = post(JSONObject().put("action","notifications"))

    fun markNotification(id: String) =
        post(JSONObject().put("action","mark_notification_read").put("notification_id",id))

    fun geofences(childId: String) =
        post(JSONObject().put("action","geofences").put("child_id",childId))

    fun createGeofence(childId: String, name: String, lat: Double, lon: Double, radius: Double) =
        post(JSONObject().put("action","create_geofence").put("child_id",childId).put("name",name)
            .put("latitude",lat).put("longitude",lon).put("radius_m",radius))

    fun updateGeofence(id: String, enabled: Boolean) =
        post(JSONObject().put("action","update_geofence").put("geofence_id",id).put("enabled",enabled))

    fun deleteGeofence(id: String) =
        post(JSONObject().put("action","delete_geofence").put("geofence_id",id))
}
