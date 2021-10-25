package com.wish.videopath.demo2

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wish.videopath.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.lang.Exception


/**
 * 类名称：Demo2Activity
 * 类描述：
 *
 * 创建时间：2021/10/25
 */
class Demo2Activity : AppCompatActivity() {

    private lateinit var mRecord: Button
    private lateinit var mPlayView: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo2)
        mRecord = findViewById<Button>(R.id.record)
        mPlayView = findViewById<Button>(R.id.play)
        mRecord.setOnClickListener {
            //当前正在录音，停止录音
            if (isRecording) {
                isRecording = false
                mRecord.text = "录音完成"
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                record()
                return@setOnClickListener
            }

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    666
                )
            } else {
                record()
            }
        }
        mPlayView.setOnClickListener { play() }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 666 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
            record()
        } else {
            Toast.makeText(this, "请授予录音权限权限", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * 采样率，现在能够保证在所有设备上使用的采样率是44100Hz, 但是其他的采样率（22050, 16000, 11025）在一些设备上也可以使用。
     */
    private final val SAMPLE_RATE_INHZ = 44100

    /**
     * 声道数。CHANNEL_IN_MONO and CHANNEL_IN_STEREO. 其中CHANNEL_IN_MONO是可以保证在所有设备能够使用的。
     */
    private val CHANNEL_CONFIG: Int = AudioFormat.CHANNEL_IN_MONO

    /**
     * 返回的音频数据的格式。 ENCODING_PCM_8BIT, ENCODING_PCM_16BIT, and ENCODING_PCM_FLOAT.
     */
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    //缓冲区
    private val minBufferSize =
        AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT)

    private val PCM_NAME = "demo2.pcm"

    private var isRecording = false

    private var audioRecord: AudioRecord? = null

    private fun record() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_INHZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )

        val data = ByteArray(minBufferSize)
        val dirFile = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        dirFile?.mkdirs()

        val file = File(dirFile, PCM_NAME)

        audioRecord?.startRecording()
        isRecording = true
        mRecord.text = "正在录音"
        Thread {
            val os = FileOutputStream(file)
            try {
                while (isRecording) {
                    val read = audioRecord?.read(data, 0, minBufferSize)
                    //没有错误就写入文件
                    if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                        os.write(data)
                    }
                }
                os.close()
            } catch (e: IOException) {
                runOnUiThread {
                    Log.i("音视频", "录音异常：" + e.toString())
                    mRecord.text = "录音异常"
                    Toast.makeText(this, "录音异常", Toast.LENGTH_SHORT).show()
                }
            } finally {
                audioRecord?.stop()
                os.close()
            }
        }.start()
    }


    //播放音频
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun play() {
        playOfSteam()
//        playOfStatic()
    }


    private var audioTrack: AudioTrack? = null

    /**
     * static模式
     * 先一次性读取数据到内存中，再进行播放，读取时间会比较长
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playOfStatic() {
        GlobalScope.launch(Dispatchers.IO) {//开启协程运行在IO线程
            var input: InputStream? = null
            try {
                val audioFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), PCM_NAME)
                input = FileInputStream(audioFile)
                val out = ByteArrayOutputStream()
                var b: Int
                var audioData: ByteArray? = null
                while (input.read().also { b = it } != -1) {
                    out.write(b)
                    audioData = out.toByteArray()
                }
                //开启协程运行在主线程
                GlobalScope.launch(Dispatchers.Main) {
                    startStatic(audioData)
                }
            } catch (e: Exception) {
                GlobalScope.launch(Dispatchers.Main) {
                    Log.i("音视频", "播放异常：$e")
                    mPlayView.text = "播放异常"
                    Toast.makeText(this@Demo2Activity, "录音异常", Toast.LENGTH_SHORT).show()
                }
            } finally {
                input?.close()
            }
        }


    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startStatic(audioData: ByteArray?) {
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            audioData!!.size,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.write(audioData, 0, audioData.size)
        audioTrack?.play()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playOfSteam() {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val minBufferSize =
            AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, channelConfig, AUDIO_FORMAT)

        val audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(channelConfig)
                .build(),
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        //开始播放
        audioTrack.play()

        val audioFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), PCM_NAME)

        try {
            val fileInputStream = FileInputStream(audioFile)
            Thread {
                try {
                    val tempBuffer = ByteArray(minBufferSize)
                    while (fileInputStream.available() > 0) {
                        val readCount: Int = fileInputStream.read(tempBuffer)
                        //错误的字节再次循环
                        if (readCount == AudioTrack.ERROR_INVALID_OPERATION ||
                            readCount == AudioTrack.ERROR_BAD_VALUE
                        ) {
                            continue
                        }
                        if (readCount != 0 && readCount != -1) {
                            audioTrack.write(tempBuffer, 0, readCount)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Log.i("音视频", "录音播放异常：" + e.toString())
                Toast.makeText(this, "录音播放异常", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveWav(view: View) {
        val pcmFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), PCM_NAME)
        val wavFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "haha.wav")
        val wavUtil = PCMCovWavUtil(
            pcmFile,
            wavFile,
            SAMPLE_RATE_INHZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )
        wavUtil.convertWaveFile()
    }


    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.release()

        audioTrack?.release()
    }


}