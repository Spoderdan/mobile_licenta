package com.example.mobile_licenta

import android.os.AsyncTask
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GetQuestion : AsyncTask<String?, Void?, JSONObject>() {
    override fun doInBackground(vararg params: String?): JSONObject {
        var result: JSONObject
        val mURL = URL(params[0])

        with(mURL.openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = StringBuffer()

                var inputLine = it.readLine()
                while (inputLine != null) {
                    response.append(inputLine)
                    inputLine = it.readLine()
                }
                it.close()
                result = JSONObject(response.toString())
            }
        }

        return result
    }

    override fun onPostExecute(result: JSONObject) {
        super.onPostExecute(result)
    }
}