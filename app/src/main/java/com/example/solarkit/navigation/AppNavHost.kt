package com.example.solarkit.navigation

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.solarkit.CameraScreen.CameraScreen
import com.example.solarkit.HomeScreen.HomeScreen
import com.example.solarkit.OtpScreen.OtpScreen
import com.example.solarkit.OtpScreen.OtpVerificationScreen
import com.example.solarkit.Permission.PermissionRequestScreen

@Composable
fun AppNavHost(activity: Activity) {
    val navController = rememberNavController()
    val sharedPref = activity.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    val isLoggedIn = sharedPref.getBoolean("isLoggedIn", false)

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "permission_screen" else "otp_screen"
    ) {
        // OTP screen
        composable("otp_screen") {
            OtpScreen(
                onOtpSent = { phoneNumber, verificationId ->
                    navController.navigate("otp_verification_screen/$phoneNumber/$verificationId")
                }
            )
        }

        // OTP verification
        composable(
            "otp_verification_screen/{phoneNumber}/{verificationId}",
            arguments = listOf(
                navArgument("phoneNumber") { type = NavType.StringType },
                navArgument("verificationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            val verificationId = backStackEntry.arguments?.getString("verificationId") ?: ""
            OtpVerificationScreen(
                phoneNumber = phoneNumber,
                verificationId = verificationId,
                onOtpVerified = {
                    sharedPref.edit().putBoolean("isLoggedIn", true).apply()
                    navController.navigate("permission_screen") {
                        popUpTo("otp_screen") { inclusive = true }
                    }
                }
            )
        }

        // Permissions check screen
        composable("permission_screen") {
            PermissionRequestScreen(
                onPermissionsGranted = {
                    navController.navigate("home") {
                        popUpTo("permission_screen") { inclusive = true }
                    }
                },
                onCancel = {
                    Toast.makeText(activity, "Permissions required to use the app", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Home screen
        composable("home") {
            HomeScreen(navController = navController)
        }

        // Permissions check for Camera
        composable("camera_screen") {
            PermissionRequestScreen(
                onPermissionsGranted = {
                    navController.navigate("camera_screen") {
                        popUpTo("camera_screen") { inclusive = true }
                    }
                },
                onCancel = {
                    Toast.makeText(activity, "Camera permissions are required!", Toast.LENGTH_SHORT).show()
                }
            )
        }

// Actual Camera Screen UI (only called if permissions granted)
        composable("camera_screen") {
            CameraScreen(onBack = { navController.popBackStack() })
        }
    }
}
