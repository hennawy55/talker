package com.talker.offline

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    companion object { init { System.loadLibrary("audiocpp"); System.loadLibrary("talker_jni") } }
    private external fun nativeLoad(modelPath: String): String?
    private external fun nativeGenerate(text: String, language: String, voice: FloatArray?, voiceRate: Int, voiceChannels: Int, referenceText: String?): FloatArray?
    private external fun nativeLastError(): String
    private external fun nativeSampleRate(): Int
    private external fun nativeClose()

    private val executor = Executors.newSingleThreadExecutor()
    private val modelUrl = "https://huggingface.co/mohammedaly22/VoiceTut-TTS-GGUF/resolve/main/voicetut-tts-q8_0.gguf"
    private val modelName = "voicetut-tts-q8_0.gguf"
    private val bg = Color.rgb(12, 16, 24)
    private val surface = Color.rgb(24, 31, 43)
    private val accent = Color.rgb(117, 151, 255)
    private val muted = Color.rgb(164, 176, 196)
    private lateinit var status: TextView
    private lateinit var engineStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var download: Button
    private lateinit var textInput: EditText
    private lateinit var referenceText: EditText
    private lateinit var language: Spinner
    private lateinit var chooseVoice: Button
    private lateinit var generate: Button
    private var voiceSamples: FloatArray? = null
    private var voiceRate = 0
    private var voiceChannels = 0
    private var engineReady = false
    private var player: MediaPlayer? = null
    private var lastOutput: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        refreshModelStatus()
        if (modelFile().exists() && modelFile().length() > 1_000_000_000L) loadEngine()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        root.addView(label("TALKER OFFLINE", 13, accent, true))
        root.addView(label("صوتك.. من غير إنترنت", 27, Color.WHITE, true, 12))
        root.addView(label("توليد صوت عربي مصري وإنجليزي واستنساخ صوت محليًا على الهاتف.", 14, muted, false, 8))

        val modelCard = card()
        modelCard.addView(label("نموذج VoiceTut Q8_0", 17, Color.WHITE, true))
        modelCard.addView(label("حجم التنزيل نحو 1.35 جيجابايت. بعد التنزيل يعمل المحرك دون اتصال.", 13, muted, false, 8))
        status = label("", 13, accent, true, 12)
        modelCard.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        modelCard.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12) })
        download = actionButton("تنزيل النموذج")
        download.setOnClickListener { downloadModel() }
        modelCard.addView(download, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(modelCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })

        val engineCard = card()
        engineStatus = label("محرك الصوت: غير جاهز", 14, muted, true)
        engineCard.addView(engineStatus)
        root.addView(engineCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        root.addView(label("تحويل النص إلى صوت", 19, Color.WHITE, true, 26))
        language = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("العربية المصرية", "English"))
        }
        root.addView(language, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        textInput = EditText(this).apply {
            hint = "اكتب النص الذي تريد تحويله إلى صوت"
            setTextColor(Color.WHITE); setHintTextColor(muted); textSize = 16f
            gravity = Gravity.TOP or Gravity.RIGHT; minLines = 4
            setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(surface)
        }
        root.addView(textInput, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(label("استنساخ صوتك", 19, Color.WHITE, true, 24))
        root.addView(label("اختر تسجيلًا واضحًا من 5 إلى 10 ثوانٍ، ثم اكتب الكلام المنطوق فيه بدقة.", 13, muted, false, 6))
        chooseVoice = actionButton("اختيار تسجيل مرجعي WAV")
        chooseVoice.setOnClickListener { openVoicePicker() }
        root.addView(chooseVoice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        referenceText = EditText(this).apply {
            hint = "اكتب النص المطابق للتسجيل المرجعي (مطلوب للاستنساخ)"
            setTextColor(Color.WHITE); setHintTextColor(muted); textSize = 15f
            gravity = Gravity.TOP or Gravity.RIGHT; minLines = 2
            setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(surface)
        }
        root.addView(referenceText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        generate = actionButton("إنشاء الصوت")
        generate.isEnabled = false
        generate.setOnClickListener { generateAudio() }
        root.addView(generate, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        root.addView(label("النتيجة", 18, Color.WHITE, true, 24))
        val playback = actionButton("تشغيل آخر ملف صوتي")
        playback.setOnClickListener { playLastOutput() }
        root.addView(playback, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(label("الخصوصية: التسجيل المرجعي والنصوص وملفات الصوت تبقى داخل مساحة التطبيق الخاصة.", 13, muted, false, 20))
        setContentView(scroll)
    }

    private fun downloadModel() {
        if (modelFile().exists() && modelFile().length() > 1_000_000_000L) { loadEngine(); return }
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
                    connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Talker-Android"); connect()
                }
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(1024 * 1024); var received = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            output.write(buffer, 0, count); received += count
                            val pct = if (total > 0) ((received * 100) / total).toInt() else 0
                            runOnUiThread {
                                progress.progress = pct.coerceIn(0, 100)
                                status.text = "تم تنزيل ${received / (1024 * 1024)} من ${if (total > 0) total / (1024 * 1024) else 0} ميجابايت"
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (temp.length() < 1_000_000_000L) error("حجم الملف أقل من المتوقع")
                if (!temp.renameTo(target)) error("تعذر حفظ ملف النموذج")
                runOnUiThread { status.text = "اكتمل تنزيل النموذج"; loadEngine() }
            } catch (e: Exception) {
                temp.delete()
                runOnUiThread { status.text = "فشل التنزيل: ${e.message ?: "خطأ غير معروف"}"; download.isEnabled = true; download.text = "إعادة محاولة التنزيل" }
            } finally { connection?.disconnect() }
        }
    }

    private fun loadEngine() {
        if (!modelFile().exists()) return
        engineStatus.text = "جارٍ تحميل المحرك والنموذج في الذاكرة..."
        generate.isEnabled = false
        executor.execute {
            val error = try { nativeLoad(modelFile().absolutePath) } catch (e: Exception) { e.message ?: "تعذر تشغيل مكتبة المحرك" }
            runOnUiThread {
                engineReady = error == null
                engineStatus.text = if (engineReady) "المحرك جاهز — التوليد محلي على الهاتف" else "تعذر تحميل المحرك: $error"
                generate.isEnabled = engineReady
                download.isEnabled = true
                download.text = "النموذج محفوظ"
            }
        }
    }

    private fun openVoicePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/wav"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 41)
    }

    @Deprecated("Legacy activity result for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            executor.execute {
                try {
                    val parsed = contentResolver.openInputStream(uri)?.use { readWav(it) } ?: error("تعذر قراءة الملف")
                    voiceSamples = parsed.first; voiceRate = parsed.second; voiceChannels = parsed.third
                    runOnUiThread { chooseVoice.text = "تم اختيار التسجيل المرجعي ✓" }
                } catch (e: Exception) {
                    runOnUiThread { chooseVoice.text = "فشل فتح التسجيل: ${e.message}" }
                }
            }
        }
    }

    private fun generateAudio() {
        val text = textInput.text.toString().trim()
        if (text.isEmpty()) { textInput.error = "اكتب النص أولًا"; return }
        val ref = voiceSamples
        val transcript = referenceText.text.toString().trim()
        if (ref != null && transcript.isEmpty()) { referenceText.error = "اكتب النص المنطوق في التسجيل"; return }
        generate.isEnabled = false
        generate.text = "جارٍ إنشاء الصوت..."
        executor.execute {
            try {
                val lang = if (language.selectedItemPosition == 0) "arz" else "en"
                val audio = nativeGenerate(text, lang, ref, voiceRate, voiceChannels, if (ref != null) transcript else null)
                    ?: error(nativeLastError())
                val rate = nativeSampleRate()
                val out = File(filesDir, "audio-${System.currentTimeMillis()}.wav")
                writeWav(out, audio, rate, 1)
                lastOutput = out
                runOnUiThread {
                    generate.text = "تم إنشاء الصوت ✓"
                    Toast.makeText(this, "تم حفظ الصوت داخل التطبيق", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { generate.text = "فشل التوليد: ${e.message}"; Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            } finally { runOnUiThread { generate.isEnabled = engineReady } }
        }
    }

    private fun playLastOutput() {
        val file = lastOutput ?: filesDir.listFiles()?.filter { it.name.startsWith("audio-") && it.extension == "wav" }?.maxByOrNull { it.lastModified() }
        if (file == null || !file.exists()) { Toast.makeText(this, "لا يوجد ملف صوتي محفوظ بعد", Toast.LENGTH_SHORT).show(); return }
        try {
            player?.release()
            player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start() }
            lastOutput = file
        } catch (e: Exception) { Toast.makeText(this, "تعذر تشغيل الصوت: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun readWav(input: InputStream): Triple<FloatArray, Int, Int> {
        val bytes = input.readBytes()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") error("الملف ليس WAV صالحًا")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12; var format = 1; var channels = 0; var rate = 0; var bits = 0
        var dataOffset = -1; var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4); val size = b.getInt(pos + 4).coerceAtLeast(0)
            val start = pos + 8
            if (id == "fmt " && size >= 16) {
                format = b.getShort(start).toInt() and 0xffff
                channels = b.getShort(start + 2).toInt() and 0xffff
                rate = b.getInt(start + 4)
                bits = b.getShort(start + 14).toInt() and 0xffff
            } else if (id == "data") { dataOffset = start; dataSize = min(size, bytes.size - start); break }
            pos = start + size + (size and 1)
        }
        if (dataOffset < 0 || channels !in 1..2 || rate <= 0) error("بيانات WAV غير مكتملة")
        val bytesPerSample = bits / 8
        if (bytesPerSample !in 2..4 || (format != 1 && !(format == 3 && bits == 32))) error("يدعم التطبيق WAV PCM 16/24/32-bit أو Float32")
        val count = dataSize / bytesPerSample
        val out = FloatArray(count)
        for (i in 0 until count) {
            val p = dataOffset + i * bytesPerSample
            out[i] = when {
                format == 3 -> b.getFloat(p).coerceIn(-1f, 1f)
                bits == 16 -> (b.getShort(p).toInt() / 32768f)
                bits == 24 -> {
                    var v = (bytes[p].toInt() and 255) or ((bytes[p+1].toInt() and 255) shl 8) or ((bytes[p+2].toInt() and 255) shl 16)
                    if ((v and 0x800000) != 0) v = v or -0x1000000
                    v / 8388608f
                }
                else -> (b.getInt(p) / 2147483648f)
            }
        }
        return Triple(out, rate, channels)
    }

    private fun writeWav(file: File, samples: FloatArray, rate: Int, channels: Int) {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort((it.coerceIn(-1f, 1f) * 32767f).toInt().toShort()) }
        val data = pcm.array()
        FileOutputStream(file).use { out ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + data.size); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
            h.putInt(rate); h.putInt(rate * channels * 2); h.putShort((channels * 2).toShort()); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(data.size)
            out.write(h.array()); out.write(data)
        }
    }

    private fun modelFile() = File(filesDir, modelName)
    private fun refreshModelStatus() {
        val file = modelFile(); val ready = file.exists() && file.length() > 1_000_000_000L
        status.text = if (ready) "النموذج محفوظ محليًا (${file.length() / (1024 * 1024)} MB)" else "النموذج غير موجود على الهاتف"
        download.text = if (ready) "النموذج محفوظ" else "تنزيل النموذج"
        download.isEnabled = !ready; progress.progress = if (ready) 100 else 0
    }
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)); setBackgroundColor(surface) }
    private fun label(text: String, size: Int, color: Int, bold: Boolean, top: Int = 0) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.RIGHT; setPadding(0, dp(top), 0, 0)
    }
    private fun actionButton(text: String) = Button(this).apply { this.text = text; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(accent) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        try { player?.release(); nativeClose() } catch (_: Exception) {}
        executor.shutdownNow()
        super.onDestroy()
    }
}
