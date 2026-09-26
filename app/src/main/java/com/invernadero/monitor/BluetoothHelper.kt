package com.invernadero.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Conexión Bluetooth clásica (SPP) con el HC-05. El formato de los mensajes
 * está definido en [ArduinoProtocol]. Todos los callbacks del Listener se
 * entregan en el hilo principal.
 */
@SuppressLint("MissingPermission")
class BluetoothHelper(
    private val context: Context,
    private val listener: Listener
) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    enum class Failure { NO_PERMISSION, BLUETOOTH_OFF, DEVICE_NOT_PAIRED, CONNECTION_FAILED, CONNECTION_LOST }

    interface Listener {
        fun onStateChanged(state: State, failure: Failure? = null)
        fun onMessage(message: ArduinoProtocol.Message)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val writeLock = Any()

    @Volatile private var state = State.DISCONNECTED
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var closing = false

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun isConnected(): Boolean = state == State.CONNECTED

    fun connect() {
        if (state != State.DISCONNECTED) return

        if (!hasPermission()) {
            notifyState(State.DISCONNECTED, Failure.NO_PERMISSION)
            return
        }
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            notifyState(State.DISCONNECTED, Failure.BLUETOOTH_OFF)
            return
        }
        val device = adapter.bondedDevices.firstOrNull {
            it.name?.contains(DEVICE_NAME_HINT, ignoreCase = true) == true
        }
        if (device == null) {
            notifyState(State.DISCONNECTED, Failure.DEVICE_NOT_PAIRED)
            return
        }

        closing = false
        notifyState(State.CONNECTING)
        Thread({ runConnection(device) }, "bt-hc05").start()
    }

    fun disconnect() {
        closing = true
        closeSocket()
        state = State.DISCONNECTED
    }

    /** Envía una línea de comando (se agrega el salto de línea). Devuelve false si no hay conexión. */
    fun sendCommand(command: String): Boolean {
        val out = output ?: return false
        return try {
            synchronized(writeLock) {
                out.write((command + "\n").toByteArray(Charsets.US_ASCII))
                out.flush()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun runConnection(device: BluetoothDevice) {
        val newSocket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            notifyState(State.DISCONNECTED, Failure.CONNECTION_FAILED)
            return
        }

        socket = newSocket
        output = newSocket.outputStream
        notifyState(State.CONNECTED)

        val buffer = ByteArray(256)
        val pending = StringBuilder()
        try {
            val input = newSocket.inputStream
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                pending.append(String(buffer, 0, read, Charsets.US_ASCII))
                var newline = pending.indexOf("\n")
                while (newline >= 0) {
                    val line = pending.substring(0, newline)
                    pending.delete(0, newline + 1)
                    ArduinoProtocol.parseLine(line)?.let { message ->
                        mainHandler.post { listener.onMessage(message) }
                    }
                    newline = pending.indexOf("\n")
                }
                if (pending.length > MAX_LINE_LENGTH) pending.setLength(0)
            }
        } catch (e: IOException) {
            // Se trata igual que una desconexión normal.
        }

        closeSocket()
        if (!closing) notifyState(State.DISCONNECTED, Failure.CONNECTION_LOST)
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Nada que hacer si ya estaba cerrado.
        }
        socket = null
        output = null
    }

    private fun notifyState(newState: State, failure: Failure? = null) {
        state = newState
        mainHandler.post { listener.onStateChanged(newState, failure) }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val DEVICE_NAME_HINT = "HC-05"
        private const val MAX_LINE_LENGTH = 128
    }
}
