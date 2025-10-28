package com.example.solarkit.HomeScreen.dubugging

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlin.math.*

data class CelestialPosition(val azimuth: Double, val altitude: Double)

class SunMoonDebugHelper(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun startDebug() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "⚠ Location permission missing!", Toast.LENGTH_SHORT).show()
            return
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000L, // update every 2 sec
            0f,
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val now = System.currentTimeMillis()

                    val sunPos = getSunPosition(lat, lon, now)
                    val moonPos = getMoonPosition(lat, lon, now)

                    val msg = "☀ Sun → Az: %.1f°, Alt: %.1f°\n🌙 Moon → Az: %.1f°, Alt: %.1f°".format(
                        sunPos.azimuth, sunPos.altitude,
                        moonPos.azimuth, moonPos.altitude
                    )

                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    Log.d("SunMoonDebug", msg)
                }
            })
    }

    // ---- Simple calculation (approximate formulas) ----
    private fun getSunPosition(lat: Double, lon: Double, timeMillis: Long): CelestialPosition {
        val d = (timeMillis / 86400000.0) + 2440587.5 // Julian days
        val n = d - 2451545.0
        val L = (280.460 + 0.9856474 * n) % 360
        val g = Math.toRadians((357.528 + 0.9856003 * n) % 360)
        val lambda = Math.toRadians((L + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360)
        val epsilon = Math.toRadians(23.439 - 0.0000004 * n)

        val alpha = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val delta = asin(sin(epsilon) * sin(lambda))

        return getHorizontalPosition(lat, lon, n, alpha, delta)
    }

    private fun getMoonPosition(lat: Double, lon: Double, timeMillis: Long): CelestialPosition {
        val d = (timeMillis / 86400000.0) + 2440587.5 // Julian days
        val n = d - 2451545.0
        val L = Math.toRadians((218.316 + 13.176396 * n) % 360)
        val M = Math.toRadians((134.963 + 13.064993 * n) % 360)
        val F = Math.toRadians((93.272 + 13.229350 * n) % 360)

        val lambda = L + Math.toRadians(6.289) * sin(M)
        val beta = Math.toRadians(5.128) * sin(F)

        val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
        val alpha = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val delta = asin(sin(epsilon) * sin(lambda) * cos(beta) + sin(beta) * cos(epsilon))

        return getHorizontalPosition(lat, lon, n, alpha, delta)
    }

    private fun getHorizontalPosition(
        lat: Double, lon: Double, n: Double, alpha: Double, delta: Double
    ): CelestialPosition {
        val lst = (280.16 + 360.9856235 * n + lon) % 360
        val H = Math.toRadians((lst - Math.toDegrees(alpha) + 360) % 360)

        val phi = Math.toRadians(lat)
        val altitude = Math.toDegrees(asin(sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(H)))
        val azimuth = (Math.toDegrees(atan2(-sin(H), tan(delta) * cos(phi) - sin(phi) * cos(H))) + 360) % 360

        return CelestialPosition(azimuth, altitude)
    }
}