package com.wild.android.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

internal fun Context.bluetoothManagerCompat(): BluetoothManager? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        getSystemService(BluetoothManager::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
}
