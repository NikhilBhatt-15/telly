package com.nikhilbhatt.telly.data

object Presets {
    /** Starter app tiles. Links follow the Android TV remote app-link conventions; all are editable. */
    val apps: List<Shortcut> = listOf(
        Shortcut(id = "app-netflix", name = "Netflix", steps = listOf(Step.Link("https://www.netflix.com/title")), color = 0xFFE50914, isApp = true, pinned = true),
        Shortcut(id = "app-youtube", name = "YouTube", steps = listOf(Step.Link("https://www.youtube.com")), color = 0xFFFF0033, isApp = true, pinned = true),
        Shortcut(id = "app-prime", name = "Prime Video", steps = listOf(Step.App("com.amazon.amazonvideo.livingroom")), color = 0xFF00A8E1, isApp = true, pinned = true),
        Shortcut(id = "app-hotstar", name = "JioHotstar", steps = listOf(Step.App("in.startv.hotstar")), color = 0xFF1A3FD6, isApp = true, pinned = true),
        Shortcut(id = "app-spotify", name = "Spotify", steps = listOf(Step.App("com.spotify.tv.android")), color = 0xFF1DB954, isApp = true),
    )

    /** Link patterns for opening something specific inside an app. */
    val linkTemplates: List<Pair<String, String>> = listOf(
        "YouTube search" to "https://www.youtube.com/results?search_query=",
        "YouTube video" to "https://www.youtube.com/watch?v=",
        "Netflix title" to "https://www.netflix.com/title/",
        "Prime Video title" to "https://app.primevideo.com/detail?gti=",
    )

    /** Popular Indian channels for quick-add. Numbers differ by operator and region, so the user types them. */
    val channelNames: List<Pair<String, String>> = listOf(
        "Colors" to "Entertainment", "Star Plus" to "Entertainment", "Sony TV" to "Entertainment",
        "Zee TV" to "Entertainment", "Sony SAB" to "Entertainment", "Star Bharat" to "Entertainment", "&TV" to "Entertainment",
        "Aaj Tak" to "News", "ABP News" to "News", "NDTV India" to "News", "India TV" to "News",
        "Zee News" to "News", "Republic Bharat" to "News", "DD National" to "News",
        "Star Sports 1" to "Sports", "Star Sports 1 Hindi" to "Sports", "Sony Sports Ten 1" to "Sports",
        "Star Gold" to "Movies", "Sony Max" to "Movies", "Zee Cinema" to "Movies", "Colors Cineplex" to "Movies",
        "Discovery" to "Knowledge", "Nat Geo" to "Knowledge",
        "Cartoon Network" to "Kids", "Pogo" to "Kids", "Nick" to "Kids", "Sony Yay" to "Kids",
    )

    val palette: List<Long> = listOf(
        0xFFE5484D, 0xFFFF6B4A, 0xFFF5A524, 0xFF46A758, 0xFF12A594,
        0xFF0090FF, 0xFF3E63DD, 0xFF6E56CF, 0xFFD6409F, 0xFF3B4252,
    )

    fun colorFor(name: String): Long = palette[(name.hashCode() and 0x7FFFFFFF) % (palette.size - 1)]
}
