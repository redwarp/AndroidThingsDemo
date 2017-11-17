package com.example.androidthings.peripherals

import android.os.Handler
import com.google.firebase.database.DatabaseReference
import java.text.SimpleDateFormat
import java.util.*

class Gardening(val database: DatabaseReference) {
    private var lastStop: Long = 0L
    private var start: Long = 0L
    private val handler: Handler = Handler()

    fun start() {
        if (start == 0L) {
            start = System.currentTimeMillis()
        } else {
            handler.removeCallbacks(stopAction)
        }
    }

    fun stop() {
        lastStop = System.currentTimeMillis()
        handler.postDelayed(stopAction, 200)
    }

    private val stopAction = Runnable {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val date = dateFormat.format(Date())
        val duration = (lastStop - start)
        database.child(lastStop.toString())?.setValue("$date (Water ran for ${duration}ms)")
        start = 0L
    }
}