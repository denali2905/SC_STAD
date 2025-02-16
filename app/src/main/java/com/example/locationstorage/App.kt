package com.example.locationstorage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build


class App: Application() {

    override fun onCreate() {
        super.onCreate()

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelOne = NotificationChannel(
                LocationTrackerService.LOCATION_CHANNEL,
                "Location",
                NotificationManager.IMPORTANCE_LOW
            )

            val channelTwo = NotificationChannel(
                LocationTrackerService.LOCATION_CHANNEL_2,
                "No File",
                NotificationManager.IMPORTANCE_HIGH
            )

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channelOne)
            notificationManager.createNotificationChannel(channelTwo)
        }
    }

}