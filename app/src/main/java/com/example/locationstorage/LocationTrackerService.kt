package com.example.locationstorage

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

var globalRoute = mutableListOf<RoutePoint>()
var globalLines = mutableListOf<RouteLine>()

data class RouteLine(val lat1: Double, val lon1: Double, val lat2: Double, val lon2: Double){

    val m = (lon1 - lon2) / (lat1 - lat2)

    val c = lon1 - (m * lat1)

}

class LocationTrackerService: Service() {

    var lastLocation = null

    private var bundle: Bundle? =null
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    //val fileWriter = FileWriter("result.txt",true)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {

        when (intent?.action) {
            Action.START.name -> start()
            Action.STOP.name -> stop()
            Action.MONITOR.name -> startMonitoring()
        }
        bundle = intent?.extras
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start() {

        val locationManager = LocationManager(applicationContext)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat
            .Builder(this, LOCATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location Tracker")
            .setStyle(NotificationCompat.BigTextStyle())

        startForeground(1, notification.build())

        scope.launch {
            locationManager.trackLocation().collect {location ->
                val latitude = location.latitude.toString()
                val longitude = location.longitude.toString()
                val time = location.time

               applicationContext.openFileOutput("routes.txt",Context.MODE_APPEND).use{
                   it.write("$latitude,$longitude,$time\n".toByteArray())
               }

                applicationContext.openFileOutput("newResult.txt",Context.MODE_APPEND).use{
                    it.write("$latitude,$longitude,$time\n".toByteArray())
                }


                notificationManager.notify(
                    1,
                    notification.setContentText(
                        "Location: ..$latitude / ..$longitude"
                    ).build()
                )

            }

        }
    }


    private fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        applicationContext.openFileOutput("routes.txt",Context.MODE_APPEND).use{
            it.write("-1\n\n".toByteArray())
        }
    }

    private fun startMonitoring() {



        val locationManager = LocationManager(applicationContext)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat
            .Builder(this, LOCATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location Tracker")
            .setStyle(NotificationCompat.BigTextStyle())

        val notificationTwo = NotificationCompat
            .Builder(this, LOCATION_CHANNEL_2)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Position Alert")
            .setStyle(NotificationCompat.BigTextStyle())

        startForeground(1, notification.build())

        val routeName = globalRouteName
//        bundle?.let {
//            bundle?.apply {
//                routeName = getString("File Name")
//            }
//        }
//        if(routeName == "which")
//            notificationManager.notify(2,notificationTwo.build())

        val routeToTrack = mutableListOf<RoutePoint>()
        var read = false

        //notificationManager.notify(2,notificationTwo.setContentText("File Name: $routeName").build())
        if (routeName != "No Route") {
            applicationContext.openFileInput("routes.txt").bufferedReader().forEachLine {
                if (it == routeName) {
                    read = true
                }
                else if (it == "-1")
                    read = false
                else if (read)
                {
                    val point = it.split(",")
                    val location = RoutePoint(point[0].toDouble(),point[1].toDouble() , point[2].toLong())
                    routeToTrack.add(location)
                }
            }
            //notificationManager.notify(2,notificationTwo.build())
        }
        globalRoute = routeToTrack
        val iterator = routeToTrack.listIterator()
        val routeAsLines = routeToLines(iterator)
        globalLines = routeAsLines



        scope.launch {
            locationManager.trackLocation().collect {location ->
                val latitude = location.latitude.toString()
                val longitude = location.longitude.toString()
                val time = location.time

                val point = Triple(location.latitude,location.longitude,location.time)
                val linesIterator = routeAsLines.iterator()
                val onLine = onLine(point,linesIterator)
                if (onLine)
                    notificationManager.notify(2,notificationTwo.setContentText("On Safe Route").build())
                else
                    notificationManager.notify(2,notificationTwo.setContentText("Off Route").build())


                notificationManager.notify(
                    1,
                    notification.setContentText(
                        "Location: ..$latitude / ..$longitude"
                    ).build()
                )

            }

        }
    }

    private fun routeToLines(route: MutableListIterator<RoutePoint>) : MutableList<RouteLine>{
        val lines: MutableList<RouteLine> = mutableListOf()

        var point =  RoutePoint(0.0,0.0,0)
        var pointTwo : RoutePoint
        while(route.hasNext())
        {
            if (route.nextIndex() == 0)
                point = route.next()
            pointTwo = route.next()

            val lat1 = point.latitude
            val lon1 = point.longitude
            val lat2 = pointTwo.latitude
            val lon2 = pointTwo.longitude
            val latDifference = lat1 -lat2
            val lonDifference = lon1 - lon2
            if ((latDifference > 0.00001 || latDifference < -0.00001) || (lonDifference > 0.00001 || lonDifference < -0.00001)) {

                lines.add(RouteLine(lat1,lon1,lat2,lon2))
                point = pointTwo
            }



        }
        return lines
    }
    private fun onLine(point: Triple<Double,Double,Long>,lines : MutableIterator<RouteLine>) : Boolean
    {
        lines.forEach{
            val c = point.second - it.m*point.first
            if ((c - it.c)<0.001 && (c - it.c)>-0.001)
            {
                if((point.first in it.lat1-0.0015..it.lat2+0.0015) || (point.first in it.lat2-0.0015..it.lat1+0.0015))
                    if ((point.second in it.lon1-0.0015..it.lon2+0.0015) || (point.second in it.lon2-0.0015..it.lon1+0.0015))
                        return true
            }
        }
        return false
    }
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    enum class Action {
        START, STOP, MONITOR
    }

    companion object {
        const val LOCATION_CHANNEL = "location_channel"
        const val LOCATION_CHANNEL_2 = "location_channel_2"
    }
}