
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var recording by mutableStateOf(false)
    private var status by mutableStateOf("Ready to record")
    private var lastFile by mutableStateOf<String?>(null)
    private var recordings by mutableStateOf<List<File>>(emptyList())

    private var playingPath by mutableStateOf<String?>(null)
    private var playbackPosition by mutableIntStateOf(0)
    private var playbackDuration by mutableIntStateOf(0)

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
                    playbackPosition = playbackPosition,
                    playbackDuration = playbackDuration,
                    onToggleRecording = {
                        if (recording) {
                            stopRecording()
                        } else {
                            requestRecordingPermission()
                        }
                    },
                    onPlayRecording = { file ->
                        playRecording(file)
                    },
                    onPlaybackTick = {
                        updatePlaybackPosition()
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

            player.setOnPreparedListener {
                playbackDuration = it.duration
                playbackPosition = 0
                playingPath = file.absolutePath
                status = "Playing ${file.name}"
                it.start()
            }

            player.setOnCompletionListener {
                if (mediaPlayer === it) {
                    mediaPlayer = null
                    playingPath = null
                    playbackPosition = 0
                    it.release()
                }
            }

            player.setOnErrorListener { mp, _, _ ->
                if (mediaPlayer === mp) {
                    mediaPlayer = null
                    playingPath = null
                }
                mp.release()
                playbackPosition = 0
                status = "Could not play recording"
                true
            }

            playingPath = file.absolutePath
            player.prepareAsync()
        } catch (e: Exception) {
            stopPlayback()
            status = "Playback failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun updatePlaybackPosition() {
        val player = mediaPlayer ?: return

        try {
            if (player.isPlaying) {
                playbackPosition = player.currentPosition
            }
        } catch (_: IllegalStateException) {
            // Player may be preparing or already released.
        }
    }

    private fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        playingPath = null
        playbackPosition = 0
        playbackDuration = 0

        try {
            player?.stop()
        } catch (_: Exception) {
            // Player may not have started yet.
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
    playbackPosition: Int,
    playbackDuration: Int,
    onToggleRecording: () -> Unit,
    onPlayRecording: (File) -> Unit,
    onPlaybackTick: () -> Unit
) {
    LaunchedEffect(playingPath) {
        while (playingPath != null) {
            onPlaybackTick()
            delay(250)
        }
    }

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
                            playbackPosition = playbackPosition,
                            playbackDuration = playbackDuration,
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
    playbackPosition: Int,
    playbackDuration: Int,
    onPlay: () -> Unit
) {
    val date = remember(file.lastModified()) {
        SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.getDefault()
        ).format(Date(file.lastModified()))
    }

    val sizeKb = file.length() / 1024.0

    val durationMs = remember(file.lastModified(), file.length()) {
        readWavDurationMs(file)
    }

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

            Text(
                text = "$date  •  ${"%.1f".format(Locale.US, sizeKb)} KB",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Duration: ${formatDuration(durationMs)}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (isPlaying && playbackDuration > 0) {
                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = {
                        (playbackPosition.toFloat() / playbackDuration)
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${formatDuration(playbackPosition)} / ${
                        formatDuration(playbackDuration)
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }

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

private fun readWavDurationMs(file: File): Int {
    return try {
        RandomAccessFile(file, "r").use { wav ->
            if (wav.length() < 44) return 0

            wav.seek(24)
            val sampleRate = readLittleEndianInt(wav)

            wav.seek(34)
            val bitsPerSample = readLittleEndianShort(wav)

            wav.seek(40)
            val dataSize = readLittleEndianInt(wav).toLong() and 0xffffffffL

            val bytesPerSecond =
                sampleRate.toLong() * (bitsPerSample / 8)

            if (bytesPerSecond <= 0L) {
                0
            } else {
                (dataSize * 1000L / bytesPerSecond)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    } catch (_: Exception) {
        0
    }
}

private fun readLittleEndianInt(file: RandomAccessFile): Int {
    val b0 = file.read()
    val b1 = file.read()
    val b2 = file.read()
    val b3 = file.read()

    if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
        throw java.io.EOFException()
    }

    return b0 or
        (b1 shl 8) or
        (b2 shl 16) or
        (b3 shl 24)
}

private fun readLittleEndianShort(file: RandomAccessFile): Int {
    val b0 = file.read()
    val b1 = file.read()

    if (b0 < 0 || b1 < 0) {
        throw java.io.EOFException()
    }

    return b0 or (b1 shl 8)
}

private fun formatDuration(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d".format(Locale.US, minutes, seconds)
}
