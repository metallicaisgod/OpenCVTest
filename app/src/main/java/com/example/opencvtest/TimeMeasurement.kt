package com.example.opencvtest

import android.util.Log

object TimeMeasurement {

    private const val TAG = "TimeMeasurement"
    private var startTime = 0L
    private var stopTime = 0L

    fun startTimeMeasurement() {
        startTime = System.currentTimeMillis()
    }

    fun stopTimeMeasurement(message: String = "") {
        if(startTime != 0L) {
            stopTime = System.currentTimeMillis()
            Log.d(TAG, "$message interval: ${stopTime - startTime}")
        }
    }




}