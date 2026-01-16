package com.example.segnmea

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class BluetoothManager(
    private val context: Context,
    private val onDataReceived: (String) -> Unit,
    private val onStatusChange: (String) -> Unit
) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "BluetoothManager"

    // Standard Serial Port Profile UUID
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices ?: emptySet()
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        if (bluetoothAdapter == null) {
            onStatusChange("Bluetooth not supported")
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

            // Cancel any existing threads
            disconnect()

            connectThread = ConnectThread(device)
            connectThread?.start()
            onStatusChange("Connecting...")
        } catch (e: Exception) {
            onStatusChange("Error connecting: ${e.message}")
        }
    }

    fun disconnect() {
        connectThread?.cancel()
        connectedThread?.cancel()
        connectThread = null
        connectedThread = null
        onStatusChange("Disconnected")
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
            } catch (e: IOException) {
                try {
                    mmSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2)
                }
                handler.post { onStatusChange("Connection Failed") }
                return
            }

            synchronized(this@BluetoothManager) {
                connectThread = null
            }

            mmSocket?.let {
                connected(it)
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }

    private fun connected(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        handler.post { onStatusChange("Connected") }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val buffer = ByteArray(1024)

        override fun run() {
            var bytes: Int
            val stringBuilder = StringBuilder()

            while (true) {
                try {
                    bytes = mmInStream.read(buffer)
                    val readMessage = String(buffer, 0, bytes)
                    stringBuilder.append(readMessage)

                    // Process complete lines
                    var newlineIndex = stringBuilder.indexOf('\n')
                    while (newlineIndex != -1) {
                        val line = stringBuilder.substring(0, newlineIndex).trim()
                        if (line.isNotEmpty()) {
                            handler.post { onDataReceived(line) }
                        }
                        stringBuilder.delete(0, newlineIndex + 1)
                        newlineIndex = stringBuilder.indexOf('\n')
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    handler.post { onStatusChange("Disconnected") }
                    break
                }
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }
}
