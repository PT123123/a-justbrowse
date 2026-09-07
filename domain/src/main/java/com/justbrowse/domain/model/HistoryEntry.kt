package com.justbrowse.domain.model

data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val faviconUrl: String? = null,
    val visitedAt: Long,
    val visitCount: Int = 1
)
