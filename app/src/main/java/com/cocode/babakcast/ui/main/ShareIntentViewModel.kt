package com.cocode.babakcast.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped ViewModel that holds a pending shared URL from an ACTION_SEND intent.
 * MainScreen reads this and applies it to the URL field, then clears it.
 */
class ShareIntentViewModel : ViewModel() {

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    fun setPendingUrl(url: String?) {
        _pendingSharedUrl.value = url
    }

    fun clearPendingUrl() {
        _pendingSharedUrl.value = null
    }
}
