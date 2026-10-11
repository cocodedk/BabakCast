package com.cocode.babakcast.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.cocode.babakcast.R

fun Context.openUrl(url: String): Boolean = runCatching {
    startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}.isSuccess

fun Context.openUrlOrToast(url: String) {
    if (!openUrl(url)) {
        Toast.makeText(this, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
    }
}
