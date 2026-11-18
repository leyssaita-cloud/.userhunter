package com.userhunter.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.userhunter.app.R
import com.userhunter.app.data.ResultWithFavorite
import com.userhunter.app.ui.DropdownSelector
import com.userhunter.app.ui.MainViewModel
import com.userhunter.app.ui.openTikTokProfile
import kotlinx.coroutines.launch

private enum class ResultSort { RARITY, RECENT, LENGTH }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ResultsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val rows by viewModel.results.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("") }
    var lengthFilter by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(ResultSort.RARITY) }
    val selected = remember { mutableStateListOf<String>() }
    var exportSnapshot by remember { mutableStateOf<List<String>>(emptyList()) }

    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch { viewModel.exportResults(uri, exportSnapshot, false) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { viewModel.exportResults(uri, exportSnapshot, true) }
    }

    val filtered = remember(rows, search, tagFilter, lengthFilter, sort) {
        val query = search.trim().lowercase()
        val tag = tagFilter.trim().lowercase()
        rows.filter { row ->
            (query.isEmpty() || row.result.username.contains(query)) &&
                (lengthFilter == 0 || row.result.length == lengthFilter) &&
                (tag.isEmpty() || row.result.tagsCsv.lowercase().contains(tag))
        }.let { list ->
            when (sort) {
                ResultSort.RARITY -> list.sortedByDescending { it.result.rarity }
                ResultSort.RECENT -> list.sortedByDescending { it.result.discoveredAt }
                ResultSort.LENGTH -> list.sortedWith(compareBy<ResultWithFavorite> { it.result.length }.thenByDescending { it.result.rarity })
            }
        }
    }
    val exportNames = selected.toList().ifEmpty { filtered.map { it.result.username } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.results_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.results_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) }
            )
            OutlinedTextField(
                value = tagFilter,
                onValueChange = { tagFilter = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.filter_tag_hint)) }
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2, 3, 4).forEach { length ->
                    FilterChip(
                        selected = lengthFilter == length,
                        onClick = { lengthFilter = length },
                        label = { Text(if (length == 0) stringResource(R.string.filter_length) + ": " + stringResource(R.string.all) else length.toString()) }
                    )
                }
            }
            DropdownSelector(
                label = stringResource(R.string.sort_by),
                selected = sort,
                options = ResultSort.entries,
                optionLabel = {
                    when (it) {
                        ResultSort.RARITY -> stringResource(R.string.sort_rarity)
                        ResultSort.RECENT -> stringResource(R.string.sort_recent)
                        ResultSort.LENGTH -> stringResource(R.string.sort_length)
                    }
                },
                onSelected = { sort = it }
            )
        }
        item {
            Text(stringResource(R.string.selected_count, selected.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(exportNames.joinToString("\n")))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }, enabled = exportNames.isNotEmpty()) {
                    Text(stringResource(if (selected.isEmpty()) R.string.copy_all else R.string.copy_selected))
                }
                TextButton(onClick = {
                    exportSnapshot = exportNames
                    txtLauncher.launch("userhunter-results.txt")
                }, enabled = exportNames.isNotEmpty()) { Text(stringResource(R.string.export_txt)) }
                TextButton(onClick = {
                    exportSnapshot = exportNames
                    csvLauncher.launch("userhunter-results.csv")
                }, enabled = exportNames.isNotEmpty()) { Text(stringResource(R.string.export_csv)) }
            }
        }
        if (filtered.isEmpty()) {
            item { Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(filtered, key = { it.result.username }) { item ->
                ResultCard(
                    row = item,
                    selected = item.result.username in selected,
                    onSelected = { value ->
                        if (value && item.result.username !in selected) selected.add(item.result.username)
                        if (!value) selected.remove(item.result.username)
                    },
                    onFavorite = { viewModel.toggleFavorite(item.result.username, !item.isFavorite) },
                    onCopy = {
                        clipboard.setText(AnnotatedString(item.result.username))
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    },
                    onOpen = { openTikTokProfile(context, item.result.username) }
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    row: ResultWithFavorite,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = selected, onCheckedChange = onSelected)
                Column(Modifier.weight(1f)) {
                    Text(row.result.username, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${stringResource(R.string.rarity)}: ${row.result.rarity}/100  •  ${stringResource(R.string.tags)}: ${localizedTags(row.result.tagsCsv)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onFavorite) {
                    Text(if (row.isFavorite) "★" else "☆")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open_tiktok)) }
            }
        }
    }
}


@Composable
private fun localizedTags(rawTags: String): String = rawTags.split(',')
    .filter { it.isNotBlank() }
    .joinToString("، ") { tag ->
        stringResource(
            when (tag) {
                "letters-only" -> R.string.tag_letters_only
                "digits-only" -> R.string.tag_digits_only
                "underscore" -> R.string.tag_underscore
                "period" -> R.string.tag_period
                "vowel-heavy" -> R.string.tag_vowel_heavy
                "palindrome" -> R.string.tag_palindrome
                "same-letter" -> R.string.tag_same_letter
                "doubles" -> R.string.tag_doubles
                "alternating" -> R.string.tag_alternating
                "pronounceable" -> R.string.tag_pronounceable
                else -> R.string.tags
            }
        )
    }
