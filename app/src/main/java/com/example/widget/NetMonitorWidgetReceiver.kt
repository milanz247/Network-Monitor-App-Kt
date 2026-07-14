package com.example.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The actual AppWidgetProvider the system talks to; Glance generates the RemoteViews under the hood. */
class NetMonitorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetMonitorWidget()
}
