
package com.harshjyoti.vaaniflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.harshjyoti.vaaniflow.START"
        const val ACTION_STOP = "com.harshjyoti.vaaniflow.STOP"
        const val ACTION_STATUS = "com.harshjyoti.vaaniflow.STATUS"
        const val ACTION_STATE = "com.harshjyoti.vaaniflow.STATE"

        const val EXTRA_RECORDING = "recording"
        const val EXTRA_FILE = "file"

        private const val CHANNEL_ID = "vaaniflow_recording"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = 16000
    }

    private val running = AtomicBoolean(false)

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var outputPath: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
            ACTION_STATUS -> broadcastState(running.get())
        }

        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (running.get()) {
            broadcastState(true)
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            check(bufferSize > 0) {
                "Unsupported microphone configuration"
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(bufferSize, SAMPLE_RATE)
            )

            check(
                audioRecord.state == AudioRecord.STATE_INITIALIZED
            ) {
                "AudioRecord initialization failed"
            }

            val directory = File(
                getExternalFilesDir(null) ?: filesDir,
                "recordings"
            )

            check(directory.exists() || directory.mkdirs()) {
                "Cannot create recordings directory"
            }

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

            val file = File(directory, "vaaniflow_$timestamp.wav")

            RandomAccessFile(file, "rw").use {
                it.setLength(0)
                it.write(ByteArray(44))
            }

            recorder = audioRecord
            outputPath = file.absolutePath
            running.set(true)

            audioRecord.startRecording()
            broadcastState(true, file.absolutePath)

            worker = Thread {
                Process.setThreadPriority(
                    Process.THREAD_PRIORITY_AUDIO
                )

                try {
                    RandomAccessFile(file, "rw").use { output ->
                        output.seek(44)

                        val buffer = ByteArray(bufferSize)
                        var totalBytes = 0L

                        while (running.get()) {
                            val count = audioRecord.read(
                                buffer,
                                0,
                                buffer.size,
                                AudioRecord.READ_BLOCKING
                            )

                            if (count > 0) {
                                output.write(buffer, 0, count)
                                totalBytes += count
                            }
                        }

                        writeWavHeader(output, totalBytes)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "VaaniFlow",
                        "Recording write failed",
                        e
                    )
                }
            }.also {
                it.start()
            }

        } catch (e: Exception) {
            android.util.Log.e(
                "VaaniFlow",
                "Could not start recording",
                e
            )

            running.set(false)
            recorder?.release()
            recorder = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            broadcastState(false)
        }
    }

    private fun stopRecording() {
        running.set(false)

        try {
            recorder?.stop()
        } catch (e: Exception) {
            android.util.Log.w(
                "VaaniFlow",
                "Recorder already stopped",
                e
            )
        }

        try {
            worker?.join(2000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        recorder?.release()
        recorder = null
        worker = null

        broadcastState(false, outputPath)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun writeWavHeader(
        file: RandomAccessFile,
        audioBytes: Long
    ) {
        file.seek(0)

        val byteRate = SAMPLE_RATE * 2

        file.writeBytes("RIFF")
        writeIntLE(file, (36 + audioBytes).toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        writeIntLE(file, 16)
        writeShortLE(file, 1)
        writeShortLE(file, 1)
        writeIntLE(file, SAMPLE_RATE)
        writeIntLE(file, byteRate)
        writeShortLE(file, 2)
        writeShortLE(file, 16)
        file.writeBytes("data")
        writeIntLE(file, audioBytes.toInt())
    }

    private fun writeIntLE(
        file: RandomAccessFile,
        value: Int
    ) {
        file.write(value and 0xff)
        file.write((value shr 8) and 0xff)
        file.write((value shr 16) and 0xff)
        file.write((value shr 24) and 0xff)
    }

    private fun writeShortLE(
        file: RandomAccessFile,
        value: Int
    ) {
        file.write(value and 0xff)
        file.write((value shr 8) and 0xff)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VaaniFlow recording",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("VaaniFlow")
            .setContentText("Recording audio")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun broadcastState(
        recording: Boolean,
        path: String? = null
    ) {
        sendBroadcast(
            Intent(ACTION_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_RECORDING, recording)
                .putExtra(EXTRA_FILE, path)
        )
    }

    override fun onDestroy() {
        if (running.get()) {
            stopRecording()
        }
        super.onDestroy()
    }
}
