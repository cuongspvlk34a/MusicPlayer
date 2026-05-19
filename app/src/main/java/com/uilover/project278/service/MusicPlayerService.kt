package com.uilover.project278.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.uilover.project278.R
import com.uilover.project278.activities.MainActivity
import com.uilover.project278.activities.PlayerActivity

class MusicPlayerService : Service() {

    companion object {
        const val REPEAT_NONE = 0
        const val REPEAT_ONE  = 1
        const val REPEAT_ALL  = 2

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "music_playback_channel"
    }

    // [SỬA] repeatMode có custom setter — sync player.repeatMode natively khi đổi mode.
    // REPEAT_ONE dùng Player.REPEAT_MODE_ONE: ExoPlayer tự loop, STATE_ENDED không fire.
    // REPEAT_ALL và REPEAT_NONE dùng REPEAT_MODE_OFF, xử lý thủ công trong onPlaybackStateChanged.
    var repeatMode: Int = REPEAT_NONE
        set(value) {
            field = value
            _player?.repeatMode = if (value == REPEAT_ONE) Player.REPEAT_MODE_ONE
                                   else Player.REPEAT_MODE_OFF
        }

    var isShuffle: Boolean = false

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private var notificationManager: PlayerNotificationManager? = null

    // ── Binder ─────────────────────────────────────────────────────────────────

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent): IBinder = binder

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        buildPlayer()
        buildNotificationManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY  // Hệ thống restart service nếu bị kill

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swipe app khỏi recents → dừng hẳn
        stopSelf()
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        _player?.release()
        _player = null
        super.onDestroy()
    }

    // ── Player init ────────────────────────────────────────────────────────────

    private fun buildPlayer() {
        _player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state != Player.STATE_ENDED) return
                    // [SỬA] REPEAT_ONE không xử lý ở đây — ExoPlayer.REPEAT_MODE_ONE tự loop,
                    // STATE_ENDED không bao giờ fire khi mode đó đang active.
                    // Chỉ xử lý REPEAT_ALL và REPEAT_NONE.
                    when (repeatMode) {
                        REPEAT_ALL -> advanceToNext()
                        else -> { // REPEAT_NONE: chỉ advance nếu chưa ở bài cuối
                            val lastIndex = MainActivity.globalMusicList.size - 1
                            if (MainActivity.currentIndex < lastIndex) advanceToNext()
                            // Bài cuối + REPEAT_NONE → dừng lại, không làm gì
                        }
                    }
                }
            })
        }
    }

    // ── Notification (PlayerNotificationManager tự tạo channel + style) ───────

    private fun buildNotificationManager() {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setChannelNameResourceId(R.string.app_name)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence =
                    MainActivity.globalMusicList.getOrNull(MainActivity.currentIndex)?.title ?: "Unknown"

                override fun createCurrentContentIntent(player: Player): PendingIntent = contentIntent

                override fun getCurrentContentText(player: Player): CharSequence? {
                    val artist = MainActivity.globalMusicList.getOrNull(MainActivity.currentIndex)?.artist
                        ?: return null
                    return if (artist == "<unknown>" || artist.isBlank()) "Unknown Artist" else artist
                }

                // Large icon null → ExoPlayer dùng app icon mặc định
                override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                    if (ongoing) startForeground(notificationId, notification)
                    else stopForeground(STOP_FOREGROUND_DETACH)  // minSdk 26, không cần compat
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            })
            .build()
            .also { it.setPlayer(_player) }
    }

    // ── Public API cho PlayerActivity ─────────────────────────────────────────

    fun loadSong(index: Int) {
        val list = MainActivity.globalMusicList
        if (index !in list.indices) return
        MainActivity.currentIndex = index
        _player?.run {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(list[index].path))
            // [SỬA] Sau khi set media item mới, apply lại repeat mode cho item này.
            // repeatMode setter tự sync player.repeatMode, nhưng stop()+clearMediaItems()
            // không reset player.repeatMode — vẫn giữ nguyên. Dòng dưới phòng thủ.
            repeatMode = if (this@MusicPlayerService.repeatMode == REPEAT_ONE) Player.REPEAT_MODE_ONE
                         else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
        // PlayerNotificationManager tự refresh title/text khi player state thay đổi
    }

    fun skipToNext()     = advanceToNext()

    fun skipToPrevious() {
        if ((_player?.currentPosition ?: 0L) > 3_000L) {
            _player?.seekTo(0L)
            return
        }
        val list = MainActivity.globalMusicList
        if (list.isEmpty()) return
        val prev = if (MainActivity.currentIndex > 0) MainActivity.currentIndex - 1
                   else list.size - 1
        loadSong(prev)
    }

    private fun advanceToNext() {
        val list = MainActivity.globalMusicList
        if (list.isEmpty()) return
        val next = if (isShuffle) (0 until list.size).random()
                   else (MainActivity.currentIndex + 1) % list.size
        loadSong(next)
    }
}
