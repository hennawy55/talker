package com.talker.offline

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val modelUrl = "https://huggingface.co/mohammedaly22/VoiceTut-TTS-GGUF/resolve/main/voicetut-tts-q8_0.gguf"
    private val modelName = "voicetut-tts-q8_0.gguf"
    private val bg = Color.rgb(12, 16, 24)
    private val surface = Color.rgb(24, 31, 43)
    private val accent = Color.rgb(117, 151, 255)
    private val muted = Color.rgb(164, 176, 196)
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var download: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        root.addView(label("TALKER OFFLINE", 13, accent, true))
        root.addView(label("صوتك.. من غير إنترنت", 27, Color.WHITE, true, 12))
        root.addView(label("نسخة Android مستقلة. تنزيل النموذج يتم إلى مساحة التطبيق الخاصة على الهاتف.", 14, muted, false, 8))

        val modelCard = card()
        modelCard.addView(label("نموذج VoiceTut Q8_0", 17, Color.WHITE, true))
        modelCard.addView(label("الحجم التقريبي ١٫٣٥ جيجابايت. التنزيل مطلوب مرة واحدة، وبعده يبقى الملف محفوظًا محليًا.", 13, muted, false, 8))
        status = label("", 13, accent, true, 12)
        modelCard.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        modelCard.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12) })
        download = actionButton("تنزيل النموذج")
        download.setOnClickListener { downloadModel() }
        modelCard.addView(download, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(modelCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        refreshModelStatus()

        root.addView(label("تحويل النص إلى صوت", 18, Color.WHITE, true, 26))
        root.addView(label("محرك التوليد المحلي لم يُدمج بعد؛ لذلك لن نعرض زرًا يوحي بأن إنشاء الصوت يعمل.", 13, muted, false, 7))
        val textInput = EditText(this).apply {
            hint = "اكتب النص العربي أو الإنجليزي هنا"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.RIGHT
            minLines = 4
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(surface)
            isEnabled = false
        }
        root.addView(textInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(label("استنساخ الصوت", 18, Color.WHITE, true, 24))
        root.addView(label("اختيار التسجيل المرجعي والتوليد سيُفعّلان بعد ربط محرك OmniVoice الأصلي عبر JNI.", 13, muted, false, 7))
        root.addView(label("الخصوصية", 18, Color.WHITE, true, 24))
        root.addView(label("النموذج يُحفظ داخل مجلد التطبيق الخاص. لا يتم رفع التسجيلات أو النصوص إلى خادم.", 13, muted, false, 7))
        setContentView(scroll)
    }

    private fun downloadModel() {
        if (modelFile().exists() && modelFile().length() > 1_000_000_000L) {
            refreshModelStatus()
            return
        }
        download.isEnabled = false
        download.text = "جارٍ التنزيل..."
        status.text = "جارٍ الاتصال بمستودع النموذج..."
        progress.progress = 0
        executor.execute {
            val target = modelFile()
            val temp = File(target.parentFile, "$modelName.part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Talker-Android")
                    connect()
                }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var count: Int
                        var received = 0L
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            received += count
                            val pct = if (total > 0) ((received * 100) / total).toInt() else 0
                            runOnUiThread {
                                progress.progress = pct.coerceIn(0, 100)
                                status.text = "تم تنزيل ${received / (1024 * 1024)} ميجابايت" +
                                    if (total > 0) " من ${total / (1024 * 1024)} ميجابايت" else ""
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (temp.length() < 1_000_000_000L) error("حجم الملف أقل من المتوقع")
                if (!temp.renameTo(target)) error("تعذر حفظ ملف النموذج")
                runOnUiThread { refreshModelStatus() }
            } catch (e: Exception) {
                temp.delete()
                runOnUiThread { status.text = "فشل التنزيل: ${e.message ?: "خطأ غير معروف"}" }
            } finally {
                connection?.disconnect()
                runOnUiThread {
                    download.isEnabled = true
                    download.text = if (target.exists()) "النموذج محفوظ" else "إعادة محاولة التنزيل"
                }
            }
        }
    }

    private fun modelFile() = File(filesDir, modelName)

    private fun refreshModelStatus() {
        val file = modelFile()
        val ready = file.exists() && file.length() > 1_000_000_000L
        status.text = if (ready) "النموذج محفوظ محليًا (${file.length() / (1024 * 1024)} MB)" else "النموذج غير موجود على الهاتف"
        download.text = if (ready) "النموذج محفوظ" else "تنزيل النموذج"
        download.isEnabled = !ready
        progress.progress = if (ready) 100 else 0
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(surface)
    }
    private fun label(text: String, size: Int, color: Int, bold: Boolean, top: Int = 0) =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
            setPadding(0, dp(top), 0, 0)
        }
    private fun actionButton(text: String) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(accent)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
