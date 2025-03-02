package com.example.locationstorage

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.text.InputType
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat.isLocationEnabled
import com.example.locationstorage.ui.theme.LocationStorageTheme
import com.example.locationstorage.ui.theme.isSafe
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import java.io.File
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.round

var snoozeTime : Long = 0
data class RoutePoint(val latitude:Double,val longitude:Double, var timeInUNIX: Long)
{
    fun time(): Calendar{
        val temp = Calendar.getInstance(TimeZone.getDefault())
        temp.timeInMillis=timeInUNIX
        return temp
    }

    val day = time().get(Calendar.DAY_OF_WEEK)

    val hour = time().get(Calendar.HOUR_OF_DAY)

    val minute = time().get(Calendar.MINUTE)

    val timeInMinutes = hour*60+minute

    fun isEqualTo(nextPoint: RoutePoint): Boolean{
        val latDiff = latitude-nextPoint.latitude
        val lonDiff = longitude-nextPoint.longitude
        return !((latDiff>0.001 || latDiff<-0.001)||lonDiff>0.001||lonDiff<-0.001)
    }

}
var globalRouteName = ""
@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private val locationManager by lazy {
        LocationManager(applicationContext)
    }
    private val routes = mutableListOf<MutableList<RoutePoint>>(mutableListOf())

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            false
                        ) -> {
                    Toast.makeText(this, "Location access granted", Toast.LENGTH_SHORT).show()

                    if (!isLocationEnabled(getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager)) {
                        Toast.makeText(this, "Please turn ON location.", Toast.LENGTH_SHORT)
                            .show()
                        createLocationRequest()
                    }
                }

                else -> {
                    Toast.makeText(this, "No location access", Toast.LENGTH_SHORT).show()
                }

            }
            when{
                permissions.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false)
                        -> { Toast.makeText(this, "Notification access granted", Toast.LENGTH_SHORT).show() }
                else -> {
                    Toast.makeText(this, "No Notification access", Toast.LENGTH_SHORT).show()
                }
            }
            when{
                permissions.getOrDefault(Manifest.permission.SEND_SMS, false)
                    -> { Toast.makeText(this, "SMS access granted", Toast.LENGTH_SHORT).show() }
                else -> {
                    Toast.makeText(this, "No SMS access", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = File(applicationContext.filesDir,"result.txt")

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.SEND_SMS
            )
        )

        setContent {
            LocationStorageTheme() {
                Screen()
            }
        }
    }

    @Composable
    fun Screen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            var locationText by remember {
                mutableStateOf("")
            }

            var locations by remember {
                mutableStateOf("")
            }

            var routes by remember {
                mutableStateOf("")
            }

            var linesComposable by remember {
                mutableStateOf("routeName")
            }

            var emergencyContacts by remember {
                mutableStateOf("routeName")
            }

            var point by remember { mutableStateOf(Pair(0.0,0.0)) }

            Text(text = locationText)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    locationManager.getLocation {lat, lon ->
                        locationText = "Location: $lat, $lon"
                    }
                }
            ) {
                Text(text = "Get Location")
            }

            Spacer(modifier = Modifier.height(50.dp))

            Button(
                onClick = {
                    Intent(
                        applicationContext, LocationTrackerService::class.java
                    ).also {
                        it.action = LocationTrackerService.Action.START.name
                        startService(it)


                    }
                })
             {
                Text(text = "Start Tracking")
            }
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    Intent(
                        applicationContext, LocationTrackerService::class.java
                    ).also {
                        it.action = LocationTrackerService.Action.STOP.name
                        startService(it)
                    }
                }
            ) {
                Text(text = "Stop Tracking")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {

                    sendAlert()
                }
            ) {
                Text(text = "Send Alert")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    addEmergencyContact()
                }
            ) {
                Text(text = "Add Emergency Contact")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = emergencyContacts)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    clearEmergencyContacts()
                }
            ) {
                Text(text = "Clear Emergency Contact")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    emergencyContacts = printEmergencyContacts()
                }
            ) {
                Text(text = "Print contacts")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    snoozeAlerts()
                }
            ) {
                Text(text = "Snooze Alerts")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isSafe = true
                    Toast.makeText(applicationContext, "Nice to hear.", Toast.LENGTH_SHORT)
                        .show()
                }
            ) {
                Text(text = "I am Safe")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    chooseRoute()
                }
            ) {
                Text(text = "Start Monitoring")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    clearLocations()
                }
            ) {
                Text(text = "Clear Locations")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    locations = printLocations()
                }
            ) {
                Text(text = "Print Locations")
            }
            Spacer(modifier = Modifier.height(10.dp))

            Text(text = locations)

            Button(
                onClick = {
                    routes = printRoutesNew()

                }
            ) {
                Text(text = "Print Routes")
            }
            Spacer(modifier = Modifier.height(10.dp))

            Text(text = routes)

            Button(
                onClick = {
                    linesComposable = ""
                    for (i in 0..<globalLines.size) {
                        linesComposable += "( ${globalLines[i].lat1.toString()}, ${globalLines[i].lon1.toString()} ) -> (" +
                                "${globalLines[i].lat2.toString() } , ${globalLines[i].lon2.toString()}) : " +
                                "${ globalLines[i].m.toString() },${globalLines[i].c.toString()} \n"
                    }
                }
            ) {
                Text(text = "Print Locations")
            }
            Spacer(modifier = Modifier.height(10.dp))

            Text(text = linesComposable)
        }
    }



    private fun createLocationRequest() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000).build()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { }

        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                try {
                    e.startResolutionForResult(
                        this, 100
                    )
                } catch (sendEx: java.lang.Exception) {
                }
            }
        }
    }
    private fun printLocations() : String {
        applicationContext.openFileInput("main.txt").bufferedReader().use { output ->
            return output.readText()
        }
    }

    private fun printRoutesNew() : String {
        applicationContext.openFileInput("savedRoutes.txt").bufferedReader().use { output ->
            return output.readText()
        }
    }

    private fun printEmergencyContacts() : String {
        applicationContext.openFileInput("EmergencyContacts.txt").bufferedReader().use { output ->
            return output.readText()
        }
    }
    private fun clearEmergencyContacts(){
        applicationContext.openFileOutput("EmergencyContacts.txt",Context.MODE_PRIVATE).use{
            it.write("".toByteArray())
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

    private fun sendAlert(){
        val smsManager: SmsManager = this.getSystemService(SmsManager::class.java)
        val contacts = readEmergencyContacts()

        locationManager.getLocation {lat, lon ->
                for (contact in contacts) {
                    val message = arrayListOf( "ATTENTION ${contact.name}, I may be in danger...\n Please reach out to me\n",
                     "I am here -> https://www.google.com/maps/search/?api=1&query=$lat,$lon")

                    smsManager.sendMultipartTextMessage(contact.number,null,message,null,null)

                    Toast.makeText(this, "Messages sent", Toast.LENGTH_SHORT).show()
                }
        }






    }

    private fun clearLocations(){

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder
            .setMessage("Data will be permanently lost")
            .setTitle("Are you sure?")
            .setPositiveButton("Yes") { dialog, which ->
                applicationContext.openFileOutput("main.txt",Context.MODE_PRIVATE).use{
                    it.write("".toByteArray())
                }
                applicationContext.openFileOutput("savedRoutes.txt",Context.MODE_PRIVATE).use{
                    it.write("".toByteArray())
                }
            }
            .setNegativeButton("No") { dialog, which ->
            }

        val dialog: AlertDialog = builder.create()
        dialog.show()

        }

    private fun populateRoute(): String{
//        var tempRoute : Route
        lateinit var location: RoutePoint
        lateinit var lastLocation: RoutePoint
        var newRoute = false
        var isNotMoving = true
        applicationContext.openFileInput("result.txt").bufferedReader().forEachLine {
            val point = it.split(",")
//                location.longitude = point[0].toDouble()
//                location.latitude = point[1].toDouble()
//                location.time = point[2].toLong()
            val lat = round(point[0].toDouble() * 1000) / 1000
            val lon = round(point[1].toDouble() * 1000) / 1000
            location = RoutePoint(lat,lon,point[2].toLong())
            if (routes.last().isEmpty()) {
                newRoute=false
                routes.last().add(location)
            }
            else if(isNotMoving && (lastLocation.latitude == location.latitude ||
                lastLocation.longitude == location.longitude))
            {
                routes.last()[0]=location
            }
            else if (routes.last().last().latitude != location.latitude ||
                routes.last().last().longitude != location.longitude
            ) {
                routes.last().add(location)
                isNotMoving = false
            }
            else {
                lastLocation = location
                isNotMoving = true
                newRoute = true
            }
                if ( newRoute)
                    routes.add(mutableListOf())

        }
            var out = ""
            for (y in routes) {
                out += "Route ${routes.indexOf(y)}\n"
                for (x in y) {
                    out += "[" + x.latitude.toString() + "~" + x.longitude.toString() + "~" + x.timeInUNIX.toString() + "]\n"
                }
            }
            return out
        }
    private fun printRoute() : String {
        var out = ""
        for (x in globalRoute) {
                out += "[" + x.latitude.toString() + "~" + x.longitude.toString() + "~" + x.timeInUNIX.toString() + "]\n"
            }

        return out
    }

//    private fun CompareRoutes()
//    {
//        var tempRoute  = mutableListOf<routePoint>()
//        var lastRoutePoint: routePoint = Triple(0.0,0.0,0)
//        applicationContext.openFileInput("newResult.txt").bufferedReader().forEachLine {
//            val point = it.split(",")}
//
//    }

    private fun addEmergencyContact(){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val nameInput = EditText(this)
        val phoneNumberInput = EditText(this)
        var userConfirmation = false

        nameInput.inputType = InputType.TYPE_CLASS_TEXT
        nameInput.hint = "Name"
        phoneNumberInput.inputType = InputType.TYPE_CLASS_PHONE
        phoneNumberInput.hint = "Phone Number"
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(nameInput)
        layout.addView(phoneNumberInput)
        builder
            .setTitle("Add Emergency Contact")
            .setView(layout)
            .setPositiveButton("Confirm") { _,_ ->


                applicationContext.openFileOutput("EmergencyContacts.txt",Context.MODE_APPEND).use{
                    it.write("${nameInput.text},${phoneNumberInput.text}\n".toByteArray())
                }

            }
            .setNegativeButton("Cancel") { dialog, which ->

            }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun snoozeAlerts(){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)


        val dayPicker = NumberPicker(this)
        val hourPicker = NumberPicker(this)
        val minutePicker = NumberPicker(this)

        val days = snoozeTime/4320
        var remainder = snoozeTime%4320
        dayPicker.maxValue = 30
        dayPicker.minValue = 0
        dayPicker.wrapSelectorWheel = true
        dayPicker.value = days.toInt()
        dayPicker.setPadding(5,10,20,10)

        val hours = remainder/180
        remainder %= 180
        hourPicker.maxValue = 24
        hourPicker.minValue = 0
        hourPicker.wrapSelectorWheel = true
        hourPicker.value = hours.toInt()
        hourPicker.setPadding(5,10,20,10)

        val minutes= remainder/3
        minutePicker.maxValue = 60
        minutePicker.minValue = 0
        minutePicker.wrapSelectorWheel = true
        minutePicker.value = minutes.toInt()
        minutePicker.setPadding(5,10,20,10)
        val dayText = TextView(this)
        dayText.text = "Days: "
       // dayText.gravity=Gravity.CENTER_VERTICAL
        val hoursText = TextView(this)
        hoursText.text = "Hours: "
     //   hoursText.setPadding(20,20,10,20)
        //hoursText.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        //hoursText.gravity=Gravity.CENTER_VERTICAL
        val minuteText = TextView(this)
        minuteText.text = "Minutes: "
        //minuteText.setPadding(20,20,10,20)
        //minuteText.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        //minuteText.gravity=Gravity.CENTER_VERTICAL
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.addView(dayText)
        layout.addView(dayPicker)
        layout.addView(hoursText)
        layout.addView(hourPicker)
        layout.addView(minuteText)
        layout.addView(minutePicker)
     //   layout.textAlignment= View.TEXT_ALIGNMENT_CENTER
        layout.gravity = Gravity.CENTER
        builder
            .setTitle("Specify Snooze Time")
            .setView(layout)
            .setPositiveButton("Confirm") { _,_ ->

                snoozeTime = ((dayPicker.value*4320)+(hourPicker.value*180)+(minutePicker.value*3)).toLong()

            }
            .setNegativeButton("Cancel") { dialog, which ->

            }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
    private fun nameRoute() {

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        val input = EditText(this)
        var userConfirmation = false

        input.inputType = InputType.TYPE_CLASS_TEXT
        builder
            .setTitle("What is this Route?")
            .setView(input)
            .setPositiveButton("Confirm") { _,_ ->


                applicationContext.openFileOutput("routeNames.txt",Context.MODE_APPEND).use{
                    it.write("${input.text}\n".toByteArray())
                }
                    Intent(
                        applicationContext, LocationTrackerService::class.java
                    ).also {
                        it.action = LocationTrackerService.Action.START.name
                        startService(it)

                    }
                applicationContext.openFileOutput("routes.txt",Context.MODE_APPEND).use{
                    it.write("${input.text}\n".toByteArray())
                }
            }
            .setNegativeButton("Cancel") { dialog, which ->

            }

        val dialog: AlertDialog = builder.create()
        dialog.show()


    }
    private fun chooseRoute(){
        val routeNamesRead = mutableListOf<String>()
        applicationContext.openFileInput("routeNames.txt").bufferedReader().forEachLine {
            routeNamesRead.add(it)}
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder
            .setTitle("What is this Route?")
            .setPositiveButton("Confirm") { _,_ ->

            }
            .setNegativeButton("Cancel") { dialog, which ->

            }
            .setItems(routeNamesRead.toTypedArray()){ dialog,which->

                globalRouteName = routeNamesRead[which]
                val intent = Intent(
                    applicationContext, LocationTrackerService::class.java
                )
                    //intent.putExtra("File Name", routeName[which])
                    intent.action = LocationTrackerService.Action.MONITOR.name
                    startService(intent)


            }



        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
    }


