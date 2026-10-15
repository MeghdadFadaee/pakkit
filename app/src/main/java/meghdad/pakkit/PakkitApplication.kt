package meghdad.pakkit

import android.app.Application
import meghdad.pakkit.data.PackageStore

class PakkitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PackageStore(this).clean()
    }
}
