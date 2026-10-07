package com.cocode.babakcast.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.BuildConfig
import com.cocode.babakcast.R
import com.cocode.babakcast.util.openUrlOrToast

/**
 * The About page: name and version with the update button, what the app does, the privacy
 * promises, links, credits, who made it. Every section title is a TalkBack heading. Links open in
 * the browser; the app never checks for updates over the network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val open = { link: AboutLink -> context.openUrlOrToast(aboutUrl(link)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            NameAndVersionSection(
                version = versionLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                onCheckForUpdates = { open(AboutLink.Update) }
            )
            WhatItDoesSection()
            PrivacySection(onReadPolicy = { open(AboutLink.Privacy) })
            LinksSection(open = open)
            CreditsSection()
            MadeBySection(onOpenCocode = { open(AboutLink.MadeBy) })
            // Support slot: reserved for the Support phase of the cocode-apps standard
            // (standard/support.md). Nothing is shown here until then.
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
