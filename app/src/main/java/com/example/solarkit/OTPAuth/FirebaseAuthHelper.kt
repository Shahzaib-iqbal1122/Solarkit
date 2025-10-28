package com.example.solarkit.OTPAuth
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

object FirebaseAuthHelper {

    private var otpAlreadySent = false
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null // ✅ store token



    fun sendOtp(
        phoneNumber: String,
        context: Context,
        activity: Activity,
        forceResend: Boolean = false, // ✅ flag for resend
        onCodeSent: (verificationId: String) -> Unit,
        onVerificationFailed: (exception: FirebaseException) -> Unit= { e ->
            Toast.makeText(context, "OTP failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    ) {
        val auth = FirebaseAuth.getInstance()


        // ✅ reset flag only when user starts a new request
        otpAlreadySent = false

        val builder  = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    // Auto-verification (optional)
                    Toast.makeText(context, "Phone automatically verified", Toast.LENGTH_SHORT).show()
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    onVerificationFailed(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (!otpAlreadySent) {   // ✅ ab sirf ek dafa trigger hoga
                        otpAlreadySent = true
                        resendToken = token  // ✅ save token
//                        Toast.makeText(context, "OTP sent!", Toast.LENGTH_SHORT).show()+
                        onCodeSent(verificationId)
                    }
                }

            })
// 🔹 agar resend button dabaya gaya hai to token add karo
        if (forceResend && resendToken != null) {
            builder.setForceResendingToken(resendToken!!)
        }

        val options = builder.build()
        PhoneAuthProvider.verifyPhoneNumber(options )
    }
}