package sui.k.als

import android.app.Application

class ALSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ALSLog.install(this, getProcessName() == packageName)
    }

    companion object {
        lateinit var instance: ALSApplication
            private set
    }
}
