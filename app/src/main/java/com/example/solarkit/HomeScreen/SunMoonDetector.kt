package com.example.solarkit.HomeScreen

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.solarkit.HomeScreen.SunMoonCalc.SunMoonCalculator
import kotlin.math.abs

class SunMoonDetector(
    private val context: Context,
    private val onDetection: (String?) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private var hasGravity = false
    private var hasGeomagnetic = false

    private var latitude: Double? = null
    private var longitude: Double? = null

    // Alag flags for Sun and Moon
    private var sunAlreadyDetected = false
    private var moonAlreadyDetected = false

    fun setLocation(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
        Log.d("SunMoonDebug", "📍 Location set: lat=$lat, lon=$lon")
    }

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.d("SunMoonDebug", "▶️ Sensor listening started")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d("SunMoonDebug", "⏹ Sensor listening stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, gravity.size)
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.size)
                hasGeomagnetic = true
            }
        }

        if (hasGravity && hasGeomagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                SensorManager.getOrientation(rotationMatrix, orientation)

                val sensorAz = (Math.toDegrees(orientation[0].toDouble()) + 360) % 360
                val sensorPitch = Math.toDegrees(orientation[1].toDouble()) // Pitch (-90 to 90)

                Log.d("SunMoonDebug", "📐 Sensor → azimuth=$sensorAz pitch=$sensorPitch")

                checkDetection(sensorAz, sensorPitch)
            }
        }
    }

    private fun checkDetection(sensorAz: Double, sensorPitch: Double) {
        val lat = latitude ?: return
        val lon = longitude ?: return

        val sunPos = SunMoonCalculator.getSunPosition(lat, lon)
        val moonPos = SunMoonCalculator.getMoonPosition(lat, lon)

        // Get magnetic declination for your location
        val magneticDeclination = getMagneticDeclination(lat, lon)

        // Adjust sensor azimuth from Magnetic North to True North
        val adjustedSensorAz = (sensorAz + magneticDeclination + 360) % 360

        // ✅ CORRECTED ELEVATION CALCULATION
        val deviceElevation = calculateDeviceElevation(sensorPitch)

        Log.d(
            "SunMoonDebug",
            "📐 Sensor → RawAz=$sensorAz AdjAz=$adjustedSensorAz Pitch=$sensorPitch° → Elevation=$deviceElevation°"
        )
        Log.d(
            "SunMoonDebug",
            "☀ Sun → az=${sunPos.azimuth} alt=${sunPos.altitude} | 🌙 Moon → az=${moonPos.azimuth} alt=${moonPos.altitude}"
        )

        // Detect Sun with corrected values
        detectObject(
            "☀ Sun",
            adjustedSensorAz,
            deviceElevation,
            sunPos.azimuth,
            sunPos.altitude,
            azTolerance = 10.0,
            altTolerance = 5.0,
            isSun = true
        )

        // Detect Moon with corrected values
        detectObject(
            "🌙 Moon",
            adjustedSensorAz,
            deviceElevation,
            moonPos.azimuth,
            moonPos.altitude,
            azTolerance = 10.0,
            altTolerance = 5.0,
            isSun = false
        )
    }

    // ✅ FINAL CORRECTED ELEVATION CALCULATION
    private fun calculateDeviceElevation(pitchDegrees: Double): Double {
        // Aap ke phone ka sensor coordinate system bilkul ulta hai:
        // - Camera sky ki taraf → Negative pitch values
        // - Camera ground ki taraf → Less negative values

        // ✅ CORRECT FORMULA: Add 90 degrees to fix the coordinate system
        val elevation = abs(pitchDegrees)

        Log.d("CameraDebug", "📷 Camera: RawPitch=$pitchDegrees° → CorrectedElevation=$elevation°")
        return elevation.coerceIn(0.0, 90.0)
    }

    private fun detectObject(
        label: String,
        sensorAz: Double,
        sensorAlt: Double,
        objAz: Double,
        objAlt: Double,
        azTolerance: Double,
        altTolerance: Double,
        isSun: Boolean
    ) {
        val azDiff = azimuthDiff(sensorAz, objAz)
        val altDiff = abs(sensorAlt - objAlt)

        // ✅ DETAILED DEBUG LOGS ADDED HERE
        Log.d("checking", "=======================================")
        Log.d("checking", "🔍 $label Detailed Analysis:")
        Log.d("checking", "   Sensor: Azimuth=$sensorAz°, Elevation=$sensorAlt°")
        Log.d("checking", "   Object: Azimuth=$objAz°, Altitude=$objAlt°")
        Log.d("checking", "   Differences: AzDiff=$azDiff° (max: $azTolerance°)")
        Log.d("checking", "                AltDiff=$altDiff° (max: $altTolerance°)")
        Log.d("checking", "   Conditions: Azimuth OK = ${azDiff <= azTolerance}")
        Log.d("checking", "               Altitude OK = ${altDiff <= altTolerance}")
        Log.d("checking", "   FINAL RESULT = ${azDiff <= azTolerance && altDiff <= altTolerance}")

        // ✅ ADDED: Check which condition is failing
        if (azDiff > azTolerance) {
            Log.d("checking", "   ❌ FAILED: Azimuth difference too large")
        }
        if (altDiff > altTolerance) {
            Log.d("checking", "   ❌ FAILED: Altitude difference too large")
        }
        Log.d("checking", "=======================================")

        if (azDiff <= azTolerance && altDiff <= altTolerance) {
            if (isSun && !sunAlreadyDetected) {
                Log.d(
                    "Deubbing",
                    "✅ $label detected! → sensorAz=$sensorAz sensorAlt=$sensorAlt vs az=$objAz alt=$objAlt"
                )
                onDetection("☀ $label detected!")
                sunAlreadyDetected = true
            } else if (!isSun && !moonAlreadyDetected) {
                Log.d(
                    "Deubbing",
                    "✅ $label detected! → sensorAz=$sensorAz sensorAlt=$sensorAlt vs az=$objAz alt=$objAlt"
                )
                onDetection("🌙 $label detected!")
                moonAlreadyDetected = true
            }
        } else {
            if (isSun) {
                sunAlreadyDetected = false
            } else {
                moonAlreadyDetected = false
            }
        }
    }

    private fun azimuthDiff(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }

    // Approximate magnetic declination calculation
    private fun getMagneticDeclination(lat: Double, lon: Double): Double {
        return 4.0 // degrees east for Pakistan
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}