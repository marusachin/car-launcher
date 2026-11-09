package com.carlauncher.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {

    companion object {
        private val BG = 0xFF15181C.toInt()
        private val CARD = 0xFF1D2228.toInt()
        private val STROKE = 0xFF2A3038.toInt()
        private val TEXT = 0xFFE6EEF5.toInt()
        private val MUTED = 0xFF9AA6B2.toInt()
        private val ACCENT = 0xFF4FB3FF.toInt()
        private const val APPS_TAB = "__apps__"
        private const val MAX_TABS = 8
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private var u = 2f
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private lateinit var speedo: SpeedometerView
    private lateinit var tvPlace: TextView
    private lateinit var tvPlace2: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvRange: TextView
    private lateinit var tvDist: TextView
    private lateinit var tvVolt: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var editBar: LinearLayout
    private lateinit var editPill: TextView
    private val dot = GradientDrawable()

    private var locationManager: LocationManager? = null
    private var editing = false
    private var tabs: MutableList<String> = mutableListOf()
    private var tripMeters = 0f
    private var lastGps: Location? = null
    private var lastGpsTime = 0L
    private var lastPlaceTime = 0L
    private var lastPlaceLoc: Location? = null
    private var lastWeatherTime = 0L
    private var lastSave = 0L

    private data class AppInfo(val label: String, val pkg: String)

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        u = resources.displayMetrics.heightPixels / 340f
        tripMeters = prefs.getFloat("trip", 0f)
        loadTabs()
        setContentView(buildUi())
        renderDock()
        updateDistance()
        updateVoltage()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
        handler.post(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // This is the home screen, so back does nothing.
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        try { locationManager?.removeUpdates(listener) } catch (e: Exception) {}
        prefs.edit().putFloat("trip", tripMeters).apply()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    // ---------------------------------------------------------------- UI helpers

    private fun px(v: Float) = (v * u).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u)
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun clock(f12: String, f24: String, size: Float, color: Int, bold: Boolean): TextClock =
        TextClock(this).apply {
            format12Hour = f12
            format24Hour = f24
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u)
            setTextColor(color)
            includeFontPadding = false
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = px(12f).toFloat()
            setStroke(max(1, px(1f)), STROKE)
        }
        setPadding(px(14f), px(8f), px(14f), px(8f))
    }

    private fun pill(text: String, bg: Int, fg: Int): TextView = tv(text, 13f, fg).apply {
        gravity = Gravity.CENTER
        setPadding(px(14f), px(7f), px(14f), px(7f))
        background = GradientDrawable().apply {
            setColor(bg)
            cornerRadius = px(20f).toFloat()
            setStroke(max(1, px(1f)), STROKE)
        }
    }

    private fun vlp(weight: Float) = LinearLayout.LayoutParams(MATCH, 0, weight).apply {
        setMargins(px(5f), px(5f), px(5f), px(5f))
    }

    private fun mlp() = LinearLayout.LayoutParams(WRAP, WRAP).apply {
        setMargins(px(4f), 0, px(4f), 0)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // left column: clock and weather
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(left, LinearLayout.LayoutParams(0, MATCH, 2f))

        val clockCard = card().apply { gravity = Gravity.CENTER }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        timeRow.addView(clock("h:mm", "HH:mm", 40f, TEXT, true))
        val ampm = clock("a", "a", 13f, ACCENT, false)
        ampm.setPadding(px(4f), 0, 0, 0)
        if (DateFormat.is24HourFormat(this)) ampm.visibility = View.GONE
        timeRow.addView(ampm)
        clockCard.addView(timeRow)
        clockCard.addView(clock("EEEE, d MMM", "EEEE, d MMM", 12f, MUTED, false))
        left.addView(clockCard, vlp(1f))

        val weatherCard = card().apply { gravity = Gravity.CENTER_VERTICAL }
        tvTemp = tv("--", 30f, TEXT, true)
        tvDesc = tv("Waiting for location", 12f, TEXT)
        tvRange = tv("", 11f, MUTED)
        weatherCard.addView(tvTemp)
        weatherCard.addView(tvDesc)
        weatherCard.addView(tvRange)
        left.addView(weatherCard, vlp(1f))

        // centre: speedometer
        speedo = SpeedometerView(this)
        body.addView(speedo, LinearLayout.LayoutParams(0, MATCH, 3f).apply {
            setMargins(px(5f), px(5f), px(5f), px(5f))
        })

        // right column: location, distance, battery
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(right, LinearLayout.LayoutParams(0, MATCH, 2f))

        val locCard = card().apply { gravity = Gravity.CENTER_VERTICAL }
        locCard.addView(tv("Location", 11f, MUTED))
        tvPlace = tv("Searching for GPS...", 16f, TEXT, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        tvPlace2 = tv("", 12f, MUTED).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        locCard.addView(tvPlace)
        locCard.addView(tvPlace2)
        right.addView(locCard, vlp(1f))

        val distCard = card().apply { gravity = Gravity.CENTER_VERTICAL }
        distCard.addView(tv("Distance travelled", 11f, MUTED))
        val distRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        tvDist = tv("0.0", 28f, TEXT, true)
        distRow.addView(tvDist)
        distRow.addView(tv(" km", 14f, MUTED))
        distRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val reset = tv("Reset trip", 12f, ACCENT)
        reset.setPadding(px(6f), px(6f), 0, px(2f))
        reset.setOnClickListener {
            tripMeters = 0f
            prefs.edit().putFloat("trip", 0f).apply()
            updateDistance()
        }
        distRow.addView(reset)
        distCard.addView(distRow)
        right.addView(distCard, vlp(1f))

        val voltCard = card().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dot.shape = GradientDrawable.OVAL
        dot.setColor(MUTED)
        val dotView = View(this)
        dotView.background = dot
        voltCard.addView(dotView, LinearLayout.LayoutParams(px(8f), px(8f)).apply { rightMargin = px(8f) })
        voltCard.addView(tv("Battery", 12f, MUTED))
        voltCard.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tvVolt = tv("-- V", 16f, TEXT, true)
        voltCard.addView(tvVolt)
        right.addView(voltCard, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(px(5f), px(5f), px(5f), px(5f))
        })

        // edit bar (only while editing the dock)
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val addPill = pill("+ Add tab", CARD, ACCENT)
        addPill.setOnClickListener {
            if (tabs.size >= MAX_TABS) {
                Toast.makeText(this, "The dock is full. Remove a tab first.", Toast.LENGTH_SHORT).show()
            } else {
                pickApp("Add a tab", tabs) { pkg ->
                    tabs.add(pkg)
                    saveTabs()
                    renderDock()
                }
            }
        }
        val resetPill = pill("Reset all", CARD, TEXT)
        resetPill.setOnClickListener {
            tabs = defaultTabs()
            saveTabs()
            renderDock()
        }
        editBar.addView(addPill, mlp())
        editBar.addView(resetPill, mlp())
        root.addView(editBar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(6f) })

        // dock
        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        editPill = pill("Edit", ACCENT, 0xFF0B0C0E.toInt())
        editPill.setOnClickListener { setEditing(!editing) }
        dockRow.addView(tabsRow)
        dockRow.addView(editPill, mlp())
        root.addView(dockRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(8f) })

        return root
    }

    // ---------------------------------------------------------------- dock

    private fun launchableApps(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun defaultTabs(): MutableList<String> {
        val apps = launchableApps()
        val out = mutableListOf(APPS_TAB)
        val groups = listOf(
            listOf("map"), listOf("radio"), listOf("music", "player"),
            listOf("video"), listOf("phone", "dialer"), listOf("cleaner")
        )
        for (keys in groups) {
            val hit = apps.firstOrNull { a ->
                a.pkg !in out && keys.any { k ->
                    a.label.lowercase().contains(k) || a.pkg.lowercase().contains(k)
                }
            }
            if (hit != null) out.add(hit.pkg)
        }
        return out
    }

    private fun loadTabs() {
        val s = prefs.getString("tabs", null)
        tabs = if (s == null) defaultTabs()
        else s.split("\n").filter { it.isNotBlank() }.toMutableList()
        if (s == null) saveTabs()
    }

    private fun saveTabs() {
        prefs.edit().putString("tabs", tabs.joinToString("\n")).apply()
    }

    private fun setEditing(on: Boolean) {
        editing = on
        editBar.visibility = if (on) View.VISIBLE else View.GONE
        editPill.text = if (on) "Done" else "Edit"
        renderDock()
    }

    private fun pickApp(title: String, exclude: List<String>, onPick: (String) -> Unit) {
        val apps = launchableApps().filter { it.pkg !in exclude }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps to show", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(apps.map { it.label }.toTypedArray()) { _, i -> onPick(apps[i].pkg) }
            .show()
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun renderDock() {
        tabsRow.removeAllViews()
        val pm = packageManager
        for (entry in tabs.toList()) {
            val label: String = if (entry == APPS_TAB) {
                "Apps"
            } else {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(entry, 0)).toString()
                } catch (e: Exception) {
                    continue
                }
            }
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val p = pill(label, CARD, TEXT)
            p.setOnClickListener {
                if (entry == APPS_TAB) pickApp("All apps", emptyList()) { pkg -> launch(pkg) }
                else launch(entry)
            }
            p.setOnLongClickListener { setEditing(true); true }
            wrap.addView(p)
            if (editing && tabs.size > 1) {
                val x = tv("\u00D7", 14f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFFE5484D.toInt())
                    }
                    setOnClickListener {
                        tabs.remove(entry)
                        saveTabs()
                        renderDock()
                    }
                }
                wrap.addView(x, LinearLayout.LayoutParams(px(22f), px(22f)).apply { leftMargin = -px(8f) })
            }
            tabsRow.addView(wrap, mlp())
        }
    }

    // ---------------------------------------------------------------- location, speed, distance

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { onLoc(location) }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun startLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.allProviders.contains(p) && lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 1000L, 0f, listener)
                    lm.getLastKnownLocation(p)?.let {
                        maybeUpdatePlace(it)
                        maybeUpdateWeather(it)
                    }
                }
            }
        } catch (e: SecurityException) {
            // permission not granted
        }
    }

    private fun onLoc(l: Location) {
        if (l.provider == LocationManager.GPS_PROVIDER) {
            lastGpsTime = System.currentTimeMillis()
            var kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
            if (kmh < 3f) kmh = 0f
            speedo.setSpeed(kmh)
            val prev = lastGps
            if (prev != null && l.accuracy <= 25f && l.hasSpeed() && l.speed > 1f) {
                val d = prev.distanceTo(l)
                if (d in 3f..500f) {
                    tripMeters += d
                    updateDistance()
                }
            }
            lastGps = l
        }
        maybeUpdatePlace(l)
        maybeUpdateWeather(l)
    }

    private fun updateDistance() {
        tvDist.text = String.format(Locale.US, "%.1f", tripMeters / 1000f)
    }

    // ---------------------------------------------------------------- network: place name and weather

    private fun httpGet(url: String): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "CarLauncher/1.0")
            c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private fun maybeUpdatePlace(l: Location) {
        val now = System.currentTimeMillis()
        val prev = lastPlaceLoc
        if (now - lastPlaceTime < 15000) return
        if (prev != null && now - lastPlaceTime < 60000 && prev.distanceTo(l) < 300f) return
        lastPlaceTime = now
        lastPlaceLoc = l
        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&lat=%.5f&lon=%.5f",
            l.latitude, l.longitude
        )
        Thread {
            val res = httpGet(url)
            var line1 = String.format(Locale.US, "%.4f, %.4f", l.latitude, l.longitude)
            var line2 = ""
            if (res != null) {
                try {
                    val a = JSONObject(res).optJSONObject("address")
                    if (a != null) {
                        val road = a.optString("road")
                        val area = listOf("suburb", "neighbourhood", "city_district")
                            .map { a.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
                        val city = listOf("city", "town", "village", "state_district")
                            .map { a.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
                        line1 = if (road.isNotEmpty()) road else if (area.isNotEmpty()) area else line1
                        line2 = listOf(area, city).filter { it.isNotEmpty() && it != line1 }.joinToString(", ")
                    }
                } catch (e: Exception) {
                    // keep coordinates
                }
            }
            val t1 = line1
            val t2 = line2
            runOnUiThread {
                tvPlace.text = t1
                tvPlace2.text = t2
            }
        }.start()
    }

    private fun weatherText(c: Int): String = when (c) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Weather"
    }

    private fun maybeUpdateWeather(l: Location) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherTime < 30L * 60L * 1000L) return
        lastWeatherTime = now
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            l.latitude, l.longitude
        )
        Thread {
            val retryAt = System.currentTimeMillis() - 25L * 60L * 1000L
            val res = httpGet(url)
            if (res == null) {
                lastWeatherTime = retryAt
            } else {
                try {
                    val j = JSONObject(res)
                    val cur = j.getJSONObject("current")
                    val t = cur.getDouble("temperature_2m")
                    val code = cur.getInt("weather_code")
                    val daily = j.getJSONObject("daily")
                    val hi = daily.getJSONArray("temperature_2m_max").getDouble(0)
                    val lo = daily.getJSONArray("temperature_2m_min").getDouble(0)
                    runOnUiThread {
                        tvTemp.text = String.format(Locale.US, "%.0f\u00B0C", t)
                        tvDesc.text = weatherText(code)
                        tvRange.text = String.format(Locale.US, "High %.0f\u00B0  \u00B7  Low %.0f\u00B0", hi, lo)
                    }
                } catch (e: Exception) {
                    lastWeatherTime = retryAt
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- battery voltage

    private fun readVoltage(): Float? {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (mv > 0) return mv / 1000f
        try {
            val s = File("/sys/class/power_supply/battery/voltage_now").readText().trim().toFloat()
            if (s > 0f) return if (s > 100000f) s / 1000000f else s / 1000f
        } catch (e: Exception) {
            // not readable on this unit
        }
        return null
    }

    private fun updateVoltage() {
        val v = readVoltage()
        if (v == null) {
            tvVolt.text = "-- V"
            dot.setColor(MUTED)
            return
        }
        tvVolt.text = String.format(Locale.US, "%.1f V", v)
        dot.setColor(
            if (v < 11.5f) 0xFFE5484D.toInt()
            else if (v < 12.0f) 0xFFFFB020.toInt()
            else 0xFF3DDC84.toInt()
        )
    }

    // ---------------------------------------------------------------- periodic work

    private val ticker = object : Runnable {
        override fun run() {
            updateVoltage()
            val now = System.currentTimeMillis()
            if (now - lastGpsTime > 5000) speedo.setSpeed(0f)
            if (now - lastSave > 10000) {
                prefs.edit().putFloat("trip", tripMeters).apply()
                lastSave = now
            }
            handler.postDelayed(this, 2000)
        }
    }
}
