package com.example.net

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

object InstalledAppsManager {

    fun getInstalledAppsJson(context: Context): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val jsonArray = JSONArray()

        val list = apps.mapNotNull { appInfo ->
            try {
                val label = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                Pair(label, pkg)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.first.lowercase() }

        for (item in list) {
            val obj = JSONObject().apply {
                put("label", item.first)
                put("packageName", item.second)
            }
            jsonArray.put(obj)
        }

        return jsonArray.toString()
    }
}
