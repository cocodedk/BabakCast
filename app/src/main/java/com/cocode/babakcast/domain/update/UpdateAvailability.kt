package com.cocode.babakcast.domain.update

sealed class UpdateAvailability {
    data class UpToDate(val installed: AppVersion) : UpdateAvailability()
    data class UpdateAvailable(
        val installed: AppVersion,
        val latest: AppVersion,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long
    ) : UpdateAvailability()
    data class CheckFailed(val reason: String) : UpdateAvailability()
}
