package com.example.solarkit.HomeScreen.SunMoonCalc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

// ✅ Alias for readability
typealias CelestialPosition = SunMoonCoordinates

// Data class for coordinates
data class SunMoonCoordinates(
    val altitude: Double,  // Angle above horizon
    val azimuth: Double    // Compass direction (0° = North, 90° = East)
)



object SunMoonCalculator {

    // 🌍 Get current device location
    fun getCurrentLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    // 📌 Convert RA/Dec → Alt/Az (robust version)
    private fun raDecToHorizontal(
        ra: Double,
        dec: Double,
        lat: Double,
        lon: Double,
        jd: Double
    ): SunMoonCoordinates {
        val D2R = Math.PI / 180.0
        val R2D = 180.0 / Math.PI

        // Days since J2000.0
        val d = jd - 2451545.0

        // Greenwich Sidereal Time (GST)
        var gst = (280.46061837 + 360.98564736629 * d) % 360.0
        if (gst < 0) gst += 360.0

        // Local Sidereal Time (LST)
        var lst = (gst + lon) % 360.0
        if (lst < 0) lst += 360.0

        val latRad = lat * D2R
        val H = lst * D2R - ra // Hour angle in radians

        // Altitude
        val sinAlt = sin(latRad) * sin(dec) + cos(latRad) * cos(dec) * cos(H)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0)) // Clamp to prevent NaN

        // Azimuth
        val cosAz = (sin(dec) - sin(alt) * sin(latRad)) / (cos(alt) * cos(latRad))
        val sinAz = -cos(dec) * sin(H) / cos(alt)
        val az = atan2(sinAz, cosAz)

        return SunMoonCoordinates(
            altitude = alt * R2D,
            azimuth = (az * R2D + 360) % 360
        )
    }

    // ☀ Sun position
    fun getSunPosition(
        lat: Double,
        lon: Double,
        cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    ): SunMoonCoordinates {
        val jd = julianDay(cal)
        val n = jd - 2451545.0

        val L = (280.46 + 0.9856474 * n) % 360
        val g = Math.toRadians((357.528 + 0.9856003 * n) % 360)
        val lambda = Math.toRadians(L + 1.915 * sin(g) + 0.02 * sin(2 * g))

        val epsilon = Math.toRadians(23.439 - 0.0000004 * n)
        val alpha = atan2(cos(epsilon) * sin(lambda), cos(lambda))  // RA
        val delta = asin(sin(epsilon) * sin(lambda))                // Dec

        return raDecToHorizontal(alpha, delta, lat, lon, jd)
    }

    // 🌙 Moon position
    fun getMoonPosition(
        lat: Double,
        lon: Double,
        cal: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    ): SunMoonCoordinates {
        val jd = julianDay(cal)
        val T = (jd - 2451545.0) / 36525.0
        val D2R = Math.PI / 180.0

        // Mean orbital elements
        val L1 = (218.3164477 + 481267.88123421 * T) % 360
        val D = (297.8501921 + 445267.1114034 * T) % 360
        val M1 = (134.9633964 + 477198.8675055 * T) % 360
        val F = (93.2720950 + 483202.0175233 * T) % 360

        // Longitude & Latitude corrections
        val lonMoon = L1 +
                6.289 * sin(D2R * M1) +
                1.274 * sin(D2R * (2 * D - M1)) +
                0.658 * sin(D2R * (2 * D)) +
                0.214 * sin(D2R * (2 * M1)) -
                0.186 * sin(D2R * (357.529 + 35999.05 * T)) // M

        val latMoon = 5.128 * sin(D2R * F) +
                0.280 * sin(D2R * (M1 + F)) +
                0.277 * sin(D2R * (M1 - F)) +
                0.173 * sin(D2R * (2 * D - F))

        val lonRad = lonMoon * D2R
        val latRad = latMoon * D2R
        val epsilon = (23.439291 - 0.0000137 * T) * D2R

        // Ecliptic → Equatorial (RA/Dec)
        val xe = cos(latRad) * cos(lonRad)
        val ye = cos(epsilon) * cos(latRad) * sin(lonRad) - sin(epsilon) * sin(latRad)
        val ze = sin(epsilon) * cos(latRad) * sin(lonRad) + cos(epsilon) * sin(latRad)

        val RA = atan2(ye, xe)
        val Dec = asin(ze.coerceIn(-1.0, 1.0))

        return raDecToHorizontal(RA, Dec, lat, lon, jd)
    }

    // 📅 Julian Day calculation
    private fun julianDay(cal: Calendar): Double {
        var year = cal.get(Calendar.YEAR)
        var month = cal.get(Calendar.MONTH) + 1
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)

        val A = year / 100
        val B = 2 - A + (A / 4)
        val jdDay = (365.25 * (year + 4716)).toInt() + (30.6001 * (month + 1)).toInt() + day + B - 1524.5
        val jdTime = (hour + min / 60.0 + sec / 3600.0) / 24.0

        return jdDay + jdTime
    }
}
