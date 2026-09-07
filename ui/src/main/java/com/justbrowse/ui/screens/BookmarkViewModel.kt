package com.justbrowse.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.justbrowse.domain.model.Bookmark
import com.justbrowse.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(title: String, url: String) {
        val now = System.currentTimeMillis()
        val normalizedUrl = if (url.startsWith("http")) url else "https://$url"
        viewModelScope.launch {
            bookmarkRepository.save(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    title = title.ifEmpty { normalizedUrl },
                    url = normalizedUrl,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun update(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkRepository.save(bookmark.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    fun isBookmarked(url: String): Boolean = bookmarks.value.any { it.url == url }
}
