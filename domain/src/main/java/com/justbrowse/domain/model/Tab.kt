package com.justbrowse.domain.model

data class Tab(
    val id: String,
    val url: String = "about:blank",
    val title: String = "",
    val faviconUrl: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isActive: Boolean = false
)
