package local.ksm.aux

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors

/** Kotlin host; all rendering uses the device's android.webkit.WebView. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private data class Export(val file: File, val name: String)
    private val queue = ArrayDeque<Export>()
    private var saving: Export? = null
    private var copying = false
    private val lock = Any()
    private val partial = HashMap<String, Export>()
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // Only this host's abandoned export files; never source documents.
        cacheDir.listFiles()?.filter { it.name.startsWith("export-") && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.settings.setSupportMultipleWindows(false)
        web.addJavascriptInterface(Bridge(), "NativeExport")
        web.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse {
                if (url == "https://app.local/index.html") {
                    return WebResourceResponse("text/html", "UTF-8", assets.open("index.html"))
                }
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
        })
        web.setWebChromeClient(object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                chooser?.onReceiveValue(null)
                chooser = cb
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }, 10)
                } catch (e: Exception) {
                    chooser = null; cb.onReceiveValue(null)
                    message("文件选择器启动失败：${e.message}")
                }
                return true
            }
        })
        web.loadUrl("https://app.local/index.html")
    }

    private fun message(text: String) {
        runOnUiThread { if (!closed) Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }

    inner class Bridge {
        @JavascriptInterface fun begin(name: String): String = synchronized(lock) {
            check(!closed) { "页面已关闭" }
            val id = UUID.randomUUID().toString()
            val clean = name.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[\\p{Cntrl}]"), "_").take(180).ifEmpty { "output.txt" }
            partial[id] = Export(File.createTempFile("export-", ".part", cacheDir), clean)
            id
        }
        @JavascriptInterface fun append(id: String, data: String): Boolean = synchronized(lock) {
            val item = partial[id] ?: return@synchronized false
            try {
                check(!closed)
                require(data.length <= 131072)
                FileOutputStream(item.file, true).use { it.write(Base64.decode(data, Base64.NO_WRAP)) }
                true
            } catch (e: Exception) {
                partial.remove(id); item.file.delete(); message("准备保存失败：${e.message}"); false
            }
        }
        @JavascriptInterface fun abort(id: String) {
            synchronized(lock) { partial.remove(id)?.file?.delete() }
        }
        @JavascriptInterface fun finish(id: String) {
            val item = synchronized(lock) { partial.remove(id) } ?: return
            runOnUiThread {
                if (closed || isFinishing) item.file.delete()
                else { queue.add(item); saveNext() }
            }
        }
        @JavascriptInterface fun incompatible() {
            runOnUiThread {
                if (!closed && !isFinishing) AlertDialog.Builder(this@MainActivity)
                    .setTitle("请更新系统 WebView")
                    .setMessage("本页面需要支持 deflate-raw 解压和 Streams 的系统 WebView。请更新 Android System WebView 或系统指定的 WebView 提供程序后重新打开。")
                    .setPositiveButton("知道了", null).show()
            }
        }
    }

    private fun saveNext() {
        if (saving != null || queue.isEmpty() || closed || isFinishing) return
        saving = queue.removeFirst()
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (saving!!.name.endsWith(".zip", true)) "application/zip" else "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, saving!!.name)
            }, 20)
        } catch (e: Exception) {
            message("保存窗口启动失败：${e.message}")
            saving?.file?.delete(); saving = null; saveNext()
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == 10) {
            val cb = chooser; chooser = null
            if (result != RESULT_OK || data == null) cb?.onReceiveValue(null)
            else {
                val clip = data.clipData
                val uris = if (clip != null) Array(clip.itemCount) { clip.getItemAt(it).uri }
                    else data.data?.let { arrayOf(it) }
                cb?.onReceiveValue(uris)
            }
        }
        if (request == 20) {
            val item = saving ?: return
            val uri = data?.data
            if (result != RESULT_OK || uri == null) {
                message("已取消保存：${item.name}；可在页面再次下载")
                item.file.delete(); saving = null; saveNext(); return
            }
            copying = true
            io.execute {
                try {
                    val out = contentResolver.openOutputStream(uri, "wt")
                        ?: throw java.io.IOException("目标文件未打开")
                    out.use { stream -> item.file.inputStream().use { it.copyTo(stream) } }
                    message("已保存：${item.name}")
                } catch (e: Exception) {
                    message("保存失败：${e.message}；请在页面再次下载")
                } finally {
                    item.file.delete()
                    runOnUiThread { copying = false; saving = null; saveNext() }
                }
            }
        }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this).setTitle("退出应用？").setMessage("未保存的转换结果将丢失。")
            .setNegativeButton("继续使用", null).setPositiveButton("退出") { _, _ -> finish() }.show()
    }
    override fun onDestroy() {
        closed = true
        chooser?.onReceiveValue(null); chooser = null
        web.removeJavascriptInterface("NativeExport"); web.destroy()
        synchronized(lock) { partial.values.forEach { it.file.delete() }; partial.clear() }
        queue.forEach { it.file.delete() }; queue.clear()
        if (!copying) saving?.file?.delete()
        io.shutdown()
        super.onDestroy()
    }
}
