package dev.heyduk.relay.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records audio using AudioRecord API, producing 16kHz mono 16-bit PCM WAV files.
 * This format is native to whisper.cpp -- no transcoding needed.
 */
class AudioRecorder(private val cacheDir: File) {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var outputFile: File? = null

    /**
     * Start recording audio to a temp WAV file.
     * Returns immediately -- recording happens on a background thread.
     * Call stopRecording() to finalize and get the WAV file.
     */
    @SuppressLint("MissingPermission")  // Permission checked by caller
    suspend fun startRecording(): File = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        val file = File(cacheDir, "relay_recording_${System.currentTimeMillis()}.wav")
        outputFile = file

        // Write WAV header placeholder (44 bytes), will be finalized in stopRecording
        val fos = FileOutputStream(file)
        writeWavHeader(fos, 0)

        audioRecord = record
        isRecording = true
        record.startRecording()

        val buffer = ByteArray(bufferSize)
        var totalBytesWritten = 0L

        try {
            while (isRecording && isActive) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    fos.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                }
            }
        } finally {
            fos.close()
        }

        // Finalize WAV header with actual data size
        finalizeWavHeader(file, totalBytesWritten)

        Timber.i("Recording saved: ${file.name}, $totalBytesWritten bytes PCM data")
        file
    }

    /** Stop the current recording. The WAV file returned by startRecording() is now finalized. */
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    /** Get the output file path (available after startRecording returns). */
    fun getOutputFile(): File? = outputFile

    private fun writeWavHeader(out: FileOutputStream, dataSize: Long) {
        val totalSize = 36 + dataSize
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalSize.toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)           // PCM format chunk size
        header.putShort(1)          // PCM format
        header.putShort(1)          // Mono
        header.putInt(SAMPLE_RATE)  // Sample rate
        header.putInt(SAMPLE_RATE * 2) // Byte rate (16-bit mono)
        header.putShort(2)          // Block align
        header.putShort(16)         // Bits per sample
        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())
        out.write(header.array())
    }

    private fun finalizeWavHeader(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // Update RIFF chunk size at offset 4
            raf.seek(4)
            raf.write(intToLittleEndianBytes((36 + dataSize).toInt()))
            // Update data chunk size at offset 40
            raf.seek(40)
            raf.write(intToLittleEndianBytes(dataSize.toInt()))
        }
    }

    private fun intToLittleEndianBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
