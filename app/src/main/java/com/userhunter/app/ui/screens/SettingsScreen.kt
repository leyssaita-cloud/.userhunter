package com.userhunter.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.userhunter.app.R
import com.userhunter.app.data.AppSettings
import com.userhunter.app.ui.MainViewModel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selfTest by viewModel.selfTest.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var recheckText by remember(settings.recheckHours) { mutableStateOf(settings.recheckHours.toString()) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCheckedCache()
                    showClearDialog = false
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.language == "ar",
                        onClick = { viewModel.setLanguage("ar") },
                        label = { Text(stringResource(R.string.language_arabic)) }
                    )
                    FilterChip(
                        selected = settings.language == "en",
                        onClick = { viewModel.setLanguage("en") },
                        label = { Text(stringResource(R.string.language_english)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.theme))
                    Switch(checked = settings.darkTheme, onCheckedChange = viewModel::setDarkTheme)
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.speed_setting), style = MaterialTheme.typography.titleMedium)
                Text("${"%.1f".format(settings.requestsPerSecond)} req/s")
                Slider(
                    value = settings.requestsPerSecond.toFloat(),
                    onValueChange = { viewModel.setSpeed(it.toDouble()) },
                    valueRange = 0.2f..2.0f,
                    steps = 17
                )
                Text(stringResource(R.string.concurrency_setting), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { value ->
                        FilterChip(
                            selected = settings.concurrency == value,
                            onClick = { viewModel.setConcurrency(value) },
                            label = { Text(value.toString()) }
                        )
                    }
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.connect_timeout), style = MaterialTheme.typography.titleMedium)
                Text("${settings.connectTimeoutSeconds}s")
                Slider(
                    value = settings.connectTimeoutSeconds.toFloat(),
                    onValueChange = { viewModel.setConnectTimeout(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10
                )
                Text(stringResource(R.string.read_timeout), style = MaterialTheme.typography.titleMedium)
                Text("${settings.readTimeoutSeconds}s")
                Slider(
                    value = settings.readTimeoutSeconds.toFloat(),
                    onValueChange = { viewModel.setReadTimeout(it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10
                )
                Text(stringResource(R.string.recheck_hours), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = recheckText,
                    onValueChange = { value ->
                        recheckText = value.filter { it.isDigit() }.take(3)
                        recheckText.toIntOrNull()?.let(viewModel::setRecheckHours)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.auto_resume))
                    Switch(checked = settings.autoResumeWhenOnline, onCheckedChange = viewModel::setAutoResume)
                }
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.self_test), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.self_test_note), style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::runSelfTest, enabled = !selfTest.busy) {
                    Text(if (selfTest.busy) stringResource(R.string.checking) else stringResource(R.string.run_self_test))
                }
                Text(stringResource(R.string.self_test_taken))
                SelfTestLine(
                    value = when (selfTest.knownPassed) {
                        true -> stringResource(R.string.self_test_pass)
                        false -> stringResource(R.string.self_test_fail)
                        null -> stringResource(R.string.self_test_wait)
                    },
                    detail = selfTest.knownTaken?.state?.name.orEmpty()
                )
                Text(stringResource(R.string.self_test_random))
                SelfTestLine(
                    value = when (selfTest.randomPassed) {
                        true -> stringResource(R.string.self_test_pass)
                        false -> stringResource(R.string.self_test_fail)
                        null -> stringResource(R.string.self_test_wait)
                    },
                    detail = selfTest.randomAvailable?.state?.name.orEmpty()
                )
            }
        }
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.battery_settings), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.battery_note), style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }) { Text(stringResource(R.string.battery_settings)) }
                TextButton(onClick = { showClearDialog = true }) { Text(stringResource(R.string.clear_cache)) }
                Text(stringResource(R.string.app_version), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SelfTestLine(value: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(value, color = if (value == stringResource(R.string.self_test_fail)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall)
    }
}
