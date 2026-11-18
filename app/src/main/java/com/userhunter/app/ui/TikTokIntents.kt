package com.userhunter.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openTikTokProfile(context: Context, username: String) {
    val uri = Uri.parse("https://www.tiktok.com/@$username")
    val candidatePackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    for (packageName in candidatePackages) {
        val appIntent = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
        if (appIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(appIntent)
            return
        }
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
