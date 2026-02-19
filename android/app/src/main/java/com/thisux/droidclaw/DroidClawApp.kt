package com.thisux.pocketagent

import android.app.Application
import com.thisux.pocketagent.data.SettingsStore

class PocketAgentApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
    }
}
