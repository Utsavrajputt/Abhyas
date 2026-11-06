package com.invictus.abhyas

import android.app.Application
import com.invictus.abhyas.data.local.AbhyasDatabase

class AbhyasApp : Application() {
    val database: AbhyasDatabase by lazy { AbhyasDatabase.build(this) }
}
