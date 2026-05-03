package com.arny.habrrss.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun SettingsScreen(
    state: ReaderUiState,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onOpenLinksInsideChanged: (Boolean) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SettingsSectionTitle("Чтение")
        SliderSetting(
            label = "Размер текста",
            value = state.settings.fontScale,
            valueRange = 0.85f..1.35f,
            onValueChange = onFontScaleChanged,
        )
        SliderSetting(
            label = "Межстрочный интервал",
            value = state.settings.lineHeightScale,
            valueRange = 1.0f..1.6f,
            onValueChange = onLineHeightChanged,
        )
        SettingsSectionTitle("Лента")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedCardMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.feedCardMode == mode,
                    onClick = { onCardModeChanged(mode) },
                    label = {
                        Text(
                            when (mode) {
                                FeedCardMode.CompactText -> "Компактно"
                                FeedCardMode.Comfortable -> "Удобно"
                                FeedCardMode.Magazine -> "Журнал"
                            },
                        )
                    },
                )
            }
        }
        SettingsSectionTitle("Избранные хабы и теги")
        Text(
            "Избранное видно здесь и в верхней панели ленты: хабы и теги со звёздочкой поднимаются первыми.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.favoriteHubs.isEmpty() && state.favoriteTags.isEmpty()) {
            Text(
                "Нажмите звёздочку у хаба или тега внутри статьи, чтобы закрепить его в ленте.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.favoriteHubs.forEach { (hubId, title) ->
                    FilterChip(
                        selected = true,
                        onClick = { onFavoriteHubToggled(hubId) },
                        label = { Text("★ $title") },
                    )
                }
                state.favoriteTags.forEach { (tagId, title) ->
                    FilterChip(
                        selected = true,
                        onClick = { onFavoriteTagToggled(tagId) },
                        label = { Text("★ #$title") },
                    )
                }
            }
        }
        SettingsSectionTitle("Ссылки")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Открывать ссылки внутри приложения")
                Text(
                    "Функция недоступна. Ссылки открываются во внешнем браузере.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.settings.openLinksInsideApp,
                onCheckedChange = null, // Disabled - feature not implemented
                enabled = false,
            )
        }
        SettingsSectionTitle("Офлайн")
        SettingsRow("Политика кеша", state.cachePolicyLabel)
        SettingsRow("Размер кеша", "${state.settings.cacheSizeMb} MB")
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(
                text = "${(value * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
