package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class StoryWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "NewStoryChannel"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            createNotificationChannel()

            val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val notificationsEnabled = prefs.getBoolean("notificationsEnabled", false)
            val threshold = prefs.getInt("ThresholdValue", 250)

            val (existingIds, existingStories) = StoryStore.load(context)

            val fetchedIds = existingIds.toMutableSet()
            val fetchedStories = existingStories.toMutableList()

            val newIds = fetchTopAndBestStories() - existingIds

            for (id in newIds) {
                val story = fetchStoryDetails(id, threshold) ?: continue

                // Avoid duplicates
                if (fetchedIds.add(story.id)) {
                    fetchedStories.add(story)

                    if (notificationsEnabled) {
                        showNotification(story)
                    }

                    StoryStore.save(
                        context,
                        fetchedIds,
                        fetchedStories.sortedByDescending { it.score }
                    )

                    notifyDataUpdated()

                    Log.d("StoryWorker", "Saved story ${story.id}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }


    // --- Network ---
    private fun fetchTopAndBestStories(): Set<Int> {
        val urls = listOf(
            "https://hacker-news.firebaseio.com/v0/beststories.json",
            "https://hacker-news.firebaseio.com/v0/topstories.json"
        )

        val result = mutableSetOf<Int>()
        urls.forEach {
            val json = performGet(it) ?: return@forEach
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) result.add(arr.getInt(i))
        }
        return result
    }

    private fun fetchStoryDetails(id: Int, threshold: Int): Story? {
        val json = performGet("https://hacker-news.firebaseio.com/v0/item/$id.json")
            ?: return null

        val obj = JSONObject(json)
        val score = obj.optInt("score", 0)

        if (score <= threshold || obj.optString("type") != "story") return null

        return Story(
            id = id,
            title = obj.optString("title"),
            url = obj.optString("url"),
            score = score
        )
    }

    private fun performGet(urlString: String): String? {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // --- Notifications ---
    private fun showNotification(story: Story) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(story.url))
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.campana)
            .setContentTitle(story.title)
            .setContentText("[${story.score}] ${story.title}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(story.id, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New Stories",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun notifyDataUpdated() {
        val intent = Intent("STORIES_UPDATED")
        context.sendBroadcast(intent)
    }
}
