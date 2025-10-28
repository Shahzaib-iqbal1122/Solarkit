package com.example.solarkit.OtpScreen

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimatable
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.solarkit.OTPAuth.FirebaseAuthHelper
import com.example.solarkit.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(onOtpSent: (phoneNumber: String, verificationId: String) -> Unit) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(0) } // Track attempts
    var showRetryDelay by remember { mutableStateOf(false) }
    var retryCountdown by remember { mutableStateOf(0) }

    val successComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.happysun))
    val errorComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.incorrectnum))

    // Countdown timer for retry delay
    LaunchedEffect(showRetryDelay) {
        if (showRetryDelay) {
            retryCountdown = 30 // 30 second delay
            while (retryCountdown > 0) {
                delay(1000)
                retryCountdown--
            }
            showRetryDelay = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lock Icon inside red circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFFF3B30), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Verification",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Verification",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Enter your phone number",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "with Country Code e.g: +1123456789",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                // Show attempt information
                if (attemptCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Attempt: $attemptCount/3",
                        fontSize = 12.sp,
                        color = if (attemptCount >= 2) Color.Red else Color.Yellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Phone input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 50.dp)
                    .height(50.dp)
                    .background(Color(0xFF2C2C2E), RoundedCornerShape(32.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        // Clear error when user types
                        if (errorMessage != null) {
                            errorMessage = null
                        }
                    },
                    placeholder = {
                        Text(
                            "Phone Number : e.g +1123456789 ",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text // Better keyboard type
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Get OTP Button with retry logic
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        // Validate phone number format
                        if (!phoneNumber.startsWith("+") || phoneNumber.length < 10) {
                            Toast.makeText(
                                context,
                                "Please enter valid phone number with country code",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null
                        attemptCount++

                        // Enhanced Firebase OTP sending
                        FirebaseAuthHelper.sendOtp(
                            phoneNumber = phoneNumber.trim(),
                            context = context,
                            activity = context as Activity,
                            onCodeSent = { verificationId ->
                                isLoading = false
                                // Reset attempt count on success
                                attemptCount = 0
                                onOtpSent(phoneNumber.trim(), verificationId)
                            },
                            onVerificationFailed = { exception ->
                                isLoading = false
                                val errorMsg = when {
                                    exception.message?.contains("network", true) == true ->
                                        "Network error.\nCheck connection"
                                    exception.message?.contains("quota", true) == true ->
                                        "Too many attempts.\nTry later"
                                    exception.message?.contains("invalid", true) == true ->
                                        "Invalid phone number.\nCheck format"
                                    attemptCount >= 3 -> {
                                        showRetryDelay = true
                                        "Max attempts reached.\nWait 30 seconds"
                                    }
                                    else -> "Verification failed.\nTry again"
                                }
                                errorMessage = errorMsg
                            }
                        )
                    } else {
                        Toast.makeText(
                            context,
                            "Please enter your phone number",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = !isLoading && !showRetryDelay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .padding(horizontal = 90.dp),
                shape = RoundedCornerShape(62.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showRetryDelay) Color.Gray else Color(0xFFFF3B30)
                )
            ) {
                Text(
                    text = if (showRetryDelay) "Wait $retryCountdown sec" else "Get OTP",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }

            // Tips for better success rate
            if (attemptCount > 0 && !isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "💡 Tips:\n• Check internet connection\n• Use correct country code\n• Wait if too many attempts",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Enhanced Loader Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.size(280.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (errorMessage == null) {
                            // Loading Animation
                            LottieAnimation(
                                composition = successComposition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.size(150.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Sending OTP...", fontSize = 16.sp, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "This may take up to 30 seconds",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            // Error Animation
                            LottieAnimation(
                                composition = errorComposition,
                                iterations = 2,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Retry Button (only if not in cooldown)
                            if (!showRetryDelay) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        isLoading = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF3B30)
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .padding(horizontal = 32.dp)
                                        .height(45.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text("Try Again", color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewOptimizedOtpScreen() {
    OtpScreen { _, _ -> }
}