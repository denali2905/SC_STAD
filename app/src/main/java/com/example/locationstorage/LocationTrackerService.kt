package com.example.locationstorage

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.locationstorage.ui.theme.MyBroadcastReceiver
import com.example.locationstorage.ui.theme.isSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

var globalRoute = mutableListOf<RoutePoint>()
var globalLines = mutableListOf<RouteLine>()
var numberOfRoutes = 0
var notificationSent = false
var sendAlertNow = false

data class RouteLine(val lat1: Double, val lon1: Double, val lat2: Double, val lon2: Double){

    val m = (lon1 - lon2) / (lat1 - lat2)

    val c = lon1 - (m * lat1)

}

data class EmergencyContact(val name: String,val number: String)

class LocationTrackerService: Service() {

    // private var savedRoutes : MutableList<MutableList<RoutePoint>> = mutableListOf()
    var lastLocation = null
    private var savedRoutesInfo = mutableListOf<Triple<Int,Int,Int>>()
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

        val safeAlertIntent = Intent(this, MyBroadcastReceiver::class.java).apply {
            action = "safe"
        }

        val notSafeAlertIntent = Intent(this, MyBroadcastReceiver::class.java).apply {
            action = "not safe"
        }

        val safeAlertPendingIntent:PendingIntent = PendingIntent.getBroadcast(this,0,safeAlertIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notSafeAlertPendingIntent:PendingIntent = PendingIntent.getBroadcast(this,0,notSafeAlertIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat
            .Builder(this, LOCATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location Tracker")
            .setStyle(NotificationCompat.BigTextStyle())
            .addAction(com.google.android.gms.base.R.drawable.common_google_signin_btn_icon_light,"Not Safe",notSafeAlertPendingIntent)

        val notificationTwo = NotificationCompat
            .Builder(this, LOCATION_CHANNEL_2)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Position Alert")
            .setStyle(NotificationCompat.BigTextStyle())
            .addAction(com.google.android.gms.base.R.drawable.common_google_signin_btn_icon_light,"Safe",safeAlertPendingIntent)
            .addAction(com.google.android.gms.base.R.drawable.common_google_signin_btn_icon_light,"Not Safe",notSafeAlertPendingIntent)
            .setAutoCancel(true)


        startForeground(1, notification.build())
        var lastPoint = RoutePoint(0.0,0.0,0)
        var currentRoute = mutableListOf<RoutePoint>()
        var pointsWithoutMoving = 0
        //applicationContext.openFileInput("savedRoutes.txt").available()
        val savedRoutes = readSavedRoutes()
        numberOfRoutes = savedRoutes.size
        val savedLines : MutableList<MutableList<RouteLine>> = mutableListOf()
        val sameStartingPoint = mutableListOf<Boolean>()
        val pointsOnRoute = mutableListOf<Int>()
        var pointFound = false
        var pointsNotFound = 0
        var saveRoute = true
        var pointsNotSafe = 0

        if (savedRoutes.isNotEmpty()) {
            for (route in savedRoutes) {
                val iterator = route.listIterator()
                savedLines.add(routeToLines(iterator))
                sameStartingPoint.add(false)
                pointsOnRoute.add(0)
            }
        }

        notificationManager.notify(2,
            notificationTwo.setContentText("Are you okay?").build())


        scope.launch {
            locationManager.trackLocation().collect {location ->
                if (sendAlertNow)
                {
                    sendAlert(location.latitude,location.longitude)
                    sendAlertNow = false
                }
                val thisPoint = RoutePoint(location.latitude,location.longitude,location.time)
                if (!thisPoint.isEqualTo(lastPoint)) {
                    applicationContext.openFileOutput("main.txt", Context.MODE_APPEND).use {
                        it.write("${thisPoint.latitude},${thisPoint.longitude},${thisPoint.timeInUNIX}\n".toByteArray())
                    }
                    lastPoint = thisPoint
                }

                if(currentRoute.isEmpty()) {
                    currentRoute.add(thisPoint)
                    for (route in savedRoutes)
                    {
                        if (route[0].isEqualTo(thisPoint))
                            sameStartingPoint[savedRoutes.indexOf(route)]=true
                    }
                }
                else {
                    if (currentRoute.last().isEqualTo(thisPoint)) {
                        if (currentRoute.size == 1)
                            currentRoute[0].timeInUNIX = thisPoint.timeInUNIX
                        else{
                            pointsWithoutMoving++
                            if(savedRoutes.isNotEmpty())
                            {
                                if (!pointFound)
                                    pointsNotFound++
                            }
                        }
                    }
                    else {
                        currentRoute.add(thisPoint)
                        pointsWithoutMoving = 0
                        if (savedRoutes.isNotEmpty()) {
                            for (i in 0..<savedRoutes.size) {
                                //if (sameStartingPoint[i]) {
                                    val iterator = savedLines[i].listIterator()
                                    if (onLine(thisPoint, iterator)) {
                                        pointsOnRoute[i]++
                                        pointFound = true
                                    }
                                //}
                            }
                            if (!pointFound)
                                pointsNotFound++
                            else {
                                pointsNotFound = 0
                                pointFound = false
                            }
                        }

                    }
                }
                if(pointsNotFound>15 && !notificationSent && !isSafe)
                {
                    notificationManager.notify(2,
                        notificationTwo.setContentText("Are you okay?").build())
                    notificationSent = true
                }

                if (notificationSent){
                    if (isSafe)
                    {
                        saveRoute = true
                        pointsNotSafe = 0
                    }
                    else
                        pointsNotSafe ++

                }

                if(pointsNotSafe>30)
                {
                    sendAlert(thisPoint.latitude,thisPoint.longitude)
                    pointsNotSafe = 0
                    saveRoute = false
                }

                if (pointsWithoutMoving>30){
                    if (saveRoute){
                        numberOfRoutes++
                        writeRoute(currentRoute.listIterator())
                        savedRoutes.add(currentRoute)
                        val iterator = currentRoute.listIterator()
                        savedLines.add(routeToLines(iterator))
                        sameStartingPoint.add(false)
                        pointsOnRoute.add(0)
                        currentRoute = mutableListOf()
                    }
                    saveRoute = true
                    pointsWithoutMoving = 0
                    pointsNotFound = 0
                    isSafe = false
                }



                notificationManager.notify(
                    1,
                    notification.setContentText(
                        "Location: ..${thisPoint.latitude} / ..${thisPoint.longitude}"
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

                val point = RoutePoint(location.latitude,location.longitude,location.time)
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

    private fun readEmergencyContacts() : MutableList<EmergencyContact> {
        val contacts = mutableListOf<EmergencyContact>()
        val file = File(filesDir,"EmergencyContacts.txt")
        if (file.exists()) {
            applicationContext.openFileInput("EmergencyContacts.txt").bufferedReader().forEachLine {
                val contact = it.split(",")
                contacts.add(EmergencyContact(contact[0],contact[1]))
            }

        }
        return contacts
    }

    private fun sendAlert(latitude: Double,longitude:Double){
        val contacts = readEmergencyContacts()

        try {
            val smsManager: SmsManager = this.getSystemService(SmsManager::class.java)
            for (contact in contacts) {
                val message = arrayListOf( "ATTENTION ${contact.name}, I may be in danger...\n Please reach out to me\n",
                    "I am here -> https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")

                smsManager.sendMultipartTextMessage(contact.number,null,message,null,null)



                //Toast.makeText(this, "Messages sent", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            //Toast.makeText(applicationContext, "Messages failed", Toast.LENGTH_SHORT).show()
        }

    }

    private fun readSavedRoutes() : MutableList<MutableList<RoutePoint>>
    {
        var tempRoute = mutableListOf<RoutePoint>()
        val tempRoutes = mutableListOf<MutableList<RoutePoint>>()
        var tempRouteInfo = mutableListOf<Triple<Int,Int,Int>>()
        var secondLine = false
        val file = File(filesDir,"savedRoutes.txt")
        if(file.exists()) {
            applicationContext.openFileInput("savedRoutes.txt").bufferedReader().forEachLine {
                if (it.startsWith("Route")) {
                    tempRoute = mutableListOf()
                    secondLine = true

                } else if (secondLine) {
                    val info = it.split(",")
                    tempRouteInfo.add(Triple(info[0].toInt(), info[1].toInt(), info[2].toInt()))
                    secondLine = false
                } else if (it == "End") {
                    tempRoutes.add(tempRoute)
                } else if (it.isNotBlank()) {
                    val point = it.split(",")
                    val location =
                        RoutePoint(point[0].toDouble(), point[1].toDouble(), point[2].toLong())
                    tempRoute.add(location)
                }
            }
        }
        return tempRoutes
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
    private fun onLine(point: RoutePoint,lines : MutableIterator<RouteLine>) : Boolean
    {
        lines.forEach{
            val c = point.longitude - it.m*point.latitude
            if ((c - it.c)<0.001 && (c - it.c)>-0.001)
            {
                if((point.latitude in it.lat1-0.0015..it.lat2+0.0015) || (point.latitude in it.lat2-0.0015..it.lat1+0.0015))
                    if ((point.longitude in it.lon1-0.0015..it.lon2+0.0015) || (point.longitude in it.lon2-0.0015..it.lon1+0.0015))
                        return true
            }
        }
        return false
    }
    private fun writeRoute(route: MutableListIterator<RoutePoint>){
        applicationContext.openFileOutput("savedRoutes.txt", Context.MODE_APPEND).use {
            it.write("Route_$numberOfRoutes\n1,0,0\n".toByteArray())
            route.forEach { thisPoint ->

                it.write("${thisPoint.latitude},${thisPoint.longitude},${thisPoint.timeInUNIX}\n".toByteArray())
                }
            it.write("End\n\n".toByteArray())
        }

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