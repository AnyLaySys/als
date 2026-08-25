package sui.k.als

import android.app.Application
import sui.k.als.log.ALSLog

class ALSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ALSLog.install(this, Application.getProcessName() == packageName)
    }

    companion object {
        lateinit var instance: ALSApplication
            private set
    }
}
