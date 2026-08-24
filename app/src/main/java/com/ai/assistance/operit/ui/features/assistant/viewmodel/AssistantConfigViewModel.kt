package com.ai.assistance.operit.ui.features.assistant.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 助手配置 ViewModel
 * 负责语音唤醒等助手配置界面的轻量状态管理。
 */
class AssistantConfigViewModel(
    private val context: Context
) : ViewModel() {

    data class UiState(
        val errorMessage: String? = null,
        val operationSuccess: Boolean = false,
        val scrollPosition: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun updateScrollPosition(position: Int) {
        _uiState.value = _uiState.value.copy(scrollPosition = position)
    }

    fun clearOperationSuccess() {
        _uiState.value = _uiState.value.copy(operationSuccess = false)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AssistantConfigViewModel(context) as T
        }
    }
}