package org.openui.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openui.contacts.R
import org.openui.contacts.data.AppThemeMode
import org.openui.contacts.ui.theme.AppTheme

@Composable
fun SettingsDialog(
    currentThemeMode: AppThemeMode,
    currentStorageTarget: String,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onStorageTargetClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.contacts_close),
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Appearance & Theme
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_appearance_header),
                        color = colors.primaryBrand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    ThemeOptionRow(
                        title = stringResource(R.string.settings_theme_open_ui),
                        subtitle = stringResource(R.string.settings_theme_open_ui_desc),
                        isSelected = currentThemeMode == AppThemeMode.OPEN_UI,
                        onClick = { onThemeModeChanged(AppThemeMode.OPEN_UI) }
                    )

                    ThemeOptionRow(
                        title = stringResource(R.string.settings_theme_m3),
                        subtitle = stringResource(R.string.settings_theme_m3_desc),
                        isSelected = currentThemeMode == AppThemeMode.MATERIAL_3_EXPRESSIVE,
                        onClick = { onThemeModeChanged(AppThemeMode.MATERIAL_3_EXPRESSIVE) }
                    )
                }

                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

                // Section 2: Contacts Storage
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_storage_header),
                        color = colors.primaryBrand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    StandardPreferenceRow(
                        icon = Icons.Rounded.SimCard,
                        title = stringResource(R.string.settings_default_storage_title),
                        subtitle = if (currentStorageTarget.startsWith("SIM")) currentStorageTarget else stringResource(R.string.contacts_storage_phone_title),
                        onClick = onStorageTargetClick
                    )
                }

                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

                // Section 3: About App
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.contacts_about),
                        color = colors.primaryBrand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    StandardPreferenceRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.about_version_tag),
                        onClick = onAboutClick
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryBrand),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.contacts_close),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primaryBrand,
                unselectedColor = colors.textMuted
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StandardPreferenceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.cardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
