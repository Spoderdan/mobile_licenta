package com.example.mobile_licenta

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Base64.DEFAULT
import android.util.Base64.decode
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.internal.ContextUtils.getActivity
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.File
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity() {
    lateinit var btn: Button
    lateinit var txt: TextView

    lateinit var mqttClient: MqttAndroidClient
    var isConnected = false

    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = MediaRecorder()
    var state = false

    var prev = ""
    var next = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val response = findViewById<TextView>(R.id.response)
        connect(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            val cw = ContextWrapper(this)
            output = cw.getExternalFilesDir(Environment.DIRECTORY_MUSIC).toString() + "/recording.mp3"
        } else {
            output = Environment.getExternalStorageDirectory().absolutePath + "/recording.mp3"
        }
        val buttonStartRecording = findViewById<Button>(R.id.record)
        buttonStartRecording.setOnClickListener {
            println(ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED)
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                ActivityCompat.requestPermissions(this, permissions, 0)
            } else {
                startRecording()
            }
        }

        val buttonStopRecording = findViewById<Button>(R.id.stop)
        buttonStopRecording.setOnClickListener {
            stopRecording()
            val task = GetTextFromSpeech()
            task.execute(output)
            val resp = task.get()

            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            val dialogLayout = inflater.inflate(R.layout.edit_text_layout, null)
            val editText = dialogLayout.findViewById<EditText>(R.id.et_editText)
            editText.setText(resp)
            with(builder) {
                setTitle("Is this correct?")
                setPositiveButton("OK"){dialog, which ->
                    val task = GetQuestion()
                    task.execute("https://api-licenta-anghel-dan.herokuapp.com/question?answer=${editText.text}&next=${next}&prev=${prev}")
                    val resp = task.get()
                    response.text = resp.get("question") as CharSequence?
                    next = resp.get("next") as Int
                    prev = resp.get("question") as String
                    if (isConnected) {
                        publish("zbos/dialog/set/message", resp.get("question").toString())
                    }
                }
                setView(dialogLayout)
                show()
            }
        }

        btn = findViewById(R.id.stt)
        txt = findViewById(R.id.textStt)
        btn.setOnClickListener {
            speechToText()
        }
    }

    private fun decodeImage(base64String: String) {
        val imageBytes = decode(base64String, DEFAULT)
        val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        //findViewById<ImageView>(R.id.imageView).setImageBitmap(decodedImage)
    }

    private fun startRecording() {
        try {
            mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder?.setAudioChannels(1);
            mediaRecorder?.setAudioEncodingBitRate(128000);
            mediaRecorder?.setAudioSamplingRate(48000);
            mediaRecorder?.setOutputFile(output)
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            state = true
            Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        if (state) {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
        } else {
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speechToText() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Something")
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            val filename = "logcat_" + System.currentTimeMillis() + ".txt"
            val output = File(filename)
            Runtime.getRuntime().exec("logcat -f " + output.absolutePath)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 || data != null) {
            if (data != null) {
                val res: ArrayList<String> =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>
                //txt.text = res[0]
                val task = GetQuestion()
                task.execute("https://api-licenta-anghel-dan.herokuapp.com/question?answer=${res[0]}&next=${next}&prev=${prev}")
                val resp = task.get()
                txt.text = resp.get("question") as CharSequence?
                next = resp.get("next") as Int
                prev = resp.get("question") as String
                if (isConnected) {
                    publish("zbos/dialog/set/message", resp.get("question").toString())
                }
            }
        }
    }

    private fun connect(context: Context) {
        val serverURI = "tcp://192.168.0.244"
        mqttClient = MqttAndroidClient(context, serverURI, "kotlin_client")
        mqttClient.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAG, "Connection lost ${cause.toString()}")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })
        val options = MqttConnectOptions()
        try {
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnected = true
                    Log.d(TAG, "Connection success")
                    Toast.makeText(applicationContext, "Connected!", Toast.LENGTH_LONG).show()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Connection failure")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }

    }

    private fun publish(topic: String, msg: String, qos: Int = 1, retained: Boolean = false) {
        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            message.isRetained = retained
            mqttClient.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "$msg published to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAG, "Failed to publish $msg to $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}