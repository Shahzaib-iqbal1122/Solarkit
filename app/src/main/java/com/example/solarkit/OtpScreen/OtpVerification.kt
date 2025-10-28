package com.example.solarkit.OtpScreen

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.solarkit.OTPAuth.FirebaseAuthHelper
import com.example.solarkit.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay

enum class VerifyStatus {
    NORMAL,
    LOADING,
    SUCCESS,
    ERROR
}


@Composable
fun OtpVerificationScreen(
    phoneNumber: String,
    verificationId: String,
    onOtpVerified: () -> Unit
) {

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var otpCode by remember { mutableStateOf("") }
    var timer by remember { mutableStateOf(30) } // 30 sec countdown
    var isRunning by remember { mutableStateOf(true) } // 🔹 timer chal raha hai ya nahi
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var verifyStatus by remember { mutableStateOf(VerifyStatus.NORMAL) }


    //Lottie animations
    val loadingAnim by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.happysun))
    val successAnim by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.success))
    val errorAnim by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.error))

    // 🔹 Background Image


    // Countdown timer
    LaunchedEffect(isRunning) {
        if (isRunning) {
            timer = 30
            while (timer > 0) {
                delay(1000)
                timer--
            }
            isRunning = false
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
     )

    {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circle Icon (placeholder 🔒)
            Box(
                modifier = Modifier
                    .size(90.dp)
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
                text = "Verification Code",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter the verification code sent to $phoneNumber",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(30.dp))

            // OTP Digit Boxes
            // OTP circles
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable( // pura row clickable hai
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusManager.clearFocus()   // clear pehle
                        focusRequester.requestFocus() // dobara force focus
                    }
            ) {
                (0 until 6).forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2C2C2E), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = otpCode.getOrNull(index)?.toString() ?: "",
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hidden TextField (for actual input)
            TextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6) otpCode = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Transparent,
                    unfocusedTextColor = Color.Transparent
                ),
                modifier = Modifier
                    .size(1.dp) // invisible
                    .focusRequester(focusRequester) // link with focus requester
            )


            Spacer(modifier = Modifier.height(30.dp))

            // Verify Button
            Button(
                onClick = {
                    if (otpCode.length == 6) {
                        verifyStatus = VerifyStatus.LOADING
                        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                        auth.signInWithCredential(credential)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    verifyStatus = VerifyStatus.SUCCESS

                                } else {
                                    verifyStatus = VerifyStatus.ERROR
                                }
                            }
                    } else {
                        Toast.makeText(context, "Enter 6-digit OTP", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .padding(horizontal = 60.dp),
                shape = RoundedCornerShape(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
            ) {
                Text("Verify", fontSize = 18.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(15.dp))

            // Resend Section
            Row {
                Text(
                    "Didn’t receive a code? ",
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Text(
                    text = if (timer > 0) "Resend($timer)" else "Resend",
                    color = if (timer > 0) Color.Gray else Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clickable(enabled = timer == 0) {
                            FirebaseAuthHelper.sendOtp(
                                phoneNumber = phoneNumber,
                                context = context,
                                activity = context as Activity,
                                forceResend = true,
                                onCodeSent = { _ ->
                                    Toast.makeText(context, "OTP resent successfully!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            // 🔹 Resend ke baad timer reset
                            isRunning = true
                        }
                )
            }
        }

        // 🔹 Overlay Animation (same style as sending OTP screen)
        if (verifyStatus != VerifyStatus.NORMAL) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.size(200.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (verifyStatus) {
                            VerifyStatus.LOADING -> {
                                LottieAnimation(loadingAnim, iterations = LottieConstants.IterateForever, modifier = Modifier.size(100.dp))
                                Text("Verifying...", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                            }
                            VerifyStatus.SUCCESS -> {
                                LottieAnimation(successAnim, iterations = 1, modifier = Modifier.size(100.dp))
                                Text("Success!", fontSize = 16.sp, color = Color.Green, fontWeight = FontWeight.Bold)

                                LaunchedEffect(Unit) {
                                    delay(1000) // 1 sec ruk kar
                                    onOtpVerified()
                                }
                            }
                            VerifyStatus.ERROR -> {
                                LottieAnimation(errorAnim, iterations = 1, modifier = Modifier.size(100.dp))
                                Text("Invalid OTP", fontSize = 16.sp, color = Color.Red, fontWeight = FontWeight.Bold)

                                Button(
                                    onClick = {
                                        // 👇 Reset state so user can re-enter number
                                        otpCode = ""
                                        verifyStatus = VerifyStatus.NORMAL
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .padding(horizontal = 32.dp)
                                        .height(45.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text("Retry", color = Color.White, fontSize = 16.sp)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewOtpScreen() {
    // Preview ke liye safe dummy UI call
    OtpVerificationScreenPreview()
}

@Composable
fun OtpVerificationScreenPreview() {
    // Bas UI show karna hai, Firebase/Context related cheezen hata di
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(90.dp)
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

            Text("Verification Code", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Enter the verification code sent to +92 3001234567", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(30.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2C2C2E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = (it + 1).toString(), color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(45.dp).padding(horizontal = 90.dp),
                shape = RoundedCornerShape(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
            ) {
                Text("Verify", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}