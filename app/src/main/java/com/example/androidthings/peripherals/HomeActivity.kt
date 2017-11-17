/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.peripherals

import android.animation.ArgbEvaluator
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManagerService
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : Activity() {


    companion object {
        private val CS_PIN = "GPIO_37"
        private val CLOCK_PIN = "GPIO_32"
        private val MOSI_PIN = "GPIO_34"
        private val MISO_PIN = "GPIO_33"

        private val TAG = "HomeActivity"
        private val PUMP_PIN = "GPIO_10"
        private val PUMPTEMP_PIN = "GPIO_39"
    }

    // GPIO connection to button input
    private var mSensorGpio: Gpio? = null
    private var mPumpGpio: Gpio? = null
    private var mPumpTempGpio: Gpio? = null
    private var running = true


    private var mcpHandler: Handler? = null
    private var mcpRunnable: (() -> Unit)? = null

    private var mcpPumpHandler: Handler? = null
    private var mcpPumpRunnable: (() -> Unit)? = null

    private val mcp3008 = MCP3008(CS_PIN, CLOCK_PIN, MOSI_PIN, MISO_PIN)

    private var mPumping: Boolean = false
    private var mDatabase: FirebaseDatabase? = null
    private var mStatusRef: DatabaseReference? = null
    private var mValueRef: DatabaseReference? = null
    private var mGardeningRef: DatabaseReference? = null
    private var mCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Firebase Setup
        mDatabase = FirebaseDatabase.getInstance()
        mStatusRef = mDatabase?.getReference("status")
        mValueRef = mDatabase?.getReference("value")
        mGardeningRef = mDatabase?.getReference("gardening")

        val service = PeripheralManagerService()
        Log.d(TAG, "Available GPIO: " + service.gpioList)
        Log.d(TAG, "SPI List: " + service.spiBusList)

        try {
            mcp3008.register()
            mPumpGpio = service.openGpio(PUMP_PIN)
            mPumpGpio!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

            mPumpTempGpio = service.openGpio(PUMPTEMP_PIN)
            mPumpTempGpio!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

            val interpolator = ArgbEvaluator()

            mcpHandler = Handler()
            mcpPumpHandler = Handler()

            mcpRunnable = mcpRunnable@ {
                if (!running) {
                    return@mcpRunnable
                }
                mStatusRef?.setValue("reading")

                val readAdc0 = mcp3008.readAdc(0)

                val convertedValue = convertAdcValue(readAdc0)
                val log = "Adc values: channel 0 = $readAdc0, to float = $convertedValue"
                Log.d(TAG, log)
                findViewById<TextView>(R.id.spi_reading)!!.text = log

                mPumpGpio!!.value = convertedValue > 0.5f
                Log.d(TAG, "Pump value = ${mPumpGpio!!.value}")
                findViewById<WaterLevelView>(R.id.water_level).value = 1.0f - convertedValue
                findViewById<View>(R.id.main_view).setBackgroundColor(
                        interpolator.evaluate(convertedValue, Color.GREEN, Color.RED) as Int)

                if (running) {
                    mcpHandler?.postDelayed(mcpRunnable, 60)
                    mCounter += 60
                    if (mCounter >= 1000) {
                        mCounter = 0
                        mValueRef?.setValue(convertedValue)
                    }
                }

                if (convertedValue > 0.5f && !mPumping) {
                    mStatusRef?.setValue("pumping")
                    Log.d(TAG, "Staring to pump")
                    mPumping = true
                    mPumpTempGpio!!.value = true
                    mcpPumpRunnable = mcpRunnable@ {
                        mPumpTempGpio!!.value = false
                        mPumping = false
                        Log.d(TAG, "Stopping to pump")

                        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
                        val date = dateFormat.format(Date())
                        mGardeningRef?.child(System.currentTimeMillis().toString())?.setValue(date)

                    }
                    mcpPumpHandler?.postDelayed(mcpPumpRunnable, 1000)
                }
            }
            mcpHandler?.post(mcpRunnable)

        } catch (e: IOException) {
            Log.w(TAG, "Error initializing mcp3008", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Close the button
        if (mSensorGpio != null) {
            try {
                mSensorGpio!!.close()
            } catch (e: IOException) {
                Log.w(TAG, "Error closing GPIO", e)
            }
        }

        running = false
        mcp3008.unregister()
    }

    fun convertAdcValue(value: Int): Float {
        val caped = minOf(maxOf(value - 400, 0), 623)
        return caped.toFloat() / 623f * 1.0f
    }
}
