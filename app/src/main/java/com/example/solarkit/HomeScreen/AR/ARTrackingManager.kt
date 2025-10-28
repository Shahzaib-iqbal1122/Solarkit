package com.example.solarkit.HomeScreen.AR

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solarkit.HomeScreen.SunMoonCalc.SunMoonCalculator
import com.example.solarkit.HomeScreen.SunMoonCalc.CelestialPosition
import kotlinx.coroutines.delay
import java.util.*
import androidx.compose.ui.graphics.Path
import kotlin.math.*

data class CelestialObject(
    val type: String, // "SUN" or "MOON"
    val position: CelestialPosition,
    val detectedAt: Long,
    val isVisible: Boolean
)

data class ARTrackingPath(
    val currentAzimuth: Double,
    val currentAltitude: Double,
    val futurePositions: List<TrackingPoint>,
    val isTracking: Boolean
)

data class TrackingPoint(
    val azimuth: Double,
    val altitude: Double,
    val timeOffset: Int // intervals from now
)

class ARTrackingManager {

    private val azimuthThreshold = 15.0
    private val altitudeThreshold = 10.0
    private val maxPitchForSkyView = -10.0

    fun detectCelestialObject(
        latitude: Double,
        longitude: Double,
        deviceAzimuth: Double,
        devicePitch: Double,
        onDetection: (CelestialObject, String) -> Unit
    ): Boolean {
        val sunPos = SunMoonCalculator.getSunPosition(latitude, longitude)
        val moonPos = SunMoonCalculator.getMoonPosition(latitude, longitude)

        if (devicePitch < maxPitchForSkyView) {
            Log.d("ARTracking", "Camera not pointing at sky, pitch=$devicePitch")
            return false
        }

        // Sun detection
        if (sunPos.altitude > -5) {
            val azDiff = getAngularDifference(deviceAzimuth, sunPos.azimuth)
            val altDiff = abs(devicePitch - sunPos.altitude)

            if (azDiff <= azimuthThreshold && altDiff <= altitudeThreshold) {
                onDetection(
                    CelestialObject("SUN", sunPos, System.currentTimeMillis(), sunPos.altitude > 0),
                    "☀️ SUN DETECTED!"
                )
                return true
            }
        }

        // Moon detection
        if (moonPos.altitude > -5) {
            val azDiff = getAngularDifference(deviceAzimuth, moonPos.azimuth)
            val altDiff = abs(devicePitch - moonPos.altitude)

            if (azDiff <= azimuthThreshold && altDiff <= altitudeThreshold) {
                onDetection(
                    CelestialObject("MOON", moonPos, System.currentTimeMillis(), moonPos.altitude > 0),
                    "🌙 MOON DETECTED!"
                )
                return true
            }
        }
        return false
    }

    fun generateTrackingPath(
        objectType: String,
        latitude: Double,
        longitude: Double
    ): ARTrackingPath? {
        val nowUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        val currentPos = if (objectType == "SUN") {
            SunMoonCalculator.getSunPosition(latitude, longitude, nowUtc)
        } else {
            SunMoonCalculator.getMoonPosition(latitude, longitude, nowUtc)
        }

        // If below horizon, stop tracking
        if (currentPos.altitude < -5.0) {
            Log.d("ARTracking", "$objectType below horizon - stopping tracking")
            return null
        }

        val futurePositions = mutableListOf<TrackingPoint>()

        // Generate more points with shorter intervals
        val intervalMinutes = 30  // 30-minute intervals
        val maxPoints = 30       // More total points possible

        for (interval in 1..maxPoints) {
            val futureCal = nowUtc.clone() as Calendar
            futureCal.add(Calendar.MINUTE, interval * intervalMinutes)

            val pos = if (objectType == "SUN") {
                SunMoonCalculator.getSunPosition(latitude, longitude, futureCal)
            } else {
                SunMoonCalculator.getMoonPosition(latitude, longitude, futureCal)
            }

            // More lenient horizon check for better path visibility
            if (pos.altitude > -15.0) {  // Lowered threshold
                futurePositions.add(
                    TrackingPoint(
                        azimuth = normalizeAzimuth(pos.azimuth),
                        altitude = pos.altitude,
                        timeOffset = interval * intervalMinutes  // Real time offset
                    )
                )

                Log.d("ARTracking", "Point ${interval}: ${interval * intervalMinutes}min -> Az=${pos.azimuth.format(1)}°, Alt=${pos.altitude.format(1)}°")
            } else {
                Log.d("ARTracking", "$objectType will set in ${interval * intervalMinutes} minutes (Alt=${pos.altitude.format(1)}°)")
                break
            }
        }

        Log.d("ARTracking", "Generated ${futurePositions.size} future positions for $objectType")

        return ARTrackingPath(
            currentAzimuth = normalizeAzimuth(currentPos.azimuth),
            currentAltitude = currentPos.altitude,
            futurePositions = futurePositions,
            isTracking = true
        )
    }

    // NEW: Generate complete daily path for world-space tracking
    fun generateCompleteDailyPath(
        objectType: String,
        latitude: Double,
        longitude: Double
    ): List<TrackingPoint> {
        val positions = mutableListOf<TrackingPoint>()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        if (objectType == "SUN") {
            // Sun: Generate sunrise to sunset (6 AM - 6 PM)
            calendar.set(Calendar.HOUR_OF_DAY, 6)
            calendar.set(Calendar.MINUTE, 0)

            // Generate positions every 10 minutes for more detailed path
            for (i in 0 until 72) { // 12 hours * 6 (10-min intervals) = more points
                val pos = SunMoonCalculator.getSunPosition(latitude, longitude, calendar)

                positions.add(
                    TrackingPoint(
                        azimuth = normalizeAzimuth(pos.azimuth),
                        altitude = pos.altitude,
                        timeOffset = i * 10 // 10-minute intervals for more detail
                    )
                )

                calendar.add(Calendar.MINUTE, 10) // Shorter intervals
                if (pos.altitude < -10.0 && i > 36) break // After 6 hours minimum
            }
        } else {
            // Moon: More complex - find moonrise to moonset
            val currentTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

            // Try to find when moon was/will be rising (altitude > 0)
            var searchTime = currentTime.clone() as Calendar
            searchTime.add(Calendar.HOUR, -12) // Start 12 hours ago

            val candidatePositions = mutableListOf<TrackingPoint>()

            // Generate 48 hours of moon positions with 10-minute intervals (more detailed)
            for (i in 0 until 288) { // 48 hours * 6 (10-min intervals)
                val pos = SunMoonCalculator.getMoonPosition(latitude, longitude, searchTime)

                candidatePositions.add(
                    TrackingPoint(
                        azimuth = normalizeAzimuth(pos.azimuth),
                        altitude = pos.altitude,
                        timeOffset = (i - 144) * 10 // Relative to current time, 10-min intervals
                    )
                )

                searchTime.add(Calendar.MINUTE, 10) // More frequent updates
            }

            // Filter only visible positions (above horizon with some margin)
            positions.addAll(candidatePositions.filter { it.altitude > -10.0 })
        }

        Log.d("WorldAR", "Generated ${positions.size} positions for complete ${objectType} path")
        return positions
    }

    suspend fun updateTrackingPath(
        trackedObject: CelestialObject,
        latitude: Double,
        longitude: Double,
        onUpdate: (ARTrackingPath?) -> Unit,
        onStopTracking: () -> Unit
    ) {
        while (true) {
            val updatedPath = generateTrackingPath(
                trackedObject.type,
                latitude,
                longitude
            )

            if (updatedPath == null) {
                onStopTracking()
                break
            } else {
                onUpdate(updatedPath)
            }

            delay(5000) // Update every 5 seconds
        }
    }

    private fun getAngularDifference(angle1: Double, angle2: Double): Double {
        var diff = angle2 - angle1
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        return abs(diff)
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}

// ========================
// AR DRAWING FUNCTIONS - UPDATED WITH WORLD-SPACE MODE
// ========================

fun DrawScope.drawARTracking(
    trackingPath: ARTrackingPath,
    cameraAzimuth: Double,
    cameraPitch: Double,
    objectType: String,
    isWorldSpaceMode: Boolean = false,
    latitude: Double = 31.46,
    longitude: Double = 74.31
) {
    val centerX = size.width / 2
    val centerY = size.height / 2

    if (isWorldSpaceMode) {
        drawWorldSpaceARTracking(trackingPath, cameraAzimuth, cameraPitch, objectType, centerX, centerY, latitude, longitude)
    } else {
        drawCameraRelativeTracking(trackingPath, cameraAzimuth, cameraPitch, objectType, centerX, centerY)
    }
}

fun DrawScope.drawWorldSpaceARTracking(
    trackingPath: ARTrackingPath,
    cameraAzimuth: Double,
    cameraPitch: Double,
    objectType: String,
    centerX: Float,
    centerY: Float,
    latitude: Double,
    longitude: Double
) {
    // NATURAL FOV calculation based on typical phone camera
    val horizontalFOV = 70f  // degrees (typical phone camera)
    val verticalFOV = 50f    // degrees (typical phone camera)

    // Calculate natural sensitivity from FOV
    val sensitivityX = size.width / horizontalFOV   // pixels per degree
    val sensitivityY = size.height / verticalFOV    // pixels per degree

    // Generate complete daily path using the manager function
    val arManager = ARTrackingManager()
    val completePositions = arManager.generateCompleteDailyPath(objectType, latitude, longitude)

    val objectColor = if (objectType == "SUN") Color.Yellow else Color.Cyan
    val pathColor = objectColor.copy(alpha = 0.6f)

    // Find current position in complete path
    val currentIndex = findCurrentPositionIndex(trackingPath.currentAzimuth, trackingPath.currentAltitude, completePositions)

    Log.d("WorldAR", "=== MORNING PATH DEBUG ===")
    Log.d("WorldAR", "Current: Az=${trackingPath.currentAzimuth.format(1)}°, Alt=${trackingPath.currentAltitude.format(1)}°")
    Log.d("WorldAR", "Camera: Az=${cameraAzimuth.format(1)}°, Pitch=${cameraPitch.format(1)}°")
    Log.d("WorldAR", "Drawing complete daily path with ${completePositions.size} positions, current at index $currentIndex")

    // Draw complete path with different visual styles
    completePositions.forEachIndexed { index, position ->
        val azDiff = getScreenAzimuthDiff(position.azimuth, cameraAzimuth)
        val altDiff = position.altitude - cameraPitch

        // DEBUG: Check if this is a future rising position
        val isFuturePosition = index > currentIndex
        val isRising = position.altitude > trackingPath.currentAltitude

        // FIXED: Correct coordinate calculation for rising positions
        val positionSensitivityY = if (isRising) {
            -8f  // Rising: NEGATIVE sensitivity so higher altitude = screen UP
        } else {
            8f   // Setting: positive sensitivity so lower altitude = screen DOWN
        }

        val screenX = centerX + (azDiff * sensitivityX)
        val screenY = centerY - (altDiff * sensitivityY) // Fixed calculation

        val isVisible = position.altitude > -10.0
        val isPast = index < currentIndex
        val isCurrent = Math.abs(index - currentIndex) <= 2
        val isFuture = index > currentIndex

        // Extended screen bounds
        val isOnScreen = screenX in -100f..(size.width + 100f) && screenY in -100f..(size.height + 100f)

        // DEBUG LOG for morning positions
        if (index >= currentIndex && index <= currentIndex + 5) {
            Log.d("WorldAR", "Position $index: Az=${position.azimuth.format(1)}°, Alt=${position.altitude.format(1)}° (${if(isRising) "RISING" else "SETTING"}) -> Screen($screenX, $screenY)")
            Log.d("WorldAR", "  AzDiff: ${azDiff.format(1)}°, AltDiff: ${altDiff.format(1)}°, SensY: $positionSensitivityY")
            Log.d("WorldAR", "  Expected: ${if(isFuture && isRising) "TOP-RIGHT of current" else "Other direction"}")
        }

        if (isVisible && isOnScreen) {
            when {
                isCurrent -> {
                    // Current position - bright and prominent
                    drawCircle(
                        color = objectColor,
                        radius = 45f,
                        center = Offset(screenX.toFloat(), screenY.toFloat()),
                        style = Stroke(width = 10f)
                    )
                    drawCircle(
                        color = objectColor.copy(alpha = 0.7f),
                        radius = 35f,
                        center = Offset(screenX.toFloat(), screenY.toFloat())
                    )
                    drawCircle(
                        color = objectColor.copy(alpha = 0.3f),
                        radius = 15f,
                        center = Offset(screenX.toFloat(), screenY.toFloat()),
                        style = Stroke(width = 2f)
                    )

                    // Direction line from center to current object
                    drawLine(
                        color = objectColor.copy(alpha = 0.9f),
                        start = Offset(centerX, centerY),
                        end = Offset(screenX.toFloat(), screenY.toFloat()),
                        strokeWidth = 4f
                    )
                }
                isPast -> {
                    // Past positions - faded trail
                    drawCircle(
                        color = pathColor.copy(alpha = 0.4f),
                        radius = 8f,
                        center = Offset(screenX.toFloat(), screenY.toFloat())
                    )
                }
                isFuture -> {
                    // Future positions - predicted path
                    val futureAlpha = (0.8f - ((index - currentIndex) * 0.03f)).coerceAtLeast(0.3f)
                    val futureRadius = (12f - ((index - currentIndex) * 0.3f)).coerceAtLeast(6f)

                    // Color-code based on movement for debugging
                    val debugColor = if (isRising) Color.Green else Color.Red

                    drawCircle(
                        color = if (index <= currentIndex + 5) debugColor.copy(alpha = futureAlpha) else pathColor.copy(alpha = futureAlpha),
                        radius = futureRadius,
                        center = Offset(screenX.toFloat(), screenY.toFloat())
                    )

                    // Outer ring
                    drawCircle(
                        color = objectColor.copy(alpha = futureAlpha * 0.5f),
                        radius = futureRadius + 3f,
                        center = Offset(screenX.toFloat(), screenY.toFloat()),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }
    }

    // Connect path with individual sensitivity
    drawCompletePathWithIndividualSensitivity(completePositions, cameraAzimuth, cameraPitch, centerX, centerY, sensitivityX,sensitivityX, pathColor, currentIndex, trackingPath.currentAltitude)
}

fun DrawScope.drawCameraRelativeTracking(
    trackingPath: ARTrackingPath,
    cameraAzimuth: Double,
    cameraPitch: Double,
    objectType: String,
    centerX: Float,
    centerY: Float
) {

    // NATURAL FOV calculation based on typical phone camera
    val horizontalFOV = 70f  // degrees (typical phone camera)
    val verticalFOV = 50f    // degrees (typical phone camera)

    // Calculate natural sensitivity from FOV
    val sensitivityX = size.width / horizontalFOV   // pixels per degree
    val sensitivityY = size.height / verticalFOV    // pixels per degree


    Log.d("ARTracking", "Drawing $objectType with ${trackingPath.futurePositions.size} future positions")

    // Calculate object's screen position relative to camera
    val azimuthDiff = getScreenAzimuthDiff(trackingPath.currentAzimuth, cameraAzimuth)
    val altitudeDiff = trackingPath.currentAltitude - cameraPitch

    // Calculate dynamic Y sensitivity for current position
    val currentSensitivityY = if (trackingPath.futurePositions.isNotEmpty()) {
        val firstFuture = trackingPath.futurePositions.first()
        if (firstFuture.altitude > trackingPath.currentAltitude) 12f else -12f
    } else {
        -12f // Default for setting
    }

    Log.d("ARTracking", "Object movement: ${if(trackingPath.futurePositions.isNotEmpty() && trackingPath.futurePositions.first().altitude > trackingPath.currentAltitude) "RISING" else "SETTING"}, sensitivityY = $currentSensitivityY")

    // ✅ Current object screen pos
    val objectScreenX = centerX + (azimuthDiff * sensitivityX).toFloat()
    val objectScreenY = centerY - (altitudeDiff * sensitivityY).toFloat()

    Log.d("ARTracking", "✅ USING Natural sensitivity: X=$sensitivityX, Y=$sensitivityY")
    Log.d("ARTracking", "Screen size: ${size.width} x ${size.height}")

    // Object colors
    val objectColor = if (objectType == "SUN") Color.Yellow else Color.Cyan
    val pathColor = objectColor.copy(alpha = 0.6f)

    Log.d("ARTracking", "Drawing $objectType at screen position: ($objectScreenX, $objectScreenY)")
    Log.d("ARTracking", "Camera Az=${cameraAzimuth.format(1)}°, Object Az=${trackingPath.currentAzimuth.format(1)}°")
    Log.d("ARTracking", "Azimuth diff: ${azimuthDiff.format(1)}°, Altitude diff: ${altitudeDiff.format(1)}°")

    // Add altitude analysis debug
    Log.d("ARTracking", "=== ALTITUDE ANALYSIS ===")
    Log.d("ARTracking", "Current Alt: ${trackingPath.currentAltitude.format(2)}°")
    trackingPath.futurePositions.take(5).forEachIndexed { index, point ->
        val comparison = if (point.altitude > trackingPath.currentAltitude) "HIGHER" else "LOWER"
        Log.d("ARTracking", "Point ${index+1}: Alt=${point.altitude.format(2)}° ($comparison than current)")
    }

    // Draw main object indicator (current position)
    drawCircle(
        color = objectColor,
        radius = 45f,
        center = Offset(objectScreenX, objectScreenY),
        style = Stroke(width = 10f)
    )

    // Inner circle
    drawCircle(
        color = objectColor.copy(alpha = 0.7f),
        radius = 35f,
        center = Offset(objectScreenX, objectScreenY)
    )

    // Pulsing outer ring
    drawCircle(
        color = objectColor.copy(alpha = 0.3f),
        radius = 15f,
        center = Offset(objectScreenX, objectScreenY),
        style = Stroke(width = 2f)
    )

    // Draw direction line from center to object
    val lineColor = objectColor.copy(alpha = 0.9f)
    drawLine(
        color = lineColor,
        start = Offset(centerX, centerY),
        end = Offset(objectScreenX, objectScreenY),
        strokeWidth = 4f
    )

    // ✅ Future path
    if (trackingPath.futurePositions.isNotEmpty()) {
        Log.d("ARTracking", "Drawing future path with ${trackingPath.futurePositions.size} points")

        // Create path showing movement direction (current → future)
        val currentPosition = Offset(objectScreenX, objectScreenY)
        var previousPosition = currentPosition

        // Draw future positions in chronological order
        trackingPath.futurePositions.take(10).forEachIndexed { index, point ->
            val futureAzDiff = getScreenAzimuthDiff(point.azimuth, cameraAzimuth)
            val futureAltDiff = point.altitude - cameraPitch

            // INDIVIDUAL SENSITIVITY FOR EACH POINT - BALANCED!
            val pointIsRising = point.altitude > trackingPath.currentAltitude
            val pointSensitivityY = if (pointIsRising) 4f else -4f  // Reduced for straighter path


            val futureX = centerX + (futureAzDiff * sensitivityX).toFloat()
            val futureY = centerY + (futureAltDiff * sensitivityY).toFloat()
            val futurePosition = Offset(futureX, futureY)

            Log.d("ARTracking", "Point ${index+1}: futureAzDiff=${futureAzDiff.format(1)}°, futureX=$futureX (${if(futureX > centerX) "RIGHT" else "LEFT"} of center)")

            // Calculate visual properties based on time progression
            val progress = (index + 1).toFloat() / 10f  // 0.1 to 1.0
            val alpha = (0.9f - (progress * 0.4f)).coerceAtLeast(0.3f)
            val strokeWidth = (5f - (progress * 2f)).coerceAtLeast(2f)
            val dotRadius = (12f - (progress * 4f)).coerceAtLeast(4f)

            // Draw connecting line from previous to current future position
            drawLine(
                color = pathColor.copy(alpha = alpha),
                start = previousPosition,
                end = futurePosition,
                strokeWidth = strokeWidth
            )

            // Draw future position marker

            drawCircle(
                color = pathColor.copy(alpha = alpha),
                radius = dotRadius,
                center = futurePosition
            )

            // Draw outer ring for better visibility
            drawCircle(
                color = objectColor.copy(alpha = alpha * 0.6f),
                radius = dotRadius + 3f,
                center = futurePosition,
                style = Stroke(width = 2f)

            )

            // Add this in future positions loop


            previousPosition = futurePosition

            Log.d("ARTracking", "Point ${index + 1}: Alt=${point.altitude.format(1)}° (${if(pointIsRising) "RISING" else "SETTING"}), sensitivityY=$pointSensitivityY -> Screen($futureX, $futureY)")
        }

        // Debug info
        Log.d("ARTracking", "Sun Az: ${trackingPath.currentAzimuth}° → ${trackingPath.futurePositions.firstOrNull()?.azimuth}°")
        Log.d("ARTracking", "Movement: ${if((trackingPath.futurePositions.firstOrNull()?.azimuth ?: 0.0) > trackingPath.currentAzimuth) "West (Az increasing)" else "East (Az decreasing)"}")
        Log.d("ARTracking", "Screen movement: Current($objectScreenX) vs Center($centerX) = ${if(objectScreenX > centerX) "RIGHT" else "LEFT"}")
    }
}

// Helper functions
fun findCurrentPositionIndex(currentAz: Double, currentAlt: Double, positions: List<TrackingPoint>): Int {
    var closestIndex = 0
    var minDistance = Double.MAX_VALUE

    positions.forEachIndexed { index, pos ->
        val azDiff = Math.abs(pos.azimuth - currentAz)
        val altDiff = Math.abs(pos.altitude - currentAlt)
        val distance = Math.sqrt(azDiff * azDiff + altDiff * altDiff)

        if (distance < minDistance) {
            minDistance = distance
            closestIndex = index
        }
    }

    return closestIndex
}

fun DrawScope.drawCompletePath(
    positions: List<TrackingPoint>,
    cameraAzimuth: Double,
    cameraPitch: Double,
    centerX: Float,
    centerY: Float,
    sensitivityX: Float,
    sensitivityY: Float,
    pathColor: Color,
    currentIndex: Int
) {
    for (i in 1 until positions.size) {
        val prevPos = positions[i-1]
        val currentPos = positions[i]

        if (prevPos.altitude > -5.0 && currentPos.altitude > -5.0) {
            val prevAzDiff = getScreenAzimuthDiff(prevPos.azimuth, cameraAzimuth)
            val prevAltDiff = prevPos.altitude - cameraPitch
            val prevScreenX = centerX + (prevAzDiff * sensitivityX)
            val prevScreenY = centerY - (prevAltDiff * sensitivityY)

            val currAzDiff = getScreenAzimuthDiff(currentPos.azimuth, cameraAzimuth)
            val currAltDiff = currentPos.altitude - cameraPitch
            val currScreenX = centerX + (currAzDiff * sensitivityX)
            val currScreenY = centerY - (currAltDiff * sensitivityY)

            val alpha = when {
                i <= currentIndex -> 0.3f // Past path
                i > currentIndex -> 0.6f // Future path
                else -> 0.5f
            }

            val strokeWidth = when {
                i <= currentIndex -> 2f // Past path - thinner
                i > currentIndex -> 3f // Future path - thicker
                else -> 2.5f
            }

            // Extended screen bounds for wider visibility
            val isOnScreen = prevScreenX in -50f..(size.width + 50f) && prevScreenY in -50f..(size.height + 50f) &&
                    currScreenX in -50f..(size.width + 50f) && currScreenY in -50f..(size.height + 50f)

            if (isOnScreen) {
                drawLine(
                    color = pathColor.copy(alpha = alpha),
                    start = Offset(prevScreenX.toFloat(), prevScreenY.toFloat()),
                    end = Offset(currScreenX.toFloat(), currScreenY.toFloat()),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

// NEW: Line drawing function with individual sensitivity
fun DrawScope.drawCompletePathWithIndividualSensitivity(
    positions: List<TrackingPoint>,
    cameraAzimuth: Double,
    cameraPitch: Double,
    centerX: Float,
    centerY: Float,
    sensitivityX: Float,
    sensitivityY: Float,
    pathColor: Color,
    currentIndex: Int,
    currentAltitude: Double
) {
    for (i in 1 until positions.size) {
        val prevPos = positions[i-1]
        val currentPos = positions[i]

        if (prevPos.altitude > -5.0 && currentPos.altitude > -5.0) {
            // FIXED: Individual sensitivity with corrected logic
            val prevIsRising = prevPos.altitude > currentAltitude
            val prevSensitivityY = if (prevIsRising) 4f else -4f  // FLIPPED
            val currIsRising = currentPos.altitude > currentAltitude
            val currSensitivityY = if (currIsRising) 4f else -4f  // FLIPPED

            val prevAzDiff = getScreenAzimuthDiff(prevPos.azimuth, cameraAzimuth)
            val prevAltDiff = prevPos.altitude - cameraPitch
            val prevScreenX = centerX + (prevAzDiff * sensitivityX)
            val prevScreenY = centerY - (prevAltDiff * sensitivityY) // Fixed calculation

            val currAzDiff = getScreenAzimuthDiff(currentPos.azimuth, cameraAzimuth)
            val currAltDiff = currentPos.altitude - cameraPitch
            val currScreenX = centerX + (currAzDiff * sensitivityX)
            val currScreenY = centerY - (currAltDiff * sensitivityY) // Fixed calculation

            val alpha = when {
                i <= currentIndex -> 0.3f // Past path
                i > currentIndex -> 0.6f // Future path
                else -> 0.5f
            }

            val strokeWidth = when {
                i <= currentIndex -> 2f // Past path - thinner
                i > currentIndex -> 3f // Future path - thicker
                else -> 2.5f
            }

            // Extended screen bounds for wider visibility
            val isOnScreen = prevScreenX in -50f..(size.width + 50f) && prevScreenY in -50f..(size.height + 50f) &&
                    currScreenX in -50f..(size.width + 50f) && currScreenY in -50f..(size.height + 50f)

            if (isOnScreen) {
                drawLine(
                    color = pathColor.copy(alpha = alpha),
                    start = Offset(prevScreenX.toFloat(), prevScreenY.toFloat()),
                    end = Offset(currScreenX.toFloat(), currScreenY.toFloat()),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

private fun normalizeAzimuth(azimuth: Double): Double {
    var normalized = azimuth
    while (normalized < 0) normalized += 360
    while (normalized >= 360) normalized -= 360
    return normalized
}


private fun getScreenAzimuthDiff(targetAzimuth: Double, cameraAzimuth: Double): Double {
    val target = normalizeAzimuth(targetAzimuth)
    val camera = normalizeAzimuth(cameraAzimuth)

    var diff = target - camera
    if (diff > 180) diff -= 360
    if (diff < -180) diff += 360

    return diff
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

// ========================
// COMPOSABLE UI COMPONENTS
// ========================

@Composable
fun ARDetectionMessage(
    message: String,
    isVisible: Boolean,
    objectType: String,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = if (objectType == "SUN")
                    Color(0xFFFFA726).copy(alpha = 0.9f)
                else
                    Color(0xFF42A5F5).copy(alpha = 0.9f)
            )
        ) {
            Text(
                text = "$message\n🎯 Now Tracking Path!",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}



@Composable
fun ARTrackingStatus(
    trackedObject: CelestialObject?,
    trackingPath: ARTrackingPath?,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    trackedObject?.let { obj ->
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = onStopTracking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .height(48.dp)
                    .width(180.dp)
            ) {
                Text(
                    "Stop Tracking ${obj.type}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

