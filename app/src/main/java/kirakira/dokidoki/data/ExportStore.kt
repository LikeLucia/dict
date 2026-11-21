package kirakira.dokidoki.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import java.io.FileInputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportWriteResult(
    val fileName: String,
    val locationLabel: String,
    val cleanupWarning: String? = null,
)

class ExportStore(private val context: Context) {
    private val resolver: ContentResolver
        get() = context.contentResolver

    fun canWriteDefaultDownload(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun directoryName(uri: Uri): String? {
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (!documentId.isNullOrBlank() && documentId.contains(':')) {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':')
            if (path.isNotBlank()) {
                return (if (volume == "primary") path else volume + "/" + path)
            }
        }
        return displayName
    }

    fun save(
        payload: ExportPayload,
        directoryUri: String?,
        directoryName: String,
        conflictMode: ExportConflictMode,
    ): ExportWriteResult {
        val name = ExportNaming.safeName(payload.name)
        val safePayload = if (name == payload.name) payload else payload.copy(name = name)
        return if (directoryUri != null) {
            saveToTree(Uri.parse(directoryUri), safePayload, directoryName, conflictMode)
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                error("当前系统需要选择保存位置")
            }
            saveToDownloads(safePayload, conflictMode)
        }
    }

    /**
     * Streams the payload into [output]. Multiple outputs are packed as a ZIP on the fly so the
     * caller never has to build a potentially huge archive on the UI thread.
     */
    fun writePayload(payload: ExportPayload, output: OutputStream) {
        if (payload.entries.size == 1) {
            FileInputStream(payload.entries[0].file).use { input ->
                input.copyTo(output, 64 * 1024)
            }
            return
        }
        // ZipOutputStream 默认会关闭底层流；导出调用方还需要继续 flush/close，包装成不关闭底层流。
        val nonClosing = object : OutputStream() {
            override fun write(b: Int) = output.write(b)

            override fun write(b: ByteArray, off: Int, len: Int) = output.write(b, off, len)

            override fun flush() = output.flush()

            override fun close() {
                flush()
            }
        }
        val zip = ZipOutputStream(nonClosing, Charsets.UTF_8)
        try {
            for (entry in payload.entries) {
                zip.putNextEntry(ZipEntry(entry.name))
                FileInputStream(entry.file).use { input ->
                    input.copyTo(zip, 64 * 1024)
                }
                zip.closeEntry()
            }
            zip.finish()
        } finally {
            runCatching { zip.close() }
        }
    }

    private fun saveToTree(
        treeUri: Uri,
        payload: ExportPayload,
        directoryName: String,
        conflictMode: ExportConflictMode,
    ): ExportWriteResult {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val existing = linkedMapOf<String, String>()
        val directories = linkedSetOf<String>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                // 目录不是覆盖对象；只在自动编号时避开同名目录，避免创建文件失败。
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.isNullOrBlank()) directories.add(name)
                    continue
                }
                if (!id.isNullOrBlank() && !name.isNullOrBlank()) existing[name] = id
            }
        }

        val finalName = when (conflictMode) {
            // 目标同名项是文件夹时不能覆盖，自动改用不冲突的文件名。
            ExportConflictMode.OVERWRITE ->
                if (payload.name in directories) ExportNaming.uniqueName(payload.name, existing.keys + directories) else payload.name
            ExportConflictMode.RENAME -> ExportNaming.uniqueName(payload.name, existing.keys + directories)
        }
        val tempName = temporaryName(payload.name, existing.keys + directories)
        val tempUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            ExportNaming.mimeFor(finalName),
            tempName,
        ) ?: error("无法在所选目录创建临时文件")
        var tempExists = true
        var backupUri: Uri? = null
        var finalCreated = false
        try {
            resolver.openOutputStream(tempUri, "w")?.use { out ->
                writePayload(payload, out)
                out.flush()
            } ?: error("无法写入所选目录")

            val oldDocumentId = if (conflictMode == ExportConflictMode.OVERWRITE) existing[finalName] else null
            if (oldDocumentId != null) {
                val oldUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, oldDocumentId)
                val backupName = temporaryName(payload.name + ".backup", existing.keys + directories + tempName)
                backupUri = DocumentsContract.renameDocument(resolver, oldUri, backupName)
                    ?: error("当前文件提供方不支持安全覆盖，请改用“不覆盖，自动加序号”")
            }

            DocumentsContract.renameDocument(resolver, tempUri, finalName)
                ?: error("无法在所选目录创建文件")
            finalCreated = true
            tempExists = false
            val cleanupWarning = backupUri?.let { backup ->
                val removed = runCatching { DocumentsContract.deleteDocument(resolver, backup) }.getOrDefault(false)
                if (removed) null else "新文件已保存；旧同名文件清理失败，旧副本以备份名保留：" + (backup.lastPathSegment ?: backup.toString())
            }
            backupUri = null

            return ExportWriteResult(finalName, directoryName.ifBlank { "已选目录" }, cleanupWarning)
        } catch (t: Throwable) {
            if (tempExists) runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            val backup = backupUri
            if (backup != null && !finalCreated) {
                runCatching { DocumentsContract.renameDocument(resolver, backup, finalName) }
            }
            throw t
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(payload: ExportPayload, conflictMode: ExportConflictMode): ExportWriteResult {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val exactPath = Environment.DIRECTORY_DOWNLOADS + "/"
        val existing = linkedMapOf<String, MutableList<Uri>>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                val path = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                // 只看 Download 根目录，子目录里的同名文件不参与覆盖/编号。
                if (!path.trimEnd('/').equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) continue
                val name = cursor.getString(nameIndex) ?: continue
                val id = cursor.getLong(idIndex)
                existing.getOrPut(name) { mutableListOf() }.add(Uri.withAppendedPath(collection, id.toString()))
            }
        }

        val finalName = when (conflictMode) {
            ExportConflictMode.OVERWRITE -> payload.name
            ExportConflictMode.RENAME -> ExportNaming.uniqueName(payload.name, existing.keys)
        }
        val tempName = temporaryName(payload.name, existing.keys)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, tempName)
            put(MediaStore.MediaColumns.MIME_TYPE, ExportNaming.mimeFor(finalName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, exactPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建下载文件")
        var newFileSaved = false
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                writePayload(payload, out)
                out.flush()
            } ?: error("无法写入下载目录")

            // 先让新文件完整可见，再处理同名旧文件，避免旧文件先被删除后写入失败。
            if (resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) <= 0) {
                error("无法完成下载文件")
            }
            if (resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                }, null, null) <= 0) {
                error("无法设置下载文件名")
            }
            newFileSaved = true
            val failedOldCleanup = if (conflictMode == ExportConflictMode.OVERWRITE) {
                existing[finalName].orEmpty().count { old ->
                    runCatching { resolver.delete(old, null, null) > 0 }.getOrDefault(false).not()
                }
            } else 0
            val warning = failedOldCleanup.takeIf { it > 0 }?.let {
                "新文件已保存；旧同名文件清理失败（" + it + " 个旧文件仍保留）"
            }
            return ExportWriteResult(finalName, Environment.DIRECTORY_DOWNLOADS, warning)
        } catch (t: Throwable) {
            if (!newFileSaved) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            throw IllegalStateException("新文件已保存；后续旧文件清理失败：" + (t.message ?: "未知错误"), t)
        }
    }

    private fun temporaryName(name: String, existing: Collection<String>): String {
        val extension = name.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else "." + extension
        while (true) {
            val candidate = "dict-tmp-" + UUID.randomUUID().toString().take(8) + suffix
            if (candidate !in existing) return candidate
        }
    }
}
