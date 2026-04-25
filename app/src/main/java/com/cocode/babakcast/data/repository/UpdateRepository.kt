package com.cocode.babakcast.data.repository

import com.cocode.babakcast.data.remote.GitHubReleaseClient
import com.cocode.babakcast.data.remote.LatestRelease
import com.cocode.babakcast.domain.update.AppVersion
import com.cocode.babakcast.domain.update.UpdateAvailability
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @Named("installedVersionName") private val installedVersionName: String,
    private val fetchLatest: suspend () -> Result<LatestRelease>
) {

    suspend fun checkForUpdate(): UpdateAvailability {
        val installed = AppVersion.parse(installedVersionName)
            ?: return UpdateAvailability.CheckFailed("Could not read installed version: $installedVersionName")

        val release = fetchLatest().getOrElse { error ->
            return UpdateAvailability.CheckFailed(error.message ?: "Update check failed")
        }

        val latest = AppVersion.parse(release.tagName)
            ?: return UpdateAvailability.CheckFailed("GitHub returned unparseable tag: ${release.tagName}")

        return if (latest > installed) {
            UpdateAvailability.UpdateAvailable(
                installed = installed,
                latest = latest,
                apkDownloadUrl = release.apkDownloadUrl,
                apkSizeBytes = release.apkSizeBytes
            )
        } else {
            UpdateAvailability.UpToDate(installed)
        }
    }

    companion object {
        const val OWNER = "cocodedk"
        const val REPO = "BabakCast"

        fun fromClient(
            installedVersionName: String,
            client: GitHubReleaseClient
        ): UpdateRepository = UpdateRepository(
            installedVersionName = installedVersionName,
            fetchLatest = { client.fetchLatest(OWNER, REPO) }
        )
    }
}
