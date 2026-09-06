package com.smsforwarder.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.smsforwarder.data.model.SimCardInfo

class SimUtil(private val context: Context) {

    /**
     * Queries Android SubscriptionManager for all currently active SIM cards.
     */
    fun getActiveSimCards(): List<SimCardInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted, cannot read SIM details")
            return emptyList()
        }

        return try {
            val list: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            list?.map { subInfo ->
                SimCardInfo(
                    slotIndex = subInfo.simSlotIndex,
                    subscriptionId = subInfo.subscriptionId,
                    displayName = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}",
                    carrierName = subInfo.carrierName?.toString() ?: "Unknown Carrier",
                    phoneNumber = subInfo.number
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active SIM cards", e)
            emptyList()
        }
    }

    /**
     * Defensively extracts the SIM slot index from the incoming SMS intent bundle.
     * Different OEM manufacturers (Samsung, Xiaomi, Pixel, MediaTek, Huawei) use different bundle keys.
     */
    fun extractSimSlotFromIntent(intent: Intent, activeSims: List<SimCardInfo>): Int {
        val bundle = intent.extras ?: return 0

        // 1. Check for standard & OEM subscription ID keys
        val subId = bundle.getInt("subscription", -1).takeIf { it != -1 }
            ?: bundle.getInt("subscriptionId", -1).takeIf { it != -1 }
            ?: bundle.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1).takeIf { it != -1 }
            ?: bundle.getInt("simId", -1).takeIf { it != -1 }
            ?: bundle.getInt("sub_id", -1).takeIf { it != -1 }

        if (subId != null) {
            val matchedSim = activeSims.find { it.subscriptionId == subId }
            if (matchedSim != null) {
                Log.d(TAG, "Resolved SIM slot ${matchedSim.slotIndex} from subscriptionId: $subId")
                return matchedSim.slotIndex
            }
        }

        // 2. Check for direct slot index keys
        val directSlot = bundle.getInt("slot", -1).takeIf { it != -1 }
            ?: bundle.getInt("slot_id", -1).takeIf { it != -1 }
            ?: bundle.getInt("simSlot", -1).takeIf { it != -1 }
            ?: bundle.getInt("sim_slot", -1).takeIf { it != -1 }
            ?: bundle.getInt("phone", -1).takeIf { it != -1 }

        if (directSlot != null) {
            Log.d(TAG, "Resolved SIM slot directly: $directSlot")
            return directSlot
        }

        Log.d(TAG, "Could not resolve specific SIM slot from extras, defaulting to slot 0 (SIM 1)")
        return 0
    }

    companion object {
        private const val TAG = "SimUtil"
    }
}
