package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import java.io.File
import java.io.IOException
import java.io.FileNotFoundException
import kotlin.collections.mutableSetOf
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.FileReader
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL_ID = "NewStoryChannel"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationToggleButton: ToggleButton
    private lateinit var thresholdEditText: EditText

    var notificationsEnabled = false
    var threshold = 250

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationToggleButton = findViewById(R.id.notificationToggleButton)
        thresholdEditText = findViewById(R.id.thresholdEditText)
        threshold = readThresholdState()
        thresholdEditText.setText(threshold.toString())

        // Set a text change listener to update the threshold variable
        thresholdEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val inputText = s.toString()
                threshold = if (inputText.isNotBlank()) {
                    inputText.toIntOrNull() ?: 250
                } else {
                    250
                }
                // Save the threshold value to shared preferences
                saveThresholdState(threshold)
            }
        })

        // Read the notification state from SharedPreferences and set the toggle button accordingly
        notificationsEnabled = readNotificationState()
        notificationToggleButton.isChecked = notificationsEnabled

        notificationToggleButton.setOnClickListener {
            // Update the notificationsEnabled variable and save the state to SharedPreferences
            notificationsEnabled = notificationToggleButton.isChecked
            saveNotificationState(notificationsEnabled)
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val existingIdsFilename = "ids.txt"
        val existingDataFilename = "data.txt"
        var existingIds = readIdsFromFile(existingIdsFilename)
        var existingData = readDataFromFile(existingDataFilename)

        recyclerView.adapter = CardAdapter(existingData)

        createNotificationChannel()

        CoroutineScope(Dispatchers.Default).launch {

            var newIds = mutableSetOf<Int>()

            while (true) {

                try {
                    // Uncomment the following line to fetch new stories
                    // val newStories = fetchNewStories()

                    // Use the following line to fetch top and best stories
                    val newStories = fetchTopAndBestStories()

                    if (newStories.isEmpty()) {
                        Log.d(TAG, "No new stories found.")
                    } else {
                        newIds = (newStories - existingIds).toMutableSet()

                        if (newIds.isEmpty()) {
                            Log.d(TAG, "No new stories.")
                        }

                        val fetchedIds = mutableSetOf<Int>()
                        val fetchedData = mutableListOf<Map<String, Any>>()
                        // Fetch details for new IDs and send notifications
                        for (storyId in newIds) {
                            val storyData = fetchStoryDetails(storyId, threshold)
                            if (storyData != null) {
                                if (notificationsEnabled)
                                    showNotification(storyData)
                                printStoryDetails(storyData)
                                fetchedIds.add(storyId)
                                fetchedData.add(storyData)
                            }
                        }

                        existingIds.addAll(fetchedIds)

                        // these lists are disjoint, we won't have duplicates
                        existingData = existingData + fetchedData

                        val intersectionResult = existingIds.toMutableSet() // Create a copy of set1
                        intersectionResult.retainAll(newStories) // Calculate the intersection
                        existingIds = intersectionResult

                        var filteredData = existingData.filter { storyData ->
                            val id = storyData["id"] as? Int
                            id != null && existingIds.contains(id)
                        }

                        // Write the updated existing_ids to the file
                        writeIdsToFile(existingIds, existingIdsFilename)

                        // Write updated data to the file
                        writeDataToFile(filteredData, existingDataFilename)

                        filteredData = filteredData.reversed()

                        // Update the RecyclerView with the filtered data
                        updateRecyclerView(filteredData)

                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurred: ${e.message}")
                }

                delay(60000) // Wait for 1 minute before the next check
            }
        }
    }
    // API call to fetch top and best stories
    private fun fetchTopAndBestStories(): MutableSet<Int> {
        val urls = listOf(
            "https://hacker-news.firebaseio.com/v0/beststories.json",
            "https://hacker-news.firebaseio.com/v0/topstories.json"
        )
        val result = mutableSetOf<Int>()
        for (url in urls) {
            val response = performGetRequest(url)
            if (response != null) {
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    result.add(jsonArray.getInt(i) as Int)
                }
            } else {
                Log.e(TAG, "Failed to fetch new stories. URL: $url")
            }
        }
        return result
    }

    // Perform HTTP GET request and return response as a String
    private fun performGetRequest(urlString: String): String? {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                return response.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error occurred during GET request: ${e.message}")
        } finally {
            reader?.close()
            connection?.disconnect()
        }
        return null
    }

    // Fetch story details using the story ID
    private fun fetchStoryDetails(storyId: Int, threshold: Int): Map<String, Any>? {
        val url = "https://hacker-news.firebaseio.com/v0/item/$storyId.json"
        val response = performGetRequest(url)
        if (response != null) {
            val storyData = JSONObject(response)
            val id = storyData.optInt("id", 0)
            val score = storyData.optInt("score", 0)
            val type = storyData.optString("type", "")
            val url = storyData.optString("url", "")
            if (score > threshold && type == "story" && url.isNotEmpty()) {
                return mapOf(
                    "id" to id,
                    "title" to storyData.optString("title", ""),
                    "url" to url,
                    "score" to score
                )
            }
        }
        return null
    }

    private fun printStoryDetails(storyData: Map<String, Any>?) {
        if (storyData != null) {
            val title = storyData["title"] as? String
            val url = storyData["url"] as? String
            val score = storyData["score"] as? Int

            if (title != null && url != null && score != null) {
                println("Title: $title\nURL: $url\nScore: $score")
            }
        }
    }

    // Function to show a notification
    private fun showNotification(storyData: Map<String, Any>) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(storyData["url"] as String))
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.campana)
            .setContentTitle(storyData["title"] as String)
            .setContentText(storyData["title"] as String)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Create a BigTextStyle and set it to the notification builder
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(storyData["title"] as String)
        builder.setStyle(bigTextStyle)

        // Issue the notification by calling notify()
        val notificationId = storyData["id"] as Int // You can use a unique id for each notification
        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "Notification Sent")
    }

    // Create a notification channel for Android 8.0 and above
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New Story Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Function to write IDs to a file
    private fun writeIdsToFile(ids: Set<Int>, filename: String) {
        val fileContent = ids.joinToString(separator = "\n")
        applicationContext.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(fileContent.toByteArray())
        }
    }

    private fun ensureFileExists(filename: String) {
        val file = File(applicationContext.filesDir, filename)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Error creating file: ${e.message}")
            }
        }
    }

    private fun readIdsFromFile(filename: String): MutableSet<Int> {
        ensureFileExists(filename)

        val ids = mutableSetOf<Int>()
        try {
            applicationContext.openFileInput(filename)?.use { stream ->
                stream.bufferedReader().forEachLine { line ->
                    line.trim().toIntOrNull()?.let { id ->
                        ids.add(id)
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: $filename")
        }
        return ids
    }

    private fun readDataFromFile(filename: String): List<Map<String, Any>> {
        ensureFileExists(filename)

        val stories = mutableListOf<Map<String, Any>>()

        try {
            applicationContext.openFileInput(filename)?.use { stream ->
                var storyData = mutableMapOf<String, Any>()

                stream.bufferedReader().forEachLine { line ->
                    when {
                        line.startsWith("Id:") -> storyData["id"] = line.substringAfter("Id: ").toIntOrNull() ?: 0
                        line.startsWith("Title: ") -> storyData["title"] = line.substringAfter("Title: ")
                        line.startsWith("URL: ") -> storyData["url"] = line.substringAfter("URL: ")
                        line.startsWith("Score: ") -> {
                            storyData["score"] = line.substringAfter("Score: ").toIntOrNull() ?: 0
                        }
                        line.isBlank() -> {
                            // Empty line indicates the end of an object
                            if (storyData.isNotEmpty()) {
                                stories.add(storyData.toMap())
                                storyData.clear()
                            }
                        }
                    }
                }

                // Check if there's any remaining data after the last object
                if (storyData.isNotEmpty()) {
                    stories.add(storyData.toMap())
                }
            }
        } catch (e: Exception) {
            // Handle any exceptions that may occur during file reading
            println("Error reading data from file: ${e.message}")
        }

        return stories
    }

    private fun writeDataToFile(stories: List<Map<String, Any>>, filename: String) {
        val fileContent = buildString {
            for (storyData in stories) {
                val id = storyData["id"] as Int
                val title = storyData["title"] as? String
                val url = storyData["url"] as? String
                val score = storyData["score"] as? Int

                if (title != null && url != null && score != null) {
                    append("Id: $id\nTitle: $title\nURL: $url\nScore: $score\n\n")
                }
            }
        }

        applicationContext.openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(fileContent.toByteArray())
        }
        Log.d("Data", "Data Written")
    }

    private fun readNotificationState(): Boolean {
        val sharedPrefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("notificationsEnabled", false)
    }

    private fun saveNotificationState(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putBoolean("notificationsEnabled", enabled)
        editor.apply()
    }

    private fun saveThresholdState(value: Int) {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("ThresholdValue", value)
        editor.apply()
    }

    private fun readThresholdState(): Int {
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("ThresholdValue", 50)
    }

    private fun updateRecyclerView(data: List<Map<String, Any>>) {
        runOnUiThread {
            recyclerView.adapter = CardAdapter(data)
        }
    }

    class CardAdapter(private val data: List<Map<String, Any>>) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

        class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
            val urlTextView: TextView = itemView.findViewById(R.id.urlTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_card_layout, parent, false)
            return CardViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {

            val storyData = data[position]
            val title = storyData["title"] as? String
            val url = storyData["url"] as? String

            // Set the data to the TextViews in the card layout
            holder.titleTextView.text = title
            holder.urlTextView.text = url

            // Set a click listener to handle item clicks
            holder.itemView.setOnClickListener {
                // Handle the click action here, e.g., redirect to the URL
                url?.let { urlString ->
                    val openURL = Intent(Intent.ACTION_VIEW)
                    openURL.data = Uri.parse(urlString)
                    it.context.startActivity(openURL)
                }
            }
        }
        override fun getItemCount(): Int {
            return data.size
        }
    }
}


