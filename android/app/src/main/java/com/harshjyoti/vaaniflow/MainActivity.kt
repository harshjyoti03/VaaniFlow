
package com.harshjyoti.vaaniflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var recording by mutableStateOf(false)
    private var status by mutableStateOf("Ready to record")
    private var lastFile by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()
            } else {
                status = "Microphone permission denied"
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecordingService.ACTION_STATE) {
                return
            }

            recording = intent.getBooleanExtra(
                RecordingService.EXTRA_RECORDING,
                false
            )

            val path = intent.getStringExtra(
                RecordingService.EXTRA_FILE
            )

            if (path != null) {
                lastFile = path
            }

            status = if (recording) {
                "Recording in progress"
            } else if (lastFile != null) {
                "Recording saved"
            } else {
                "Ready to record"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(RecordingService.ACTION_STATE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                stateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }

        setContent {
            MaterialTheme {
                VaaniFlowScreen(
                    recording = recording,
                    status = status,
                    lastFile = lastFile,
                    onToggleRecording = {
                        if (recording) {
                            stopRecording()
                        } else {
                            requestRecordingPermission()
                        }
                    }
                )
            }
        }
    }

    private fun requestRecordingPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecording()
        } else {
            permissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startRecording() {
        val intent = Intent(
            this,
            RecordingService::class.java
        ).setAction(RecordingService.ACTION_START)

        ContextCompat.startForegroundService(this, intent)
        status = "Starting recorder..."
    }

    private fun stopRecording() {
        startService(
            Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_STOP)
        )
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }
}

@Composable
fun VaaniFlowScreen(
    recording: Boolean,
    status: String,
    lastFile: String?,
    onToggleRecording: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "VaaniFlow",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(8.dp))

            Text("Your multilingual conversation assistant")

            Spacer(Modifier.height(24.dp))

            Text(status)

            Spacer(Modifier.height(16.dp))

            Button(onClick = onToggleRecording) {
                Text(
                    if (recording) "Stop recording"
                    else "Start recording"
                )
            }

            if (lastFile != null) {
                Spacer(Modifier.height(16.dp))
                Text("Last recording:")
                Text(lastFile, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
