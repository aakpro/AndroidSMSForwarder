package com.smsforwarder.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

object NetworkUtil {

    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "UNKNOWN"
        val activeNetwork = connectivityManager.activeNetwork ?: return "OFFLINE"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "OFFLINE"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "OTHER"
        }
    }

    data class BatteryInfo(
        val percentage: Int,
        val isCharging: Boolean,
        val plugType: String
    )

    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo(-1, false, "Unknown")

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "Battery"
        }

        return BatteryInfo(percentage, isCharging, plugType)
    }
}
