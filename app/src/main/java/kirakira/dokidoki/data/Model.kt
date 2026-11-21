package kirakira.dokidoki.data

import java.io.File

enum class ProcessMode(val title: String, val subtitle: String, val engineValue: String) {
    PINYIN_AND_AUX("刷拼音和辅助码", "补全缺编码行的拼音并写入辅助码", "autoAux"),
    PINYIN_ONLY("仅刷拼音", "只补拼音，不动辅助码", "autoOnly"),
    AUX_ONLY("仅刷辅助码", "保留已有拼音，只替换辅助码", "existing"),
    AUX_STRIP("仅删辅助码", "保留已有拼音，仅删除其中的辅助码", "strip"),
}

enum class AuxType(val label: String, val key: String) {
    YUANMO("渊默码", "yuanmo"),
    ZIRAN("自然码", "ziranma"),
    XIAOHE("小鹤（万象野鹤扩展）", "xiaohe"),
    MOQI("墨奇", "moqi"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class ToneMode(val label: String, val engineValue: String) {
    TONED("有声调", "symbol"),
    TONELESS("无声调", "none"),
}

enum class OutputDelimiter(val label: String, val engineValue: String) {
    TAB("Tab", "tab"),
    SPACE("空白", "space"),
}

enum class FileSortMode(val label: String) {
    MODIFIED("修改时间"),
    SIZE("文件大小"),
    NAME("文件名"),
}

enum class InputEncoding(val label: String, val engineValue: String) {
    UTF8("UTF-8", "UTF-8"),
    GB18030("GB18030", "GB18030"),
}

enum class ExportConflictMode(val label: String) {
    OVERWRITE("覆盖原文件"),
    RENAME("不覆盖，自动加序号"),
}

data class QueueFile(
    val id: String,
    val name: String,
    val processing: Boolean = false,
)

data class ProcessOptions(
    val mode: ProcessMode = ProcessMode.PINYIN_AND_AUX,
    val auxType: AuxType = AuxType.YUANMO,
    val auxSeparator: String = ";",
    val replaceAux: Boolean = true,
    val replacePinyin: Boolean = false,
    val tone: ToneMode = ToneMode.TONED,
    val outputDelimiter: OutputDelimiter = OutputDelimiter.TAB,
    val inputEncoding: InputEncoding = InputEncoding.UTF8,
    val wordColumn: Int = 1,
    val pinyinColumn: Int = 2,
    val weight: Int = 120,
)

data class ExportEntry(
    val name: String,
    val file: File,
)

data class ExportPayload(
    val name: String,
    val entries: List<ExportEntry>,
    val fileId: String? = null,
)

data class AmbiguousReading(
    val character: String,
    val used: String,
    val others: List<String>,
    val toneUnspecified: Boolean = false,
)

data class AmbiguousEntry(
    val fileName: String,
    val line: Int,
    val message: String,
    val fileId: String = fileName,
    val pinyin: String = "",
    val readings: List<AmbiguousReading> = emptyList(),
)

data class AmbiguousFileSummary(
    val fileId: String,
    val fileName: String,
    val count: Int,
    val dataPath: String,
    val indexPath: String,
    val startIndex: Int,
)

data class AmbiguousFileGroup(
    val fileId: String,
    val fileName: String,
    val entries: List<AmbiguousEntry> = emptyList(),
    val summary: AmbiguousFileSummary? = null,
) {
    val entryCount: Int get() = summary?.count ?: entries.size
}

fun ProcessResult.ambiguityGroups(): List<AmbiguousFileGroup> = if (ambiguousFiles.isNotEmpty()) {
    ambiguousFiles.map { AmbiguousFileGroup(it.fileId, it.fileName, summary = it) }
} else {
    // Preview/sample results have a small in-memory list; production results use the sidecars.
    ambiguousLines.groupBy { it.fileId }.map { (id, rows) -> AmbiguousFileGroup(id, rows.first().fileName, rows) }
}

data class ProcessResult(
    val totalLines: Int,
    val pinyinAdded: Int,
    val auxReplaced: Int,
    val ambiguousCount: Int,
    val ambiguousLines: List<AmbiguousEntry> = emptyList(),
    val elapsedSeconds: Double,
    val ambiguousFiles: List<AmbiguousFileSummary> = emptyList(),
    val outputName: String,
    val counts: Map<String, Int> = emptyMap(),
    val samples: List<String> = emptyList(),
    val missingChars: String = "",
)

data class InputFile(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long = file.length(),
)

data class ProcessOutput(
    val name: String,
    val file: File,
    val sizeBytes: Long = file.length(),
    val result: ProcessResult,
)

data class ProcessOutcome(
    val result: ProcessResult,
    val outputs: List<ProcessOutput>,
)

interface ProcessingEngine {
    suspend fun process(
        file: InputFile,
        options: ProcessOptions,
        onProgress: (Float) -> Unit,
    ): ProcessOutcome
}

class MockProcessingEngine : ProcessingEngine {
    override suspend fun process(
        file: InputFile,
        options: ProcessOptions,
        onProgress: (Float) -> Unit,
    ): ProcessOutcome {
        onProgress(0.35f)
        kotlinx.coroutines.delay(250)
        onProgress(0.7f)
        kotlinx.coroutines.delay(250)
        onProgress(1f)
        val result = demoProcessResult(file.name)
        val output = File.createTempFile("dict-mock-", ".txt")
        file.file.copyTo(output, overwrite = true)
        return ProcessOutcome(result, listOf(ProcessOutput(file.name, output, output.length(), result)))
    }
}

fun demoProcessResult(outputName: String = "万象.dict.yaml"): ProcessResult = ProcessResult(
    totalLines = 12842,
    pinyinAdded = 9630,
    auxReplaced = 12830,
    ambiguousCount = 12,
    ambiguousLines = listOf(
        AmbiguousEntry("万象.dict.yaml", 214, "多音字：重要（重、要）"),
        AmbiguousEntry("万象.dict.yaml", 1021, "多音字：银行（行）"),
        AmbiguousEntry("万象.dict.yaml", 2873, "多音字：长大（长、大）"),
        AmbiguousEntry("万象.dict.yaml", 3390, "多音字：降落（落）"),
        AmbiguousEntry("万象.dict.yaml", 4102, "多音字：根据地（地）"),
        AmbiguousEntry("八股文.dict.yaml", 88, "多音字：成长（长）"),
        AmbiguousEntry("八股文.dict.yaml", 612, "多音字：鲜血（血）"),
        AmbiguousEntry("八股文.dict.yaml", 3721, "多音字：教课（教）"),
        AmbiguousEntry("八股文.dict.yaml", 4556, "多音字：重创（重）"),
        AmbiguousEntry("术语库.tsv", 204, "多音字：强迫（强）"),
        AmbiguousEntry("术语库.tsv", 1204, "多音字：得劲（得）"),
        AmbiguousEntry("术语库.tsv", 3300, "多音字：相似（似）"),
    ),
    elapsedSeconds = 3.2,
    outputName = outputName,
    counts = mapOf("多音字行" to 12),
)

data class DictUiState(
    val selectedTab: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val processMode: ProcessMode = ProcessMode.PINYIN_AND_AUX,
    val auxType: AuxType = AuxType.YUANMO,
    val auxSeparator: String = ";",
    val replaceAux: Boolean = true,
    val replacePinyin: Boolean = false,
    val tone: ToneMode = ToneMode.TONED,
    val outputDelimiter: OutputDelimiter = OutputDelimiter.TAB,
    val inputEncoding: InputEncoding = InputEncoding.UTF8,
    val wordColumn: Int = 1,
    val pinyinColumn: Int = 2,
    val weight: Int = 120,
    val processedLines: Int = 0,
    val processedPinyin: Int = 0,
    val sortMode: FileSortMode = FileSortMode.MODIFIED,
    val sortDescending: Boolean = true,
    val exportDirectoryUri: String? = null,
    val exportDirectoryName: String = "Download",
    val exportConflictMode: ExportConflictMode = ExportConflictMode.RENAME,
    val files: List<DictFile> = emptyList(),
    val currentFileId: String? = null,
    val processing: Boolean = false,
    val estimatedSeconds: Int = 0,
    val processingFile: String? = null,
    val progress: Float = 0f,
    val hasResult: Boolean = false,
    val result: ProcessResult = emptyProcessResult(),
    val selectionMode: Boolean = false,
    val selectedFileIds: Set<String> = emptySet(),
    val autoExportPending: Boolean = false,
    val queueFiles: List<QueueFile> = emptyList(),
    val showQueue: Boolean = false,
    val libraryFocusId: String? = null,
    val libraryFocusRequest: Int = 0,
    val message: String? = null,
)

data class DictFile(
    val name: String,
    val meta: String,
    val status: FileStatus,
    val id: String? = null,
    val sizeBytes: Long = 0,
    val uri: String? = null,
    val hasOutput: Boolean = false,
    val modifiedAt: Long = 0L,
)

enum class FileStatus(val label: String, val highlighted: Boolean) {
    AUX_DONE("已刷辅助码", true),
    AUX_STRIPPED("已删辅助码", true),
    PINYIN_DONE("已刷拼音", true),
    EXPORTED("已导出", true),
    CANCELLED("已取消", false),
    PENDING("待处理", false),
    FAILED("处理失败", false),
}

fun emptyProcessResult(): ProcessResult = ProcessResult(
    totalLines = 0,
    pinyinAdded = 0,
    auxReplaced = 0,
    ambiguousCount = 0,
    ambiguousLines = emptyList(),
    elapsedSeconds = 0.0,
    outputName = "",
)

val SampleFiles = listOf(
    DictFile("万象.dict.yaml", "Rime YAML · 1.2 MB · 昨天", FileStatus.AUX_DONE, id = "sample", sizeBytes = 1_258_291, hasOutput = true, modifiedAt = 6L),
    DictFile("八股文.dict.yaml", "Rime YAML · 860 KB · 3 天前", FileStatus.PINYIN_DONE, sizeBytes = 880_640, hasOutput = true, modifiedAt = 5L),
    DictFile("user_words.txt", "TXT · 12 KB · 上周", FileStatus.PENDING, sizeBytes = 12_288, modifiedAt = 4L),
    DictFile("术语库.tsv", "TSV · 204 KB · 上周", FileStatus.AUX_DONE, sizeBytes = 208_896, hasOutput = true, modifiedAt = 3L),
    DictFile("moqi_backup.zip", "ZIP · 4.8 MB · 上月", FileStatus.PENDING, sizeBytes = 5_033_164, modifiedAt = 2L),
    DictFile("自定义词条.dict.yaml", "Rime YAML · 96 KB · 上月", FileStatus.PINYIN_DONE, sizeBytes = 98_304, hasOutput = true, modifiedAt = 1L),
)