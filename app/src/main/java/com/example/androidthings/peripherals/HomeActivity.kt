package com.example.androidthings.peripherals

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManagerService
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : AppCompatActivity() {


    companion object {
        private val CS_PIN = "GPIO_37"
        private val CLOCK_PIN = "GPIO_32"
        private val MOSI_PIN = "GPIO_34"
        private val MISO_PIN = "GPIO_33"

        private val TAG = "HomeActivity"
        private val LED_PIN = "GPIO_10"
        private val PUMP_PIN = "GPIO_39"
    }

    // GPIO connection to button input
    private var mLed: Gpio? = null
    private var mPump: Gpio? = null
    private val mcp3008 = MCP3008(CS_PIN, CLOCK_PIN, MOSI_PIN, MISO_PIN)

    private var mIsRunning = true
    private var mIsPumping = false
    private var mCounter = 0

    private var mEventHandler: Handler = Handler()
    private var mPumpHandler: Handler = Handler()

    private var mDatabase: FirebaseDatabase? = null
    private var mStatusRef: DatabaseReference? = null
    private var mValueRef: DatabaseReference? = null
    private var mGardeningRef: DatabaseReference? = null

    private var mMainView: View? = null
    private var mWaterView: WaterLevelView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        mMainView = findViewById(R.id.main_view)
        mWaterView = findViewById(R.id.water_level)

        // Firebase Setup
        mDatabase = FirebaseDatabase.getInstance()
        mStatusRef = mDatabase?.getReference("status")
        mValueRef = mDatabase?.getReference("value")
        mGardeningRef = mDatabase?.getReference("gardening")

        // Peripheral Setup
        val service = PeripheralManagerService()
        Log.d(TAG, "Available GPIO: " + service.gpioList)
        Log.d(TAG, "SPI List: " + service.spiBusList)
        try {
            mcp3008.register()
        } catch (e: IOException) {
            Log.w(TAG, "Error initializing mcp3008", e)
        }
        mLed = service.openGpio(LED_PIN)
        mLed!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        mPump = service.openGpio(PUMP_PIN)
        mPump!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        // Start the main event Loop
        mEventHandler.post(mEventLoopRunnable)
    }

    private var mEventLoopRunnable = object : Runnable {
        override fun run() {
            if (!mIsRunning) {
                return
            }

            mStatusRef?.setValue("reading")

            val readAdc0 = mcp3008.readAdc(0)
            val convertedValue = convertAdcValue(readAdc0)

            mLed?.value = convertedValue > 0.5f
            mWaterView?.value = 1.0f - convertedValue

            Log.d(TAG, "Pump value = ${mLed!!.value}, Adc values: channel 0 = $readAdc0, to float = $convertedValue")


            if (mIsRunning) {
                mEventHandler.postDelayed(this, 60)

                // Filtering values sent to Firebase to one every second.
                mCounter += 60
                if (mCounter >= 1000) {
                    mCounter = 0
                    mValueRef?.setValue(convertedValue)
                }
            }

            if (convertedValue > 0.5f && !mIsPumping) {
                mStatusRef?.setValue("pumping")
                Log.d(TAG, "Staring to pump")

                mIsPumping = true
                mPump!!.value = true
                mPumpHandler.postDelayed(mStopPumpRunnable, 1000)
            }
        }
    }

    private var mStopPumpRunnable = Runnable {
        mPump!!.value = false
        mIsPumping = false
        Log.d(TAG, "Stopping to pump")

        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val date = dateFormat.format(Date())
        mGardeningRef?.child(System.currentTimeMillis().toString())?.setValue(date)
    }

    override fun onDestroy() {
        super.onDestroy()

        mcp3008.unregister()
        mLed?.close()
        mPump?.close()

        mIsRunning = false
    }

    private fun convertAdcValue(value: Int): Float {
        val caped = minOf(maxOf(value - 400, 0), 623)
        return caped.toFloat() / 623f * 1.0f
    }
}
