package com.cocode.babakcast.ui

import androidx.annotation.StringRes
import com.cocode.babakcast.R
import com.cocode.babakcast.data.model.SummaryLength

/** The words shown for a summary length, as a string resource. */
@StringRes
fun SummaryLength.labelRes(): Int = when (this) {
    SummaryLength.SHORT -> R.string.summary_length_short
    SummaryLength.MEDIUM -> R.string.summary_length_medium
    SummaryLength.LONG -> R.string.summary_length_long
}
