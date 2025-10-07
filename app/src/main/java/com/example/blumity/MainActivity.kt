package com.example.blumity

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : Activity() {
    private val REQUEST_ENABLE_BT = 1
    private val REQUEST_PERMISSIONS = 2
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private lateinit var messagesView: TextView
    private lateinit var sendButton: Button
    private lateinit var messageInput: EditText
    private lateinit var connectButton: Button
    private lateinit var deviceSpinner: Spinner
    private var pairedDevicesList: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messagesView = findViewById(R.id.messages_view)
        sendButton = findViewById(R.id.send_button)
        messageInput = findViewById(R.id.message_input)
        connectButton = findViewById(R.id.connect_button)
        deviceSpinner = findViewById(R.id.device_spinner)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Request Bluetooth permissions if necessary
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQUEST_PERMISSIONS
        )

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        connectButton.setOnClickListener {
            loadPairedDevices()
        }

        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotBlank()) {
                sendMessage(message)
                appendMessage("Me: $message")
                messageInput.setText("")
            }
        }
    }

    private fun loadPairedDevices() {
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        if (pairedDevices.isNotEmpty()) {
            pairedDevicesList = pairedDevices.toList()
            val deviceNames = pairedDevicesList.map { it.name }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            deviceSpinner.adapter = adapter

            deviceSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val device = pairedDevicesList[position]
                    connectToDevice(device)
                    deviceSpinner.setSelection(-1)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            })
        } else {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                socket?.connect()
                outputStream = socket?.outputStream
                inputStream = socket?.inputStream
                runOnUiThread { appendMessage("Connected to ${device.name}") }
                listenForMessages()
            } catch (e: IOException) {
                runOnUiThread { appendMessage("Connection failed: ${e.message}") }
            }
        }.start()
    }

    private fun sendMessage(message: String) {
        try {
            outputStream?.write(message.toByteArray())
        } catch (e: IOException) {
            appendMessage("Send failed: ${e.message}")
        }
    }

    private fun listenForMessages() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        runOnUiThread { appendMessage("Peer: $message") }
                    }
                } catch (e: IOException) {
                    break
                }
            }
        }.start()
    }

    private fun appendMessage(message: String) {
        messagesView.append("$message\n")
    }
}