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
                    
                    // Process complete lines or sentences starting with $ or ! (AIS)
                    var delimiterIndex = indexOfDelimiter(stringBuilder)
                    while (delimiterIndex != -1) {
                        // Find the next start delimiter or newline
                        var nextDelimiter = -1
                        val nextDollar = stringBuilder.indexOf('$', delimiterIndex + 1)
                        val nextExclamation = stringBuilder.indexOf('!', delimiterIndex + 1)
                        val nextNewline = stringBuilder.indexOf('\n', delimiterIndex)
                        
                        // Find the earliest occurrence of $, !, or \n
                        var minIndex = Int.MAX_VALUE
                        if (nextDollar != -1) minIndex = Math.min(minIndex, nextDollar)
                        if (nextExclamation != -1) minIndex = Math.min(minIndex, nextExclamation)
                        if (nextNewline != -1) minIndex = Math.min(minIndex, nextNewline)
                        
                        if (minIndex != Int.MAX_VALUE) {
                            nextDelimiter = minIndex
                        }

                        if (nextDelimiter != -1) {
                            val line = stringBuilder.substring(delimiterIndex, nextDelimiter).trim()
                            if (line.isNotEmpty()) {
                                handler.post { onDataReceived(line) }
                            }
                            // If delimiter was start char, we keep it for next iteration
                            if (nextDelimiter == nextDollar || nextDelimiter == nextExclamation) {
                                stringBuilder.delete(0, nextDelimiter)
                            } else {
                                stringBuilder.delete(0, nextDelimiter + 1)
                            }
                            delimiterIndex = indexOfDelimiter(stringBuilder)
                        } else {
                            // No end of sentence found yet, wait for more data
                            break 
                        }
                    }
                    
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    handler.post { onStatusChange("Disconnected") }
                    break
                }
            }
        }
        
        private fun indexOfDelimiter(sb: StringBuilder): Int {
            val dollar = sb.indexOf('$')
            val exclamation = sb.indexOf('!')
            
            if (dollar == -1) return exclamation
            if (exclamation == -1) return dollar
            return Math.min(dollar, exclamation)
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
