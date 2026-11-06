package org.verco.telefoon

import android.app.Application

class VercoApp : Application() {
    lateinit var store: AccountStore
    lateinit var recents: RecentStore
    lateinit var phone: PhoneManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = AccountStore(this)
        recents = RecentStore(this)
        phone = PhoneManager(this, recents)
        // Firebase klaarzetten, ook als Android de app alleen start voor een pushbericht
        store.config.value?.let { Push.init(this, it) }
    }

    companion object {
        lateinit var instance: VercoApp
            private set
    }
}
