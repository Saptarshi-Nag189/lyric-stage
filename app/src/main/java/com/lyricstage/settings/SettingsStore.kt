package com.lyricstage.settings

import android.content.Context
import android.provider.Settings as SystemSettings
import androidx.core.content.edit
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.stage.LyricFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundMode { LIQUID, ARTWORK, SOLID }

/** What sends ripples through the liquid background. */
enum class RippleMode {
    /** Each sung word; beats or a gentle pace when nothing is sung. */
    WORDS,
    /** Only the beat of the music (heard through the microphone), for every song. */
    BEATS,
}

data class StageSettings(
    /** Null means Auto. */
    val template: TemplateId? = null,
    val background: BackgroundMode = BackgroundMode.LIQUID,
    val fontScale: Float = 1f,
    /** 0 keeps highlights subtle, 1 is fully vivid with glow. */
    val highlightIntensity: Float = 0.85f,
    val reduceMotion: Boolean = false,
    val showFps: Boolean = false,
    /** Fetch lyrics in the background as songs change, so they are ready when the app opens. */
    val backgroundPrefetch: Boolean = true,
    /** Nudge estimated word times towards vocal onsets heard in the playing audio. */
    val audioSync: Boolean = false,
    /** Look ahead to queued songs only on Wi-Fi (or another unmetered network). */
    val lookAheadWifiOnly: Boolean = true,
    /** Pure black background and simple fades only, to save battery. */
    val batterySaver: Boolean = false,
    val ripples: RippleMode = RippleMode.WORDS,
    /** Extra delay for Bluetooth audio, in ms, applied only while it is connected. */
    val bluetoothDelayMs: Int = 0,
    /** The stage's typeface for Latin lyrics; every app screen stays on Bricolage. */
    val lyricFont: LyricFont = LyricFont.BRICOLAGE,
    /** Show unsynced lyrics as scrolling text; off by default, so a song without timing
     *  shows a short notice instead of a wall of text. */
    val paragraphLyrics: Boolean = false,
    /** Brightness and size of the background's colour pools, 0 to 2 (1 is the standard look). */
    val pools: Float = 1f,
    /** Strength of the ripples, 0 to 2. */
    val rippleStrength: Float = 1f,
    /** The one glass switch: the lyrics and the UI are glass together. */
    val glassOn: Boolean = true,
    /** Frosting of the glass, the lyrics and the panels alike: 0 clear, 1 milky. */
    val frost: Float = 0.4f,
    /** The layouts Auto picks from, when no template is locked. Empty means all of them. */
    val autoPool: Set<TemplateId> = TemplateId.entries.toSet(),
) {
    /** How much glass the lyrics are drawn with: all of it when the switch is on, none
     *  when it is off. The stage still turns the line to glass while the controls are
     *  open whatever the switch says - there the glass is the dimming. */
    val glassAmount: Float
        get() = if (glassOn) 1f else 0f
}

/** Small settings store on SharedPreferences, exposed as a StateFlow. */
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<StageSettings> = _settings.asStateFlow()

    fun update(transform: (StageSettings) -> StageSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString(K_TEMPLATE, next.template?.name)
            putString(K_BACKGROUND, next.background.name)
            putFloat(K_FONT, next.fontScale)
            putFloat(K_HIGHLIGHT, next.highlightIntensity)
            putBoolean(K_REDUCE, next.reduceMotion)
            putBoolean(K_FPS, next.showFps)
            putBoolean(K_PREFETCH, next.backgroundPrefetch)
            putBoolean(K_AUDIO, next.audioSync)
            putBoolean(K_WIFI, next.lookAheadWifiOnly)
            putBoolean(K_SAVER, next.batterySaver)
            putString(K_RIPPLES, next.ripples.name)
            putInt(K_BT_DELAY, next.bluetoothDelayMs)
            putString(K_LYRIC_FONT, next.lyricFont.name)
            putBoolean(K_PARAGRAPH, next.paragraphLyrics)
            putFloat(K_POOLS, next.pools)
            putFloat(K_RIPPLE_STRENGTH, next.rippleStrength)
            putBoolean(K_GLASS_ON, next.glassOn)
            putFloat(K_FROST, next.frost)
            putString(K_AUTO_POOL, next.autoPool.joinToString(",") { it.name })
        }
        _settings.value = next
    }

    /** True when the system animator duration scale is 0 (animations off). */
    fun systemReducesMotion(): Boolean =
        SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private fun read() = StageSettings(
        template = TemplateId.fromName(prefs.getString(K_TEMPLATE, null)),
        background = prefs.getString(K_BACKGROUND, null)?.let { runCatching { BackgroundMode.valueOf(it) }.getOrNull() } ?: BackgroundMode.LIQUID,
        fontScale = prefs.getFloat(K_FONT, 1f),
        highlightIntensity = prefs.getFloat(K_HIGHLIGHT, 0.85f),
        reduceMotion = prefs.getBoolean(K_REDUCE, false),
        showFps = prefs.getBoolean(K_FPS, false),
        backgroundPrefetch = prefs.getBoolean(K_PREFETCH, true),
        audioSync = prefs.getBoolean(K_AUDIO, false),
        lookAheadWifiOnly = prefs.getBoolean(K_WIFI, true),
        batterySaver = prefs.getBoolean(K_SAVER, false),
        ripples = prefs.getString(K_RIPPLES, null)?.let { runCatching { RippleMode.valueOf(it) }.getOrNull() } ?: RippleMode.WORDS,
        bluetoothDelayMs = prefs.getInt(K_BT_DELAY, 0),
        lyricFont = LyricFont.fromName(prefs.getString(K_LYRIC_FONT, null)),
        paragraphLyrics = prefs.getBoolean(K_PARAGRAPH, false),
        pools = prefs.getFloat(K_POOLS, 1f),
        rippleStrength = prefs.getFloat(K_RIPPLE_STRENGTH, 1f),
        // the glass-strength slider and the glass-controls switch it replaces are read
        // one last time, so the switch is on when either of them had the glass showing
        glassOn = if (prefs.contains(K_GLASS_ON)) prefs.getBoolean(K_GLASS_ON, true)
            else prefs.getFloat(K_GLASS, 0f) > 0.01f || prefs.getBoolean(K_GLASS_UI, true),
        frost = prefs.getFloat(K_FROST, 0.4f),
        autoPool = prefs.getString(K_AUTO_POOL, null)
            ?.split(',')?.mapNotNull(TemplateId::fromName)?.toSet().orEmpty(),
    )

    private companion object {
        const val K_TEMPLATE = "template"
        const val K_BACKGROUND = "background"
        const val K_FONT = "font_scale"
        const val K_HIGHLIGHT = "highlight"
        const val K_REDUCE = "reduce_motion"
        const val K_FPS = "show_fps"
        const val K_PREFETCH = "background_prefetch"
        const val K_AUDIO = "audio_sync"
        const val K_WIFI = "look_ahead_wifi_only"
        const val K_SAVER = "battery_saver"
        const val K_RIPPLES = "ripples"
        const val K_BT_DELAY = "bluetooth_delay_ms"
        const val K_LYRIC_FONT = "lyric_font"
        const val K_PARAGRAPH = "paragraph_lyrics"
        const val K_POOLS = "pools"
        const val K_RIPPLE_STRENGTH = "ripple_strength"
        const val K_GLASS_ON = "glass_on"
        const val K_FROST = "frost"
        /** The two controls the one switch replaced; read once more to carry the choice over. */
        const val K_GLASS = "glass"
        const val K_GLASS_UI = "glass_ui"
        const val K_AUTO_POOL = "auto_pool"
    }
}
