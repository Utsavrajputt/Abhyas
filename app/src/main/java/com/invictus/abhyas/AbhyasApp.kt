package com.invictus.abhyas

import android.app.Application
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.prefs.SettingsStore

class AbhyasApp : Application() {
    val database: AbhyasDatabase by lazy { AbhyasDatabase.build(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
