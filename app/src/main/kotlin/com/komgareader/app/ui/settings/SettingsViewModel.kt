package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.repository.KomgaUrl
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, "de")
    val server = servers.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(value: String) = viewModelScope.launch { settings.setThemeMode(value) }.let {}
    fun setLanguage(value: String) = viewModelScope.launch { settings.setLanguage(value) }.let {}
    fun saveServer(
        name: String,
        baseUrl: String,
        apiKey: String,
        username: String,
        password: String,
    ) = viewModelScope.launch {
        servers.save(
            ServerConfig(
                name = name,
                baseUrl = KomgaUrl.normalize(baseUrl),
                apiKey = apiKey.trimToNull(),
                username = username.trimToNull(),
                password = password.trimToNull(),
            )
        )
    }.let {}

    private fun String.trimToNull(): String? = trim().ifBlank { null }
    fun disconnect() = viewModelScope.launch { servers.clear() }.let {}
}
