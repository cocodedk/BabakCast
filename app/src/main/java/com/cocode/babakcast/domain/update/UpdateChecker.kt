package com.cocode.babakcast.domain.update

fun interface UpdateChecker {
    suspend fun checkForUpdate(): UpdateAvailability
}
