package com.cocode.babakcast.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.R
import com.cocode.babakcast.ui.settings.SectionHeader
import com.cocode.babakcast.ui.theme.BabakCastColors

private val promises = listOf(
    R.string.about_privacy_no_tracking,
    R.string.about_privacy_no_server,
    R.string.about_privacy_direct,
    R.string.about_privacy_keys,
    R.string.about_privacy_local
)

@Composable
private fun SectionTitle(@StringRes title: Int) = SectionHeader(
    title = stringResource(title),
    modifier = Modifier.padding(top = 24.dp).semantics { heading() }
)

/** A section's content, inset like the settings screen's rows. */
@Composable
private fun SectionContent(content: @Composable ColumnScope.() -> Unit) = Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content
)

@Composable
private fun Body(@StringRes text: Int) = Text(
    text = stringResource(text),
    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    color = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun LinkButton(@StringRes label: Int, onClick: () -> Unit) = OutlinedButton(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(48.dp),
    shape = MaterialTheme.shapes.medium,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
) {
    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun NameAndVersionSection(version: String, onCheckForUpdates: () -> Unit) {
    // The app name is this section's title: it is the "name" of "name and version".
    SectionHeader(
        title = stringResource(R.string.app_name),
        modifier = Modifier.padding(top = 4.dp).semantics { heading() }
    )
    SectionContent {
        Text(
            text = stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onCheckForUpdates,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = BabakCastColors.PrimaryAccent,
                contentColor = BabakCastColors.BackgroundPrimary
            )
        ) {
            Text(
                stringResource(R.string.about_check_updates),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
internal fun WhatItDoesSection() {
    SectionTitle(R.string.about_what_title)
    SectionContent { Body(R.string.about_what_body) }
}

@Composable
internal fun PrivacySection(onReadPolicy: () -> Unit) {
    SectionTitle(R.string.about_privacy_title)
    SectionContent {
        promises.forEach { Body(it) }
        LinkButton(R.string.about_privacy_link, onReadPolicy)
    }
}

@Composable
internal fun LinksSection(open: (AboutLink) -> Unit) {
    SectionTitle(R.string.about_links_title)
    SectionContent {
        LinkButton(R.string.about_website) { open(AboutLink.Website) }
        LinkButton(R.string.about_source) { open(AboutLink.Source) }
        LinkButton(R.string.about_report) { open(AboutLink.Issues) }
    }
}

@Composable
internal fun CreditsSection() {
    SectionTitle(R.string.about_credits)
    SectionContent {
        Body(R.string.about_license)
        Body(R.string.about_credits_body)
    }
}

@Composable
internal fun MadeBySection(onOpenCocode: () -> Unit) {
    SectionTitle(R.string.about_made_by_title)
    SectionContent {
        Body(R.string.about_created_by)
        LinkButton(R.string.about_made_by, onOpenCocode)
    }
}
