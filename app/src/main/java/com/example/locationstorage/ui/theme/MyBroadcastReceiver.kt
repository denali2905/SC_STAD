package com.example.locationstorage.ui.theme

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.example.locationstorage.notificationSent
import com.example.locationstorage.sendAlertNow

var isSafe = false

class MyBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(intent?.action=="safe")
        {
            isSafe = true
            Toast.makeText(context, "Nice to hear.", Toast.LENGTH_SHORT)
                .show()
            notificationManager.cancel(2)
        }
        else if (intent?.action=="not safe")
        {
            isSafe = false
            sendAlertNow = true
            Toast.makeText(context, "Alert sent", Toast.LENGTH_SHORT)
                .show()
            notificationSent = false
            notificationManager.cancel(2)
        }
    }
}