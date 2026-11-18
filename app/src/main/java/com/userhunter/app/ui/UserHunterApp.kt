package com.userhunter.app.ui

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.userhunter.app.R
import com.userhunter.app.ui.screens.HuntScreen
import com.userhunter.app.ui.screens.QuickCheckScreen
import com.userhunter.app.ui.screens.ResultsScreen
import com.userhunter.app.ui.screens.SettingsScreen
import com.userhunter.app.ui.theme.UserHunterTheme
import java.util.Locale

private enum class AppTab { HUNT, RESULTS, QUICK, SETTINGS }

@Composable
fun UserHunterApp(viewModel: MainViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val form by viewModel.form.collectAsState()
    val session by viewModel.session.collectAsState()
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(baseContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val locale = remember(settings.language) {
        if (settings.language == "ar") Locale.forLanguageTag("ar") else Locale.ENGLISH
    }
    val localizedConfiguration = remember(baseConfiguration, locale) {
        Configuration(baseConfiguration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }
    val layoutDirection = if (settings.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
        LocalLayoutDirection provides layoutDirection
    ) {
        UserHunterTheme(darkTheme = settings.darkTheme) {
            var selectedTab by rememberSaveable { mutableStateOf(AppTab.HUNT) }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == AppTab.HUNT,
                            onClick = { selectedTab = AppTab.HUNT },
                            icon = { Text("⌕") },
                            label = { Text(androidx.compose.ui.res.stringResource(R.string.tab_hunt)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.RESULTS,
                            onClick = { selectedTab = AppTab.RESULTS },
                            icon = { Text("★") },
                            label = { Text(androidx.compose.ui.res.stringResource(R.string.tab_results)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.QUICK,
                            onClick = { selectedTab = AppTab.QUICK },
                            icon = { Text("✓") },
                            label = { Text(androidx.compose.ui.res.stringResource(R.string.tab_quick_check)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppTab.SETTINGS,
                            onClick = { selectedTab = AppTab.SETTINGS },
                            icon = { Text("⚙") },
                            label = { Text(androidx.compose.ui.res.stringResource(R.string.tab_settings)) }
                        )
                    }
                }
            ) { innerPadding ->
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    when (selectedTab) {
                        AppTab.HUNT -> HuntScreen(viewModel, form, settings, session)
                        AppTab.RESULTS -> ResultsScreen(viewModel)
                        AppTab.QUICK -> QuickCheckScreen(viewModel)
                        AppTab.SETTINGS -> SettingsScreen(viewModel, settings)
                    }
                }
            }
        }
    }
}
