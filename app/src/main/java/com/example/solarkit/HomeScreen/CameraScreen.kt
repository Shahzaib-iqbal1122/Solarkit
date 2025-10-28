package com.example.solarkit.CameraScreen

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.Alignment
import com.example.solarkit.HomeScreen.AR.ARDetectionMessage
import com.example.solarkit.HomeScreen.AR.ARTrackingManager
import com.example.solarkit.HomeScreen.AR.ARTrackingPath
import com.example.solarkit.HomeScreen.AR.ARTrackingStatus
import com.example.solarkit.HomeScreen.AR.CelestialObject
import com.example.solarkit.HomeScreen.AR.drawARTracking
import com.example.solarkit.HomeScreen.SunMoonCalc.SunMoonCalculator
import com.example.solarkit.HomeScreen.debugging.LocationHelper
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun CameraScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    // ✅ AR Tracking Manager Instance
    val arTrackingManager = remember { ARTrackingManager() }
    // ✅ AR Tracking States
    var trackedObject by remember { mutableStateOf<CelestialObject?>(null) }
    var trackingPath by remember { mutableStateOf<ARTrackingPath?>(null) }
    var showDetectionMessage by remember { mutableStateOf(false) }
    var detectionMessageText by remember { mutableStateOf("") }

    // State variables
    var detectionMessage by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }

    // Sensor values
    var azimuth by remember { mutableStateOf(0f) }
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }

    // Detection state
    var lastDetectionTime by remember { mutableStateOf(0L) }
    var isDetecting by remember { mutableStateOf(false) }
    val detectionCooldown = 3000L // 3 seconds between detections

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Location Helper
    val locationHelper = remember {
        LocationHelper(context) { lat, lon ->
            latitude = lat
            longitude = lon
            Log.d("Location", "Updated: Lat=$lat, Lon=$lon")
        }
    }

    // ✅ Auto-hide detection message (one-time only)
    LaunchedEffect(showDetectionMessage) {
        if (showDetectionMessage) {
            delay(3000)
            showDetectionMessage = false
        }
    }

    // ✅ Real-time tracking path updates
    LaunchedEffect(trackedObject, latitude, longitude) {
        trackedObject?.let { obj ->
            if (latitude != 0.0 && longitude != 0.0) {
                arTrackingManager.updateTrackingPath(
                    obj, latitude, longitude,
                    onUpdate = { updatedPath ->
                        trackingPath = updatedPath
                    },
                    onStopTracking = {
                        trackedObject = null
                        trackingPath = null
                        Log.d("ARTracking", "${obj.type} tracking stopped - below horizon")
                    }
                )
            }
        }
    }


    // Setup location and sensors
    DisposableEffect(Unit) {
        locationHelper.requestLocation()

        // Setup sensor manager
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val magneticField = FloatArray(3)
        val gravity = FloatArray(3)
        var haveMagnetic = false
        var haveGravity = false

        // ✅ AR Detection Function (one-time only)
        fun checkForARDetection() {
            if (latitude != 0.0 && longitude != 0.0 && trackedObject == null) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastDetectionTime > detectionCooldown) {

                    val detected = arTrackingManager.detectCelestialObject(
                        latitude = latitude,
                        longitude = longitude,
                        deviceAzimuth = azimuth.toDouble(),
                        devicePitch = pitch.toDouble()
                    ) { detectedObj, message ->
                        // Show one-time detection message
                        detectionMessageText = message
                        showDetectionMessage = true
                        lastDetectionTime = currentTime

                        // Start AR tracking
                        trackedObject = detectedObj
                        Log.d("ARTracking", "Started tracking ${detectedObj.type}")
                    }
                }
            }
        }

        val sensorListener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        // Most accurate method - use rotation vector if available
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                        // 🔥 Remap for LANDSCAPE as base orientation
                        val remappedMatrix = FloatArray(9)
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_Y,  // Y ko forward axis banao
                            SensorManager.AXIS_MINUS_X, // X ko sideways banao
                            remappedMatrix
                        )

                        // Step 3: Use remapped matrix for orientation
                        SensorManager.getOrientation(remappedMatrix, orientationAngles)

                        // Convert to degrees
                        azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                        if (azimuth < 0) azimuth += 360f

                        var adjustedAzimuth = azimuth
                        if (pitch < 0) {
                            adjustedAzimuth = -azimuth
                        }





                         // ✅ Normalize pitch (horizontal = 0, sky = +, ground = -)
                        pitch = -(-pitch + 90f)

                         // ✅ Handle left vs right landscape (normalize roll)
                        if (roll < -90 || roll > 90) {
                            pitch = -pitch
                        }


                        Log.d("SensorDebug", "Raw Orientation - Az: ${azimuth.format(1)}°, Pitch: ${pitch.format(1)}°, Roll: ${roll.format(1)}°")

                        checkForARDetection()
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        // Fallback method - part 1
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        haveGravity = true

                        if (haveMagnetic && haveGravity) {
                            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, magneticField)) {
                                // ✅ SAME FIX: Apply coordinate system remapping for fallback method
                                val remappedMatrix = FloatArray(9)

                                if (SensorManager.remapCoordinateSystem(
                                        rotationMatrix,
                                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                                        remappedMatrix
                                    )) {
                                    SensorManager.getOrientation(remappedMatrix, orientationAngles)
                                } else {
                                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                                }

                                azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                                pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                                roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                                if (azimuth < 0) azimuth += 360f

// ✅ Fix for landscape camera
                                azimuth = (azimuth + 90f) % 360f
                                if (roll < -90 || roll > 90) {
                                    azimuth = (azimuth + 180f) % 360f
                                }

                                Log.d("SensorDebug", "Fallback Remapped - Az: ${azimuth.format(1)}°, Pitch: ${pitch.format(1)}°, Roll: ${roll.format(1)}°")

                                checkForARDetection()
                            }
                        }
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        // Fallback method - part 2
                        System.arraycopy(event.values, 0, magneticField, 0, 3)
                        haveMagnetic = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                when (accuracy) {
                    SensorManager.SENSOR_STATUS_UNRELIABLE ->
                        Log.w("Sensor", "Sensor accuracy: Unreliable")
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW ->
                        Log.w("Sensor", "Sensor accuracy: Low")
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ->
                        Log.i("Sensor", "Sensor accuracy: Medium")
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH ->
                        Log.i("Sensor", "Sensor accuracy: High")
                }
            }
        }

        // Register sensors - try rotation vector first, then fallback
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(
                sensorListener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            Log.d("Sensor", "Using Rotation Vector sensor")
        } else {
            // Fallback to accelerometer + magnetometer
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            if (accelerometer != null && magnetometer != null) {
                sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI)
                Log.d("Sensor", "Using Accelerometer + Magnetometer fallback")
            } else {
                Log.e("Sensor", "No suitable sensors available!")
            }
        }

        onDispose {
            locationHelper.stopLocationUpdates()
            sensorManager.unregisterListener(sensorListener)
        }
    }

    // Camera setup
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var recording by remember { mutableStateOf<Recording?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val previewUseCase = CameraPreview.Builder().build()
                    previewUseCase.setSurfaceProvider(previewView.surfaceProvider)

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            previewUseCase,
                            imageCapture,
                            videoCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // ✅ AR Canvas Overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            // Basic crosshair
            drawCrosshair()

            // AR tracking overlay
            trackedObject?.let { obj ->
                trackingPath?.let { path ->
                    drawARTracking(
                        trackingPath = path,
                        cameraAzimuth = azimuth.toDouble(),
                        cameraPitch = pitch.toDouble(),
                        objectType = obj.type
                    )
                }
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // ✅ AR Tracking Status (Top Center)
        ARTrackingStatus(
            trackedObject = trackedObject,
            trackingPath = trackingPath,
            onStopTracking = {
                trackedObject = null
                trackingPath = null
                Log.d("ARTracking", "Manual stop tracking")
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        )

        // ✅ One-time Detection Message
        ARDetectionMessage(
            message = detectionMessageText,
            isVisible = showDetectionMessage,
            objectType = trackedObject?.type ?: "SUN",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )

        // Capture Photo button
        Button(
            onClick = {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                }
                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Toast.makeText(context, "✅ Photo saved!", Toast.LENGTH_SHORT).show()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                            Toast.makeText(context, "❌ Error saving photo", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Text("📸 Capture")
        }

        // Record Video button
        Button(
            onClick = {
                if (recording != null) {
                    recording?.stop()
                    recording = null
                } else {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                    }

                    val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
                        context.contentResolver,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    ).setContentValues(contentValues).build()

                    recording = videoCapture.output
                        .prepareRecording(context, mediaStoreOutputOptions)
                        .start(ContextCompat.getMainExecutor(context)) { event ->
                            if (event is VideoRecordEvent.Finalize) {
                                Toast.makeText(context, "🎬 Video saved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording != null) Color.Red else Color.Blue
            )
        ) {
            Text(if (recording != null) "⏹ Stop" else "🎥 Record")
        }

        // Detection message overlay
        detectionMessage?.let { msg ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.contains("SUN"))
                        Color(0xFFFFA726).copy(alpha = 0.9f)
                    else
                        Color(0xFF42A5F5).copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        // Info Panel
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp, 70.dp, 16.dp, 16.dp)
                .widthIn(max = 150.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(15.dp)
            ) {
                if (latitude != 0.0 && longitude != 0.0) {
                    val sunPos = SunMoonCalculator.getSunPosition(latitude, longitude)
                    val moonPos = SunMoonCalculator.getMoonPosition(latitude, longitude)

                    // Location
                    Text("📍 Location", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${latitude.format(2)}°, ${longitude.format(2)}°", color = Color.White, fontSize = 11.sp)

                    Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                    // Sun
                    Row {
                        Text("☀️ ", color = Color.Yellow, fontSize = 14.sp)
                        Column {
                            Text("Alt: ${sunPos.altitude.format(1)}°", color = Color.White, fontSize = 11.sp)
                            Text("Az: ${sunPos.azimuth.format(1)}°", color = Color.White, fontSize = 11.sp)
                            Text(
                                if (sunPos.altitude > 0) "Visible" else "Below horizon",
                                color = if (sunPos.altitude > 0) Color.Green else Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Moon
                    Row {
                        Text("🌙 ", color = Color.Cyan, fontSize = 14.sp)
                        Column {
                            Text("Alt: ${moonPos.altitude.format(1)}°", color = Color.White, fontSize = 11.sp)
                            Text("Az: ${moonPos.azimuth.format(1)}°", color = Color.White, fontSize = 11.sp)
                            Text(
                                if (moonPos.altitude > 0) "Visible" else "Below horizon",
                                color = if (moonPos.altitude > 0) Color.Green else Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

                    // Camera orientation
                    Text("📱 Camera", color = Color.Magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Az: ${azimuth.format(1)}°", color = Color.White, fontSize = 11.sp)
                    Text("Pitch: ${pitch.format(1)}°", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// Draw crosshair in center of screen
private fun DrawScope.drawCrosshair() {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val lineLength = 30f
    val lineWidth = 2f

    // Horizontal line
    drawLine(
        color = Color.Red.copy(alpha = 0.7f),
        start = Offset(centerX - lineLength, centerY),
        end = Offset(centerX + lineLength, centerY),
        strokeWidth = lineWidth
    )

    // Vertical line
    drawLine(
        color = Color.Red.copy(alpha = 0.7f),
        start = Offset(centerX, centerY - lineLength),
        end = Offset(centerX, centerY + lineLength),
        strokeWidth = lineWidth
    )

    // Center circle
    drawCircle(
        color = Color.Red.copy(alpha = 0.5f),
        radius = 5f,
        center = Offset(centerX, centerY)
    )
}

// Detect if camera is pointing at sun or moon
private fun detectCelestialBody(
    latitude: Double,
    longitude: Double,
    deviceAzimuth: Double,
    devicePitch: Double,
    onDetection: (String) -> Unit
) {
    val sunPos = SunMoonCalculator.getSunPosition(latitude, longitude)
    val moonPos = SunMoonCalculator.getMoonPosition(latitude, longitude)

    // Detection thresholds
    val azimuthThreshold = 2.0  // degrees
    val altitudeThreshold = 1.0  // degrees

    // Check Sun
    if (sunPos.altitude > -5) {
        val azDiff = getAngularDifference(deviceAzimuth, sunPos.azimuth)
        val altDiff = abs(devicePitch - sunPos.altitude)

        Log.d("SunDetection",
            "Device Az: ${deviceAzimuth.format(1)}°, Sun Az: ${sunPos.azimuth.format(1)}°, Az Diff: ${azDiff.format(1)}°")
        Log.d("SunDetection",
            "Device Pitch: ${devicePitch.format(1)}°, Sun Alt: ${sunPos.altitude.format(1)}°, Alt Diff: ${altDiff.format(1)}°")

        if (azDiff <= azimuthThreshold && altDiff <= altitudeThreshold) {
            val status = when {
                sunPos.altitude > 30 -> "High in sky"
                sunPos.altitude > 10 -> "Low in sky"
                sunPos.altitude > 0 -> "Near horizon"
                else -> "Just below horizon"
            }
            onDetection("☀️ SUN DETECTED!\n${status}\nAlt: ${sunPos.altitude.format(1)}°")
            return
        }
    }

    // Check Moon
    if (moonPos.altitude > -5) {
        val azDiff = getAngularDifference(deviceAzimuth, moonPos.azimuth)
        val altDiff = abs(devicePitch - moonPos.altitude)

        Log.d("MoonDetection",
            "Device Az: ${deviceAzimuth.format(1)}°, Moon Az: ${moonPos.azimuth.format(1)}°, Az Diff: ${azDiff.format(1)}°")
        Log.d("MoonDetection",
            "Device Pitch: ${devicePitch.format(1)}°, Moon Alt: ${moonPos.altitude.format(1)}°, Alt Diff: ${altDiff.format(1)}°")

        if (azDiff <= azimuthThreshold && altDiff <= altitudeThreshold) {
            val status = when {
                moonPos.altitude > 30 -> "High in sky"
                moonPos.altitude > 10 -> "Low in sky"
                moonPos.altitude > 0 -> "Near horizon"
                else -> "Just below horizon"
            }
            onDetection("🌙 MOON DETECTED!\n${status}\nAlt: ${moonPos.altitude.format(1)}°")
            return
        }
    }
}

// Calculate angular difference handling 360° wrap
private fun getAngularDifference(angle1: Double, angle2: Double): Double {
    val diff = abs(angle1 - angle2)
    return if (diff > 180) 360 - diff else diff
}

// Extension functions for formatting
private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

