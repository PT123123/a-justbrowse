package com.justbrowse.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 自家视频播放器：播放嗅探/拦截到的视频直链，全程不经过网页播放器。
 *
 * 交互（对应产品要求）：
 *  - **默认先进悬浮小窗（PiP）**：进入画面平移为悬浮窗播放，边看边浏览；
 *  - PiP 窗口带一个**全屏按钮**（RemoteAction），点击退出小窗进全屏；
 *  - 显示**片名**（顶部栏）与**封面**（首帧前占位）；
 *  - 支持 HLS/DASH/mp4/webm（media3-exoplayer-hls/dash）。
 */
class VideoPlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "video_url"
        private const val EXTRA_TITLE = "video_title"
        private const val EXTRA_POSTER = "video_poster"

        private const val ACTION_FULLSCREEN = "com.justbrowse.FULLSCREEN"
        private const val REQ_FULLSCREEN = 9001

        fun intent(context: Context, url: String, title: String = "", poster: String? = null): Intent =
            Intent(context, VideoPlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_POSTER, poster)
    }

    private var player: ExoPlayer? = null
    private var posterView: ImageView? = null
    private var nameBar: LinearLayout? = null

    private var isPip = false
    private var pendingFullscreen = false
    private var videoW = 0
    private var videoH = 0
    private var pipEnterRequested = false
    private val handler = Handler(Looper.getMainLooper())

    private val pipEnterRunnable = Runnable {
        if (!pipEnterRequested && !isFinishing) enterPip()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val poster = intent.getStringExtra(EXTRA_POSTER)

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTitle(title)

        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoW = videoSize.width
                        videoH = videoSize.height
                    }
                }
                override fun onRenderedFirstFrame() {
                    // 视频首帧出来，撤掉封面占位
                    posterView?.visibility = View.GONE
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) posterView?.visibility = View.GONE
                }
                override fun onPlayerError(error: PlaybackException) {
                    // 播放失败不崩溃：停在错误态，用户可返回重试/换源
                }
            })
        }
        player = exoPlayer

        val playerView = PlayerView(this).apply {
            player = exoPlayer
            useController = true
            controllerAutoShow = true
            setBackgroundColor(Color.BLACK)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        posterView = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = if (poster.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        nameBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            visibility = View.GONE
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad / 2, pad / 2)
            addView(
                TextView(context).apply {
                    text = title.ifBlank { "\u00A0" }
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(
                ImageButton(context).apply {
                    setImageDrawable(getDrawable(R.drawable.ic_fullscreen_exit))
                    background = null
                    setOnClickListener { enterPip() }
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                }
            )
            addView(
                ImageButton(context).apply {
                    setImageDrawable(getDrawable(R.drawable.ic_close))
                    background = null
                    setOnClickListener { finish() }
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                }
            )
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                playerView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                posterView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                nameBar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP
                )
            )
        }
        setContentView(root)

        if (!poster.isNullOrEmpty()) {
            loadPoster(poster)
        }

        // 进入后先平移成悬浮小窗（PiP）；若系统不支持 PiP 则保持全屏。
        pipEnterRequested = false
        handler.postDelayed(pipEnterRunnable, 500)
    }

    override fun onStart() {
        super.onStart()
        player?.play()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ACTION_FULLSCREEN == intent.action) {
            if (isInPictureInPictureMode) {
                pendingFullscreen = true // 等离开 PiP 的回调落地
            } else {
                enterFullscreen()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户切走（尤其全屏时按 Home）：自动收成悬浮小窗保持播放
        if (!isInPictureInPictureMode) enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPip = isInPictureInPictureMode
        pipEnterRequested = true
        if (isInPictureInPictureMode) {
            // 小窗里不显示片名栏（放不下），交给系统窗口
            nameBar?.visibility = View.GONE
            leaveFullscreen()
        } else if (pendingFullscreen) {
            pendingFullscreen = false
            enterFullscreen()
        } else {
            // 用户点了 PiP 窗口本体展开：恢复可操作态（显示片名栏）
            nameBar?.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        // 处于 PiP 时不要停播
        if (!isPip) player?.pause()
    }

    override fun onStop() {
        super.onStop()
        if (!isPip) player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pipEnterRunnable)
        player?.release()
        player = null
        super.onDestroy()
    }

    // ===== PiP =====

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isInPictureInPictureMode) return
        val aspect = if (videoW > 0 && videoH > 0) {
            Rational(videoW, videoH)
        } else {
            Rational(16, 9)
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(listOf(fullscreenAction()))
            .build()
        setPictureInPictureParams(params)
        try {
            enterPictureInPictureMode(params)
        } catch (_: Exception) {
            // 设备不支持 PiP 时保持全屏即可
        }
    }

    private fun fullscreenAction(): RemoteAction = RemoteAction(
        Icon.createWithResource(this, R.drawable.ic_fullscreen),
        "全屏",
        "进入全屏播放",
        PendingIntent.getActivity(
            this,
            REQ_FULLSCREEN,
            Intent(this, VideoPlayerActivity::class.java)
                .setAction(ACTION_FULLSCREEN)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )

    // ===== 全屏切换 =====

    private fun enterFullscreen() {
        nameBar?.visibility = View.VISIBLE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        player?.play()
    }

    private fun leaveFullscreen() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ===== 封面 =====

    private fun loadPoster(src: String) {
        val uri = try {
            Uri.parse(src)
        } catch (_: Exception) { null } ?: return
        if (uri.scheme !in setOf("http", "https")) return
        Thread {
            try {
                val conn = java.net.URL(src).openConnection().apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val bmp = android.graphics.BitmapFactory.decodeStream(conn.getInputStream())
                if (bmp != null) {
                    runOnUiThread {
                        if (!isFinishing && player?.playbackState != Player.STATE_READY) {
                            posterView?.setImageBitmap(bmp)
                            posterView?.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}