package com.example.ui.screens.settings

/** Local, in-memory navigation for the Settings tab - deliberately not androidx-navigation, since the rest of the app already uses simple tab/state switching (see MainActivity). */
enum class SettingsRoute(val title: String) {
    HUB("Settings"),
    APP_LOCK("App Lock"),
    DIAGNOSTICS("Speed & Diagnostics"),
    TRENDS_ALERTS("Trends & Alerts"),
    WIFI_RADIO("Wi-Fi & Radio Reminders"),
    APP_BLOCKER("App Data Blocker"),
    BACKUP_RESET("Backup, Reset & Share"),
    ADVANCED("Advanced"),
}
