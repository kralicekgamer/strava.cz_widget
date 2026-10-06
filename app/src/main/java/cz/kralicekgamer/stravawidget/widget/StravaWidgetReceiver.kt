package cz.kralicekgamer.stravawidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import cz.kralicekgamer.stravawidget.data.AppLog

class StravaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StravaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppLog.add(context, "Widget přidán na plochu")
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        AppLog.add(context, "Widget odebrán z plochy")
    }
}
