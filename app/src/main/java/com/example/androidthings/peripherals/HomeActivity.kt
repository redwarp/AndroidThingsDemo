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
import com.google.android.things.pio.GpioCallback
import com.google.android.things.pio.PeripheralManagerService
import java.io.IOException

class HomeActivity : Activity() {


    companion object {
        private val CS_PIN = "GPIO_37"
        private val CLOCK_PIN = "GPIO_32"
        private val MOSI_PIN = "GPIO_34"
        private val MISO_PIN = "GPIO_33"

        private val TAG = "HomeActivity"
        private val PUMP_PIN = "GPIO_10"
    }

    // GPIO connection to button input
    private var mSensorGpio: Gpio? = null
    private var mPumpGpio: Gpio? = null
    private var running = true


    private var mcpHandler: Handler? = null
    private var mcpRunnable: (() -> Unit)? = null

    private val mcp3008 = MCP3008(CS_PIN, CLOCK_PIN, MOSI_PIN, MISO_PIN)

    private val mCallback = object : GpioCallback() {
        override fun onGpioEdge(gpio: Gpio?): Boolean {
            try {
                val value = gpio!!.value
                Log.i(TAG, "GPIO changed, sensor " + value)
                if (value) {
                    findViewById<View>(R.id.main_view).setBackgroundColor(Color.GREEN)
                } else {
                    findViewById<View>(R.id.main_view).setBackgroundColor(Color.RED)
                }
            } catch (e: IOException) {
                Log.w(TAG, "Error reading GPIO")
            }

            // Return true to keep callback active.
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val service = PeripheralManagerService()
        Log.d(TAG, "Available GPIO: " + service.gpioList)
        Log.d(TAG, "SPI List: " + service.spiBusList)


//        try {
//            mSpiDevice = service.openSpiDevice(SENSOR_SPI_NAME)
//            mSpi1Device = service.openSpiDevice(SENSOR_SPI1_NAME)
//            if (mSpiDevice != null) {
//                mSpiDevice!!.setBitsPerWord(8)
//                mSpiDevice!!.setBitJustification(false)
//                mSpiDevice!!.setMode(SpiDevice.MODE0)
//                mSpiDevice!!.setFrequency(1200000)
//
//                mHandler = Handler()
//                mRunnable = Runnable {
//                    val response = ByteArray(32)
//                    try {
//                        mSpiDevice!!.read(response, response.size)
//                        val hexString = Arrays.toString(response)
//                        Log.d(TAG, "SPI value: " + hexString)
//                        val view = findViewById(R.id.spi_reading) as TextView
//                        view.text = hexString
//                    } catch (e: IOException) {
//                        Log.w(TAG, "Error reading SPI", e)
//                    }
//
//
//                    mHandler!!.postDelayed(mRunnable, 1000)
//                }
//                mHandler!!.post(mRunnable)
//            }
//
//            if (mSpi1Device != null) {
//                mSpi1Device!!.setBitsPerWord(8)
//                mSpi1Device!!.setBitJustification(false)
//                mSpi1Device!!.setMode(SpiDevice.MODE1)
//                mSpi1Device!!.setFrequency(1200000)
//
//                mHandler1 = Handler()
//                mRunnable1 = Runnable {
//                    val response = ByteArray(64)
//                    try {
//                        mSpi1Device!!.read(response, response.size)
//                        val hexString = Arrays.toString(response)
//
//                        Log.d(TAG, "SPI1 value: " + hexString)
//                        val view = findViewById(R.id.spi_reading) as TextView
//                        view.text = hexString
//                    } catch (e: IOException) {
//                        Log.w(TAG, "Error reading SPI1", e)
//                    }
//
//
//                    mHandler1!!.postDelayed(mRunnable1, 1000)
//                }
//                mHandler1!!.post(mRunnable1)
//            }
//        } catch (e: IOException) {
//            Log.w(TAG, "Error opening SPI", e)
//        }
//
//        try {
//
//            // Create GPIO connection.
//            mSensorGpio = service.openGpio(SENSOR_PIN_NAME)
//
//            // Configure as an input, trigger events on every change.
//            mSensorGpio!!.setDirection(Gpio.DIRECTION_IN)
//            mSensorGpio!!.setEdgeTriggerType(Gpio.EDGE_BOTH)
//            // Value is true when the pin is LOW
//            mSensorGpio!!.setActiveType(Gpio.ACTIVE_LOW)
//            // Register the event callback.
//            mSensorGpio!!.registerGpioCallback(mCallback)
//        } catch (e: IOException) {
//            Log.w(TAG, "Error opening GPIO", e)
//        }

        try {
            mcp3008.register()
            mPumpGpio = service.openGpio(PUMP_PIN)
            mPumpGpio!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

            val interpolator = ArgbEvaluator()

            mcpHandler = Handler()
            mcpRunnable = mcpRunnable@ {
                if (!running) {
                    return@mcpRunnable
                }

                val readAdc0 = mcp3008.readAdc(0)

                val convertedValue = convertAdcValue(readAdc0)
                val log = "Adc values: channel 0 = $readAdc0, to float = $convertedValue"
                Log.d(TAG, log)
                findViewById<TextView>(R.id.spi_reading)!!.text = log

                mPumpGpio!!.value = convertedValue > 0.5f
                Log.d(TAG, "Pump value = ${mPumpGpio!!.value}")
                findViewById<WaterLevelView>(R.id.water_level).value = 1.0f - convertedValue

                findViewById<View>(R.id.main_view).setBackgroundColor(interpolator.evaluate(convertedValue, Color.GREEN, Color.RED) as Int)

                if (running) {
                    mcpHandler?.postDelayed(mcpRunnable, 60)
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
            mSensorGpio!!.unregisterGpioCallback(mCallback)
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
