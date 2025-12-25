package com.example.myapplication

import StoryAdapter
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var thresholdEditText: EditText
    private lateinit var notificationToggle: ToggleButton
    private lateinit var storyAdapter: StoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupPreferencesUI()
        setupRecyclerView()

        requestNotificationPermissionIfNeeded()
        NotificationHelper.createChannel(this)

        scheduleStoryWorker()
        runWorkerNow()
        observeWorkers()
    }

    // ---------------- UI ----------------

    private fun setupPreferencesUI() {
        thresholdEditText = findViewById(R.id.thresholdEditText)

        val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val threshold = prefs.getInt("ThresholdValue", 250)
        thresholdEditText.setText(threshold.toString())

        thresholdEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: 250
                prefs.edit().putInt("ThresholdValue", value).apply()
            }
        })

        // 🔔 Notification toggle
        notificationToggle = findViewById(R.id.notificationToggleButton)
        notificationToggle.isChecked =
            prefs.getBoolean("notificationsEnabled", false)

        notificationToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean("notificationsEnabled", isChecked)
                .apply()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val (_, stories) = StoryStore.load(this)
        val sortedStories = stories.sortedByDescending { it.score }
        storyAdapter = StoryAdapter(sortedStories)
        recyclerView.adapter = storyAdapter
    }

    override fun onResume() {
        super.onResume()
        // Reload data in case worker ran in background
        val (_, stories) = StoryStore.load(this)
        recyclerView.adapter = StoryAdapter(stories)
    }

    // ---------------- Permissions ----------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    // ---------------- WorkManager ----------------

    private fun scheduleStoryWorker() {
        val work =
            PeriodicWorkRequestBuilder<StoryWorker>(1, TimeUnit.HOURS).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "story_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }
    private fun runWorkerNow() {
        val work = OneTimeWorkRequestBuilder<StoryWorker>().build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "story_worker_immediate",
                ExistingWorkPolicy.REPLACE,
                work
            )
    }

    private fun observeWorkers() {
        val workManager = WorkManager.getInstance(this)

        workManager.getWorkInfosForUniqueWorkLiveData("story_worker")
            .observe(this) { infos ->
                if (infos.any { it.state.isFinished }) {
                    reloadStories()
                }
            }

        workManager.getWorkInfosForUniqueWorkLiveData("story_worker_immediate")
            .observe(this) { infos ->
                if (infos.any { it.state.isFinished }) {
                    reloadStories()
                }
            }
    }
    private fun reloadStories() {
        val (_, stories) = StoryStore.load(this)
        storyAdapter.submitList(stories)
    }

    private val storyUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadStories()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            storyUpdateReceiver,
            IntentFilter("STORIES_UPDATED")
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(storyUpdateReceiver)
    }

}
