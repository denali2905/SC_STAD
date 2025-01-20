package com.example.locationstorage

import android.location.Location
import android.content.Context

class Route {
    lateinit var coordinates: MutableList<Triple<Double,Double,Int>>

    fun toPrint() : String{
        var out = ""
        for (x in coordinates){
            out += "[" + x.first.toString() + "~" + x.first.toString() + "~" + x.first.toString() + "]"
        }
          return out
    }

}