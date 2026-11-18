package com.userhunter.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.userhunter.app.R
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.SpecialPattern
import com.userhunter.app.data.AppSettings
import com.userhunter.app.data.HuntSessionEntity
import com.userhunter.app.generator.CandidateGenerator
import com.userhunter.app.ui.DropdownSelector
import com.userhunter.app.ui.HuntFormState
import com.userhunter.app.ui.MainViewModel
import java.math.BigInteger
import java.util.Locale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HuntScreen(
    viewModel: MainViewModel,
    form: HuntFormState,
    settings: AppSettings,
    session: HuntSessionEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val huntError by viewModel.huntError.collectAsState()
    val wordListLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.saveWordListUri(uri)
    }
    val running = session?.status == "RUNNING"
    val paused = session?.status == "PAUSED"

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.hunt_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.hunt_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.length_label), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4).forEach { length ->
                            FilterChip(
                                selected = length in form.lengths,
                                onClick = { viewModel.toggleLength(length) },
                                label = { Text(length.toString()) }
                            )
                        }
                    }
                    Text(stringResource(R.string.charset_label), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.charset.letters,
                            onClick = { viewModel.setCharset { it.copy(letters = !it.letters) } },
                            label = { Text(stringResource(R.string.letters)) }
                        )
                        FilterChip(
                            selected = form.charset.digits,
                            onClick = { viewModel.setCharset { it.copy(digits = !it.digits) } },
                            label = { Text(stringResource(R.string.digits)) }
                        )
                        FilterChip(
                            selected = form.charset.underscore,
                            onClick = { viewModel.setCharset { it.copy(underscore = !it.underscore) } },
                            label = { Text(stringResource(R.string.underscore)) }
                        )
                        FilterChip(
                            selected = form.charset.period,
                            onClick = { viewModel.setCharset { it.copy(period = !it.period) } },
                            label = { Text(stringResource(R.string.period)) }
                        )
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelector(
                        label = stringResource(R.string.mode_label),
                        selected = form.mode,
                        options = GenerationMode.entries,
                        optionLabel = { modeLabel(it) },
                        onSelected = { viewModel.updateForm { current -> current.copy(mode = it) } }
                    )
                    if (form.mode == GenerationMode.PATTERN) {
                        OutlinedTextField(
                            value = form.patternMask,
                            onValueChange = { viewModel.updateForm { current -> current.copy(patternMask = it) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.pattern_label)) },
                            supportingText = { Text(stringResource(R.string.pattern_hint)) },
                            singleLine = true
                        )
                    }
                    if (form.mode == GenerationMode.SPECIAL) {
                        DropdownSelector(
                            label = stringResource(R.string.special_pattern_label),
                            selected = form.specialPattern,
                            options = SpecialPattern.entries,
                            optionLabel = { specialLabel(it) },
                            onSelected = { viewModel.updateForm { current -> current.copy(specialPattern = it) } }
                        )
                    }
                    if (form.mode == GenerationMode.WORD_LIST) {
                        OutlinedButton(onClick = { wordListLauncher.launch(arrayOf("text/plain")) }) {
                            Text(stringResource(R.string.choose_word_list))
                        }
                        Text(
                            if (settings.importedWordListUri.isBlank()) stringResource(R.string.word_list_missing)
                            else stringResource(R.string.word_list_selected),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (huntError != null) {
            item {
                val message = when (huntError) {
                    "lengths" -> stringResource(R.string.select_lengths)
                    "charset" -> stringResource(R.string.select_charset)
                    "pattern" -> stringResource(R.string.invalid_pattern)
                    "word_list" -> stringResource(R.string.word_list_missing)
                    else -> huntError.orEmpty()
                }
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startHunt() },
                    enabled = !running && !paused,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.start)) }
                if (running) {
                    OutlinedButton(onClick = viewModel::pauseHunt, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pause))
                    }
                } else if (paused) {
                    OutlinedButton(onClick = viewModel::resumeHunt, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.resume))
                    }
                }
                if (running || paused) {
                    TextButton(onClick = viewModel::stopHunt) { Text(stringResource(R.string.stop)) }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
                    Text("${stringResource(R.string.checked)}: ${session?.checked ?: 0}   •   ${stringResource(R.string.found)}: ${session?.found ?: 0}")
                    Text("${stringResource(R.string.taken)}: ${session?.taken ?: 0}   •   ${stringResource(R.string.blocked)}: ${session?.blocked ?: 0}")
                    Text("${stringResource(R.string.errors)}: ${session?.errors ?: 0}   •   ${stringResource(R.string.speed)}: ${"%.2f".format(Locale.US, session?.requestsPerSecond ?: 0.0)}")
                    val total = remember(form) { CandidateGenerator().totalCount(form.toRequest()) }
                    val cursor = remember(session?.nextIndex) {
                        runCatching { BigInteger(session?.nextIndex ?: "0") }.getOrDefault(BigInteger.ZERO)
                    }
                    val finite = form.mode != GenerationMode.RANDOM && form.mode != GenerationMode.WORD_LIST && total > BigInteger.ZERO
                    val progress = if (finite) {
                        cursor.toDouble().div(total.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${stringResource(R.string.progress)}: ${if (finite) "${(progress * 100).toInt()}%" else "—"}")
                    val speed = session?.requestsPerSecond ?: 0.0
                    val eta = if (finite && speed > 0.0) {
                        formatEta(((total - cursor).coerceAtLeast(BigInteger.ZERO).toDouble() / speed).toLong())
                    } else "—"
                    Text("${stringResource(R.string.eta)}: $eta")
                    Text(
                        when (session?.status) {
                            "RUNNING" -> stringResource(R.string.running)
                            "PAUSED" -> "${stringResource(R.string.paused_status)}${session?.pauseReason?.let { ": $it" }.orEmpty()}"
                            "COMPLETED" -> stringResource(R.string.completed)
                            else -> stringResource(R.string.idle)
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.available_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun modeLabel(mode: GenerationMode): String = when (mode) {
    GenerationMode.RANDOM -> stringResource(R.string.mode_random)
    GenerationMode.SEQUENTIAL -> stringResource(R.string.mode_sequential)
    GenerationMode.PATTERN -> stringResource(R.string.mode_pattern)
    GenerationMode.SPECIAL -> stringResource(R.string.mode_special)
    GenerationMode.WORD_LIST -> stringResource(R.string.mode_word_list)
}

@Composable
private fun specialLabel(pattern: SpecialPattern): String = when (pattern) {
    SpecialPattern.REPEATED -> stringResource(R.string.special_repeated)
    SpecialPattern.PALINDROME -> stringResource(R.string.special_palindrome)
    SpecialPattern.DOUBLES -> stringResource(R.string.special_doubles)
    SpecialPattern.ALTERNATING -> stringResource(R.string.special_alternating)
    SpecialPattern.PRONOUNCEABLE -> stringResource(R.string.special_pronounceable)
}

private fun formatEta(seconds: Long): String {
    if (seconds < 0) return "—"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remain = seconds % 60
    return if (hours > 0) "%dh %02dm".format(Locale.US, hours, minutes)
    else if (minutes > 0) "%dm %02ds".format(Locale.US, minutes, remain)
    else "%ds".format(Locale.US, remain)
}
