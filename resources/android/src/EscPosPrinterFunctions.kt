package com.blutrixx.plugins.escposprinter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.nativephp.mobile.bridge.BridgeFunction

object EscPosPrinterFunctions {

    private const val TAG = "EscPosPrinter"

    // Shared state across bridge functions
    private var printer: EscPosPrinter? = null
    private var bluetoothConnection: BluetoothConnection? = null

    private const val DEFAULT_PRINTER_DPI = 203
    private const val DEFAULT_PRINTER_WIDTH = 48f
    private const val DEFAULT_PRINTER_CHARS_PER_LINE = 32

    /**
     * We only ever connect to already-bonded Bluetooth devices (no discovery/
     * scanning), so there is no need for ACCESS_FINE/COARSE_LOCATION or
     * BLUETOOTH_SCAN — those were legacy leftovers and are no longer declared
     * in nativephp.json.
     *
     * On API < 31 (Build.VERSION_CODES.S), BLUETOOTH / BLUETOOTH_ADMIN are
     * normal (install-time) permissions granted automatically at install —
     * there's nothing to check at runtime.
     *
     * On API >= 31, connecting to a bonded device requires the runtime
     * permission BLUETOOTH_CONNECT.
     */
    private fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    class CheckPermissions(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val granted = hasPermissions(context)
                mapOf("granted" to granted)
            } catch (e: Exception) {
                Log.e(TAG, "CheckPermissions error: ${e.message}")
                mapOf("error" to (e.message ?: "Failed to check permissions"))
            }
        }
    }

    class RequestPermissions(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val granted = hasPermissions(context)
                if (granted) {
                    mapOf("granted" to true)
                } else {
                    mapOf("granted" to false, "message" to "Bluetooth permissions required. Please enable in Settings.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "RequestPermissions error: ${e.message}")
                mapOf("error" to (e.message ?: "Failed to request permissions"))
            }
        }
    }

    class Connect(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                if (!hasPermissions(context)) {
                    return mapOf("error" to "Bluetooth permissions not granted")
                }

                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                    ?: return mapOf("error" to "Device doesn't support Bluetooth")

                if (!bluetoothAdapter.isEnabled) {
                    return mapOf("error" to "Bluetooth is not enabled")
                }

                var connectedDevice: String? = null

                bluetoothAdapter.bondedDevices?.firstOrNull { device ->
                    try {
                        bluetoothConnection = BluetoothConnection(device)
                        val isConnected = bluetoothConnection?.connect()
                        if (isConnected != null) {
                            connectedDevice = device.name ?: device.address
                            true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect to ${device.name}: ${e.message}")
                        false
                    }
                }

                if (connectedDevice != null) {
                    printer = EscPosPrinter(
                        bluetoothConnection,
                        DEFAULT_PRINTER_DPI,
                        DEFAULT_PRINTER_WIDTH,
                        DEFAULT_PRINTER_CHARS_PER_LINE
                    )
                    Log.d(TAG, "Connected to $connectedDevice")
                    mapOf("device" to connectedDevice!!)
                } else {
                    bluetoothConnection = null
                    mapOf("error" to "No printer found among paired devices")
                }
            } catch (e: Exception) {
                bluetoothConnection = null
                printer = null
                Log.e(TAG, "Connect error: ${e.message}")
                mapOf("error" to (e.message ?: "Connection failed"))
            }
        }
    }

    class Disconnect(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                bluetoothConnection?.disconnect()
                printer = null
                bluetoothConnection = null
                mapOf("disconnected" to true)
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "Disconnect failed"))
            }
        }
    }

    class IsConnected(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val connected = printer != null && bluetoothConnection != null
                mapOf("connected" to connected)
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "Connection check failed"))
            }
        }
    }

    class GetPrinters(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                if (!hasPermissions(context)) {
                    return mapOf("error" to "Bluetooth permissions not granted")
                }

                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bluetoothAdapter = bluetoothManager.adapter
                    ?: return mapOf("error" to "Device doesn't support Bluetooth")

                if (!bluetoothAdapter.isEnabled) {
                    return mapOf("error" to "Bluetooth is not enabled")
                }

                val deviceList = bluetoothAdapter.bondedDevices?.map { device ->
                    mapOf("name" to (device.name ?: "Unknown Device"), "address" to device.address)
                } ?: emptyList()

                mapOf("printers" to deviceList)
            } catch (e: Exception) {
                Log.e(TAG, "GetPrinters error: ${e.message}")
                mapOf("error" to (e.message ?: "Failed to list printers"))
            }
        }
    }

    class Print(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val commands = parameters["commands"] as? String
                    ?: return mapOf("error" to "No print commands provided")

                val currentPrinter = printer
                    ?: return mapOf("error" to "Printer not connected. Please connect a Bluetooth printer first.")

                currentPrinter.printFormattedText("[L]\n[L]\n" + commands + "[L]\n[L]\n")
                Log.d(TAG, "Print successful")
                mapOf("printed" to true)
            } catch (e: Exception) {
                Log.e(TAG, "Print error: ${e.message}")
                mapOf("error" to (e.message ?: "Print failed"))
            }
        }
    }

    class TestPrint(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val currentPrinter = printer
                    ?: return mapOf("error" to "Printer not connected")

                val testCommands = """
                    [C]TEST PRINT
                    [C]================
                    [L]Blutrixx App
                    [L]NativePHP Printer Test
                    [L]Date: ${java.util.Date()}
                    [C]================


                """.trimIndent()

                currentPrinter.printFormattedText("[L]\n[L]\n" + testCommands + "[L]\n[L]\n")
                mapOf("printed" to true)
            } catch (e: Exception) {
                Log.e(TAG, "TestPrint error: ${e.message}")
                mapOf("error" to (e.message ?: "Test print failed"))
            }
        }
    }

    class ConvertLogo(private val context: Context) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return try {
                val assetManager: AssetManager = context.assets

                // Accept a filename parameter; default to logo.png
                val filename = (parameters["filename"] as? String)?.takeIf { it.isNotEmpty() } ?: "logo.png"

                val assetFiles = assetManager.list("images")
                if (assetFiles == null || !assetFiles.contains(filename)) {
                    return mapOf("error" to "Image not found in assets/images/$filename")
                }

                val inputStream = assetManager.open("images/$filename")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap == null) {
                    return mapOf("error" to "Failed to decode image: $filename")
                }

                val hexString = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)

                if (hexString.isNullOrEmpty()) {
                    return mapOf("error" to "Generated hex string is empty")
                }

                mapOf("hex" to hexString)
            } catch (e: Exception) {
                Log.e(TAG, "ConvertLogo error: ${e.message}")
                mapOf("error" to (e.message ?: "Failed to convert logo"))
            }
        }
    }
}
