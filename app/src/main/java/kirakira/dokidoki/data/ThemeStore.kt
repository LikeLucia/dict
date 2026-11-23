package kirakira.dokidoki.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "dict_settings")

data class SavedSettings(
    val processMode: ProcessMode = ProcessMode.PINYIN_AND_AUX,
    val auxType: AuxType = AuxType.KIRAKIRA,
    val tone: ToneMode = ToneMode.TONED,
    val outputDelimiter: OutputDelimiter = OutputDelimiter.TAB,
    val inputEncoding: InputEncoding = InputEncoding.UTF8,
    val weight: Int = 120,
    val replaceAux: Boolean = true,
    val replacePinyin: Boolean = false,
    val wordColumn: Int = 1,
    val pinyinColumn: Int = 2,
    val dedupe: Boolean = false,
    val sortMode: FileSortMode = FileSortMode.MODIFIED,
    val sortDescending: Boolean = true,
    val exportDirectoryUri: String? = null,
    val exportDirectoryName: String = "Download",
    val exportConflictMode: ExportConflictMode = ExportConflictMode.RENAME,
)

data class ThemeSnapshot(
    val themeMode: ThemeMode,
    val settings: SavedSettings,
)

class ThemeStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val processModeKey = stringPreferencesKey("process_mode")
    private val auxTypeKey = stringPreferencesKey("aux_type")
    private val toneKey = stringPreferencesKey("tone_mode")
    private val outputDelimiterKey = stringPreferencesKey("output_delimiter")
    private val inputEncodingKey = stringPreferencesKey("input_encoding")
    private val weightKey = stringPreferencesKey("default_weight")
    private val replaceAuxKey = booleanPreferencesKey("replace_aux")
    private val replacePinyinKey = booleanPreferencesKey("replace_pinyin")
    private val wordColumnKey = intPreferencesKey("word_column")
    private val pinyinColumnKey = intPreferencesKey("pinyin_column")
    private val dedupeKey = booleanPreferencesKey("dedupe")
    private val sortModeKey = stringPreferencesKey("sort_mode")
    private val sortDescendingKey = booleanPreferencesKey("sort_descending")
    private val exportDirectoryUriKey = stringPreferencesKey("export_directory_uri")
    private val exportDirectoryNameKey = stringPreferencesKey("export_directory_name")
    private val exportConflictModeKey = stringPreferencesKey("export_conflict_mode")

    val snapshot: Flow<ThemeSnapshot> = context.settingsDataStore.data.map { prefs ->
        ThemeSnapshot(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] }
                ?: ThemeMode.SYSTEM,
            settings = prefs.toSavedSettings(),
        )
    }.distinctUntilChanged()

    val themeMode: Flow<ThemeMode> = snapshot.map { it.themeMode }
    val settings: Flow<SavedSettings> = snapshot.map { it.settings }

    private fun Preferences.toSavedSettings(): SavedSettings = SavedSettings(
        processMode = ProcessMode.entries.firstOrNull { it.name == this[processModeKey] }
            ?: ProcessMode.PINYIN_AND_AUX,
        auxType = AuxType.entries.firstOrNull { it.name == this[auxTypeKey] }
            ?: AuxType.KIRAKIRA,
        tone = ToneMode.entries.firstOrNull { it.name == this[toneKey] }
            ?: ToneMode.TONED,
        outputDelimiter = OutputDelimiter.entries.firstOrNull { it.name == this[outputDelimiterKey] }
            ?: OutputDelimiter.TAB,
        inputEncoding = InputEncoding.entries.firstOrNull { it.name == this[inputEncodingKey] }
            ?: InputEncoding.UTF8,
        weight = this[weightKey]?.toIntOrNull()?.takeIf { it in 1..999999 } ?: 120,
        replaceAux = this[replaceAuxKey] ?: true,
        replacePinyin = this[replacePinyinKey] ?: false,
        wordColumn = this[wordColumnKey]?.takeIf { it > 0 } ?: 1,
        pinyinColumn = this[pinyinColumnKey]?.takeIf { it > 0 } ?: 2,
        dedupe = this[dedupeKey] ?: false,
        sortMode = FileSortMode.entries.firstOrNull { it.name == this[sortModeKey] }
            ?: FileSortMode.MODIFIED,
        sortDescending = this[sortDescendingKey] ?: true,
        exportDirectoryUri = this[exportDirectoryUriKey],
        exportDirectoryName = this[exportDirectoryNameKey]?.takeIf { it.isNotBlank() } ?: "Download",
        exportConflictMode = ExportConflictMode.entries.firstOrNull { it.name == this[exportConflictModeKey] }
            ?: ExportConflictMode.RENAME,
    )

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setProcessMode(mode: ProcessMode) {
        context.settingsDataStore.edit { it[processModeKey] = mode.name }
    }

    suspend fun setAuxType(type: AuxType) {
        context.settingsDataStore.edit { it[auxTypeKey] = type.name }
    }

    suspend fun setTone(mode: ToneMode) {
        context.settingsDataStore.edit { it[toneKey] = mode.name }
    }

    suspend fun setOutputDelimiter(delimiter: OutputDelimiter) {
        context.settingsDataStore.edit { it[outputDelimiterKey] = delimiter.name }
    }

    suspend fun setInputEncoding(encoding: InputEncoding) {
        context.settingsDataStore.edit { it[inputEncodingKey] = encoding.name }
    }

    suspend fun setWeight(weight: Int) {
        context.settingsDataStore.edit { it[weightKey] = weight.toString() }
    }

    suspend fun setDedupe(value: Boolean) {
        context.settingsDataStore.edit { it[dedupeKey] = value }
    }

    suspend fun setReplaceAux(value: Boolean) {
        context.settingsDataStore.edit { it[replaceAuxKey] = value }
    }

    suspend fun setReplacePinyin(value: Boolean) {
        context.settingsDataStore.edit { it[replacePinyinKey] = value }
    }

    suspend fun setColumns(word: Int, pinyin: Int) {
        context.settingsDataStore.edit {
            it[wordColumnKey] = word
            it[pinyinColumnKey] = pinyin
        }
    }

    suspend fun setSortMode(mode: FileSortMode) {
        context.settingsDataStore.edit { it[sortModeKey] = mode.name }
    }

    suspend fun setSortDescending(descending: Boolean) {
        context.settingsDataStore.edit { it[sortDescendingKey] = descending }
    }

    suspend fun setExportDirectory(uri: String?, name: String) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(exportDirectoryUriKey) else it[exportDirectoryUriKey] = uri
            it[exportDirectoryNameKey] = name
        }
    }

    suspend fun setExportConflictMode(mode: ExportConflictMode) {
        context.settingsDataStore.edit { it[exportConflictModeKey] = mode.name }
    }
}
