package com.guard.app

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var api: GuardApi
    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage = ""
    private var lastCheck = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        api = GuardApi(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        lastPackage = pkg
        val now = System.currentTimeMillis()
        if (now - lastCheck < 10_000L) return
        lastCheck = now
        Thread { enforce(pkg) }.start()
    }

    private fun enforce(pkg: String) {
        try {
            val rules = api.deviceRules().optJSONArray("rules") ?: return
            val cal = Calendar.getInstance()
            val day = cal.get(Calendar.DAY_OF_WEEK) - 1
            val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            var blocked = false
            var reason = ""
            for (i in 0 until rules.length()) {
                val r = rules.getJSONObject(i)
                if (r.optInt("day_of_week", -1) !in listOf(-1, day)) continue
                when (r.optString("rule_type")) {
                    "DOWNTIME" -> {
                        val start = r.optInt("start_minute", -1)
                        val end = r.optInt("end_minute", -1)
                        val active = if (start < 0 || end < 0) false
                        else if (start <= end) minute in start until end
                        else minute >= start || minute < end
                        if (active) { blocked = true; reason = "Waktu istirahat aktif" }
                    }
                    "DAILY_LIMIT" -> {
                        val limit = r.optInt("limit_minutes", -1)
                        if (limit > 0 && todayUsageMinutes() >= limit) {
                            blocked = true; reason = "Batas waktu harian tercapai"
                        }
                    }
                }
                if (blocked) break
            }
            if (blocked) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun todayUsageMinutes(): Int {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
        val total = stats.filter { it.packageName != packageName }.sumOf { it.totalTimeInForeground }
        return (total / 60000L).toInt()
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
