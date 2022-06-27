package com.example.mobile_licenta

import android.os.AsyncTask
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.IOException

class GetTextFromSpeech : AsyncTask<String?, Void?, String>() {
    @Throws(IOException::class)
    fun post(path: String): String? {
        val client = OkHttpClient()
        val postBodyAudio: RequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio", "recording.mp3",
                RequestBody.create("audio/mp3".toMediaTypeOrNull(), File(path))
            )
            .build()
        val request: Request = Request.Builder()
            .url("https://api-licenta-anghel-dan.herokuapp.com/uploadfile")
            .post(postBodyAudio)
            .build()
        client.newCall(request).execute().use { response -> return response.body?.string() }
    }

    override fun doInBackground(vararg params: String?): String? {
        return post(params[0].toString())
    }

    override fun onPostExecute(result: String?) {
        super.onPostExecute(result)
    }
}