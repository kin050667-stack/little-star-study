package com.littlestar.study

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local-only MVP. Parent controls imports; lessons are copied to app-private storage. */
class MainActivity : Activity() {
    private val lessons = mutableListOf<Lesson>()
    private lateinit var root: LinearLayout
    private var currentSubject = "语文"
    private val importCode = 42
    private val bg = Color.rgb(255, 250, 240)
    private val navy = Color.rgb(56, 64, 91)
    private val pink = Color.rgb(255, 226, 216)
    private val blue = Color.rgb(219, 235, 255)

    data class Lesson(val id: String, val title: String, val subject: String, val filename: String, var done: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        loadLessons()
        home()
    }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 28, 28, 28)
        setBackgroundColor(bg)
    }
    private fun start(title: String): LinearLayout {
        activeRenderer?.close()
        activeRenderer = null
        root = column()
        val scroll = ScrollView(this).apply { fillViewport = true; addView(root) }
        setContentView(scroll)
        heading(title)
        return root
    }
    private fun heading(text: String) {
        root.addView(TextView(this).apply {
            this.text = text; textSize = 30f; setTextColor(navy); gravity = Gravity.CENTER
            setPadding(0, 16, 0, 26); setTypeface(null, 1)
        })
    }
    private fun button(label: String, tint: Int, action: () -> Unit) {
        val b = Button(this).apply {
            text = label; textSize = 23f; isAllCaps = false; setTextColor(navy)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(tint))
            setPadding(12, 20, 12, 20)
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })
    }
    private fun note(text: String) {
        root.addView(TextView(this).apply { this.text = text; textSize = 17f; setTextColor(navy); gravity = Gravity.CENTER; setPadding(0, 8, 0, 16) })
    }
    private fun home() {
        start("⭐ 小星星学习屋")
        note("今天想学什么呀？")
        button("📖 语文乐园", pink) { showSubject("语文") }
        button("🔤 英语乐园", blue) { showSubject("英语") }
        note("每学完一课，就点亮一颗小星星 ⭐")
        button("🔒 家长管理", Color.rgb(232, 232, 240)) { parentGate { parentMenu() } }
    }
    private fun showSubject(subject: String) {
        currentSubject = subject
        start(if (subject == "语文") "📖 语文乐园" else "🔤 英语乐园")
        val list = lessons.filter { it.subject == subject }
        if (list.isEmpty()) note("这里还没有课程，请家长先导入学习资料。")
        list.forEach { lesson ->
            val mark = if (lesson.done) "⭐" else "📚"
            button("$mark ${lesson.title}", if (subject == "语文") pink else blue) { openLesson(lesson) }
        }
        button("← 返回首页", Color.LTGRAY) { home() }
    }
    private fun openLesson(lesson: Lesson) {
        val file = File(filesDir, "lessons/${lesson.filename}")
        if (!file.exists()) { Toast.makeText(this, "文件不存在，请重新导入", Toast.LENGTH_LONG).show(); return }
        val ext = file.extension.lowercase()
        when (ext) {
            "pdf" -> showPdf(lesson, file)
            "jpg", "jpeg", "png", "webp" -> showImage(lesson, file)
            "mp4", "m4v", "3gp", "webm" -> showVideo(lesson, file)
            "doc", "docx" -> openExternal(lesson, file, if (ext == "doc") "application/msword" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            else -> Toast.makeText(this, "暂不支持这种文件格式", Toast.LENGTH_LONG).show()
        }
    }
    private fun completion(lesson: Lesson) {
        button(if (lesson.done) "✓ 已完成学习" else "⭐ 我学完啦！", Color.rgb(216, 241, 204)) {
            lesson.done = true; saveLessons(); showSubject(lesson.subject)
        }
        button("← 返回课程", Color.LTGRAY) { showSubject(lesson.subject) }
    }
    private fun showImage(lesson: Lesson, file: File) {
        start(lesson.title)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) { note("图片无法打开"); completion(lesson); return }
        root.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, -2))
        completion(lesson)
    }
    private fun showVideo(lesson: Lesson, file: File) {
        start(lesson.title)
        val player = VideoView(this)
        root.addView(player, LinearLayout.LayoutParams(-1, (resources.displayMetrics.widthPixels * 0.55).toInt().coerceAtLeast(280)))
        player.setVideoPath(file.absolutePath)
        player.setMediaController(MediaController(this).apply { setAnchorView(player) })
        player.setOnPreparedListener { player.start() }
        player.setOnErrorListener { _, _, _ -> Toast.makeText(this, "视频格式可能不受设备支持", Toast.LENGTH_LONG).show(); true }
        completion(lesson)
    }
    private fun showPdf(lesson: Lesson, file: File) {
        start(lesson.title)
        val descriptor = try { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } catch (_: Exception) { null }
        if (descriptor == null) { note("无法打开 PDF"); completion(lesson); return }
        val renderer = try { PdfRenderer(descriptor) } catch (_: Exception) { descriptor.close(); null }
        if (renderer == null) { note("PDF 文件损坏或受密码保护"); completion(lesson); return }
        var pageIndex = 0
        val image = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val counter = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; setTextColor(navy) }
        fun draw() {
            val page = renderer.openPage(pageIndex)
            val width = (resources.displayMetrics.widthPixels - 56).coerceAtMost(1500)
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            image.setImageBitmap(bitmap)
            counter.text = "第 ${pageIndex + 1} / ${renderer.pageCount} 页"
        }
        root.addView(image, LinearLayout.LayoutParams(-1, -2))
        root.addView(counter)
        button("⬅ 上一页", blue) { if (pageIndex > 0) { pageIndex--; draw() } }
        button("下一页 ➡", blue) { if (pageIndex < renderer.pageCount - 1) { pageIndex++; draw() } }
        draw()
        completion(lesson)
        // Reader is released on navigation or activity destruction.
        activeRenderer = renderer
    }
    private var activeRenderer: PdfRenderer? = null
    override fun onDestroy() { activeRenderer?.close(); activeRenderer = null; super.onDestroy() }
    private fun openExternal(lesson: Lesson, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "Lesson", uri)
        }
        try { startActivity(intent) } catch (_: Exception) {
            AlertDialog.Builder(this).setMessage("请先安装支持 Word 文档的阅读应用，或让老师提供 PDF。")
                .setPositiveButton("知道了", null).show()
        }
    }
    private fun parentGate(onPass: () -> Unit) {
        val answer = EditText(this).apply { inputType = 2; hint = "请输入答案" }
        AlertDialog.Builder(this).setTitle("家长验证：17 + 26 = ?")
            .setView(answer).setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                if (answer.text.toString() == "43") onPass()
                else Toast.makeText(this, "验证未通过", Toast.LENGTH_SHORT).show()
            }.show()
    }
    private fun parentMenu() {
        start("🔒 家长管理")
        note("请选择学科，再从平板文件管理器导入资料。")
        button("＋ 导入语文资料", pink) { pickFile("语文") }
        button("＋ 导入英语资料", blue) { pickFile("英语") }
        button("🗂 管理已导入课程", Color.rgb(235, 230, 247)) { manageLessons() }
        button("← 返回首页", Color.LTGRAY) { home() }
    }
    private fun pickFile(subject: String) {
        currentSubject = subject
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "video/mp4", "video/webm", "video/3gpp", "image/jpeg", "image/png", "image/webp", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        }
        startActivityForResult(intent, importCode)
    }
    @Deprecated("Activity result compatibility for minimal no-dependency MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        var displayName = "学习资料"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) displayName = it.getString(0) ?: displayName
        }
        val originalName = displayName
        val extension = originalName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("pdf", "mp4", "m4v", "3gp", "webm", "jpg", "jpeg", "png", "webp", "doc", "docx")) {
            Toast.makeText(this, "不支持该文件类型", Toast.LENGTH_LONG).show(); return
        }
        val input = EditText(this).apply { setText(originalName.substringBeforeLast('.')); selectAll() }
        AlertDialog.Builder(this).setTitle("课程名称（$currentSubject）").setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("导入") { _, _ ->
                val name = input.text.toString().trim().take(60).ifEmpty { "新课程" }
                val id = UUID.randomUUID().toString()
                val filename = "$id.$extension"
                val folder = File(filesDir, "lessons").apply { mkdirs() }
                val dest = File(folder, filename)
                try {
                    contentResolver.openInputStream(uri)?.use { source -> dest.outputStream().use { source.copyTo(it) } }
                        ?: error("无法读取文件")
                    lessons.add(Lesson(id, name, currentSubject, filename))
                    saveLessons()
                    Toast.makeText(this, "导入成功！", Toast.LENGTH_SHORT).show()
                    parentMenu()
                } catch (e: Exception) {
                    dest.delete()
                    AlertDialog.Builder(this).setMessage("导入失败：${e.message}").setPositiveButton("确定", null).show()
                }
            }.show()
    }
    private fun manageLessons() {
        start("🗂 课程管理")
        if (lessons.isEmpty()) note("还没有导入任何资料")
        lessons.toList().forEach { lesson ->
            button("删除 · ${lesson.subject} · ${lesson.title}", Color.rgb(250, 226, 226)) {
                AlertDialog.Builder(this).setTitle("确认删除？").setMessage("${lesson.title} 将从平板本地删除。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        File(filesDir, "lessons/${lesson.filename}").delete()
                        lessons.remove(lesson); saveLessons(); manageLessons()
                    }.show()
            }
        }
        button("← 返回家长管理", Color.LTGRAY) { parentMenu() }
    }
    private fun saveLessons() {
        val array = JSONArray()
        lessons.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("subject", it.subject).put("filename", it.filename).put("done", it.done)) }
        getSharedPreferences("lessons", MODE_PRIVATE).edit().putString("data", array.toString()).apply()
    }
    private fun loadLessons() {
        val raw = getSharedPreferences("lessons", MODE_PRIVATE).getString("data", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val x = array.getJSONObject(i)
                lessons.add(Lesson(x.getString("id"), x.getString("title"), x.getString("subject"), x.getString("filename"), x.optBoolean("done")))
            }
        } catch (_: Exception) { lessons.clear() }
    }
}
