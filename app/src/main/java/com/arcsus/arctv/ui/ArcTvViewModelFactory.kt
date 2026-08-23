package com.arcsus.arctv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arcsus.arctv.ArcTvApp
import com.arcsus.arctv.update.UpdateChecker

class ArcTvViewModelFactory(private val app: ArcTvApp) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) ->
            AuthViewModel(app.repository, app.allDebridRepository) as T

        modelClass.isAssignableFrom(LiveViewModel::class.java) ->
            LiveViewModel(app.liveRepository, app.settingsStore) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app.repository, app.allDebridRepository, app.tokenStore, app.settingsStore) as T

        modelClass.isAssignableFrom(DownloadsViewModel::class.java) ->
            DownloadsViewModel(app.repository) as T

        modelClass.isAssignableFrom(TorrentsViewModel::class.java) ->
            TorrentsViewModel(app.repository) as T

        modelClass.isAssignableFrom(BrowseViewModel::class.java) ->
            BrowseViewModel(app.browseRepository) as T

        modelClass.isAssignableFrom(UpdateViewModel::class.java) ->
            UpdateViewModel(UpdateChecker(app)) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
