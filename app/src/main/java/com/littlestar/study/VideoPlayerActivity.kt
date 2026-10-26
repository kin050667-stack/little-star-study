package com.littlestar.study

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import java.io.File

/** Dedicated landscape player. Does not modify lesson data or imported files. */
class VideoPlayerActivity : Activity() {
    private var player: VideoView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        val path = intent.getStringExtra("path")
        if (path.isNullOrEmpty() || !File(path).isFile || !File(path).canonicalPath.startsWith(File(filesDir, "lessons").canonicalPath + File.separator)) {
            finish(); return
        }
        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = VideoView(this)
        player = video
        container.addView(video, FrameLayout.LayoutParams(-1, -1))
        val controller = MediaController(this)
        controller.setAnchorView(video)
        video.setMediaController(controller)
        val exit = Button(this).apply {
            text = "✕ 退出全屏"; textSize = 17f; isAllCaps = false
            setOnClickListener { finish() }
        }
        container.addView(exit, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        val title = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "学习视频"
            setTextColor(Color.WHITE); textSize = 17f
            setPadding(12, 8, 12, 8)
        }
        container.addView(title, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        video.setOnPreparedListener { video.start(); controller.show(2500) }
        video.setOnErrorListener { _, _, _ ->
            title.text = "视频无法播放，请检查格式"; true
        }
        video.setVideoPath(path)
        setContentView(container)
    }
    override fun onPause() { player?.pause(); super.onPause() }
    override fun onDestroy() { player?.stopPlayback(); player = null; super.onDestroy() }
}
