package br.com.controlegastos.app

import org.json.JSONObject

internal fun readNotificationPreferences(json: JSONObject) = NotificationPreferencesView(
    dailyReminder = json.getBoolean("dailyReminder"),
    negativeBalance = json.getBoolean("negativeBalance"),
    sharedExpense = json.getBoolean("sharedExpense"),
    reminderTime = json.getString("reminderTime"),
)

internal fun notificationPreferencesJson(preferences: NotificationPreferencesView): String = JSONObject()
    .put("dailyReminder", preferences.dailyReminder)
    .put("negativeBalance", preferences.negativeBalance)
    .put("sharedExpense", preferences.sharedExpense)
    .put("reminderTime", preferences.reminderTime)
    .toString()

internal fun readHasExpense(json: JSONObject): Boolean = json.getBoolean("hasExpense")
