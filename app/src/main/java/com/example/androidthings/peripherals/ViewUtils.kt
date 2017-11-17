package com.example.androidthings.peripherals

import android.content.res.Resources

val Float.dpToPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
val Int.dpToPx: Int get() = ((this * Resources.getSystem().displayMetrics.density).toInt())
