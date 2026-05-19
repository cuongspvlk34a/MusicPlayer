package com.uilover.project278.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.uilover.project278.R
import com.uilover.project278.databinding.ItemSongBinding
import com.uilover.project278.model.MusicModel

class MusicAdapter(
    private val context: Context,
    private val musicList: List<MusicModel>,
    private val onItemClick: (position: Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    inner class MusicViewHolder(val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val music = musicList[position]

        with(holder.binding) {
            tvSongTitle.text = music.title

            // CẢI THIỆN 8: xử lý "<unknown>" artist
            tvArtist.text = when {
                music.artist == "<unknown>" || music.artist.isBlank() -> "Unknown Artist"
                else -> music.artist
            }

            tvDuration.text = music.getFormattedDuration()

            // Load album art hình tròn với Glide
            Glide.with(context)
                .load(music.getAlbumArtUri())
                .placeholder(R.drawable.fav_icon)
                .error(R.drawable.fav_icon)
                .transform(CircleCrop())
                .into(imgAlbumArt)
        }

        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount() = musicList.size
}