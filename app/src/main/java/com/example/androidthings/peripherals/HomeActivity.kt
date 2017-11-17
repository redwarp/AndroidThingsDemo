package com.example.androidthings.peripherals

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManagerService
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.IOException

class HomeActivity : AppCompatActivity() {


    companion object {
        private val CS_PIN = "GPIO_37"
        private val CLOCK_PIN = "GPIO_32"
        private val MOSI_PIN = "GPIO_34"
        private val MISO_PIN = "GPIO_33"

        private val TAG = "HomeActivity"
        private val LED_PIN = "GPIO_10"
        private val PUMP_PIN = "GPIO_39"
        private val DISARMED_PIN = "GPIO_173"
    }

    // GPIO connection to button input
    private var led: Gpio? = null
    private var disarmLed: Gpio? = null
    private var pump: Gpio? = null
    private val mcp3008 = MCP3008(CS_PIN, CLOCK_PIN, MOSI_PIN, MISO_PIN)

    private var isRunning = true
    private var isPumping = false
    private var mCounter = 0

    private var mEventHandler: Handler = Handler()
    private var mPumpHandler: Handler = Handler()

    private var mDatabase: FirebaseDatabase? = null
    private var mStatusRef: DatabaseReference? = null
    private var mValueRef: DatabaseReference? = null
    private var gardening: Gardening? = null

    private var waterLevel: Float = 0.0f
    private var isDisarmed: Boolean = false
    private var shouldManualPump: Boolean = false

    private lateinit var mWaterView: WaterLevelView
    private lateinit var mPlantView: WaterLevelView
    private lateinit var mCloudView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        mWaterView = findViewById(R.id.water_level)
        mPlantView = findViewById(R.id.plant)
        mCloudView = findViewById(R.id.cloud)

        // Firebase Setup
        mDatabase = FirebaseDatabase.getInstance()
        mStatusRef = mDatabase?.getReference("status")
        mValueRef = mDatabase?.getReference("value")
        val gardeningRef = mDatabase?.getReference("gardening")
        if (gardeningRef != null) {
            gardening = Gardening(gardeningRef)
        }

        // Peripheral Setup
        val service = PeripheralManagerService()
        Log.d(TAG, "Available GPIO: " + service.gpioList)
        Log.d(TAG, "SPI List: " + service.spiBusList)
        try {
            mcp3008.register()
        } catch (e: IOException) {
            Log.w(TAG, "Error initializing mcp3008", e)
        }
        led = service.openGpio(LED_PIN)
        led!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        disarmLed = service.openGpio(DISARMED_PIN)
        disarmLed!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        pump = service.openGpio(PUMP_PIN)
        pump!!.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)

        // Start the main event Loop
        mEventHandler.post(mEventLoopRunnable)

        findViewById<ImageButton>(R.id.start).setOnTouchListener(WaterTouchListener())

        findViewById<ImageButton>(R.id.stop).setOnClickListener {
            toggle()
        }
    }

    private var mEventLoopRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) {
                return
            }

            mStatusRef?.setValue("reading")

            val readAdc0 = mcp3008.readAdc(0)
            waterLevel = convertAdcValue(readAdc0)

            led?.value = shouldPump()
            mWaterView.value = 1.0f - waterLevel
            mPlantView.value = 1.0f - waterLevel

            Log.d(TAG, "Pump value = ${led!!.value}, Adc values: channel 0 = $readAdc0, to float = $waterLevel")

            mEventHandler.postDelayed(this, 60)

            // Filtering values sent to Firebase to one every second.
            mCounter += 60
            if (mCounter >= 1000) {
                mCounter = 0
                mValueRef?.setValue(waterLevel)
            }


            if (shouldPump()) {
                water()
            }
        }
    }

    private var mStopPumpRunnable = Runnable {
        pump!!.value = false
        isPumping = false
        Log.d(TAG, "Stopping to pump")

        gardening?.stop()
        updateCloudState()
    }

    override fun onDestroy() {
        super.onDestroy()

        mcp3008.unregister()
        led?.close()
        disarmLed?.close()
        pump?.close()

        isRunning = false
    }

    private fun convertAdcValue(value: Int): Float {
        val caped = minOf(maxOf(value - 400, 0), 623)
        return caped.toFloat() / 623f * 1.0f
    }

    fun shouldPump(): Boolean {
        if (shouldManualPump) {
            return true
        }

        if (isDisarmed) {
            return false
        }

        return waterLevel > 0.5f
    }


    fun disarm() {
        isDisarmed = true
        disarmLed?.value = true
        updateCloudState()
    }

    fun arm() {
        isDisarmed = false
        disarmLed?.value = false
        updateCloudState()
    }

    private fun updateCloudState() {
        if (isPumping) {
            mCloudView.setImageResource(R.drawable.ic_cloud_with_rain)
        } else if (isDisarmed) {
            mCloudView.setImageResource(R.drawable.ic_cloud_off)
        } else {
            mCloudView.setImageResource(R.drawable.ic_cloud_no_rain)
        }
    }


    fun toggle() {
        if (isDisarmed) {
            arm()
        } else {
            disarm()
        }
    }

    fun water() {
        if (isPumping) {
            return
        }
        gardening?.start()

        mStatusRef?.setValue("pumping")
        Log.d(TAG, "Starting to pump")

        isPumping = true
        pump!!.value = true
        updateCloudState()
        mPumpHandler.postDelayed(mStopPumpRunnable, 1000)
    }

    inner class WaterTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event != null) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    shouldManualPump = true
                    return true
                } else if (event.action == MotionEvent.ACTION_UP) {
                    shouldManualPump = false
                    return true
                }
            }
            return false
        }
    }
}
