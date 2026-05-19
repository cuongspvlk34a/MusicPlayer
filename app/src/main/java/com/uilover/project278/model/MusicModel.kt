package com.uilover.project278.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.util.concurrent.TimeUnit

data class MusicModel(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumId: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id       = parcel.readLong(),
        title    = parcel.readString() ?: "",
        artist   = parcel.readString() ?: "",
        album    = parcel.readString() ?: "",
        duration = parcel.readLong(),
        path     = parcel.readString() ?: "",
        albumId  = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeString(artist)
        parcel.writeString(album)
        parcel.writeLong(duration)
        parcel.writeString(path)
        parcel.writeLong(albumId)
    }

    override fun describeContents(): Int = 0

    fun getAlbumArtUri(): Uri =
        Uri.parse("content://media/external/audio/albumart/$albumId")

    fun getFormattedDuration(): String {
        val min = TimeUnit.MILLISECONDS.toMinutes(duration)
        val sec = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(min)
        return String.format("%02d:%02d", min, sec)
    }

    companion object CREATOR : Parcelable.Creator<MusicModel> {
        override fun createFromParcel(parcel: Parcel): MusicModel = MusicModel(parcel)
        override fun newArray(size: Int): Array<MusicModel?> = arrayOfNulls(size)
    }
}