package com.arcsus.arctv

import android.app.Application
import com.arcsus.arctv.data.AllDebridRepository
import com.arcsus.arctv.data.ArtworkRepository
import com.arcsus.arctv.data.BrowseRepository
import com.arcsus.arctv.data.LiveRepository
import com.arcsus.arctv.data.RdClient
import com.arcsus.arctv.data.RdRepository
import com.arcsus.arctv.data.SettingsStore
import com.arcsus.arctv.data.TokenStore

class ArcTvApp : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var rdClient: RdClient
        private set
    lateinit var repository: RdRepository
        private set
    lateinit var artworkRepository: ArtworkRepository
        private set
    lateinit var browseRepository: BrowseRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var allDebridRepository: AllDebridRepository
        private set
    lateinit var liveRepository: LiveRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        rdClient = RdClient(tokenStore)
        repository = RdRepository(rdClient, tokenStore)
        artworkRepository = ArtworkRepository(this)
        settingsStore = SettingsStore(this)
        allDebridRepository = AllDebridRepository(tokenStore)
        liveRepository = LiveRepository()
        browseRepository = BrowseRepository(tokenStore, settingsStore, allDebridRepository)
    }
}
