package com.lyricstage.ui

import android.os.Bundle
import kotlinx.coroutines.launch
import com.lyricstage.R
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lyricstage.LyricStageApp
import com.lyricstage.media.NotificationAccess
import com.lyricstage.ui.debug.FitCheckScreen
import com.lyricstage.ui.debug.LyricsListScreen
import com.lyricstage.ui.debug.NowPlayingDebugScreen
import com.lyricstage.ui.theme.LyricStageTheme

enum class Screen { STAGE, SETTINGS, ABOUT, DEBUG_NOW_PLAYING, DEBUG_LYRICS, FIT_CHECK }

class MainActivity : ComponentActivity() {
    private val container get() = (application as LyricStageApp).container
    private val accessGranted = mutableStateOf(false)
    private val systemReduceMotion = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSpotify(intent)
        setContent {
            LyricStageTheme {
                val vm: StageViewModel = viewModel { StageViewModel(container) }
                App(vm, accessGranted.value, systemReduceMotion.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSpotify(intent)
    }

    /** Finishes Spotify sign-in when the browser hands the redirect back to the app. */
    private fun handleSpotify(intent: Intent?) {
        val uri = intent?.data
        if (!container.spotify.isCallback(uri)) return
        setIntent(Intent(this, MainActivity::class.java))
        container.appScope.launch {
            val ok = container.spotify.complete(uri!!)
            Toast.makeText(this@MainActivity, if (ok) R.string.spotify_linked else R.string.spotify_link_failed, Toast.LENGTH_SHORT).show()
            if (ok) container.librarySync.syncNow()
        }
    }

    override fun onStart() {
        super.onStart()
        refreshAccess()
    }

    override fun onResume() {
        super.onResume()
        // the user may have just granted access in system settings
        refreshAccess()
        systemReduceMotion.value = container.settings.systemReducesMotion()
    }

    override fun onStop() {
        super.onStop()
        container.sessions.stop()
    }

    private fun refreshAccess() {
        val granted = NotificationAccess.isGranted(this)
        accessGranted.value = granted
        // access turned off in system settings: stop listening, so it starts cleanly when given back
        if (granted) container.sessions.start() else container.sessions.stop()
    }
}

@Composable
private fun App(vm: StageViewModel, accessGranted: Boolean, systemReduceMotion: Boolean) {
    // Without notification access no player can be seen and no lyrics would ever come, which
    // would look like a bug: show onboarding again. Also when the setting reads as on but
    // Android still refuses the sessions (the listener not connected yet after an update).
    val denied by vm.sessions.accessDenied.collectAsStateWithLifecycle()
    if (!accessGranted || denied) {
        OnboardingScreen()
        return
    }
    var screen by rememberSaveable { mutableStateOf(Screen.STAGE) }
    BackHandler(enabled = screen != Screen.STAGE) {
        screen = if (screen == Screen.SETTINGS) Screen.STAGE else Screen.SETTINGS
    }
    when (screen) {
        Screen.STAGE -> StageScreen(vm, systemReduceMotion, onOpenSettings = { screen = Screen.SETTINGS })
        Screen.SETTINGS -> SettingsScreen(vm, systemReduceMotion, onBack = { screen = Screen.STAGE }, onOpen = { screen = it })
        Screen.ABOUT -> AboutScreen(vm, onBack = { screen = Screen.SETTINGS })
        Screen.DEBUG_NOW_PLAYING -> NowPlayingDebugScreen(vm, onBack = { screen = Screen.SETTINGS })
        Screen.DEBUG_LYRICS -> LyricsListScreen(vm, onBack = { screen = Screen.SETTINGS })
        Screen.FIT_CHECK -> FitCheckScreen(vm, onBack = { screen = Screen.SETTINGS })
    }
}
