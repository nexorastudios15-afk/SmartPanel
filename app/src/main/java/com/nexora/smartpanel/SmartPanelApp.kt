package com.nexora.smartpanel

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SmartPanelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (e: Throwable) {
            // kalau gagal, panel tetap jalan; hanya freeform yang mungkin kurang optimal
        }
    }
}
