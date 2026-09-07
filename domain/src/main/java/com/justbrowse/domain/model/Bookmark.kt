package com.justbrowse.domain.model

data class Bookmark(
    val id: String,
    val title: String,
    val url: String,
    val folder: String? = null,
    val faviconUrl: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
