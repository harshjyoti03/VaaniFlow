
package com.harshjyoti.vaaniflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var recording by mutableStateOf(false)
    private var status by mutableStateOf("Ready to record")
    private var lastFile by mutableStateOf<String?>(null)
    private var recordings by mutableStateOf<List<File>>(emptyList())
    private var playingPath by mutableStateOf<String?>(null)

    private var mediaPlayer: MediaPlayer? = null

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
            if (intent?.action != RecordingService.ACTION_STATE) return

            recording = intent.getBooleanExtra(
                RecordingService.EXTRA_RECORDING,
                false
            )

            val path = intent.getStringExtra(RecordingService.EXTRA_FILE)

            if (path != null) {
                lastFile = path
            }

            status = when {
                recording -> "Recording in progress"
                path != null -> "Recording saved"
                else -> "Ready to record"
            }

            refreshRecordings()
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

        refreshRecordings()

        setContent {
            MaterialTheme {
                VaaniFlowScreen(
                    recording = recording,
                    status = status,
                    recordings = recordings,
                    playingPath = playingPath,
                    onToggleRecording = {
                        if (recording) {
                            stopRecording()
                        } else {
                            requestRecordingPermission()
                        }
                    },
                    onPlayRecording = { file ->
                        playRecording(file)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRecordings()
    }

    private fun refreshRecordings() {
        val directory = File(
            getExternalFilesDir(null) ?: filesDir,
            "recordings"
        )

        recordings = directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun requestRecordingPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    private fun playRecording(file: File) {
        if (playingPath == file.absolutePath) {
            stopPlayback()
            return
        }

        stopPlayback()

        try {
            val player = MediaPlayer()
            mediaPlayer = player

            player.setDataSource(file.absolutePath)

            player.setOnCompletionListener {
                if (mediaPlayer === it) {
                    mediaPlayer = null
                    playingPath = null
                    it.release()
                }
            }

            player.setOnErrorListener { mp, _, _ ->
                if (mediaPlayer === mp) {
                    mediaPlayer = null
                    playingPath = null
                }
                mp.release()
                status = "Could not play recording"
                true
            }

            player.setOnPreparedListener {
                it.start()
                playingPath = file.absolutePath
                status = "Playing ${file.name}"
            }

            playingPath = file.absolutePath
            player.prepareAsync()
        } catch (e: Exception) {
            stopPlayback()
            status = "Playback failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        playingPath = null

        try {
            player?.stop()
        } catch (_: Exception) {
            // The player may not have started yet.
        }

        player?.release()
    }

    override fun onDestroy() {
        stopPlayback()
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }
}

@Composable
fun VaaniFlowScreen(
    recording: Boolean,
    status: String,
    recordings: List<File>,
    playingPath: String?,
    onToggleRecording: () -> Unit,
    onPlayRecording: (File) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "VaaniFlow",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Your multilingual conversation assistant",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onToggleRecording,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (recording) "Stop recording"
                            else "Start recording"
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Recordings",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "${recordings.size} files",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))

            if (recordings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No recordings yet",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Your saved recordings will appear here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = recordings,
                        key = { it.absolutePath }
                    ) { file ->
                        RecordingCard(
                            file = file,
                            isPlaying = playingPath == file.absolutePath,
                            onPlay = { onPlayRecording(file) }
                        )
                    }
                }
            }

            Text(
                text = "Recordings are stored privately in app storage.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun RecordingCard(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(4.dp))

            val date = remember(file.lastModified()) {
                SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()
                ).format(Date(file.lastModified()))
            }

            val sizeKb = file.length() / 1024.0

            Text(
                text = "$date  •  ${"%.1f".format(Locale.US, sizeKb)} KB",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isPlaying) "Stop playback" else "Play recording")
            }
        }
    }
}
