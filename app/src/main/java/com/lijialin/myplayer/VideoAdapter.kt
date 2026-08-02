package com.lijialin.myplayer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class VideoAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {
    private val items = mutableListOf<EncryptedVideo>()
    private var currentIndex = RecyclerView.NO_POSITION
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())

    fun submitList(videos: List<EncryptedVideo>) {
        items.clear()
        items.addAll(videos)
        currentIndex = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun setCurrentIndex(index: Int) {
        val previous = currentIndex
        currentIndex = index
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (currentIndex != RecyclerView.NO_POSITION) notifyItemChanged(currentIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position], position == currentIndex)
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = items.size

    private fun formatMeta(video: EncryptedVideo): String {
        val sizeText = formatSize(video.size)
        val timeText = if (video.lastModified > 0L) {
            dateFormat.format(Date(video.lastModified))
        } else {
            "未知时间"
        }
        return "$sizeText  |  $timeText"
    }

    private fun formatSize(size: Long): String {
        if (size <= 0L) return "未知大小"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = size.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
        }
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)

        fun bind(video: EncryptedVideo, selected: Boolean) {
            titleText.text = video.displayName
            metaText.text = formatMeta(video)
            itemView.setBackgroundColor(
                if (selected) itemView.context.getColor(R.color.surface_selected) else Color.TRANSPARENT
            )
        }
    }
}
