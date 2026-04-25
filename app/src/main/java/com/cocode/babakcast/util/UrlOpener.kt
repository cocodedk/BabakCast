package com.cocode.babakcast.util

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.openUrl(url: String): Boolean = runCatching {
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}.isSuccess
