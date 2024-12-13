package com.example.spootify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.spootify.ui.theme.SpootifyTheme
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import android.util.Log;
import com.google.zxing.integration.android.IntentIntegrator
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.foundation.clickable
import com.journeyapps.barcodescanner.CaptureActivity
import androidx.compose.ui.Alignment // For alignment like Alignment.Center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource // To load drawable resources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableStateOf


class MainActivity : ComponentActivity() {

    private val clientId = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectUri = "http://localhost:3000"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var theUri: String? = null
    private var isPlaying = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppOutline(isPlaying = isPlaying.value)
        }
    }

    @Composable
    fun AppOutline(isPlaying: Boolean) {
        SpootifyTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {

                    // Background Image
                    Image(
                        painter = painterResource(id = R.drawable.bg10),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.FillBounds // Stretch the height; width will follow
                    )

                    // Icon displaying whether a song is playing
                    val iconResId = if (isPlaying) R.drawable.pause_btn else R.drawable.play_btn

                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = "Play/Pause Icon",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 40.dp)
                            .size(120.dp)
                            .clickable(onClick = {
                                onPlayPauseClicked()
                            })
                    )

                    // Button to scan new QR code
                    Button(
                        onClick = {
                            checkCameraPermission()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(80.dp)
                            .height(80.dp)
                            .width(200.dp)
                            .border(
                                5.dp, Color.Black, RoundedCornerShape(38.dp)
                            ),
                        shape = RoundedCornerShape(38.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF00BF63),
                            contentColor = Color.Black
                        )
                    )
                    {
                        Text(
                            text = "SCAN A QR CODE",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    private fun onPlayPauseClicked() {

        if (!this.isPlaying.value) {
            if (this.theUri != null) {
                this.spotifyAppRemote!!.playerApi.play(this.theUri)
                this.isPlaying.value = true
            }
        } else {
            this.spotifyAppRemote!!.playerApi.pause()
            this.isPlaying.value = false
        }
    }

    override fun onStart() {
        super.onStart()

        // Set the connection parameters
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
            }
        })
    }

    private fun connected() {
        // Play a song
        spotifyAppRemote?.let {
            if (this.theUri is String) {
                it.playerApi.play(this.theUri)
                this.theUri = null
                this.isPlaying.value = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }

    private fun startQrScanner() {

        // Stop playback when scanning
        if (this.spotifyAppRemote != null) {
            this.spotifyAppRemote!!.playerApi.pause()
            this.isPlaying.value = false
        }

        // Start scanner
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Scan a QR Code")
            setCameraId(0)
            setBeepEnabled(false)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true) // Lock orientation to portrait
            captureActivity = CustomCaptureActivity::class.java // Use the custom capture activity
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Unable to get song from this QR, is it a Spotify link?", Toast.LENGTH_LONG).show()
                Log.d("MainActivity", "Scanned QR Code: ${result.contents}")

            } else {
                Log.d("MainActivity", "Scanned QR Code: ${result.contents}")

                val uri = this.parseUrl(result.contents)
                Log.d("MainActivity", "URI: $uri")
                this.theUri = uri
            }
        }
    }

    // Parsing url
    private fun parseUrl(url: String): String {

        val prefix = "track/"
        val startIndex = url.indexOf(prefix) + prefix.length
        val endIndex = url.indexOf("?", startIndex).takeIf { it != -1 } ?: url.length
        val trackId = url.substring(startIndex, endIndex)

        return "spotify:track:$trackId"
    }


    // Permissions
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            startQrScanner()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startQrScanner()
        } else {
            Toast.makeText(
                this,
                "Camera permission is required to scan QR codes",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
}


class CustomCaptureActivity : CaptureActivity()