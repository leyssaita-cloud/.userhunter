package com.userhunter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.userhunter.app.R
import com.userhunter.app.core.CheckResult
import com.userhunter.app.core.CheckState
import com.userhunter.app.ui.MainViewModel
import com.userhunter.app.ui.openTikTokProfile

@Composable
fun QuickCheckScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val username by viewModel.quickUsername.collectAsState()
    val result by viewModel.quickResult.collectAsState()
    val busy by viewModel.quickBusy.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = username,
            onValueChange = viewModel::setQuickUsername,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.username_hint)) },
            singleLine = true
        )
        Button(
            onClick = viewModel::quickCheck,
            enabled = !busy && username.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator()
            else Text(stringResource(R.string.check_now))
        }
        Text(stringResource(R.string.quick_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        result?.let { checkResult ->
            CheckResultCard(checkResult)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(checkResult.username))
                }) { Text(stringResource(R.string.copy)) }
                TextButton(
                    onClick = { openTikTokProfile(context, checkResult.username) },
                    enabled = checkResult.state != CheckState.INVALID
                ) { Text(stringResource(R.string.open_tiktok)) }
            }
        }
    }
}

@Composable
fun CheckResultCard(result: CheckResult) {
    val stateLabel = when (result.state) {
        CheckState.AVAILABLE_LIKELY -> stringResource(R.string.result_available)
        CheckState.TAKEN -> stringResource(R.string.result_taken)
        CheckState.INVALID -> stringResource(R.string.result_invalid)
        CheckState.BLOCKED -> stringResource(R.string.result_blocked)
        CheckState.UNKNOWN -> stringResource(R.string.result_unknown)
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(result.username, style = MaterialTheme.typography.titleLarge)
            Text(stateLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            result.httpStatus?.let { Text("HTTP $it", style = MaterialTheme.typography.bodySmall) }
            Text(result.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.state == CheckState.AVAILABLE_LIKELY) {
                Text(stringResource(R.string.available_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
