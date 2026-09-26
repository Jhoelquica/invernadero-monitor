package com.invernadero.monitor

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity(), BluetoothHelper.Listener {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var bluetooth: BluetoothHelper

    private lateinit var tvCurrentTemp: TextView
    private lateinit var tvAlertStatus: TextView
    private lateinit var tvBtStatus: TextView
    private lateinit var switchLed: MaterialSwitch
    private lateinit var chartTemperature: TemperatureChartView

    private var isRevertingLedSwitch = false
    private var previousTemperature: Double? = null

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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        database = FirebaseDatabase.getInstance()
        bluetooth = BluetoothHelper(this, this)

        tvCurrentTemp = findViewById(R.id.tvCurrentTemp)
        tvAlertStatus = findViewById(R.id.tvAlertStatus)
        tvBtStatus = findViewById(R.id.tvBtStatus)
        switchLed = findViewById(R.id.switchLed)
        chartTemperature = findViewById(R.id.chartTemperature)

        NotificationHelper.createNotificationChannel(this)

        setupLedSwitch()
        observeCurrentTemperature()
        observeTemperatureHistory()

        tvBtStatus.setOnClickListener { requestPermissionsAndConnect() }

        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        requestPermissionsAndConnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bluetooth.isInitialized) bluetooth.disconnect()
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
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            bluetooth.connect()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setupLedSwitch() {
        switchLed.setOnCheckedChangeListener { _, isChecked ->
            if (isRevertingLedSwitch) return@setOnCheckedChangeListener
            if (!bluetooth.sendLed(isChecked)) {
                Toast.makeText(this, R.string.bt_led_not_connected, Toast.LENGTH_SHORT).show()
                isRevertingLedSwitch = true
                switchLed.isChecked = !isChecked
                isRevertingLedSwitch = false
            }
        }
    }

    override fun onStateChanged(state: BluetoothHelper.State, failure: BluetoothHelper.Failure?) {
        tvBtStatus.setText(
            when (state) {
                BluetoothHelper.State.CONNECTING -> R.string.bt_status_connecting
                BluetoothHelper.State.CONNECTED -> R.string.bt_status_connected
                BluetoothHelper.State.DISCONNECTED -> when (failure) {
                    BluetoothHelper.Failure.NO_PERMISSION -> R.string.bt_status_no_permission
                    BluetoothHelper.Failure.BLUETOOTH_OFF -> R.string.bt_status_off
                    BluetoothHelper.Failure.DEVICE_NOT_PAIRED -> R.string.bt_status_not_paired
                    BluetoothHelper.Failure.CONNECTION_FAILED -> R.string.bt_status_failed
                    BluetoothHelper.Failure.CONNECTION_LOST -> R.string.bt_status_lost
                    null -> R.string.bt_status_disconnected
                }
            }
        )
    }

    override fun onTemperature(value: Double) {
        showTemperature(value)
        uploadReading(value)
    }

    private fun uploadReading(value: Double) {
        val now = System.currentTimeMillis()
        val root = database.reference
        root.child("temperature/current").setValue(value)
        root.child("temperature/history/$now").setValue(SensorReading(value = value, timestamp = now))
    }

    private fun showTemperature(temperature: Double) {
        tvCurrentTemp.text = getString(R.string.main_temp_format, temperature)

        tvAlertStatus.text = if (isAboveThreshold(temperature)) {
            getString(R.string.main_alert_high)
        } else {
            getString(R.string.main_alert_ok)
        }

        if (shouldTriggerAlert(previousTemperature, temperature)) {
            NotificationHelper.showTemperatureAlert(this, temperature, DEFAULT_THRESHOLD_CELSIUS)
        }
        previousTemperature = temperature
    }

    private fun observeCurrentTemperature() {
        database.getReference("temperature/current")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val temperature = snapshot.getValue(Double::class.java) ?: return
                    showTemperature(temperature)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Se mantiene la última lectura mostrada.
                }
            })
    }

    private fun observeTemperatureHistory() {
        database.getReference("temperature/history")
            .limitToLast(CHART_MAX_POINTS)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val readings = snapshot.children.mapNotNull {
                        it.getValue(SensorReading::class.java)
                    }
                    chartTemperature.setData(readings)
                }

                override fun onCancelled(error: DatabaseError) {
                    // El gráfico conserva los últimos datos cargados.
                }
            })
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    companion object {
        const val DEFAULT_THRESHOLD_CELSIUS = 30.0
        private const val CHART_MAX_POINTS = 20

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
