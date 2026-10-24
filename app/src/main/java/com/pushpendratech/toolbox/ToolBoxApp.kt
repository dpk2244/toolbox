package com.pushpendratech.toolbox

import android.app.Application
import com.pushpendratech.toolbox.ads.Ads
import com.pushpendratech.toolbox.core.CrashLog
import com.pushpendratech.toolbox.core.PdfTools

class ToolBoxApp : Application() {
    override fun onCreate() { super.onCreate(); CrashLog.install(this); PdfTools.init(this); Ads.init(this) }
}
