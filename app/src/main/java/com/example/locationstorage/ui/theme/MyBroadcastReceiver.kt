package com.example.locationstorage.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.locationstorage.notificationSent
import com.example.locationstorage.sendAlertNow

var isSafe = false

class MyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action=="safe")
        {
            isSafe = true
            Toast.makeText(context, "Nice to hear.", Toast.LENGTH_SHORT)
                .show()
            notificationSent = false
        }
        else if (intent?.action=="not safe")
        {
            isSafe = false
            sendAlertNow = true
            Toast.makeText(context, "Alert sent", Toast.LENGTH_SHORT)
                .show()
            notificationSent = false
        }
    }
}