package com.example.solarkit.SplashScreen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.example.solarkit.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.delay


@Composable
fun SplashScreen(onFinished: () -> Unit) {

    var progress by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // 🔹 ExoPlayer setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_screen}")
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    // 🔹 Release ExoPlayer when Composable is destroyed
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // 🔹 Progress loop (0 → 100)
    LaunchedEffect(Unit) {
        while (progress < 100) {
            delay(20)
            progress++
        }
        delay(550)
        onFinished()
    }

    // 🔹 UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        // Background video
        AndroidView(
            factory = {
                StyledPlayerView(it).apply {
                    player = exoPlayer
                    useController = false // hide controls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM // crop + fullscreen
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Progress text overlay
        Text(
            text = "$progress %",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewSplash() {
    SplashScreen(
        onFinished = {}
    )
}


