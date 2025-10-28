package com.example.solarkit.HomeScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.solarkit.R

@Composable
fun HomeScreen(navController : NavController) {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 🔹 Background Image
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = "Background",
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        // 🔹 Title
        Text(
            text = "Welcome to SolarKit",
            color = colorResource(R.color.white),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = androidx.compose.ui.Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        )

        // 🔹 Simple Button
        Button(
            onClick = { navController.navigate("camera_screen") },
            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.red)),
            shape = RoundedCornerShape(30.dp),
            elevation = ButtonDefaults.buttonElevation(),
            modifier = androidx.compose.ui.Modifier
                .padding(horizontal = 60.dp)
                .height(60.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Open Camera",
                color = colorResource(R.color.white),
                fontSize = 20.sp
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeScreen() {
    HomeScreen(navController = TODO())
}
