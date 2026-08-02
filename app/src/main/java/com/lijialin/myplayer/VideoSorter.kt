package com.lijialin.myplayer

enum class SortMode {
    TIME,
    NAME
}

object VideoSorter {
    fun sort(videos: List<EncryptedVideo>, mode: SortMode): List<EncryptedVideo> {
        return when (mode) {
            SortMode.TIME -> videos.sortedWith(
                compareBy<EncryptedVideo> { it.lastModified }
                    .thenBy { it.displayName.lowercase() }
            )
            SortMode.NAME -> videos.sortedWith(
                compareBy<EncryptedVideo> { it.displayName.lowercase() }
                    .thenBy { it.lastModified }
            )
        }
    }
}
