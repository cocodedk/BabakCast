package com.cocode.babakcast.di

import android.content.Context
import com.cocode.babakcast.BuildConfig
import com.cocode.babakcast.data.local.SecureStorage
import com.cocode.babakcast.data.local.SettingsRepository
import com.cocode.babakcast.data.network.AndroidNetworkTypeProvider
import com.cocode.babakcast.data.remote.GitHubReleaseClient
import com.cocode.babakcast.data.repository.UpdateRepository
import com.cocode.babakcast.domain.network.NetworkTypeProvider
import com.cocode.babakcast.domain.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideNetworkTypeProvider(@ApplicationContext context: Context): NetworkTypeProvider =
        AndroidNetworkTypeProvider(context)

    @Provides
    @Named("installedVersionName")
    fun provideInstalledVersionName(): String = BuildConfig.VERSION_NAME

    @Provides
    @Singleton
    fun provideUpdateChecker(
        client: GitHubReleaseClient,
        @Named("installedVersionName") installedVersionName: String
    ): UpdateChecker = UpdateRepository.fromClient(installedVersionName, client)
}
