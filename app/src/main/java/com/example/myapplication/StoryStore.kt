package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Story(
    val id: Int,
    val title: String,
    val url: String,
    val score: Int
)

object StoryStore {

    private const val FILE_NAME = "stories.json"

    fun load(context: Context): Pair<MutableSet<Int>, MutableList<Story>> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return mutableSetOf<Int>() to mutableListOf()
        }

        val json = JSONObject(file.readText())
        val ids = json.getJSONArray("ids")
        val stories = json.getJSONArray("stories")

        val idSet = mutableSetOf<Int>()
        for (i in 0 until ids.length()) {
            idSet.add(ids.getInt(i))
        }

        val storyList = mutableListOf<Story>()
        for (i in 0 until stories.length()) {
            val s = stories.getJSONObject(i)
            storyList.add(
                Story(
                    s.getInt("id"),
                    s.getString("title"),
                    s.getString("url"),
                    s.getInt("score")
                )
            )
        }

        return idSet to storyList
    }

    fun save(context: Context, ids: Set<Int>, stories: List<Story>) {
        val json = JSONObject()
        json.put("ids", JSONArray(ids.toList()))

        val array = JSONArray()
        stories.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("score", it.score)
            )
        }

        json.put("stories", array)

        File(context.filesDir, FILE_NAME).writeText(json.toString())
    }
}
