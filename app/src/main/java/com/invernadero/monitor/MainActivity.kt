package com.invernadero.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BluetoothHelper.Listener {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var bluetooth: BluetoothHelper

    private lateinit var tvCurrentTemp: TextView
    private lateinit var tvAlertStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvLatency: TextView
    private lateinit var tvLedState: TextView
    private lateinit var tvBrightness: TextView
    private lateinit var tvThreshold: TextView
    private lateinit var icTemp: ImageView
    private lateinit var icLed: ImageView
    private lateinit var chipBt: Chip
    private lateinit var cardTemp: MaterialCardView
    private lateinit var switchLed: MaterialSwitch
    private lateinit var switchAuto: MaterialSwitch
    private lateinit var sliderBrightness: Slider
    private lateinit var sliderThreshold: Slider
    private lateinit var chartTemperature: TemperatureChartView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var syncingControls = false
    private var receivedFromBluetooth = false
    private var sensorError = false
    private var previousTemperature: Double? = null
    private var lastTemperature: Double? = null
    private var threshold = DEFAULT_THRESHOLD_CELSIUS.toInt()
    private var pingSentAt = 0L
    private var lastMessageAt = 0L

    private val pingTick = object : Runnable {
        override fun run() {
            if (!bluetooth.isConnected()) return
            val now = SystemClock.elapsedRealtime()
            if (lastMessageAt != 0L && now - lastMessageAt > SILENCE_TIMEOUT_MS) {
                tvLatency.setText(R.string.bt_no_response)
            }
            pingSentAt = now
            bluetooth.sendCommand(ArduinoProtocol.CMD_PING)
            mainHandler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            bluetooth.connect()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            goToLogin()
            return
        }

        database = FirebaseDatabase.getInstance()
        bluetooth = BluetoothHelper(this, this)
        threshold = AppPrefs.getThreshold(this)

        bindViews()
        user.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.tvGreeting).text = getString(R.string.main_greeting, it)
        }

        NotificationHelper.createNotificationChannel(this)
        NavHelper.setup(this, findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_home)

        setupControls()
        applyLevelStyle(null)
        renderBluetoothChip(BluetoothHelper.State.DISCONNECTED, null)

        observeCurrentTemperature()
        observeHumidity()
        observeTemperatureHistory()

        chipBt.setOnClickListener { requestPermissionsAndConnect() }
        requestPermissionsAndConnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(pingTick)
        if (::bluetooth.isInitialized) bluetooth.disconnect()
    }

    private fun bindViews() {
        tvCurrentTemp = findViewById(R.id.tvCurrentTemp)
        tvAlertStatus = findViewById(R.id.tvAlertStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvLatency = findViewById(R.id.tvLatency)
        tvLedState = findViewById(R.id.tvLedState)
        tvBrightness = findViewById(R.id.tvBrightness)
        tvThreshold = findViewById(R.id.tvThreshold)
        icTemp = findViewById(R.id.icTemp)
        icLed = findViewById(R.id.icLed)
        chipBt = findViewById(R.id.chipBt)
        cardTemp = findViewById(R.id.cardTemp)
        switchLed = findViewById(R.id.switchLed)
        switchAuto = findViewById(R.id.switchAuto)
        sliderBrightness = findViewById(R.id.sliderBrightness)
        sliderThreshold = findViewById(R.id.sliderThreshold)
        chartTemperature = findViewById(R.id.chartTemperature)
    }

    private fun requestPermissionsAndConnect() {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            bluetooth.connect()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    // ---------- Controles ----------

    private fun setupControls() {
        sliderThreshold.value = threshold.toFloat().coerceIn(20f, 40f)
        tvThreshold.text = getString(R.string.control_threshold, threshold)
        chartTemperature.setThreshold(threshold.toDouble())
        renderBrightness(sliderBrightness.value.toInt())
        renderLed(false)

        switchLed.setOnCheckedChangeListener { _, isChecked ->
            if (syncingControls) return@setOnCheckedChangeListener
            if (bluetooth.sendCommand(ArduinoProtocol.ledCommand(isChecked))) {
                renderLed(isChecked)
            } else {
                revert(switchLed, !isChecked)
            }
        }

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            if (syncingControls) return@setOnCheckedChangeListener
            if (bluetooth.sendCommand(ArduinoProtocol.autoCommand(isChecked))) {
                switchLed.isEnabled = !isChecked
            } else {
                revert(switchAuto, !isChecked)
            }
        }

        sliderBrightness.addOnChangeListener { _, value, _ -> renderBrightness(value.toInt()) }
        sliderBrightness.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (!bluetooth.sendCommand(ArduinoProtocol.brightnessCommand(slider.value.toInt()))) {
                    notifyNotConnected()
                }
            }
        })

        sliderThreshold.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            threshold = value.toInt()
            AppPrefs.setThreshold(this, threshold)
            tvThreshold.text = getString(R.string.control_threshold, threshold)
            chartTemperature.setThreshold(threshold.toDouble())
            lastTemperature?.let { renderLevel(it) }
        }
        sliderThreshold.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                bluetooth.sendCommand(ArduinoProtocol.thresholdCommand(threshold))
            }
        })
    }

    private fun revert(toggle: MaterialSwitch, previous: Boolean) {
        notifyNotConnected()
        syncingControls = true
        toggle.isChecked = previous
        syncingControls = false
    }

    private fun notifyNotConnected() {
        Snackbar.make(findViewById(R.id.main), R.string.bt_led_not_connected, Snackbar.LENGTH_SHORT).show()
    }

    private fun renderLed(on: Boolean) {
        tvLedState.setText(if (on) R.string.control_led_on else R.string.control_led_off)
        val color = if (on) {
            ContextCompat.getColor(this, R.color.status_warn)
        } else {
            MaterialColors.getColor(icLed, com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        ImageViewCompat.setImageTintList(icLed, ColorStateList.valueOf(color))
    }

    private fun renderBrightness(value: Int) {
        tvBrightness.text = getString(R.string.control_brightness, value * 100 / 255)
    }

    private fun syncControls(status: ArduinoProtocol.DeviceStatus) {
        syncingControls = true
        status.autoMode?.let {
            switchAuto.isChecked = it
            switchLed.isEnabled = !it
        }
        status.ledOn?.let {
            switchLed.isChecked = it
            renderLed(it)
        }
        status.brightness?.let {
            sliderBrightness.value = it.toFloat()
            renderBrightness(it)
        }
        syncingControls = false
    }

    // ---------- Bluetooth ----------

    override fun onStateChanged(state: BluetoothHelper.State, failure: BluetoothHelper.Failure?) {
        renderBluetoothChip(state, failure)
        when (state) {
            BluetoothHelper.State.CONNECTED -> {
                lastMessageAt = SystemClock.elapsedRealtime()
                bluetooth.sendCommand(ArduinoProtocol.thresholdCommand(threshold))
                mainHandler.removeCallbacks(pingTick)
                mainHandler.post(pingTick)
            }
            BluetoothHelper.State.DISCONNECTED -> {
                mainHandler.removeCallbacks(pingTick)
                tvLatency.text = ""
            }
            BluetoothHelper.State.CONNECTING -> Unit
        }
    }

    private fun renderBluetoothChip(state: BluetoothHelper.State, failure: BluetoothHelper.Failure?) {
        val (textRes, colorRes, containerRes) = when (state) {
            BluetoothHelper.State.CONNECTING ->
                Triple(R.string.bt_status_connecting, R.color.status_warn, R.color.status_warn_container)
            BluetoothHelper.State.CONNECTED ->
                Triple(R.string.bt_status_connected, R.color.status_ok, R.color.status_ok_container)
            BluetoothHelper.State.DISCONNECTED -> Triple(
                when (failure) {
                    BluetoothHelper.Failure.NO_PERMISSION -> R.string.bt_status_no_permission
                    BluetoothHelper.Failure.BLUETOOTH_OFF -> R.string.bt_status_off
                    BluetoothHelper.Failure.DEVICE_NOT_PAIRED -> R.string.bt_status_not_paired
                    BluetoothHelper.Failure.CONNECTION_FAILED -> R.string.bt_status_failed
                    BluetoothHelper.Failure.CONNECTION_LOST -> R.string.bt_status_lost
                    null -> R.string.bt_status_disconnected
                },
                R.color.status_bad,
                R.color.status_bad_container
            )
        }
        val color = ContextCompat.getColor(this, colorRes)
        chipBt.setText(textRes)
        chipBt.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, containerRes))
        chipBt.setTextColor(color)
        chipBt.chipIconTint = ColorStateList.valueOf(color)
        chipBt.chipStrokeWidth = 0f
    }

    override fun onMessage(message: ArduinoProtocol.Message) {
        lastMessageAt = SystemClock.elapsedRealtime()
        when (message) {
            is ArduinoProtocol.Message.Pong -> {
                val latency = (SystemClock.elapsedRealtime() - pingSentAt).toInt()
                tvLatency.text = getString(R.string.bt_latency, latency)
            }
            is ArduinoProtocol.Message.SensorError -> {
                sensorError = true
                renderSensorError()
            }
            is ArduinoProtocol.Message.Status -> {
                syncControls(message.status)
                message.status.temperature?.let { temperature ->
                    receivedFromBluetooth = true
                    sensorError = false
                    showTemperature(temperature, live = true)
                    message.status.humidity?.let { showHumidity(it) }
                    uploadReading(temperature, message.status.humidity)
                }
            }
        }
    }

    private fun uploadReading(temperature: Double, humidity: Double?) {
        val now = System.currentTimeMillis()
        val root = database.reference
        root.child("temperature/current").setValue(temperature)
        humidity?.let { root.child("humidity/current").setValue(it) }
        root.child("temperature/history/$now")
            .setValue(SensorReading(value = temperature, timestamp = now, humidity = humidity))
    }

    // ---------- Presentación de datos ----------

    private fun showTemperature(temperature: Double, live: Boolean) {
        lastTemperature = temperature
        tvCurrentTemp.text = getString(R.string.main_temp_format, temperature)
        tvLastUpdate.text = if (live) {
            getString(R.string.main_last_update, timeFormat.format(Date()))
        } else {
            getString(R.string.main_last_saved)
        }
        renderLevel(temperature)

        if (shouldTriggerAlert(previousTemperature, temperature, threshold.toDouble())) {
            NotificationHelper.showTemperatureAlert(this, temperature, threshold.toDouble())
        }
        previousTemperature = temperature
    }

    private fun renderLevel(temperature: Double) {
        if (sensorError) return
        applyLevelStyle(TemperatureStats.level(temperature, threshold.toDouble()))
    }

    private fun renderSensorError() {
        tvAlertStatus.setText(R.string.main_alert_sensor_error)
        styleStatus(R.color.status_bad, R.color.status_bad_container)
    }

    /** level: null sin datos, 0 normal, 1 cerca del umbral, 2 sobre el umbral. */
    private fun applyLevelStyle(level: Int?) {
        when (level) {
            0 -> {
                tvAlertStatus.setText(R.string.main_alert_ok)
                styleStatus(R.color.status_ok, R.color.status_ok_container)
            }
            1 -> {
                tvAlertStatus.setText(R.string.main_alert_near)
                styleStatus(R.color.status_warn, R.color.status_warn_container)
            }
            2 -> {
                tvAlertStatus.setText(R.string.main_alert_high)
                styleStatus(R.color.status_bad, R.color.status_bad_container)
            }
            else -> {
                tvAlertStatus.setText(R.string.main_alert_none)
                styleStatus(R.color.status_off, R.color.status_off_container)
            }
        }
    }

    private fun styleStatus(colorRes: Int, containerRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        tvAlertStatus.setTextColor(color)
        ViewCompat.setBackgroundTintList(
            tvAlertStatus,
            ColorStateList.valueOf(ContextCompat.getColor(this, containerRes))
        )
        ImageViewCompat.setImageTintList(icTemp, ColorStateList.valueOf(color))
    }

    private fun showHumidity(humidity: Double) {
        tvHumidity.text = getString(R.string.main_humidity_format, humidity)
    }

    private fun showStats(readings: List<SensorReading>) {
        val stats = TemperatureStats.compute(readings, TemperatureStats.startOfDay(System.currentTimeMillis()))
        val empty = findViewById<TextView>(R.id.tvStatsEmpty)
        val row = findViewById<View>(R.id.rowStats)
        if (stats == null) {
            empty.visibility = View.VISIBLE
            row.visibility = View.GONE
            return
        }
        empty.visibility = View.GONE
        row.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvStatMin).text = getString(R.string.stats_value, stats.min)
        findViewById<TextView>(R.id.tvStatAvg).text = getString(R.string.stats_value, stats.avg)
        findViewById<TextView>(R.id.tvStatMax).text = getString(R.string.stats_value, stats.max)
    }

    // ---------- Firebase ----------

    private fun observeCurrentTemperature() {
        database.getReference("temperature/current")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (receivedFromBluetooth) return
                    val temperature = snapshot.getValue(Double::class.java) ?: return
                    showTemperature(temperature, live = false)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun observeHumidity() {
        database.getReference("humidity/current")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (receivedFromBluetooth) return
                    snapshot.getValue(Double::class.java)?.let { showHumidity(it) }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun observeTemperatureHistory() {
        database.getReference("temperature/history")
            .limitToLast(HISTORY_WINDOW)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val readings = snapshot.children.mapNotNull {
                        it.getValue(SensorReading::class.java)
                    }
                    chartTemperature.setData(readings.takeLast(CHART_MAX_POINTS))
                    showStats(readings)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        applyFadeTransition()
        finish()
    }

    companion object {
        const val DEFAULT_THRESHOLD_CELSIUS = 30.0
        private const val CHART_MAX_POINTS = 20
        private const val HISTORY_WINDOW = 500
        private const val PING_INTERVAL_MS = 5_000L
        private const val SILENCE_TIMEOUT_MS = 15_000L

        fun isAboveThreshold(
            temperature: Double,
            threshold: Double = DEFAULT_THRESHOLD_CELSIUS
        ): Boolean = temperature > threshold

        fun shouldTriggerAlert(
            previousTemperature: Double?,
            currentTemperature: Double,
            threshold: Double = DEFAULT_THRESHOLD_CELSIUS
        ): Boolean {
            val wasAbove = previousTemperature != null && previousTemperature > threshold
            val isAbove = currentTemperature > threshold
            return isAbove && !wasAbove
        }
    }
}
