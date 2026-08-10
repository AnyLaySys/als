package sui.k.als

import android.app.Application
import sui.k.als.log.ALSLog

class ALSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ALSLog.install(this)
    }
}
