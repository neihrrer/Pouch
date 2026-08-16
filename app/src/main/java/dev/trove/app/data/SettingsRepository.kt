package dev.trove.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, SEPIA }

/** Reader typefaces. SANS is the system default. */
enum class ReaderFont { SANS, SERIF, OPENDYSLEXIC, MONOSPACE }

/** Reader text alignment. */
enum class ReaderAlign { LEFT, JUSTIFIED, CENTER }

/** How long to keep downloaded offline copies / feed items. */
enum class OfflineRetention(val millis: Long, val label: String) {
    WEEK(7L * 24 * 60 * 60 * 1000, "1 week"),
    MONTH(30L * 24 * 60 * 60 * 1000, "1 month"),
    THREE_MONTHS(91L * 24 * 60 * 60 * 1000, "3 months"),
    ALWAYS(0L, "Always"),
}

/** How often feeds are refreshed in the background. */
enum class FeedFetchInterval(val millis: Long, val label: String) {
    OFF(0L, "Off"),
    FIFTEEN(15L * 60 * 1000, "15 min"),
    THIRTY(30L * 60 * 1000, "30 min"),
    HOURLY(60L * 60 * 1000, "1 hour"),
    THREE_HOURS(3L * 60 * 60 * 1000, "3 hours"),
    SIX_HOURS(6L * 60 * 60 * 1000, "6 hours"),
    TWELVE_HOURS(12L * 60 * 60 * 1000, "12 hours"),
    DAILY(24L * 60 * 60 * 1000, "24 hours"),
}

data class ReaderSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** True OLED: pure black backgrounds in dark mode. */
    val oled: Boolean = false,
    // ---- reading mode only ----
    val readerFont: ReaderFont = ReaderFont.SANS,
    val fontSizeScale: Float = 1f,
    val lineHeightScale: Float = 1f,
    /** Extra letter spacing in sp (0–2). */
    val letterSpacing: Float = 0.2f,
    /** Extra word spacing, 0–1.5 (emulated with thin spaces). */
    val wordSpacing: Float = 0f,
    val textAlign: ReaderAlign = ReaderAlign.LEFT,
    // ---- offline ----
    /** Auto-download images of newly saved articles. */
    val autoOffline: Boolean = false,
    val offlineRetention: OfflineRetention = OfflineRetention.ALWAYS,
    // ---- feeds ----
    /** Background refresh interval for feeds. */
    val feedInterval: FeedFetchInterval = FeedFetchInterval.OFF,
    /** How long unsaved feed items are kept. */
    val feedRetention: OfflineRetention = OfflineRetention.MONTH,
    val syncOnStart: Boolean = false,
    val syncOnlyWifi: Boolean = false,
    val syncOnlyCharging: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            themeMode = ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name),
            dynamicColor = prefs[KEY_DYNAMIC] ?: true,
            oled = prefs[KEY_OLED] ?: false,
            readerFont = ReaderFont.valueOf(prefs[KEY_READER_FONT] ?: ReaderFont.SANS.name),
            fontSizeScale = prefs[KEY_FONT]?.toFloat() ?: 1f,
            lineHeightScale = prefs[KEY_LINE]?.toFloat() ?: 1f,
            letterSpacing = prefs[KEY_LETTER_SPACING] ?: 0.2f,
            wordSpacing = prefs[KEY_WORD_SPACING] ?: 0f,
            textAlign = ReaderAlign.valueOf(prefs[KEY_TEXT_ALIGN] ?: ReaderAlign.LEFT.name),
            autoOffline = prefs[KEY_AUTO_OFFLINE] ?: false,
            offlineRetention = OfflineRetention.valueOf(
                prefs[KEY_OFFLINE_RETENTION] ?: OfflineRetention.ALWAYS.name
            ),
            feedInterval = FeedFetchInterval.valueOf(
                prefs[KEY_FEED_INTERVAL] ?: FeedFetchInterval.OFF.name
            ),
            feedRetention = OfflineRetention.valueOf(
                prefs[KEY_FEED_RETENTION] ?: OfflineRetention.MONTH.name
            ),
            syncOnStart = prefs[KEY_SYNC_ON_START] ?: false,
            syncOnlyWifi = prefs[KEY_SYNC_WIFI] ?: false,
            syncOnlyCharging = prefs[KEY_SYNC_CHARGING] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[KEY_DYNAMIC] = enabled }

    suspend fun setOled(enabled: Boolean) = context.dataStore.edit { it[KEY_OLED] = enabled }

    suspend fun setReaderFont(font: ReaderFont) = context.dataStore.edit { it[KEY_READER_FONT] = font.name }

    suspend fun setFontSizeScale(scale: Float) = context.dataStore.edit { it[KEY_FONT] = scale.toDouble() }

    suspend fun setLineHeightScale(scale: Float) = context.dataStore.edit { it[KEY_LINE] = scale.toDouble() }

    suspend fun setLetterSpacing(sp: Float) = context.dataStore.edit { it[KEY_LETTER_SPACING] = sp }

    suspend fun setWordSpacing(scale: Float) = context.dataStore.edit { it[KEY_WORD_SPACING] = scale }

    suspend fun setTextAlign(align: ReaderAlign) = context.dataStore.edit { it[KEY_TEXT_ALIGN] = align.name }

    suspend fun setAutoOffline(enabled: Boolean) = context.dataStore.edit { it[KEY_AUTO_OFFLINE] = enabled }

    suspend fun setOfflineRetention(retention: OfflineRetention) =
        context.dataStore.edit { it[KEY_OFFLINE_RETENTION] = retention.name }

    suspend fun setFeedInterval(interval: FeedFetchInterval) =
        context.dataStore.edit { it[KEY_FEED_INTERVAL] = interval.name }

    suspend fun setFeedRetention(retention: OfflineRetention) =
        context.dataStore.edit { it[KEY_FEED_RETENTION] = retention.name }

    suspend fun setSyncOnStart(enabled: Boolean) =
        context.dataStore.edit { it[KEY_SYNC_ON_START] = enabled }

    suspend fun setSyncOnlyWifi(enabled: Boolean) =
        context.dataStore.edit { it[KEY_SYNC_WIFI] = enabled }

    suspend fun setSyncOnlyCharging(enabled: Boolean) =
        context.dataStore.edit { it[KEY_SYNC_CHARGING] = enabled }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        private val KEY_OLED = booleanPreferencesKey("true_oled")
        private val KEY_READER_FONT = stringPreferencesKey("reader_font")
        private val KEY_FONT = doublePreferencesKey("font_size_scale")
        private val KEY_LINE = doublePreferencesKey("line_height_scale")
        private val KEY_LETTER_SPACING = floatPreferencesKey("letter_spacing")
        private val KEY_WORD_SPACING = floatPreferencesKey("word_spacing")
        private val KEY_TEXT_ALIGN = stringPreferencesKey("text_align")
        private val KEY_AUTO_OFFLINE = booleanPreferencesKey("auto_offline")
        private val KEY_OFFLINE_RETENTION = stringPreferencesKey("offline_retention")
        private val KEY_FEED_INTERVAL = stringPreferencesKey("feed_interval")
        private val KEY_FEED_RETENTION = stringPreferencesKey("feed_retention")
        private val KEY_SYNC_ON_START = booleanPreferencesKey("sync_on_start")
        private val KEY_SYNC_WIFI = booleanPreferencesKey("sync_only_wifi")
        private val KEY_SYNC_CHARGING = booleanPreferencesKey("sync_only_charging")
    }
}
