package com.uilover.project278.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.uilover.project278.adapter.MusicAdapter
import com.uilover.project278.databinding.ActivityMainBinding
import com.uilover.project278.model.MusicModel
import com.uilover.project278.service.MusicPlayerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val musicList = mutableListOf<MusicModel>()
    private lateinit var adapter: MusicAdapter

    companion object {
        private const val PERMISSION_CODE = 1001

        var globalMusicList: List<MusicModel> = listOf()
        var currentIndex: Int = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkAndRequestPermission()
    }

    private fun setupRecyclerView() {
        adapter = MusicAdapter(this, musicList) { position ->
            globalMusicList = musicList.toList()
            currentIndex    = position

            // Start service trước để nó tồn tại độc lập với activity lifecycle
            startService(Intent(this, MusicPlayerService::class.java))

            startActivity(
                Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_LOAD_NEW_SONG, true)
                }
            )
        }
        binding.musicRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter       = this@MainActivity.adapter
        }
    }

    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadMusicFromDevice()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFromDevice()
            } else {
                Toast.makeText(this, getString(com.uilover.project278.R.string.permission_denied), Toast.LENGTH_LONG).show()
                showEmptyState()
            }
        }
    }

    private fun loadMusicFromDevice() {
        musicList.clear()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durCol)
                if (duration < 30_000L) continue  // Bỏ qua ringtone / notification sound

                musicList.add(
                    MusicModel(
                        id       = cursor.getLong(idCol),
                        title    = cursor.getString(titleCol)  ?: "Unknown",
                        artist   = cursor.getString(artistCol) ?: "<unknown>",
                        album    = cursor.getString(albumCol)  ?: "Unknown",
                        duration = duration,
                        path     = cursor.getString(dataCol)   ?: "",
                        albumId  = cursor.getLong(albumIdCol)
                    )
                )
            }
        }

        adapter.notifyDataSetChanged()
        binding.songCountText.text = "${musicList.size} songs"

        if (musicList.isEmpty()) showEmptyState() else showMusicList()
    }

    private fun showEmptyState() {
        binding.tvNoSongs.visibility       = View.VISIBLE
        binding.musicRecyclerView.visibility = View.GONE
    }

    private fun showMusicList() {
        binding.tvNoSongs.visibility       = View.GONE
        binding.musicRecyclerView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }
}
