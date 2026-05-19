package com.uilover.project278.activities

import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import com.uilover.project278.R
import com.uilover.project278.databinding.ActivityPlayerBinding
import com.uilover.project278.model.MusicModel
import com.uilover.project278.service.MusicPlayerService
import jp.wasabeef.glide.transformations.BlurTransformation
import java.util.Random
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    companion object {
        // Intent extra: MainActivity truyền true khi mở bài MỚI
        const val EXTRA_LOAD_NEW_SONG = "load_new_song"
    }

    private lateinit var binding: ActivityPlayerBinding

    // Service binding
    private var musicService: MusicPlayerService? = null
    private var serviceBound = false
    private val player: ExoPlayer? get() = musicService?.player

    // Chỉ load bài mới nếu đây là launch đầu tiên (không phải rotation)
    private var shouldLoadNewSong = false

    private lateinit var rotateAnimator: ObjectAnimator
    private val handler = Handler(Looper.getMainLooper())
    private var playerListener: Player.Listener? = null
    private var isFavorite = false
    private var lastLoadedIndex = -1

    // ── ServiceConnection ──────────────────────────────────────────────────────

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            musicService = (binder as MusicPlayerService.MusicBinder).getService()
            serviceBound = true

            val list = MainActivity.globalMusicList
            if (list.isEmpty()) { finish(); return }

            attachPlayerListener()

            val isIdle = player?.playbackState == Player.STATE_IDLE || player?.currentMediaItem == null
            when {
                isIdle || shouldLoadNewSong -> {
                    musicService?.loadSong(MainActivity.currentIndex)
                    shouldLoadNewSong = false
                }
                else -> syncUIWithCurrentState()  // Rotation hoặc return từ background
            }

            // Metadata load unconditionally để đảm bảo UI đúng
            loadSongMetadata(MainActivity.currentIndex)
            updateRepeatButtonUI()
            updateShuffleButtonUI()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            detachPlayerListener()
            serviceBound = false
            musicService = null
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // savedInstanceState != null → activity đang recreate (rotation) → KHÔNG load bài mới
        shouldLoadNewSong = savedInstanceState == null &&
                intent.getBooleanExtra(EXTRA_LOAD_NEW_SONG, false)

        initRotateAnimation()
        setupUIListeners()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicPlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        if (serviceBound) {
            detachPlayerListener()
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rotateAnimator.isInitialized) rotateAnimator.cancel()
    }

    // ── Player listener ────────────────────────────────────────────────────────

    private fun attachPlayerListener() {
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseIcon(isPlaying)
                if (::rotateAnimator.isInitialized) {
                    if (isPlaying) resumeRotation() else rotateAnimator.pause()
                }
                if (isPlaying) handler.post(updateProgressRunnable)
            }

            // Khi service advance sang bài mới, reload metadata
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && MainActivity.currentIndex != lastLoadedIndex) {
                    lastLoadedIndex = MainActivity.currentIndex
                    loadSongMetadata(MainActivity.currentIndex)
                }
            }
        }.also { listener -> player?.addListener(listener) }
    }

    private fun detachPlayerListener() {
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
    }

    private fun syncUIWithCurrentState() {
        val isPlaying = player?.isPlaying == true
        updatePlayPauseIcon(isPlaying)
        if (::rotateAnimator.isInitialized) {
            if (isPlaying) resumeRotation() else rotateAnimator.pause()
        }
        if (isPlaying) handler.post(updateProgressRunnable)
    }

    // ── Progress updater ───────────────────────────────────────────────────────

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val current  = player?.currentPosition ?: 0L
            val duration = player?.duration?.takeIf { it > 0 } ?: 1L
            val progress = current.toFloat() / duration.toFloat()

            binding.tvCurrentTime.text = formatMillis(current)
            binding.tvTotalTime.text   = formatMillis(duration)

            try { binding.waveformSeekBar.progress = progress * 100f } catch (_: Exception) {}

            if (player?.isPlaying == true) handler.postDelayed(this, 500L)
        }
    }

    // ── Load metadata (UI only, không đụng player) ────────────────────────────

    private fun loadSongMetadata(index: Int) {
        val list = MainActivity.globalMusicList
        if (index !in list.indices) return
        val music = list[index]
        lastLoadedIndex = index

        binding.tvSongTitle.text = music.title
        binding.tvArtist.text = when {
            music.artist == "<unknown>" || music.artist.isBlank() -> "Unknown Artist"
            else -> music.artist
        }
        binding.tvCurrentTime.text = "00:00"
        binding.tvTotalTime.text   = music.getFormattedDuration()

        setupWaveform(music)
        loadAlbumArt(music)

        Glide.with(this)
            .load(music.getAlbumArtUri())
            .transform(BlurTransformation(25, 3))
            .placeholder(R.drawable.bg)
            .error(R.drawable.bg)
            .into(binding.bgBlurImage)
    }

    // ── Album art + Palette ────────────────────────────────────────────────────

    private fun loadAlbumArt(music: MusicModel) {
        Glide.with(this)
            .asBitmap()
            .load(music.getAlbumArtUri())
            .placeholder(R.drawable.fav_icon)
            .error(R.drawable.fav_icon)
            .transform(CircleCrop())
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    binding.imgAlbumArt.setImageBitmap(resource)
                    resetRotation()
                    applyPaletteBackground(resource)
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    binding.imgAlbumArt.setImageResource(R.drawable.fav_icon)
                }
            })
    }

    private fun applyPaletteBackground(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            val fallback = ContextCompat.getColor(this, R.color.colorPlayerBg)
            val vibrant  = palette?.getVibrantColor(fallback) ?: fallback
            val dark     = darkenColor(vibrant, 0.40f)
            val veryDark = darkenColor(vibrant, 0.18f)
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(vibrant, dark, veryDark, Color.BLACK)
            )
            binding.playerRootLayout.background = gradient
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color)   * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
    )

    // ── Waveform ──────────────────────────────────────────────────────────────

    private fun setupWaveform(music: MusicModel) {
        val rng    = Random(music.albumId)
        val raw    = FloatArray(120) { rng.nextFloat() * 0.80f + 0.10f }
        val smooth = FloatArray(raw.size)
        smooth[0]  = (raw[0] + raw[1]) / 2f
        for (i in 1 until raw.size - 1) smooth[i] = (raw[i - 1] + raw[i] + raw[i + 1]) / 3f
        smooth[raw.size - 1] = (raw[raw.size - 2] + raw[raw.size - 1]) / 2f
        try {
            val intSamples = smooth.map { (it * 100).toInt().coerceIn(0, 100) }.toIntArray()
            binding.waveformSeekBar.setSampleFrom(intSamples)
        } catch (_: Exception) {}
    }

    // ── Rotation animation ────────────────────────────────────────────────────

    private fun initRotateAnimation() {
        rotateAnimator = ObjectAnimator.ofFloat(binding.imgAlbumArt, "rotation", 0f, 360f).apply {
            duration     = 15_000L
            repeatCount  = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun resetRotation() {
        if (!::rotateAnimator.isInitialized) return
        rotateAnimator.cancel()
        binding.imgAlbumArt.rotation = 0f
        rotateAnimator.start()
        if (player?.isPlaying != true) rotateAnimator.pause()
    }

    private fun resumeRotation() {
        if (!::rotateAnimator.isInitialized) return
        when {
            rotateAnimator.isPaused   -> rotateAnimator.resume()
            !rotateAnimator.isRunning -> rotateAnimator.start()
        }
    }

    // ── UI Listeners ──────────────────────────────────────────────────────────

    private fun setupUIListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnFavorite.setOnClickListener {
            isFavorite = !isFavorite
            binding.btnFavorite.alpha = if (isFavorite) 1.0f else 0.5f
        }

        binding.btnPlayPause.setOnClickListener {
            if (player?.isPlaying == true) player?.pause() else player?.play()
        }

        binding.btnNext.setOnClickListener     { musicService?.skipToNext()     }
        binding.btnPrevious.setOnClickListener { musicService?.skipToPrevious() }

        // Repeat: cycle NONE → ONE → ALL → NONE
        binding.btnRepeat.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            svc.repeatMode = when (svc.repeatMode) {
                MusicPlayerService.REPEAT_NONE -> MusicPlayerService.REPEAT_ONE
                MusicPlayerService.REPEAT_ONE  -> MusicPlayerService.REPEAT_ALL
                else                           -> MusicPlayerService.REPEAT_NONE
            }
            updateRepeatButtonUI()
        }

        binding.btnShuffle.setOnClickListener {
            val svc = musicService ?: return@setOnClickListener
            svc.isShuffle = !svc.isShuffle
            updateShuffleButtonUI()
        }

        binding.waveformSeekBar.apply {
            maxProgress         = 100f
            waveBackgroundColor = 0x55FFFFFF.toInt()
            waveProgressColor   = resources.getColor(R.color.colorAccent, theme)
            waveGap             = 2f
            waveWidth           = 3f
            waveMinHeight       = 4f
            waveCornerRadius    = 2f
        }
        binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
            override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    player?.seekTo((progress / 100f * duration).toLong())
                }
            }
        }
    }

    // ── Button UI state ───────────────────────────────────────────────────────

    private fun updateRepeatButtonUI() {
        val svc = musicService ?: return
        // [SỬA] Đổi icon theo mode để phân biệt rõ ràng:
        //   REPEAT_NONE  → ic_repeat mờ (40% alpha), không tint
        //   REPEAT_ONE   → ic_repeat_one sáng, tint accent
        //   REPEAT_ALL   → ic_repeat sáng, không tint (phân biệt bằng alpha vs NONE)
        when (svc.repeatMode) {
            MusicPlayerService.REPEAT_NONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 0.4f
                binding.btnRepeat.clearColorFilter()
            }
            MusicPlayerService.REPEAT_ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.alpha = 1.0f
                binding.btnRepeat.setColorFilter(
                    ContextCompat.getColor(this, R.color.colorAccent),
                    PorterDuff.Mode.SRC_IN
                )
            }
            MusicPlayerService.REPEAT_ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.alpha = 1.0f
                binding.btnRepeat.clearColorFilter()
            }
        }
    }

    private fun updateShuffleButtonUI() {
        // [SỬA] Dùng ic_shuffle, thêm tint accent khi active để nhất quán với repeat button
        val active = musicService?.isShuffle == true
        binding.btnShuffle.setImageResource(R.drawable.ic_shuffle)
        binding.btnShuffle.alpha = if (active) 1.0f else 0.4f
        if (active) {
            binding.btnShuffle.setColorFilter(
                ContextCompat.getColor(this, R.color.colorAccent),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.btnShuffle.clearColorFilter()
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else            android.R.drawable.ic_media_play
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatMillis(ms: Long): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(ms)
        val sec = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(min)
        return String.format("%02d:%02d", min, sec)
    }
}
