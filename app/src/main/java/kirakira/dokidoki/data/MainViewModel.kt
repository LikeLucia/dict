package kirakira.dokidoki.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.os.SystemClock
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.coroutines.coroutineContext

class MainViewModel(
    context: Context,
    private val themeStore: ThemeStore,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val engineDelegate = lazy { JsProcessingEngine(appContext) }
    private val engine by engineDelegate
    private val exportStore by lazy { ExportStore(appContext) }
    private val inputFiles = HashMap<String, InputFile>()
    private val outputsByFile = LinkedHashMap<String, List<ProcessOutput>>()
    private val resultsByFile = LinkedHashMap<String, ProcessResult>()
    private var lastRunFileIds = mutableListOf<String>()
    private var pendingExport: ExportPayload? = null
    private var autoExportPayload: ExportPayload? = null

    private data class QueueItem(val fileId: String, val autoCandidate: Boolean)
    private val processingQueue = ArrayDeque<QueueItem>()
    private var queueRunning = false
    private var queueDisplayEnabled = false
    private var processingJob: Job? = null
    @Volatile private var cancelRequested = false
    private var activeFileId: String? = null
    // Queue and publication are confined to Main; the active child can be cancelled independently.
    private var activeItemJob: Job? = null

    private val _state = MutableStateFlow(DictUiState())
    val state: StateFlow<DictUiState> = _state.asStateFlow()

    private val sessionDirectory = File(appContext.noBackupFilesDir, "kirakira-session")
    private val sessionInputDirectory = File(sessionDirectory, "inputs")
    private val sessionOutputDirectory = File(sessionDirectory, "outputs")
    private val sessionDetailDirectory = File(sessionDirectory, "details")
    private val sessionFile = File(sessionDirectory, "session.json")
    private val sessionWriteMutex = Mutex()
    private val sessionRevision = AtomicLong()
    private var lastWrittenSessionRevision = 0L
    private var restoredLegacySession = false

    private data class SessionSnapshot(
        val files: List<DictFile>,
        val currentFileId: String?,
        val outputsByFile: Map<String, List<ProcessOutput>>,
        val resultsByFile: Map<String, ProcessResult>,
        val lastRunFileIds: List<String>,
    )

    private val sessionRestoreJob = viewModelScope.launch(Dispatchers.IO) { restoreSession() }

    init {
        viewModelScope.launch {
            themeStore.snapshot.collect { snapshot ->
                val saved = snapshot.settings
                _state.update {
                    it.copy(
                        themeMode = snapshot.themeMode,
                        processMode = saved.processMode,
                        auxType = saved.auxType,
                        tone = saved.tone,
                        outputDelimiter = saved.outputDelimiter,
                        inputEncoding = saved.inputEncoding,
                        weight = saved.weight,
                        replaceAux = saved.replaceAux,
                        replacePinyin = saved.replacePinyin,
                        wordColumn = saved.wordColumn,
                        pinyinColumn = saved.pinyinColumn,
                        sortMode = saved.sortMode,
                        sortDescending = saved.sortDescending,
                        reportSortMode = saved.reportSortMode,
                        reportSortDescending = saved.reportSortDescending,
                        exportDirectoryUri = saved.exportDirectoryUri,
                        exportDirectoryName = saved.exportDirectoryName,
                        exportConflictMode = saved.exportConflictMode,
                    )
                }
            }
        }
    }

    private suspend fun restoreSession() {
        val snapshot = try {
            readSessionSnapshot()
        } catch (error: Exception) {
            withContext(Dispatchers.Main.immediate) {
                _state.update { it.copy(message = "本地处理记录读取失败：${error.message ?: "记录格式无效"}") }
            }
            return
        } ?: return

        val restoredInputs = linkedMapOf<String, InputFile>()
        val restoredOutputs = linkedMapOf<String, List<ProcessOutput>>()
        val restoredResults = linkedMapOf<String, ProcessResult>()
        val restoredFiles = snapshot.files.map { file ->
            val id = file.id
            if (id == null || !isSafeSessionId(id)) return@map file

            val input = File(sessionInputDirectory, "$id.bin")
            val needsInput = file.status == FileStatus.PENDING || file.status == FileStatus.FAILED
            if (needsInput && input.isFile) restoredInputs[id] = InputFile(id, file.name, input, input.length())
            else if (!needsInput) runCatching { input.delete() }

            val result = snapshot.resultsByFile[id]
            if (result != null) restoredResults[id] = result
            val outputs = snapshot.outputsByFile[id].orEmpty().mapIndexedNotNull { index, output ->
                val expectedName = "$id-$index.bin"
                if (output.file.name != expectedName) return@mapIndexedNotNull null
                val durable = File(sessionOutputDirectory, expectedName)
                if (durable.isFile) output.copy(file = durable, sizeBytes = durable.length(), result = result ?: output.result)
                else null
            }
            if (outputs.isNotEmpty()) restoredOutputs[id] = outputs

            when {
                file.hasOutput && outputs.isEmpty() -> file.copy(
                    hasOutput = false,
                    meta = file.meta + " · 输出文件缺失",
                )
                (file.status == FileStatus.PENDING || file.status == FileStatus.FAILED) && id !in restoredInputs -> file.copy(
                    status = FileStatus.FAILED,
                    meta = file.meta + " · 输入文件缺失，请重新导入",
                )
                else -> file
            }
        }
        val runIds = snapshot.lastRunFileIds.filter { it in restoredResults }
        val restoredResult = if (runIds.isEmpty()) emptyProcessResult() else {
            mergeResults(
                runIds.mapNotNull { restoredResults[it] },
                runIds.flatMap { restoredOutputs[it].orEmpty() },
            )
        }
        withContext(Dispatchers.Main.immediate) {
            inputFiles.clear()
            inputFiles.putAll(restoredInputs)
            outputsByFile.clear()
            outputsByFile.putAll(restoredOutputs)
            resultsByFile.clear()
            resultsByFile.putAll(restoredResults)
            lastRunFileIds = runIds.toMutableList()
            _state.update { current ->
                current.copy(
                    files = restoredFiles,
                    currentFileId = snapshot.currentFileId?.takeIf { id -> restoredFiles.any { it.id == id } }
                        ?: restoredFiles.firstOrNull { it.id != null }?.id,
                    result = restoredResult,
                    hasResult = runIds.isNotEmpty(),
                    processedLines = restoredResults.values.sumOf { it.totalLines },
                    processedPinyin = restoredResults.values.sumOf { it.pinyinAdded },
                    processing = false,
                    progress = 0f,
                    processingFile = null,
                    estimatedSeconds = 0,
                    queueFiles = emptyList(),
                    showQueue = false,
                    autoExportPending = false,
                )
            }
            if (restoredLegacySession && persistSession()) restoredLegacySession = false
        }
    }

    private fun readSessionSnapshot(): SessionSnapshot? {
        if (!sessionFile.exists() && !File(sessionFile.path + ".bak").exists()) return null
        val root = AtomicFile(sessionFile).openRead().bufferedReader(Charsets.UTF_8).use { reader ->
            readSessionMetadata(reader, sessionDetailDirectory).also { restoredLegacySession = it.optInt("version") == 1 }
        }
        require(root.optInt("version") in 1..2) { "不支持的记录版本" }
        val files = root.optJSONArray("files") ?: JSONArray()
        val restoredFiles = List(files.length()) { index ->
            val item = files.getJSONObject(index)
            val status = runCatching { FileStatus.valueOf(item.optString("status")) }.getOrDefault(FileStatus.FAILED)
            DictFile(
                name = item.optString("name"),
                meta = item.optString("meta"),
                status = status,
                id = item.optNullableString("id"),
                sizeBytes = item.optLong("sizeBytes"),
                uri = item.optNullableString("uri"),
                hasOutput = item.optBoolean("hasOutput"),
                modifiedAt = item.optLong("modifiedAt"),
                polyphonicHandled = item.optBoolean("polyphonicHandled"),
                processedAt = item.optLong("processedAt"),
            )
        }
        val resultObject = root.optJSONObject("resultsByFile") ?: JSONObject()
        val results = linkedMapOf<String, ProcessResult>()
        resultObject.keys().forEach { id ->
            if (isSafeSessionId(id)) results[id] = decodeProcessResult(resultObject.getJSONObject(id))
        }
        val outputObject = root.optJSONObject("outputsByFile") ?: JSONObject()
        val outputs = linkedMapOf<String, List<ProcessOutput>>()
        outputObject.keys().forEach { id ->
            if (!isSafeSessionId(id)) return@forEach
            val array = outputObject.optJSONArray(id) ?: return@forEach
            val result = results[id] ?: emptyProcessResult()
            outputs[id] = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ProcessOutput(
                    name = item.optString("name"),
                    file = File(item.optString("file")),
                    sizeBytes = item.optLong("sizeBytes"),
                    result = result,
                )
            }
        }
        val runIdsJson = root.optJSONArray("lastRunFileIds") ?: JSONArray()
        val runIds = List(runIdsJson.length()) { runIdsJson.optString(it) }.filter(::isSafeSessionId)
        return SessionSnapshot(
            files = restoredFiles,
            currentFileId = root.optNullableString("currentFileId"),
            outputsByFile = outputs,
            resultsByFile = results,
            lastRunFileIds = runIds,
        )
    }

    private suspend fun persistSession(): Boolean {
        val state = _state.value
        val snapshot = SessionSnapshot(
            files = state.files.toList(),
            currentFileId = state.currentFileId,
            outputsByFile = outputsByFile.mapValues { it.value.toList() },
            resultsByFile = resultsByFile.toMap(),
            lastRunFileIds = lastRunFileIds.toList(),
        )
        val revision = sessionRevision.incrementAndGet()
        return try {
            withContext(Dispatchers.IO) {
                sessionWriteMutex.withLock {
                    if (revision <= lastWrittenSessionRevision) return@withLock true
                    if (!sessionDirectory.exists() && !sessionDirectory.mkdirs()) {
                        throw IllegalStateException("无法创建本地记录目录")
                    }
                    val atomicFile = AtomicFile(sessionFile)
                    var stream: FileOutputStream? = null
                    try {
                        val writeStream = atomicFile.startWrite()
                        stream = writeStream
                        SpaceCheckingOutputStream(writeStream, sessionDirectory)
                            .write(encodeSessionSnapshot(snapshot).toString().toByteArray(Charsets.UTF_8))
                        writeStream.fd.sync()
                        atomicFile.finishWrite(writeStream)
                        lastWrittenSessionRevision = revision
                        true
                    } catch (error: Throwable) {
                        stream?.let { failed -> runCatching { atomicFile.failWrite(failed) } }
                        throw error
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _state.update { it.copy(message = "本地处理记录保存失败：${error.message ?: "存储错误"}") }
            false
        }
    }

    private fun encodeSessionSnapshot(snapshot: SessionSnapshot): JSONObject = JSONObject().apply {
        put("version", 2)
        put("currentFileId", snapshot.currentFileId ?: JSONObject.NULL)
        put("files", JSONArray().apply { snapshot.files.forEach { put(encodeDictFile(it)) } })
        put("resultsByFile", JSONObject().apply {
            snapshot.resultsByFile.forEach { (id, result) -> put(id, encodeProcessResult(result)) }
        })
        put("outputsByFile", JSONObject().apply {
            snapshot.outputsByFile.forEach { (id, outputs) ->
                put(id, JSONArray().apply {
                    outputs.forEach { output ->
                        put(JSONObject().apply {
                            put("name", output.name)
                            put("file", output.file.name)
                            put("sizeBytes", output.sizeBytes)
                        })
                    }
                })
            }
        })
        put("lastRunFileIds", JSONArray().apply { snapshot.lastRunFileIds.forEach { put(it) } })
    }

    private fun encodeDictFile(file: DictFile) = JSONObject().apply {
        put("name", file.name)
        put("meta", file.meta)
        put("status", file.status.name)
        put("id", file.id ?: JSONObject.NULL)
        put("sizeBytes", file.sizeBytes)
        put("uri", file.uri ?: JSONObject.NULL)
        put("hasOutput", file.hasOutput)
        put("modifiedAt", file.modifiedAt)
        put("polyphonicHandled", file.polyphonicHandled)
        put("processedAt", file.processedAt)
    }

    private fun encodeProcessResult(result: ProcessResult) = JSONObject().apply {
        put("totalLines", result.totalLines)
        put("pinyinAdded", result.pinyinAdded)
        put("auxReplaced", result.auxReplaced)
        put("ambiguousCount", result.ambiguousCount)
        put("elapsedSeconds", result.elapsedSeconds)
        put("outputName", result.outputName)
        put("counts", JSONObject().apply { result.counts.forEach { (key, value) -> put(key, value) } })
        put("samples", JSONArray().apply { result.samples.forEach { put(it) } })
        put("missingChars", result.missingChars)
        put("ambiguousFiles", JSONArray().apply {
            result.ambiguousFiles.forEach { summary ->
                put(JSONObject().apply {
                    put("fileId", summary.fileId)
                    put("fileName", summary.fileName)
                    put("count", summary.count)
                    put("dataFile", File(summary.dataPath).name)
                    put("indexFile", File(summary.indexPath).name)
                    put("startIndex", summary.startIndex)
                    put("modifiedAt", summary.modifiedAt)
                })
            }
        })
    }

    private fun decodeProcessResult(json: JSONObject): ProcessResult {
        val countObject = json.optJSONObject("counts") ?: JSONObject()
        val counts = linkedMapOf<String, Int>()
        countObject.keys().forEach { counts[it] = countObject.optInt(it) }
        val samplesJson = json.optJSONArray("samples") ?: JSONArray()
        val summariesJson = json.optJSONArray("ambiguousFiles")
        val summaries = if (summariesJson != null) {
            List(summariesJson.length()) { i ->
                val item = summariesJson.getJSONObject(i)
                val dataName = safeSessionArtifact(item.optString("dataFile")) ?: error("多音字明细文件名无效")
                val indexName = safeSessionArtifact(item.optString("indexFile")) ?: error("多音字索引文件名无效")
                AmbiguousFileSummary(
                    fileId = item.optString("fileId"),
                    fileName = item.optString("fileName"),
                    count = item.optInt("count"),
                    dataPath = File(sessionDetailDirectory, dataName).absolutePath,
                    indexPath = File(sessionDetailDirectory, indexName).absolutePath,
                    startIndex = item.optInt("startIndex"),
                    modifiedAt = item.optLong("modifiedAt"),
                )
            }
        } else {
            emptyList()
        }
        return ProcessResult(
            totalLines = json.optInt("totalLines"),
            pinyinAdded = json.optInt("pinyinAdded"),
            auxReplaced = json.optInt("auxReplaced"),
            ambiguousCount = json.optInt("ambiguousCount"),

            elapsedSeconds = json.optDouble("elapsedSeconds"),
            ambiguousFiles = summaries,
            outputName = json.optString("outputName"),
            counts = counts,
            samples = List(samplesJson.length()) { samplesJson.optString(it) },
            missingChars = json.optString("missingChars"),
        )
    }

    private fun safeSessionArtifact(name: String): String? =
        name.takeIf { it.isNotBlank() && it != "." && it != ".." && it == File(it).name && it.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '.' || ch == '_' } }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeUnless { it == "null" }

    private fun isSafeSessionId(id: String): Boolean =
        id.isNotBlank() && id.all { it.isLetterOrDigit() || it == '-' }

    fun selectTab(index: Int) = _state.update {
        it.copy(selectedTab = index, selectionMode = false, selectedFileIds = emptySet())
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { themeStore.setThemeMode(mode) }
    }

    fun setProcessMode(mode: ProcessMode) {
        _state.update { it.copy(processMode = mode) }
        viewModelScope.launch { themeStore.setProcessMode(mode) }
    }

    fun setAuxType(type: AuxType) {
        _state.update { it.copy(auxType = type) }
        viewModelScope.launch { themeStore.setAuxType(type) }
    }

    fun setReplaceAux(value: Boolean) {
        _state.update { it.copy(replaceAux = value) }
        viewModelScope.launch { themeStore.setReplaceAux(value) }
    }

    fun setReplacePinyin(value: Boolean) {
        _state.update { it.copy(replacePinyin = value) }
        viewModelScope.launch { themeStore.setReplacePinyin(value) }
    }

    fun setTone(value: ToneMode) {
        _state.update { it.copy(tone = value) }
        viewModelScope.launch { themeStore.setTone(value) }
    }

    fun setOutputDelimiter(value: OutputDelimiter) {
        _state.update { it.copy(outputDelimiter = value) }
        viewModelScope.launch { themeStore.setOutputDelimiter(value) }
    }

    fun setInputEncoding(value: InputEncoding) {
        _state.update { it.copy(inputEncoding = value) }
        viewModelScope.launch { themeStore.setInputEncoding(value) }
    }

    fun setColumns(word: Int, pinyin: Int) {
        _state.update { it.copy(wordColumn = word, pinyinColumn = pinyin) }
        viewModelScope.launch { themeStore.setColumns(word, pinyin) }
    }

    fun setSortMode(mode: FileSortMode) {
        _state.update { it.copy(sortMode = mode) }
        viewModelScope.launch { themeStore.setSortMode(mode) }
    }

    fun setSortDescending(descending: Boolean) {
        _state.update { it.copy(sortDescending = descending) }
        viewModelScope.launch { themeStore.setSortDescending(descending) }
    }

    fun setReportSortMode(mode: FileSortMode) {
        _state.update { it.copy(reportSortMode = mode) }
        viewModelScope.launch { themeStore.setReportSortMode(mode) }
    }

    fun setReportSortDescending(descending: Boolean) {
        _state.update { it.copy(reportSortDescending = descending) }
        viewModelScope.launch { themeStore.setReportSortDescending(descending) }
    }

    fun setExportConflictMode(mode: ExportConflictMode) {
        _state.update { it.copy(exportConflictMode = mode) }
        viewModelScope.launch { themeStore.setExportConflictMode(mode) }
    }

    fun setExportDirectory(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                _state.update { state -> state.copy(message = "目录授权失败，请重新选择") }
                return@launch
            }
            val name = withContext(Dispatchers.IO) {
                exportStore.directoryName(uri) ?: "已选目录"
            }
            _state.update {
                it.copy(
                    exportDirectoryUri = uri.toString(),
                    exportDirectoryName = name,
                    message = "导出路径已设为：$name",
                )
            }
            themeStore.setExportDirectory(uri.toString(), name)
        }
    }

    fun setWeight(value: Int) {
        val weight = value.coerceIn(1, 999999)
        _state.update { it.copy(weight = weight) }
        viewModelScope.launch { themeStore.setWeight(weight) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        super.onCleared()
        if (engineDelegate.isInitialized()) engine.close()
        // Session-owned input/output files survive ViewModel and process recreation.
        inputFiles.clear()
        outputsByFile.clear()
    }

    private data class ImportedFile(val file: DictFile, val input: InputFile)

    fun importFiles(resolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            sessionRestoreJob.join()
            val importedArtifacts = mutableListOf<ImportedFile>()
            var publishedIds = emptySet<String>()
            var committed = false
            val importFailures = mutableListOf<String>()
            try {
                val imported = withContext(Dispatchers.IO) {
                    if (!sessionInputDirectory.exists() && !sessionInputDirectory.mkdirs()) {
                        throw IllegalStateException("无法创建导入暂存目录")
                    }
                    uris.mapNotNull { uri ->
                        try {
                            val name = queryName(resolver, uri)
                            val id = UUID.randomUUID().toString()
                            val target = File(sessionInputDirectory, "$id.bin")
                            val temporary = File(sessionInputDirectory, "$id.tmp")
                            var copied = false
                            try {
                                resolver.openInputStream(uri)?.use { input ->
                                    BufferedOutputStream(
                                        SpaceCheckingOutputStream(FileOutputStream(temporary), sessionInputDirectory),
                                        64 * 1024,
                                    ).use { out ->
                                        val buffer = ByteArray(64 * 1024)
                                        while (true) {
                                            coroutineContext.ensureActive()
                                            val read = input.read(buffer)
                                            if (read < 0) break
                                            out.write(buffer, 0, read)
                                        }
                                    }
                                } ?: throw IllegalStateException("文件读取失败")
                                if (temporary.length() == 0L) throw IllegalStateException("文件为空")
                                if (!temporary.renameTo(target)) throw IllegalStateException("无法保存导入文件")
                                val inputFile = InputFile(id, name, target, target.length())
                                val dictFile = DictFile(
                                    name = name,
                                    meta = formatSize(target.length()) + " · 等待处理",
                                    status = FileStatus.PENDING,
                                    id = id,
                                    sizeBytes = target.length(),
                                    uri = uri.toString(),
                                    modifiedAt = queryModified(resolver, uri),
                                )
                                ImportedFile(dictFile, inputFile).also { imported ->
                                    importedArtifacts.add(imported)
                                    copied = true
                                }
                            } finally {
                                if (!copied) {
                                    runCatching { temporary.delete() }
                                    runCatching { target.delete() }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            importFailures.add(e.message ?: "文件读取失败")
                            null
                        }
                    }
                }
                if (imported.isEmpty()) {
                    _state.update { it.copy(message = importFailures.firstOrNull() ?: "没有读到可处理的文件") }
                    return@launch
                }
                imported.forEach { inputFiles[it.input.id] = it.input }
                val files = imported.map { it.file }
                publishedIds = files.mapNotNull { it.id }.toSet()
                _state.update { current ->
                    current.copy(
                        files = files + current.files,
                        currentFileId = files.last().id,
                        selectionMode = false,
                        selectedFileIds = emptySet(),
                        processedLines = resultsByFile.values.sumOf { r -> r.totalLines },
                        processedPinyin = resultsByFile.values.sumOf { r -> r.pinyinAdded },
                        message = "已导入 " + files.size + " 个文件，开始排队处理",
                    )
                }
                if (!persistSession()) throw IllegalStateException("本地记录未能保存，已回滚导入临时文件")
                committed = true
                viewModelScope.launch(Dispatchers.Default) { runCatching { engine.prewarm() } }
                enqueue(files.mapNotNull { it.id }, autoCandidate = files.size == 1, showQueueBlock = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = if (error is OutOfMemoryError) "导入时内存不足，已清除本次临时文件"
                    else error.message ?: "导入失败，已清除本次临时文件"
                _state.update { it.copy(message = message) }
            } finally {
                if (!committed) {
                    importedArtifacts.forEach { inputFiles.remove(it.input.id) }
                    _state.update { current ->
                        val remaining = current.files.filterNot { it.id in publishedIds }
                        current.copy(
                            files = remaining,
                            currentFileId = current.currentFileId?.takeUnless { it in publishedIds }
                                ?: remaining.firstOrNull { it.id != null }?.id,
                        )
                    }
                    withContext(NonCancellable + Dispatchers.IO) {
                        importedArtifacts.forEach { runCatching { it.input.file.delete() } }
                    }
                }
            }
        }
    }

    fun startProcessing() {
        if (!sessionRestoreJob.isCompleted) {
            viewModelScope.launch {
                sessionRestoreJob.join()
                startProcessing()
            }
            return
        }
        val current = _state.value
        if (current.processing || queueRunning) {
            _state.update { it.copy(message = "正在排队处理，请稍候") }
            return
        }
        val ready = current.files.filter {
            it.id != null &&
                (it.status == FileStatus.PENDING || it.status == FileStatus.FAILED) &&
                inputFiles.containsKey(it.id)
        }
        if (ready.isEmpty()) {
            _state.update { it.copy(message = "请重新导入词库文件后继续处理") }
            return
        }
        enqueue(
            ready.mapNotNull { it.id },
            autoCandidate = false,
            showQueueBlock = true,
        )
    }

    private fun enqueue(ids: List<String>, autoCandidate: Boolean, showQueueBlock: Boolean) {
        val queued = processingQueue.mapTo(mutableSetOf(activeFileId)) { it.fileId }
        val eligible = ids.filter { id -> id in inputFiles && queued.add(id) }
        if (eligible.isEmpty()) return
        eligible.forEach { processingQueue.addLast(QueueItem(it, autoCandidate)) }
        if (showQueueBlock) queueDisplayEnabled = true
        syncQueueState()
        if (!queueRunning) {
            cancelRequested = false
            queueRunning = true
            processingJob = viewModelScope.launch { drainQueue() }
        }
    }

    private fun syncQueueState() {
        _state.update { current ->
            val filesById = current.files.mapNotNull { file -> file.id?.let { it to file } }.toMap()
            val queue = buildList {
                activeFileId?.let { id ->
                    filesById[id]?.let { file ->
                        add(QueueFile(file.id ?: id, file.name, processing = true))
                    }
                }
                processingQueue.forEach { item ->
                    filesById[item.fileId]?.let { file ->
                        add(QueueFile(file.id ?: item.fileId, file.name))
                    }
                }
            }
            current.copy(
                queueFiles = queue,
                showQueue = queueDisplayEnabled && queue.isNotEmpty(),
            )
        }
    }

    fun cancelProcessing() {
        if (!_state.value.processing && processingQueue.isEmpty()) return
        cancelRequested = true
        val cancelledIds = linkedSetOf<String>().apply {
            activeFileId?.let(::add)
            processingQueue.forEach { add(it.fileId) }
        }
        processingQueue.clear()
        queueDisplayEnabled = false
        processingJob?.cancel()
        processingJob = null
        autoExportPayload = null
        val cancelledArtifacts = mutableListOf<File>()
        cancelledIds.forEach { id ->
            inputFiles.remove(id)?.file?.let(cancelledArtifacts::add)
            outputsByFile.remove(id)?.forEach { output ->
                cancelledArtifacts.add(output.file)
                collectDetailArtifacts(output.result, cancelledArtifacts)
            }
            collectDetailArtifacts(resultsByFile.remove(id), cancelledArtifacts)
            updateFile(id) { file ->
                file.copy(
                    status = FileStatus.CANCELLED,
                    hasOutput = false,
                    meta = formatSize(file.sizeBytes) + " · 已取消",
                )
            }
        }
        activeFileId = null
        lastRunFileIds.removeAll(cancelledIds)
        val remainingIds = lastRunFileIds.toList()
        val remainingResult = if (remainingIds.isEmpty()) emptyProcessResult() else mergeResults(
            remainingIds.mapNotNull { resultsByFile[it] },
            remainingIds.flatMap { outputsByFile[it].orEmpty() },
        )
        _state.update {
            it.copy(
                result = remainingResult,
                hasResult = remainingIds.isNotEmpty(),
                processedLines = resultsByFile.values.sumOf { result -> result.totalLines },
                processedPinyin = resultsByFile.values.sumOf { result -> result.pinyinAdded },
                processing = false,
                processingFile = null,
                progress = 0f,
                autoExportPending = false,
                message = "已取消处理，重新导入词库后可继续",
            )
        }
        syncQueueState()
        viewModelScope.launch {
            if (persistSession()) withContext(Dispatchers.IO) { cancelledArtifacts.forEach { it.delete() } }
        }
    }

    private suspend fun drainQueue() {
        val options = processOptions(_state.value)
        autoExportPayload = null
        val processedIds = mutableListOf<String>()
        val failures = linkedMapOf<String, String>()
        var automaticExportId: String? = null
        var successfulFiles = 0
        val totalFiles = processingQueue.size.coerceAtLeast(1)

        _state.update {
            it.copy(
                processing = true,
                estimatedSeconds = 0,
                processingFile = null,
                progress = 0f,
                autoExportPending = false,
            )
        }
        syncQueueState()

        try {
            while (processingQueue.isNotEmpty()) {
                if (cancelRequested) break
                val item = processingQueue.removeFirst()
                val snapshot = _state.value
                val file = snapshot.files.firstOrNull { it.id == item.fileId } ?: continue
                val input = inputFiles[item.fileId] ?: continue
                val completedBefore = successfulFiles + failures.size
                activeFileId = item.fileId
                // 预估只针对当前文件：记录该文件开始时刻，进度归零。
                val itemStartedAt = SystemClock.elapsedRealtime()
                _state.update {
                    val current = (completedBefore.toFloat() / totalFiles).coerceIn(0f, 1f)
                    it.copy(
                        processingFile = file.name,
                        progress = current,
                        estimatedSeconds = 0,
                    )
                }
                syncQueueState()
                val stagedDurableFiles = mutableListOf<File>()
                var processOutcome: ProcessOutcome? = null
                var committedOutput = false
                val itemJob = Job(coroutineContext.job)
                activeItemJob = itemJob
                try {
                    val durableOutputs = withContext(itemJob) {
                        val outcome = engine.process(input, options) { fraction ->
                            if (itemJob.isActive) {
                                val itemFraction = fraction.coerceIn(0f, 1f)
                                val progress = ((completedBefore + itemFraction) / totalFiles).coerceIn(0f, 1f)
                                _state.update {
                                    it.copy(
                                        progress = progress,
                                        // 只按当前文件已用时间与完成比例估算，不再按整体进度。
                                        estimatedSeconds = estimateItemSeconds(itemStartedAt, itemFraction),
                                    )
                                }
                            }
                        }
                        processOutcome = outcome
                        coroutineContext.ensureActive()
                        withContext(Dispatchers.IO) {
                            if (!sessionOutputDirectory.isDirectory && !sessionOutputDirectory.mkdirs()) {
                                throw ProcessingException("无法保存处理结果")
                            }
                            outcome.outputs.mapIndexed { index, output ->
                                coroutineContext.ensureActive()
                                val durable = File(sessionOutputDirectory, item.fileId + "-" + index + ".bin")
                                stagedDurableFiles.add(durable)
                                if (!output.file.renameTo(durable)) {
                                    output.file.inputStream().use { source ->
                                        FileOutputStream(durable).use { sink ->
                                            val out = SpaceCheckingOutputStream(sink, sessionOutputDirectory)
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                coroutineContext.ensureActive()
                                                val read = source.read(buffer)
                                                if (read < 0) break
                                                out.write(buffer, 0, read)
                                            }
                                        }
                                    }
                                }
                                output.copy(file = durable, sizeBytes = durable.length())
                            }
                        }
                    }
                    coroutineContext.ensureActive()
                    if (!itemJob.isActive || cancelRequested || !isTrackedFile(item.fileId)) continue
                    val result = requireNotNull(processOutcome).result
                    val status = when (options.mode) {
                        ProcessMode.PINYIN_ONLY -> FileStatus.PINYIN_DONE
                        ProcessMode.AUX_STRIP -> FileStatus.AUX_STRIPPED
                        ProcessMode.PINYIN_AND_AUX -> FileStatus.PINYIN_AUX_DONE
                        ProcessMode.AUX_ONLY -> FileStatus.AUX_DONE
                    }
                    // No suspension between checking eligibility and publishing metadata.
                    outputsByFile[item.fileId] = durableOutputs
                    resultsByFile[item.fileId] = result
                    lastRunFileIds = resultsByFile.keys.toMutableList()
                    val partial = mergeResults(resultsByFile.values.toList(), outputsByFile.values.flatten())
                    _state.update { current ->
                        current.copy(
                            files = current.files.map {
                                if (it.id == item.fileId) it.copy(status = status, hasOutput = true,
                                    processedAt = System.currentTimeMillis(),
                                    meta = formatSize(it.sizeBytes) + " · 已处理") else it
                            },
                            result = partial,
                            hasResult = true,
                            processedLines = partial.totalLines,
                            processedPinyin = partial.pinyinAdded,
                        )
                    }
                    committedOutput = true
                    processedIds.add(item.fileId)
                    successfulFiles++
                    if (item.autoCandidate && durableOutputs.size == 1 && result.ambiguousCount == 0) {
                        automaticExportId = item.fileId
                    }
                    if (persistSession()) {
                        inputFiles.remove(item.fileId)?.file?.let { runCatching { it.delete() } }
                    }
                } catch (e: CancellationException) {
                    // Deleting the active item does not restart the queue with new options.
                    if (!coroutineContext.isActive || cancelRequested || isTrackedFile(item.fileId)) throw e
                } catch (e: Exception) {
                    if (!cancelRequested && isTrackedFile(item.fileId) && !committedOutput) {
                        failures[item.fileId] = file.name
                        outputsByFile.remove(item.fileId)
                        resultsByFile.remove(item.fileId)
                        lastRunFileIds.remove(item.fileId)
                        updateFile(item.fileId) {
                            it.copy(status = FileStatus.FAILED, hasOutput = false,
                                meta = formatSize(it.sizeBytes) + " · " + (e.message ?: "处理失败"))
                        }
                        persistSession()
                    }
                } finally {
                    itemJob.complete()
                    // Ownership stays here until publication, including cancelled dispatcher returns.
                    processOutcome?.outputs?.forEach { output -> runCatching { output.file.delete() } }
                    if (!committedOutput) {
                        processOutcome?.result?.ambiguousFiles?.forEach { summary ->
                            runCatching { File(summary.dataPath).delete() }
                            runCatching { File(summary.indexPath).delete() }
                        }
                        outputsByFile.remove(item.fileId)
                        resultsByFile.remove(item.fileId)
                        lastRunFileIds.remove(item.fileId)
                        stagedDurableFiles.forEach { artifact -> runCatching { artifact.delete() } }
                    }
                    activeItemJob = null
                    activeFileId = null
                    syncQueueState()
                }
                val finished = successfulFiles + failures.size
                _state.update { it.copy(progress = (finished.toFloat() / totalFiles).coerceIn(0f, 1f)) }
            }
            if (cancelRequested) {
                return
            }

            val liveProcessedIds = processedIds.filter { it in resultsByFile }
            val liveFailures = failures.filterKeys(::isTrackedFile)
            if (liveProcessedIds.isNotEmpty()) {
                val merged = mergeResults(resultsByFile.values.toList(), outputsByFile.values.flatten())
                val exportId = automaticExportId?.takeIf { it == liveProcessedIds.singleOrNull() && liveFailures.isEmpty() }
                val exportOutput = exportId?.let { outputsByFile[it]?.singleOrNull() }
                if (exportOutput != null) {
                    val exportName = ExportNaming.safeName(exportOutput.name)
                    autoExportPayload = ExportPayload(
                        exportName,
                        listOf(ExportEntry(exportName, exportOutput.file)),
                        fileId = exportId,
                    )
                }
                _state.update {
                    it.copy(
                        processing = false,
                        processingFile = null,
                        progress = 1f,
                        result = merged,
                        hasResult = true,
                        processedLines = resultsByFile.values.sumOf { r -> r.totalLines },
                        processedPinyin = resultsByFile.values.sumOf { r -> r.pinyinAdded },
                        autoExportPending = exportOutput != null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        processing = false,
                        processingFile = null,
                        progress = 0f,
                        message = if (liveFailures.isNotEmpty()) "处理失败：" + liveFailures.values.joinToString("、") else it.message,
                    )
                }
            }
        } finally {
            queueRunning = false
            processingJob = null
            activeFileId = null
            _state.update { if (it.processing) it.copy(processing = false, processingFile = null) else it }
            if (processingQueue.isEmpty()) queueDisplayEnabled = false
            syncQueueState()
            cancelRequested = false
            if (processingQueue.isNotEmpty()) {
                queueRunning = true
                processingJob = viewModelScope.launch { drainQueue() }
            }
        }
    }

    /** 单个文件的剩余时间：按该文件已用时间与自身完成比例推算。 */
    private fun estimateItemSeconds(startedAt: Long, fraction: Float): Int {
        if (fraction <= 0f) return 0
        val elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
        return ceil(elapsedSeconds / fraction).toInt().coerceAtLeast(1)
    }

    private fun updateFile(id: String, transform: (DictFile) -> DictFile) {
        _state.update { current ->
            current.copy(files = current.files.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun processOptions(state: DictUiState) = ProcessOptions(
        mode = state.processMode,
        auxType = state.auxType,
        auxSeparator = state.auxSeparator,
        replaceAux = state.replaceAux,
        replacePinyin = state.replacePinyin,
        tone = state.tone,
        outputDelimiter = state.outputDelimiter,
        inputEncoding = state.inputEncoding,
        wordColumn = state.wordColumn,
        pinyinColumn = state.pinyinColumn,
        weight = state.weight,
    )

    fun enterSelection(id: String) {
        if (!_state.value.files.any { it.id == id }) return
        _state.update { it.copy(selectionMode = true, selectedFileIds = setOf(id)) }
    }

    fun toggleSelection(id: String) {
        _state.update { current ->
            if (!current.selectionMode) current
            else {
                val selected = current.selectedFileIds.toMutableSet()
                if (!selected.add(id)) selected.remove(id)
                current.copy(selectedFileIds = selected)
            }
        }
    }

    fun selectAllFiles() = _state.update { current ->
        current.copy(selectedFileIds = current.files.mapNotNull { it.id }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selectionMode = false, selectedFileIds = emptySet()) }

    private fun collectDetailArtifacts(result: ProcessResult?, target: MutableList<File>) {
        result?.ambiguousFiles?.forEach { summary ->
            target.add(File(summary.dataPath))
            target.add(File(summary.indexPath))
        }
    }

    private fun isTrackedFile(id: String): Boolean = _state.value.files.any { it.id == id }

    fun deleteSelected() {
        val ids = _state.value.selectedFileIds
        if (ids.isEmpty()) return
        val deletedArtifacts = mutableListOf<File>()
        val activeWasDeleted = activeFileId?.let { it in ids } == true
        processingQueue.removeAll { it.fileId in ids }
        ids.forEach { id ->
            inputFiles.remove(id)?.file?.let(deletedArtifacts::add)
            outputsByFile.remove(id)?.forEach { output ->
                deletedArtifacts.add(output.file)
                collectDetailArtifacts(output.result, deletedArtifacts)
            }
            collectDetailArtifacts(resultsByFile.remove(id), deletedArtifacts)
        }
        if (activeWasDeleted) activeItemJob?.cancel()
        val deletedPaths = deletedArtifacts.map { it.absolutePath }.toSet()
        if (pendingExport?.fileId in ids || pendingExport?.entries?.any { it.file.absolutePath in deletedPaths } == true) pendingExport = null
        if (autoExportPayload?.fileId in ids || autoExportPayload?.entries?.any { it.file.absolutePath in deletedPaths } == true) {
            autoExportPayload = null
            _state.update { it.copy(autoExportPending = false) }
        }
        lastRunFileIds.removeAll(ids)
        val remainingIds = lastRunFileIds.toList()
        val remainingResult = if (remainingIds.isEmpty()) {
            emptyProcessResult()
        } else {
            mergeResults(
                remainingIds.mapNotNull { resultsByFile[it] },
                remainingIds.flatMap { outputsByFile[it].orEmpty() },
            )
        }
        val hasResult = remainingIds.isNotEmpty()
        _state.update { current ->
            val remaining = current.files.filterNot { it.id in ids }
            current.copy(
                files = remaining,
                currentFileId = current.currentFileId?.takeIf { it !in ids }
                    ?: remaining.firstOrNull { it.id != null }?.id,
                selectionMode = false,
                selectedFileIds = emptySet(),
                result = remainingResult,
                processedLines = resultsByFile.values.sumOf { r -> r.totalLines },
                processedPinyin = resultsByFile.values.sumOf { r -> r.pinyinAdded },
                hasResult = hasResult,
                message = "已删除 ${ids.size} 个词库",
            )
        }
        syncQueueState()
        viewModelScope.launch {
            if (persistSession()) withContext(Dispatchers.IO) { deletedArtifacts.distinct().forEach { it.delete() } }
        }
    }

    fun requestExportFiles(ids: Set<String>): ExportPayload? {
        val outputs = ids.flatMap { outputsByFile[it].orEmpty() }
        return prepareExport(outputs, fileId = ids.singleOrNull())
    }

    private fun prepareExport(outputs: List<ProcessOutput>, fileId: String?): ExportPayload? {
        if (outputs.isEmpty()) return null
        val payload = if (outputs.size == 1) {
            val name = ExportNaming.safeName(outputs.first().name)
            ExportPayload(name, listOf(ExportEntry(name, outputs.first().file)), fileId = fileId)
        } else {
            val used = mutableSetOf<String>()
            ExportPayload(
                "dict-batch.zip",
                outputs.map { output ->
                    ExportEntry(uniqueEntryName(ExportNaming.safeName(output.name), used), output.file)
                },
            )
        }
        pendingExport = payload
        return payload
    }

    private fun uniqueEntryName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        var index = 2
        while (true) {
            val candidate = ExportNaming.numberedName(name, index)
            if (used.add(candidate)) return candidate
            index++
        }
    }

    fun takeAutoExport(): ExportPayload? {
        val payload = autoExportPayload ?: return null
        autoExportPayload = null
        pendingExport = payload
        _state.update { it.copy(autoExportPending = false) }
        return payload
    }

    fun exportPayloadToConfiguredPath(payload: ExportPayload? = null): Boolean {
        val target = payload ?: pendingExport ?: return false
        pendingExport = target
        val snapshot = _state.value
        if (snapshot.exportDirectoryUri == null && !exportStore.canWriteDefaultDownload()) {
            return false
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                exportStore.save(
                    payload = target,
                    directoryUri = snapshot.exportDirectoryUri,
                    directoryName = snapshot.exportDirectoryName,
                    conflictMode = snapshot.exportConflictMode,
                )
            }.onSuccess { saved ->
                if (pendingExport === target) pendingExport = null
                markExported(target)
                _state.update { state ->
                    state.copy(message = saved.cleanupWarning ?: ("已导出：" + saved.locationLabel + "/" + saved.fileName))
                }
                viewModelScope.launch { persistSession() }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        message = when {
                            error !is SecurityException -> error.message ?: "导出失败"
                            snapshot.exportDirectoryUri != null -> "导出目录权限已失效，请重新选择导出路径"
                            else -> "无法写入 Download，请在设置中选择导出路径"
                        },
                    )
                }
            }
        }
        return true
    }

    fun exportTo(resolver: ContentResolver, uri: Uri) {
        val payload = pendingExport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                resolver.openOutputStream(uri)?.use { output -> exportStore.writePayload(payload, output) }
                    ?: throw IllegalStateException("无法打开保存位置")
            }.onFailure {
                _state.update { s -> s.copy(message = it.message ?: "导出失败") }
            }.onSuccess {
                pendingExport = null
                markExported(payload)
                _state.update { s -> s.copy(message = "已导出：" + payload.name) }
                viewModelScope.launch { persistSession() }
            }
        }
    }

    private fun markExported(payload: ExportPayload) {
        val id = payload.fileId ?: return
        // 「已导出」不再作为状态展示：保留原处理状态，只更新 meta 文案。
        updateFile(id) { file ->
            file.copy(meta = formatSize(file.sizeBytes) + " · 已导出")
        }
    }

    private fun mergeResults(results: List<ProcessResult>, outputs: List<ProcessOutput>): ProcessResult {
        if (results.isEmpty()) return emptyProcessResult()
        val counts = linkedMapOf<String, Int>()
        val samples = mutableListOf<String>()
        val summaries = mutableListOf<AmbiguousFileSummary>()
        var total = 0
        var pinyin = 0
        var aux = 0
        var ambiguousCount = 0
        var elapsed = 0.0
        val missing = StringBuilder()
        for (r in results) {
            total += r.totalLines
            pinyin += r.pinyinAdded
            aux += r.auxReplaced
            ambiguousCount += r.ambiguousCount
            elapsed += r.elapsedSeconds
            r.counts.forEach { (k, v) -> counts[k] = (counts[k] ?: 0) + v }
            samples.addAll(r.samples.take(60 - samples.size))
            summaries.addAll(r.ambiguousFiles)
            missing.append(r.missingChars)
        }
        val name = if (outputs.size == 1) outputs.first().name else outputs.size.toString() + " 个文件"
        return ProcessResult(
            totalLines = total,
            pinyinAdded = pinyin,
            auxReplaced = aux,
            ambiguousCount = ambiguousCount,
            elapsedSeconds = elapsed,
            ambiguousFiles = summaries,
            outputName = name,
            counts = counts,
            samples = samples,
            missingChars = missing.toString(),
        )
    }

    /**
     * 多音字详情页替换拼音：只改写已处理的输出，不碰原始导入文件。
     * 替换成功后把文件标记为已处理多音字并持久化会话。
     */
    suspend fun replaceAmbiguousReading(
        fileId: String,
        line: Int,
        syllableIndex: Int,
        reading: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val output = outputsByFile[fileId].orEmpty().firstOrNull() ?: return@withContext false
        val state = _state.value
        val patched = OutputLinePatcher.replaceReading(
            output = output.file,
            line = line,
            pinyinColumn = state.pinyinColumn,
            syllableIndex = syllableIndex,
            reading = reading,
            auxSeparator = state.auxSeparator,
        )
        if (!patched) return@withContext false
        _state.update { current ->
            current.copy(
                files = current.files.map {
                    if (it.id == fileId) {
                        it.copy(polyphonicHandled = true, hasOutput = true,
                            processedAt = if (it.processedAt > 0L) it.processedAt else System.currentTimeMillis(),
                            meta = formatSize(it.sizeBytes) + " · 已处理")
                    } else it
                },
            )
        }
        persistSession()
        true
    }

    suspend fun readAmbiguousPage(fileId: String, offset: Int, limit: Int, query: String): AmbiguousDetailPage =
        withContext(Dispatchers.IO) {
            val summary = _state.value.result.ambiguousFiles.firstOrNull { it.fileId == fileId }
                ?: return@withContext AmbiguousDetailPage(emptyList(), null)
            val activeContext = coroutineContext
            AmbiguousDetailReader.readPage(summary, offset, limit, query) { activeContext.ensureActive() }
        }

    fun openLibraryFile(id: String) {
        if (_state.value.files.none { it.id == id }) return
        _state.update {
            it.copy(
                selectedTab = 1,
                currentFileId = id,
                selectionMode = false,
                selectedFileIds = emptySet(),
                libraryFocusId = id,
                libraryFocusRequest = it.libraryFocusRequest + 1,
            )
        }
    }

    fun clearLibraryFocus() = _state.update { it.copy(libraryFocusId = null) }

    private fun queryModified(resolver: ContentResolver, uri: Uri): Long {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull() ?: System.currentTimeMillis()
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "未命名词库"
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
