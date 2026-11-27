package com.talker.offline

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    companion object {
        init { System.loadLibrary("audiocpp"); System.loadLibrary("talker_jni") }
        private const val PICK_CUSTOM = 41
        private const val SAVE_OUTPUT = 42
        private const val SAVE_MODEL = 43
        private const val IMPORT_MODEL = 44
        private const val REQUEST_RECORD_AUDIO = 45
        private const val REQUEST_POST_NOTIFICATIONS = 46
        private const val RECORD_SECONDS = 15
        private const val RECORD_SAMPLE_RATE = 16000
    }

    private external fun nativeLoad(modelPath: String): String?
    private external fun nativeGenerate(
        text: String,
        language: String,
        voice: FloatArray?,
        voiceRate: Int,
        voiceChannels: Int,
        referenceText: String?,
        numSteps: Int,
        guidanceScale: Float,
        speed: Float,
        denoise: Boolean,
        preprocessPrompt: Boolean,
        postprocessOutput: Boolean
    ): FloatArray?
    private external fun nativeLastError(): String
    private external fun nativeSampleRate(): Int
    private external fun nativeClose()

    private data class Voice(val key:String,val ar:String,val en:String,val gender:String,val file:String,val text:String)
    private data class GenerationRecord(
        val id:Long,
        val fileName:String,
        val text:String,
        val voice:String,
        val duration:Double,
        val createdAt:Long
    )
    private val voices = listOf(
        Voice("ahmed","أحمد","Ahmed","male","ahmed.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("ali","علي","Ali","male","ali.wav","ااه، كل ما تيجي تفكر ان الدنيا وحشة و ان حياتك مليانة مشاكل، اِفتكر انك ف نعم كبيرة انت مش عارف عددها."),
        Voice("hassan","حسن","Hassan","male","hassan.wav","بص، يعني انا شايف انك متستعجلش في القرار، خد وقتك و فكر فيه بهدوء الأول."),
        Voice("karim","كريم","Karim","male","karim.wav","بص، يعني انا شايف انك متستعجلش في القرار، خد وقتك و فكر فيه بهدوء الأول."),
        Voice("mahmoud","محمود","Mahmoud","male","mahmoud.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("mohammed","محمد","Mohammed","male","mohammed.wav","أنا كنت رايح البيت بعد ما خلصت شغل، بس اكتشفت ان كان عندي meeting مهم بخصوص ال budget بس نسيته خالص"),
        Voice("mostafa","مصطفى","Mostafa","male","mostafa.wav","ممكن تبعتلي ال location على WhatsApp عشان أوصل للمكان من غير ما ألفّ كتير؟"),
        Voice("ramy","رامي","Ramy","male","ramy.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("shaker","شاكر","Shaker","male","shaker.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("tareq","طارق","Tareq","male","tarek.wav","الموضوع فعلا مهم جدا احنا نحتاجين ناخد بالنا الفترة الجاية عشان الموضوع مفيهوش هزار يا جماعة"),
        Voice("yehia","يحيى","Yehia","male","yehia.wav","لما حد يقول: اي حاجة، بييبقا عايز حاجة معينة، بس مش عايز يقول."),
        Voice("youssef","يوسف","Youssef","male","youssef.wav","أحب اعيد على كل المصريين اللي لسه فعلا بيشعرو بالفخر و معتزين بأرضهم و فاكرين الدم اللي اتدفع فيها"),
        Voice("hadeer","هدير","Hadeer","female","hadeer.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("hanan","حنان","Hanan","female","hanan.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ."),
        Voice("mariam","مريم","Mariam","female","mariam.wav","ال meeting بكرة الصبح بدري، ف ياريت تلبس الحتة اللي ع الحبل الله يباركلك عشان ناخد ال deal ده."),
        Voice("rana","رنا","Rana","female","rana.wav","ااه، كل ما تيجي تفكر ان الدنيا وحشة و ان حياتك مليانة مشاكل، اِفتكر انك ف نعم كبيرة انت مش عارف عددها."),
        Voice("sarah","سارة","Sarah","female","sarah.wav","بص، يعني انا شايف انك متستعجلش في القرار، خد وقتك و فكر فيه بهدوء الأول."),
        Voice("yasmin","ياسمين","Yasmin","female","yasmin.wav","ابعتلي ال file النهائي بعد ما تراجع كل التعديلات وتتأكد إن مفيش أي ملاحظات ناقصة، عشان أرفعه للعميل قبل نهاية اليوم."),
        Voice("yara","يارا","Yara","female","yara.wav","يعني كل ما تيجي تفكر ان الدنيا بايظة و ان الحياة مش قد كدة، اِفتكر ان ربنا موجود و انه قادر على كل شئ.")
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val modelUrl = "https://huggingface.co/mohammedaly22/VoiceTut-TTS-GGUF/resolve/main/voicetut-tts-q8_0.gguf"
    private val modelName = "voicetut-tts-q8_0.gguf"
    // Flat white + turquoise theme. No gradients.
    private val bg = Color.rgb(244,249,248)
    private val surface = Color.WHITE
    private val surface2 = Color.rgb(230,244,242)
    private val field = Color.rgb(247,251,250)
    private val accent = Color.rgb(0,151,143)
    private val accent2 = Color.rgb(0,112,108)
    private val muted = Color.rgb(91,117,119)
    private val white = Color.rgb(22,49,51)

    private lateinit var pages:List<View>
    private lateinit var navLabels:List<LinearLayout>
    private lateinit var navIcons:List<NavIconView>
    private lateinit var navTitles:List<TextView>
    private lateinit var headerTitle:TextView
    private lateinit var headerSubtitle:TextView
    private lateinit var modelStatus:TextView
    private lateinit var engineStatus:TextView
    private lateinit var modelProgress:ProgressBar
    private lateinit var modelDownloadButton:Button
    private lateinit var modelImportButton:Button
    private lateinit var generationLanguage:Spinner
    private lateinit var textInput:EditText
    private lateinit var generateButton:Button
    private lateinit var generationVoiceText:TextView
    private lateinit var voicePageSelectedText:TextView
    private lateinit var outputStatus:TextView
    private lateinit var playOutputButton:Button
    private lateinit var voiceLibraryStatus:TextView
    private lateinit var voiceLibraryProgress:ProgressBar
    private lateinit var voiceLibraryButton:Button
    private lateinit var voiceSearch:EditText
    private lateinit var voiceList:LinearLayout
    private lateinit var customTranscriptInput:EditText
    private lateinit var customVoiceStatus:TextView
    private lateinit var recordCustomButton:Button
    private lateinit var recordStatusText:TextView
    private lateinit var stepsValueText:TextView
    private lateinit var guidanceValueText:TextView
    private lateinit var speedValueText:TextView
    private lateinit var stepsSeekBar:SeekBar
    private lateinit var guidanceSeekBar:SeekBar
    private lateinit var speedSeekBar:SeekBar
    private lateinit var denoiseCheckBox:CheckBox
    private lateinit var preprocessCheckBox:CheckBox
    private lateinit var postprocessCheckBox:CheckBox
    private lateinit var splitSentencesCheckBox:CheckBox
    private lateinit var historyCountText:TextView
    private lateinit var historyContainer:LinearLayout
    private lateinit var compareHistoryButton:Button

    private var modelLoading = false
    private var modelDownloading = false
    private var engineReady = false
    private var player:MediaPlayer? = null
    private var lastOutput:File? = null
    private var pendingSaveFile:File? = null
    private var playingHistoryFileName:String? = null
    private val generationHistory = mutableListOf<GenerationRecord>()
    private val compareHistoryIds = linkedSetOf<Long>()
    private var generationSteps = 32
    private var guidanceScale = 2.0f
    private var speakingSpeed = 1.0f
    private var denoiseReference = true
    private var preprocessReference = true
    private var postprocessGeneratedAudio = true
    private var splitIntoSeparateSentences = true
    private var activeVoiceKey:String? = null
    private var activeVoiceTitle = "الصوت التلقائي"
    private var activeVoiceSamples:FloatArray? = null
    private var activeVoiceRate = 0
    private var activeVoiceChannels = 0
    private var activeReferenceText:String? = null
    private var customVoiceSamples:FloatArray? = null
    private var customVoiceRate = 0
    private var customVoiceChannels = 0
    private var voiceFilter = "all"
    private var voiceLibraryInstalling = false
    private var recordingReference = false
    @Volatile private var foregroundTaskRunning = false
    @Volatile private var appIsInBackground = false
    private val preferences by lazy { getSharedPreferences("talker_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildShell()
        requestNotificationPermission()
        refreshModelStatus()
        refreshVoiceLibraryStatus()
        installBundledVoiceLibrary()
        restoreSavedPreferences()
        restoreGenerationHistory()
        if (isValidModel(modelFile(),true)) loadEngine()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            clipToPadding = false
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20),dp(12),dp(20),dp(8))
            setBackgroundColor(bg)
        }
        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "TALKER"
            textSize = 19f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        val badge = TextView(this).apply {
            text = "  OFFLINE  "
            textSize = 10f
            setTextColor(accent2)
            background = rounded(surface2,50)
            setPadding(dp(9),dp(5),dp(9),dp(5))
        }
        brandRow.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        brandRow.addView(badge)
        top.addView(brandRow)
        headerTitle = TextView(this).apply {
            text = "إعداد النموذج"
            textSize = 25f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0,dp(12),0,0)
        }
        headerSubtitle = TextView(this).apply {
            text = "إدارة التنزيل والاستيراد"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.RIGHT
            setPadding(0,dp(3),0,dp(8))
        }
        top.addView(headerTitle)
        top.addView(headerSubtitle)
        root.addView(top)

        val pageHost = FrameLayout(this)
        pages = listOf(buildSettingsPage(),buildGenerationPage(),buildVoicesPage())
        pages.forEach { pageHost.addView(it,FrameLayout.LayoutParams(-1,-1)) }
        root.addView(pageHost,LinearLayout.LayoutParams(-1,0,1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8),dp(8),dp(8),dp(8))
            background = rounded(surface,22)
        }
        val titles = listOf("الإعدادات","التوليد","الأصوات")
        val iconNames = listOf("settings","wave","voice")
        val items = mutableListOf<LinearLayout>()
        val icons = mutableListOf<NavIconView>()
        val titleViews = mutableListOf<TextView>()
        titles.forEachIndexed { index,title ->
            val icon = NavIconView(iconNames[index],muted)
            val titleView = TextView(this).apply {
                text = title
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(muted)
                typeface = Typeface.DEFAULT_BOLD
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(surface,14)
                setPadding(dp(4),dp(7),dp(4),dp(7))
                addView(icon,LinearLayout.LayoutParams(dp(22),dp(22)))
                addView(titleView,LinearLayout.LayoutParams(-2,-2).apply { topMargin = dp(3) })
                setOnClickListener { showPage(index) }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,dp(58),1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
            items.add(item)
            icons.add(icon)
            titleViews.add(titleView)
        }
        navLabels = items
        navIcons = icons
        navTitles = titleViews
        val navWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(8),dp(14),dp(9))
            setBackgroundColor(bg)
            addView(nav,LinearLayout.LayoutParams(-1,-2))
        }
        root.addView(navWrap)
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val topInset = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                ).top
                val bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                view.setPadding(0, topInset, 0, bottomInset)
                insets
            }
        } else {
            root.fitsSystemWindows = true
        }
        setContentView(root)
        root.requestApplyInsets()
        showPage(0)
    }

    private fun buildSettingsPage():View {
        val content = pageContent()
        addSection(content,"النموذج الصوتي","نزّل النموذج مرة واحدة أو استورده من نسخة محفوظة خارج التطبيق.")
        val modelCard = card()
        modelCard.addView(label("VoiceTut-TTS Q8_0",18,white,true))
        modelCard.addView(label("نموذج تحويل النص إلى صوت باللهجة المصرية، ويدعم المزج مع الإنجليزية واستنساخ الصوت.",13,muted,false,7))
        modelStatus = label("النموذج غير موجود على الهاتف",13,accent,true,14)
        modelCard.addView(modelStatus)
        modelProgress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        modelCard.addView(modelProgress,LinearLayout.LayoutParams(-1,dp(7)).apply { topMargin = dp(12) })
        modelDownloadButton = primaryButton("تنزيل النموذج مباشرة")
        modelDownloadButton.setOnClickListener { requestModelDownloadLocation() }
        modelCard.addView(modelDownloadButton,fullWidth().apply { topMargin = dp(14) })
        modelImportButton = secondaryButton("استيراد ملف GGUF من الهاتف")
        modelImportButton.setOnClickListener { requestModelImport() }
        modelCard.addView(modelImportButton,fullWidth().apply { topMargin = dp(8) })
        val link = textButton("فتح رابط تنزيل النموذج على Hugging Face")
        link.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(modelUrl))) }
            catch (_:Exception) { toast("تعذر فتح رابط النموذج") }
        }
        modelCard.addView(link,fullWidth().apply { topMargin = dp(5) })
        content.addView(modelCard,fullWidth().apply { topMargin = dp(8) })

        val engineCard = card()
        engineCard.addView(label("حالة المحرك",15,white,true))
        engineStatus = label("المحرك غير جاهز",13,muted,false,8)
        engineCard.addView(engineStatus)
        val reload = secondaryButton("تحميل النموذج في الذاكرة")
        reload.setOnClickListener { loadEngine() }
        engineCard.addView(reload,fullWidth().apply { topMargin = dp(12) })
        content.addView(engineCard,fullWidth().apply { topMargin = dp(12) })

        val info = card()
        info.addView(label("الاحتفاظ بالنموذج بعد إعادة التثبيت",15,white,true))
        info.addView(label("زر التنزيل يحفظ نسخة في المكان الذي تختاره مثل مجلد Downloads، ثم يجهّز نسخة تشغيل داخل التطبيق. عند إعادة تثبيت Talker، استخدم «استيراد ملف GGUF» واختر النسخة الخارجية؛ لن تحتاج إلى تنزيل 1.35 جيجابايت مرة أخرى.",13,muted,false,8))
        info.addView(label("رابط الملف المباشر:\nhuggingface.co/mohammedaly22/VoiceTut-TTS-GGUF",11,accent2,false,10))
        content.addView(info,fullWidth().apply { topMargin = dp(12) })

        val privacy = card()
        privacy.addView(label("خصوصيتك",15,white,true))
        privacy.addView(label("النصوص والتسجيلات المرجعية ونتائج الصوت تتم معالجتها محليًا. الاتصال مطلوب فقط لتنزيل النموذج أو مكتبة الأصوات الجاهزة.",13,muted,false,8))
        content.addView(privacy,fullWidth().apply { topMargin = dp(12) })
        return wrapPage(content)
    }

    private fun buildGenerationPage():View {
        val content = pageContent()
        addSection(content,"إنشاء الصوت","اكتب النص واضبط النتيجة ثم أنشئ مقطعًا واحدًا أو مقطعًا مستقلًا لكل جملة.")

        val inputCard = card()
        inputCard.addView(label("لغة النطق",14,white,true))
        generationLanguage = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("العربية المصرية","English"))
        }
        inputCard.addView(generationLanguage,fullWidth().apply { topMargin = dp(5) })
        inputCard.addView(label("النص المراد نطقه",14,white,true,15))
        textInput = editField("اكتب النص الذي تريد تحويله إلى صوت",5)
        inputCard.addView(textInput,fullWidth().apply { topMargin = dp(8) })
        generationVoiceText = label("الصوت الحالي: الصوت التلقائي",13,accent2,true,14)
        inputCard.addView(generationVoiceText)
        val choose = secondaryButton("اختيار صوت من مكتبة الأصوات")
        choose.setOnClickListener { showPage(2) }
        inputCard.addView(choose,fullWidth().apply { topMargin = dp(10) })
        generateButton = primaryButton("إنشاء الصوت")
        generateButton.setOnClickListener { generateAudio() }
        generateButton.isEnabled = false
        inputCard.addView(generateButton,fullWidth().apply { topMargin = dp(14) })
        content.addView(inputCard,fullWidth().apply { topMargin = dp(8) })

        val tuning = card()
        tuning.addView(label("ضبط جودة الصوت",17,white,true))
        tuning.addView(label("القيم التالية تُرسل مباشرة إلى محرك VoiceTut.",12,muted,false,5))
        val stepsControl = addFlatSlider(tuning,"خطوات التوليد",8f,64f,1f,32f) {
            generationSteps = it.toInt()
            persistGenerationSettings()
        }
        stepsSeekBar = stepsControl.first
        stepsValueText = stepsControl.second
        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("سريع" to 16,"متوازن" to 32,"جودة عالية" to 64).forEach { preset ->
            val button = secondaryButton(preset.first)
            button.textSize = 11f
            button.setOnClickListener {
                stepsSeekBar.progress = preset.second - 8
            }
            presets.addView(button,LinearLayout.LayoutParams(0,dp(42),1f).apply {
                marginEnd = dp(4)
                marginStart = dp(4)
            })
        }
        tuning.addView(presets,fullWidth().apply { topMargin = dp(2) })

        val guidanceControl = addFlatSlider(tuning,"قوة الالتزام بالنص",1f,4f,0.1f,2f) {
            guidanceScale = it
            persistGenerationSettings()
        }
        guidanceSeekBar = guidanceControl.first
        guidanceValueText = guidanceControl.second
        val speedControl = addFlatSlider(tuning,"سرعة النطق",0.6f,1.6f,0.05f,1.0f) {
            speakingSpeed = it
            persistGenerationSettings()
        }
        speedSeekBar = speedControl.first
        speedValueText = speedControl.second

        denoiseCheckBox = settingsCheck("تقليل أثر التسجيل المرجعي (Denoise)",true) {
            denoiseReference = it
            persistGenerationSettings()
        }
        tuning.addView(denoiseCheckBox)
        preprocessCheckBox = settingsCheck("تنقية وتجهيز الصوت المرجعي قبل الاستنساخ",true) {
            preprocessReference = it
            persistGenerationSettings()
        }
        tuning.addView(preprocessCheckBox)
        postprocessCheckBox = settingsCheck("معالجة الصوت الناتج وتشذيب البداية والنهاية",true) {
            postprocessGeneratedAudio = it
            persistGenerationSettings()
        }
        tuning.addView(postprocessCheckBox)
        splitSentencesCheckBox = settingsCheck("إنشاء مقطع WAV منفصل لكل جملة",false) {
            splitIntoSeparateSentences = it
            persistGenerationSettings()
        }
        tuning.addView(splitSentencesCheckBox)
        content.addView(tuning,fullWidth().apply { topMargin = dp(12) })

        val output = card()
        output.addView(label("النتيجة الأخيرة",18,white,true))
        outputStatus = label("لم يتم إنشاء ملف صوتي بعد",13,muted,false,8)
        output.addView(outputStatus)
        playOutputButton = primaryButton("تشغيل آخر ملف صوتي")
        playOutputButton.setOnClickListener { playLastOutput() }
        playOutputButton.isEnabled = false
        output.addView(playOutputButton,fullWidth().apply { topMargin = dp(12) })
        val save = secondaryButton("حفظ آخر مقطع في الملفات")
        save.setOnClickListener { saveLastOutput() }
        output.addView(save,fullWidth().apply { topMargin = dp(8) })
        val share = secondaryButton("مشاركة آخر مقطع")
        share.setOnClickListener { shareLastOutput() }
        output.addView(share,fullWidth().apply { topMargin = dp(8) })
        content.addView(output,fullWidth().apply { topMargin = dp(14) })

        val history = card()
        val historyHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyCountText = label("سجل المقاطع (0)",16,white,true)
        historyHeader.addView(historyCountText,LinearLayout.LayoutParams(0,-2,1f))
        compareHistoryButton = secondaryButton("مقارنة مقطعين")
        compareHistoryButton.isEnabled = false
        compareHistoryButton.setOnClickListener { compareSelectedHistory() }
        historyHeader.addView(compareHistoryButton)
        history.addView(historyHeader)
        history.addView(label("كل نتيجة محفوظة كملف مستقل. اختر مقطعين للمقارنة أو شغّل واحفظ وشارك كل مقطع على حدة.",12,muted,false,5))
        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0,dp(9),0,0)
        }
        history.addView(historyContainer,fullWidth().apply { topMargin = dp(5) })
        content.addView(history,fullWidth().apply { topMargin = dp(14) })

        val tip = card()
        tip.addView(label("نصيحة لتحسين الاستنساخ",14,white,true))
        tip.addView(label("استخدم تسجيلًا واضحًا بصوت متحدث واحد، وفعّل تنقية التسجيل ومعالجة البداية والنهاية. جرّب تغيير قوة الالتزام والسرعة مع الاحتفاظ بالمقاطع السابقة للمقارنة.",13,muted,false,7))
        content.addView(tip,fullWidth().apply { topMargin = dp(12) })
        refreshGenerationHistory()
        return wrapPage(content)
    }

    private fun addFlatSlider(
        container:LinearLayout,
        title:String,
        minimum:Float,
        maximum:Float,
        step:Float,
        initial:Float,
        onChanged:(Float)->Unit
    ):Pair<SeekBar,TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0,dp(11),0,0)
        }
        row.addView(label(title,13,white,true),LinearLayout.LayoutParams(0,-2,1f))
        val valueText = TextView(this).apply {
            text = formatSettingValue(initial,step)
            textSize = 12f
            setTextColor(accent2)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.LEFT
        }
        row.addView(valueText,LinearLayout.LayoutParams(dp(62),-2))
        container.addView(row,fullWidth())
        val seek = SeekBar(this).apply {
            max = ((maximum - minimum) / step).toInt()
            progress = ((initial - minimum) / step).toInt().coerceIn(0,max)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent2)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(209,226,224))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar, progress:Int, fromUser:Boolean) {
                val value = minimum + progress * step
                valueText.text = formatSettingValue(value,step)
                onChanged(value)
            }
            override fun onStartTrackingTouch(bar:SeekBar) {}
            override fun onStopTrackingTouch(bar:SeekBar) {}
        })
        container.addView(seek,fullWidth().apply { topMargin = dp(1) })
        return Pair(seek,valueText)
    }

    private fun formatSettingValue(value:Float,step:Float):String =
        when {
            step >= 1f -> value.toInt().toString()
            step >= 0.1f -> String.format(Locale.US,"%.1f",value)
            else -> String.format(Locale.US,"%.2f×",value)
        }

    private fun settingsCheck(text:String,initial:Boolean,onChanged:(Boolean)->Unit):CheckBox =
        CheckBox(this).apply {
            this.text = text
            isChecked = initial
            textSize = 12.5f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setPadding(0,dp(3),0,dp(3))
            setOnCheckedChangeListener { _,checked -> onChanged(checked) }
        }

    private fun persistGenerationSettings() {
        preferences.edit()
            .putInt("generation_steps",generationSteps)
            .putFloat("generation_guidance",guidanceScale)
            .putFloat("generation_speed",speakingSpeed)
            .putBoolean("generation_denoise",denoiseReference)
            .putBoolean("generation_preprocess",preprocessReference)
            .putBoolean("generation_postprocess",postprocessGeneratedAudio)
            .putBoolean("generation_split_sentences",splitIntoSeparateSentences)
            .apply()
    }

    private fun buildVoicesPage():View {
        val content = pageContent()
        addSection(content,"اختيار نموذج الصوت","19 صوتًا مرجعيًا جاهزًا، أو استنسخ صوتًا من تسجيلك الخاص.")
        val selected = card()
        selected.addView(label("الصوت المستخدم في التوليد",14,white,true))
        voicePageSelectedText = label("الصوت الحالي: الصوت التلقائي",14,accent2,true,7)
        selected.addView(voicePageSelectedText)
        val auto = secondaryButton("استخدام الصوت التلقائي")
        auto.setOnClickListener { selectAutoVoice() }
        selected.addView(auto,fullWidth().apply { topMargin = dp(10) })
        content.addView(selected,fullWidth().apply { topMargin = dp(8) })

        val library = card()
        library.addView(label("مكتبة VoiceTut الجاهزة",17,white,true))
        library.addView(label("عينات WAV أصلية مضمّنة داخل التطبيق — جاهزة للاستخدام دون تنزيل منفصل أو اتصال بالإنترنت.",12,muted,false,7))
        voiceLibraryStatus = label("مكتبة الأصوات غير منزّلة",13,accent,true,12)
        library.addView(voiceLibraryStatus)
        voiceLibraryProgress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 19
            progressTintList = android.content.res.ColorStateList.valueOf(accent2)
        }
        library.addView(voiceLibraryProgress,fullWidth().apply { topMargin = dp(8) })
        voiceLibraryButton = primaryButton("تنزيل مكتبة الأصوات الـ19")
        voiceLibraryButton.setOnClickListener { installBundledVoiceLibrary() }
        library.addView(voiceLibraryButton,fullWidth().apply { topMargin = dp(10) })
        content.addView(library,fullWidth().apply { topMargin = dp(12) })

        val listCard = card()
        listCard.addView(label("الأصوات الجاهزة",17,white,true))
        voiceSearch = editField("ابحث باسم الصوت",1)
        voiceSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { refreshPresetCards() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        listCard.addView(voiceSearch,fullWidth().apply { topMargin = dp(9) })
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("all" to "الكل","male" to "رجالي","female" to "نسائي").forEach { item ->
            val chip = textButton(item.second)
            chip.setOnClickListener { voiceFilter = item.first; refreshPresetCards() }
            filters.addView(chip,LinearLayout.LayoutParams(0,dp(42),1f).apply { marginEnd = dp(4) })
        }
        listCard.addView(filters,fullWidth().apply { topMargin = dp(8) })
        voiceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listCard.addView(voiceList,fullWidth().apply { topMargin = dp(8) })
        content.addView(listCard,fullWidth().apply { topMargin = dp(12) })

        val custom = card()
        custom.addView(label("استنساخ صوت من تسجيلك",17,white,true))
        custom.addView(label("اختر ملف WAV واضحًا من 5 إلى 10 ثوانٍ، ثم اكتب النص المنطوق فيه كما هو.",12,muted,false,7))
        recordCustomButton = primaryButton("تسجيل صوت مباشر — 15 ثانية")
        recordCustomButton.setOnClickListener { requestReferenceRecording() }
        custom.addView(recordCustomButton,fullWidth().apply { topMargin = dp(10) })
        recordStatusText = label("سجّل صوتك مباشرة لمدة 15 ثانية أو اختر ملف WAV",12,muted,false,7)
        custom.addView(recordStatusText)
        val pick = secondaryButton("اختيار تسجيل WAV من الهاتف")
        pick.setOnClickListener { openCustomVoicePicker() }
        custom.addView(pick,fullWidth().apply { topMargin = dp(8) })
        customVoiceStatus = label("لم يتم اختيار تسجيل خاص",12,muted,false,7)
        custom.addView(customVoiceStatus)
        customTranscriptInput = editField("اكتب النص المنطوق في التسجيل كما قيل",3)
        customTranscriptInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?, start:Int, count:Int, after:Int) {}
            override fun onTextChanged(s:CharSequence?, start:Int, before:Int, count:Int) {
                if (activeVoiceKey == "custom") {
                    activeReferenceText = s?.toString()?.trim()
                    preferences.edit().putString("custom_reference_text", s?.toString() ?: "").apply()
                }
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        custom.addView(customTranscriptInput,fullWidth().apply { topMargin = dp(8) })
        val useCustom = primaryButton("استخدام الصوت المستنسخ")
        useCustom.setOnClickListener { selectCustomVoice() }
        custom.addView(useCustom,fullWidth().apply { topMargin = dp(10) })
        content.addView(custom,fullWidth().apply { topMargin = dp(12) })

        val note = card()
        note.addView(label("معلومة مهمة",14,white,true))
        note.addView(label("الأصوات الـ19 ليست 19 نماذج ذكاء اصطناعي منفصلة؛ هي 19 تسجيلًا مرجعيًا بأسمائها ونصوصها الأصلية. يستخدمها محرك VoiceTut نفسه كمدخلات لاستنساخ أساليب المتحدثين.",12,muted,false,7))
        content.addView(note,fullWidth().apply { topMargin = dp(12) })
        refreshPresetCards()
        return wrapPage(content)
    }

    private fun pageContent() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18),dp(8),dp(18),dp(24))
    }
    private fun wrapPage(content:LinearLayout):View = ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        addView(content,FrameLayout.LayoutParams(-1,-2))
    }
    private fun addSection(parent:LinearLayout,title:String,subtitle:String) {
        parent.addView(label(title,21,white,true))
        parent.addView(label(subtitle,12,muted,false,5))
    }
    private fun showPage(index:Int) {
        val selected = index.coerceIn(0,2)
        pages.forEachIndexed { i,view -> view.visibility = if (i == selected) View.VISIBLE else View.GONE }
        val titles = listOf("إعداد النموذج","توليد الصوت","مكتبة الأصوات")
        val subtitles = listOf("إدارة النموذج والتنزيل والاستيراد","اكتب النص واستمع إلى النتيجة واحفظها","اختر من الأصوات الجاهزة أو أضف صوتك")
        headerTitle.text = titles[selected]
        headerSubtitle.text = subtitles[selected]
        navLabels.forEachIndexed { i,item ->
            val active = i == selected
            navIcons[i].setTint(if (active) accent else muted)
            navTitles[i].setTextColor(if (active) accent2 else muted)
            item.background = rounded(if (active) Color.rgb(216,241,238) else surface,14)
        }
        if (selected == 2) refreshPresetCards()
    }

    private fun refreshModelStatus() {
        val file = modelFile()
        val ready = isValidModel(file,true)
        modelStatus.text = when {
            modelDownloading -> "جارٍ تنزيل أو استيراد النموذج..."
            ready -> "النموذج محفوظ داخل التطبيق (" + file.length()/(1024*1024) + " MB)"
            else -> "النموذج غير موجود — نزّله أو استورده"
        }
        modelDownloadButton.isEnabled = !modelDownloading && !modelLoading
        modelImportButton.isEnabled = !modelDownloading && !modelLoading
        if (ready) modelProgress.progress = 100
    }
    private fun requestModelDownloadLocation() {
        if (isValidModel(modelFile(),true)) { loadEngine(); return }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE,modelName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,SAVE_MODEL)
    }
    private fun requestModelImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,IMPORT_MODEL)
    }

    private fun downloadModelToExternal(uri:Uri) {
        if (modelDownloading || rejectIfTaskRunning()) return
        modelDownloading = true
        beginLongTask("تنزيل نموذج الصوت", "data")
        modelProgress.progress = 0
        modelStatus.text = "جارٍ تنزيل الملف وحفظ نسخة خارج التطبيق..."
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
            var succeeded = false
            var connection:HttpURLConnection? = null
            var external:OutputStream? = null
            val temp = File(cacheDir,modelName + ".part")
            try {
                connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 25000
                    readTimeout = 90000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent","Talker-Android")
                    connect()
                }
                if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
                val total = connection.contentLengthLong
                val externalStream = contentResolver.openOutputStream(uri,"w") ?: error("تعذر فتح مكان الحفظ الخارجي")
                external = externalStream
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { local ->
                        val buffer = ByteArray(1024*1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            local.write(buffer,0,count)
                            externalStream.write(buffer,0,count)
                            received += count
                            val percent = if (total > 0) ((received*100)/total).toInt() else 0
                            runOnUiThread {
                                modelProgress.progress = percent.coerceIn(0,100)
                                modelStatus.text = "تم تنزيل " + received/(1024*1024) + " من " +
                                    (if (total > 0) total/(1024*1024) else 0) + " ميجابايت"
                            }
                        }
                        local.fd.sync()
                    }
                }
                externalStream.flush()
                externalStream.close()
                external = null
                validateModelFile(temp)
                promoteModelFile(temp)
                succeeded = true
                runOnUiThread {
                    modelDownloading = false
                    modelStatus.text = "اكتمل التنزيل — النسخة الخارجية محفوظة أيضًا"
                    refreshModelStatus()
                }
            } catch (e:Exception) {
                try { external?.close() } catch (_:Exception) {}
                temp.delete()
                runOnUiThread {
                    modelDownloading = false
                    modelStatus.text = "فشل التنزيل: " + (e.message ?: "خطأ غير معروف")
                    refreshModelStatus()
                    toast("تعذر تنزيل النموذج أو حفظه")
                }
            } finally {
                connection?.disconnect()
                finishLongTask(
                    if (succeeded) "اكتمل تنزيل النموذج" else "تعذر تنزيل النموذج",
                    if (succeeded) "تم حفظ النموذج داخل التطبيق والنسخة الخارجية." else "لم يكتمل تنزيل النموذج.",
                    succeeded
                )
                if (succeeded) runOnUiThread { if (!isFinishing) loadEngine() }
            }
        }
    }

    private fun importModel(uri:Uri) {
        if (modelDownloading || rejectIfTaskRunning()) return
        modelDownloading = true
        beginLongTask("استيراد نموذج الصوت", "data")
        modelStatus.text = "جارٍ استيراد ملف النموذج..."
        modelProgress.progress = 0
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
            var succeeded = false
            val temp = File(cacheDir,modelName + ".importing")
            try {
                val total = try { contentResolver.openAssetFileDescriptor(uri,"r")?.length ?: -1L }
                    catch (_:Exception) { -1L }
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(1024*1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer,0,count)
                            received += count
                            val percent = if (total > 0) ((received*100)/total).toInt() else 0
                            runOnUiThread {
                                modelProgress.progress = percent.coerceIn(0,100)
                                modelStatus.text = "جارٍ استيراد " + received/(1024*1024) + " MB"
                            }
                        }
                        output.fd.sync()
                    }
                } ?: error("تعذر قراءة الملف المختار")
                validateModelFile(temp)
                promoteModelFile(temp)
                succeeded = true
                runOnUiThread {
                    modelDownloading = false
                    refreshModelStatus()
                    modelStatus.text = "تم استيراد النموذج بنجاح"
                }
            } catch (e:Exception) {
                temp.delete()
                runOnUiThread {
                    modelDownloading = false
                    modelStatus.text = "فشل الاستيراد: " + (e.message ?: "ملف غير صالح")
                    refreshModelStatus()
                    toast("تأكد أن الملف هو VoiceTut Q8_0 GGUF كامل")
                }
            } finally {
                finishLongTask(
                    if (succeeded) "اكتمل استيراد النموذج" else "تعذر استيراد النموذج",
                    if (succeeded) "تم استيراد ملف النموذج والتحقق منه." else "فشل استيراد ملف GGUF.",
                    succeeded
                )
                if (succeeded) runOnUiThread { if (!isFinishing) loadEngine() }
            }
        }
    }

    private fun validateModelFile(file:File) {
        if (!file.isFile || file.length() < 1_000_000_000L) error("حجم ملف GGUF أقل من المتوقع")
        val magic = ByteArray(4)
        FileInputStream(file).use {
            if (it.read(magic) != 4 || String(magic,Charsets.US_ASCII) != "GGUF") error("الملف المختار ليس ملف GGUF صالحًا")
        }
    }
    private fun promoteModelFile(temp:File) {
        val target = modelFile()
        if (target.exists() && !target.delete()) error("تعذر استبدال ملف النموذج القديم")
        if (!temp.renameTo(target)) {
            FileInputStream(temp).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output,1024*1024); output.fd.sync() }
            }
            temp.delete()
        }
        validateModelFile(target)
    }
    private fun isValidModel(file:File,strictSize:Boolean):Boolean {
        if (!file.isFile) return false
        if (strictSize && file.length() < 1_000_000_000L) return false
        return try {
            val magic = ByteArray(4)
            FileInputStream(file).use { it.read(magic) == 4 && String(magic,Charsets.US_ASCII) == "GGUF" }
        } catch (_:Exception) { false }
    }
    private fun loadEngine() {
        if (rejectIfTaskRunning()) return
        if (modelDownloading || modelLoading || !isValidModel(modelFile(),true)) {
            if (!modelLoading && !modelDownloading) engineStatus.text = "استورد ملف النموذج أو نزّله أولًا"
            return
        }
        modelLoading = true
        engineReady = false
        beginLongTask("تحميل محرك الصوت", "media")
        modelStatus.text = "النموذج محفوظ — جارٍ تجهيز المحرك"
        engineStatus.text = "جارٍ تحميل النموذج في الذاكرة..."
        generateButton.isEnabled = false
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
            var loadError: String? = null
            try {
                nativeClose()
                loadError = nativeLoad(modelFile().absolutePath)
            } catch (e:Exception) {
                loadError = e.message ?: "تعذر تشغيل مكتبة المحرك"
            } finally {
                val succeeded = loadError == null
                runOnUiThread {
                    modelLoading = false
                    engineReady = succeeded
                    engineStatus.text = if (engineReady) "المحرك جاهز — التوليد يعمل محليًا" else "تعذر تحميل المحرك: " + loadError
                    generateButton.isEnabled = engineReady
                    modelDownloadButton.isEnabled = true
                    modelImportButton.isEnabled = true
                    refreshModelStatus()
                }
                finishLongTask(
                    if (succeeded) "المحرك جاهز" else "فشل تحميل المحرك",
                    if (succeeded) "تم تجهيز نموذج الصوت في الذاكرة." else "تعذر تجهيز نموذج الصوت.",
                    succeeded
                )
            }
        }
    }

    private fun refreshVoiceLibraryStatus() {
        val count = voices.count { voiceFile(it).isFile && voiceFile(it).length() > 1000L }
        voiceLibraryStatus.text = if (count == voices.size) {
            "الأصوات الجاهزة متاحة دون إنترنت — 19/19"
        } else if (voiceLibraryInstalling) {
            "جارٍ تجهيز الأصوات المضمّنة: " + count + " من " + voices.size
        } else {
            "جارٍ تجهيز مكتبة الأصوات عند تشغيل التطبيق"
        }
        voiceLibraryProgress.progress = count
        voiceLibraryButton.text = when {
            count == voices.size -> "مكتبة الأصوات جاهزة — 19/19"
            voiceLibraryInstalling -> "جارٍ تجهيز الأصوات..."
            else -> "إعادة تجهيز الأصوات الجاهزة"
        }
        voiceLibraryButton.isEnabled = !voiceLibraryInstalling && count < voices.size
    }

    private fun installBundledVoiceLibrary() {
        if (voiceLibraryInstalling) return
        val missing = voices.filter { !voiceFile(it).isFile || voiceFile(it).length() < 1000L }
        if (missing.isEmpty()) {
            refreshVoiceLibraryStatus()
            refreshPresetCards()
            return
        }

        voiceLibraryInstalling = true
        voiceLibraryButton.isEnabled = false
        voiceLibraryStatus.text = "جارٍ نسخ الأصوات الجاهزة إلى مساحة التطبيق..."
        executor.execute {
            try {
                val directory = voiceDirectory()
                if (!directory.exists() && !directory.mkdirs()) error("تعذر إنشاء مجلد الأصوات")
                missing.forEachIndexed { index, voice ->
                    val target = voiceFile(voice)
                    val temp = File(directory, voice.key + ".wav.part")
                    assets.open("reference_speakers/" + voice.file).use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output, 64 * 1024)
                            output.fd.sync()
                        }
                    }
                    if (temp.length() < 1000L || !isWavFile(temp)) {
                        error("عينة " + voice.en + " المضمّنة غير صالحة")
                    }
                    if (target.exists() && !target.delete()) error("تعذر استبدال عينة " + voice.en)
                    if (!temp.renameTo(target)) error("تعذر حفظ عينة " + voice.en)
                    val done = index + 1
                    val installed = voices.size - missing.size + done
                    runOnUiThread {
                        voiceLibraryProgress.progress = installed
                        voiceLibraryStatus.text = "تم تجهيز " + installed + " من " + voices.size
                    }
                }
                runOnUiThread {
                    voiceLibraryInstalling = false
                    refreshVoiceLibraryStatus()
                    refreshPresetCards()
                    toast("الأصوات الـ19 جاهزة — يمكن استخدامها دون إنترنت")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    voiceLibraryInstalling = false
                    refreshVoiceLibraryStatus()
                    voiceLibraryStatus.text = "تعذر تجهيز مكتبة الأصوات: " + (e.message ?: "خطأ غير معروف")
                    refreshPresetCards()
                }
            }
        }
    }

    private fun refreshPresetCards() {
        if (!::voiceList.isInitialized) return
        voiceList.removeAllViews()
        val query = if (::voiceSearch.isInitialized) voiceSearch.text.toString().trim() else ""
        val filtered = voices.filter { voice ->
            val matchGender = voiceFilter == "all" || voice.gender == voiceFilter
            val matchQuery = query.isEmpty() || voice.ar.contains(query,true) || voice.en.contains(query,true)
            matchGender && matchQuery
        }
        if (filtered.isEmpty()) { voiceList.addView(label("لا توجد أصوات مطابقة للبحث",12,muted,false)); return }
        filtered.forEach { voice ->
            val available = voiceFile(voice).isFile && voiceFile(voice).length() > 1000L
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12),dp(12),dp(12),dp(12))
                background = rounded(surface2,17)
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val title = TextView(this).apply {
                text = voice.ar + "  ·  " + voice.en
                textSize = 15f
                setTextColor(white)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.RIGHT
            }
            val gender = TextView(this).apply {
                text = if (voice.gender == "male") "ذكر" else "أنثى"
                textSize = 10f
                setTextColor(if (voice.gender == "male") accent else accent2)
                setPadding(dp(8),dp(4),dp(8),dp(4))
                background = rounded(field,40)
            }
            row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(gender)
            item.addView(row)
            item.addView(label(if (!available) "لم يتم تنزيل العينة بعد" else if (activeVoiceKey == voice.key) "محدد حاليًا" else "عينة مرجعية جاهزة",11,if (activeVoiceKey == voice.key) accent2 else muted,false,5))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val preview = textButton("معاينة")
            preview.isEnabled = available
            preview.setOnClickListener { previewPreset(voice) }
            val use = if (activeVoiceKey == voice.key) primaryButton("محدد") else secondaryButton("استخدام الصوت")
            use.isEnabled = available
            use.setOnClickListener { selectPresetVoice(voice) }
            actions.addView(preview,LinearLayout.LayoutParams(0,dp(42),1f).apply { marginEnd = dp(6) })
            actions.addView(use,LinearLayout.LayoutParams(0,dp(42),1.3f))
            item.addView(actions,fullWidth().apply { topMargin = dp(8) })
            voiceList.addView(item,fullWidth().apply { topMargin = dp(7) })
        }
    }

    private fun selectPresetVoice(voice:Voice) {
        val file = voiceFile(voice)
        if (!file.isFile) { toast("نزّل مكتبة الأصوات أولًا"); return }
        if (activeVoiceKey == voice.key && activeVoiceSamples != null) { showPage(1); return }
        executor.execute {
            try {
                val parsed = FileInputStream(file).use { readWav(it) }
                runOnUiThread {
                    activeVoiceKey = voice.key
                    activeVoiceTitle = voice.ar + " (" + voice.en + ")"
                    activeVoiceSamples = parsed.first
                    activeVoiceRate = parsed.second
                    activeVoiceChannels = parsed.third
                    activeReferenceText = voice.text
                    persistVoiceSelection()
                    updateSelectedVoiceUi()
                    refreshPresetCards()
                    toast("تم اختيار صوت " + voice.ar)
                }
            } catch (e:Exception) { runOnUiThread { toast("تعذر قراءة عينة الصوت: " + e.message) } }
        }
    }
    private fun previewPreset(voice:Voice) {
        val file = voiceFile(voice)
        if (!file.isFile) { toast("نزّل مكتبة الأصوات أولًا"); return }
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { it.release(); if (player === it) player = null }
            }
        } catch (e:Exception) { toast("تعذرت معاينة الصوت: " + e.message) }
    }
    private fun selectAutoVoice() {
        activeVoiceKey = null
        activeVoiceTitle = "الصوت التلقائي"
        activeVoiceSamples = null
        activeVoiceRate = 0
        activeVoiceChannels = 0
        activeReferenceText = null
        customVoiceSamples = null
        persistVoiceSelection()
        updateSelectedVoiceUi()
        refreshPresetCards()
        toast("تم اختيار الصوت التلقائي")
    }
    private fun requestReferenceRecording() {
        if (recordingReference || rejectIfTaskRunning()) return
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startReferenceRecording()
    }

    private fun startReferenceRecording() {
        if (recordingReference) return
        recordingReference = true
        recordCustomButton.isEnabled = false
        recordCustomButton.text = "جارٍ التسجيل..."
        recordStatusText.text = "تحدث الآن — التسجيل لمدة 15 ثانية"
        executor.execute {
            var recorder: AudioRecord? = null
            var saved = false
            try {
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val encoding = AudioFormat.ENCODING_PCM_16BIT
                val minimum = AudioRecord.getMinBufferSize(RECORD_SAMPLE_RATE, channelConfig, encoding)
                if (minimum <= 0) error("تعذر تهيئة الميكروفون على هذا الجهاز")
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    RECORD_SAMPLE_RATE,
                    channelConfig,
                    encoding,
                    maxOf(minimum * 2, 8192)
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) error("تعذر تهيئة الميكروفون")
                val totalSamples = RECORD_SAMPLE_RATE * RECORD_SECONDS
                val pcm = ShortArray(totalSamples)
                recorder.startRecording()
                var offset = 0
                while (offset < totalSamples) {
                    val read = recorder.read(
                        pcm,
                        offset,
                        minOf(totalSamples - offset, maxOf(1024, minimum / 2)),
                        AudioRecord.READ_BLOCKING
                    )
                    if (read < 0) error("توقف تسجيل الميكروفون: " + read)
                    if (read == 0) continue
                    offset += read
                    val elapsed = minOf(RECORD_SECONDS, offset / RECORD_SAMPLE_RATE)
                    runOnUiThread {
                        recordStatusText.text = "جارٍ التسجيل — " + elapsed + " / " + RECORD_SECONDS + " ثانية"
                    }
                }
                recorder.stop()
                val samples = FloatArray(totalSamples) { pcm[it] / 32768f }
                val directory = File(filesDir, "my_voices")
                if (!directory.exists() && !directory.mkdirs()) error("تعذر إنشاء مجلد التسجيل")
                val file = File(directory, "custom-reference.wav")
                writeWav(file, samples, RECORD_SAMPLE_RATE, 1)
                val parsed = FileInputStream(file).use { readWav(it) }
                customVoiceSamples = parsed.first
                customVoiceRate = parsed.second
                customVoiceChannels = parsed.third
                saved = true
                runOnUiThread {
                    customVoiceStatus.text = "تم تسجيل صوتك — 15 ثانية (" + file.length() / 1024 + " KB)"
                    recordStatusText.text = "اكتمل التسجيل. اكتب النص المطابق ثم اضغط استخدام الصوت المستنسخ."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    recordStatusText.text = "فشل التسجيل: " + (e.message ?: "خطأ غير معروف")
                }
            } finally {
                try { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                recordingReference = false
                runOnUiThread {
                    recordCustomButton.isEnabled = true
                    recordCustomButton.text = "تسجيل صوت مباشر — 15 ثانية"
                    if (!saved) customVoiceStatus.text = "لم يتم حفظ تسجيل جديد"
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startReferenceRecording()
            } else {
                recordStatusText.text = "يلزم السماح باستخدام الميكروفون لتسجيل الصوت"
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
    }

    private fun persistVoiceSelection() {
        preferences.edit()
            .putString("selected_voice_key", activeVoiceKey ?: "auto")
            .putString("selected_voice_title", activeVoiceTitle)
            .putString("custom_reference_text", customTranscriptInput.text.toString())
            .apply()
    }

    private fun persistUiPreferences() {
        if (::generationLanguage.isInitialized) {
            preferences.edit()
                .putInt("generation_language", generationLanguage.selectedItemPosition)
                .putString("last_text", if (::textInput.isInitialized) textInput.text.toString() else "")
                .putString("custom_reference_text", if (::customTranscriptInput.isInitialized) customTranscriptInput.text.toString() else "")
                .putInt("generation_steps",generationSteps)
                .putFloat("generation_guidance",guidanceScale)
                .putFloat("generation_speed",speakingSpeed)
                .putBoolean("generation_denoise",denoiseReference)
                .putBoolean("generation_preprocess",preprocessReference)
                .putBoolean("generation_postprocess",postprocessGeneratedAudio)
                .putBoolean("generation_split_sentences",splitIntoSeparateSentences)
                .apply()
        }
    }

    private fun restoreSavedPreferences() {
        generationSteps = preferences.getInt("generation_steps",32).coerceIn(8,64)
        guidanceScale = preferences.getFloat("generation_guidance",2.0f).coerceIn(0f,5f)
        speakingSpeed = preferences.getFloat("generation_speed",1.0f).coerceIn(0.5f,2.0f)
        denoiseReference = preferences.getBoolean("generation_denoise",true)
        preprocessReference = preferences.getBoolean("generation_preprocess",true)
        postprocessGeneratedAudio = preferences.getBoolean("generation_postprocess",true)
        splitIntoSeparateSentences = preferences.getBoolean("generation_split_sentences",true)
        stepsSeekBar.progress = generationSteps - 8
        guidanceSeekBar.progress = ((guidanceScale - 1.0f) * 10f).toInt().coerceIn(0,30)
        speedSeekBar.progress = ((speakingSpeed - 0.6f) / 0.05f).toInt().coerceIn(0,20)
        denoiseCheckBox.isChecked = denoiseReference
        preprocessCheckBox.isChecked = preprocessReference
        postprocessCheckBox.isChecked = postprocessGeneratedAudio
        splitSentencesCheckBox.isChecked = splitIntoSeparateSentences
        val language = preferences.getInt("generation_language", 0).coerceIn(0, 1)
        generationLanguage.setSelection(language)
        textInput.setText(preferences.getString("last_text", "") ?: "")
        customTranscriptInput.setText(preferences.getString("custom_reference_text", "") ?: "")
        val savedKey = preferences.getString("selected_voice_key", "auto") ?: "auto"
        executor.execute {
            try {
                val parsed: Triple<FloatArray, Int, Int>?
                val selected = voices.firstOrNull { it.key == savedKey }
                if (savedKey == "custom") {
                    val file = File(filesDir, "my_voices/custom-reference.wav")
                    parsed = if (file.isFile) FileInputStream(file).use { readWav(it) } else null
                } else if (selected != null) {
                    val file = voiceFile(selected)
                    parsed = if (file.isFile) FileInputStream(file).use { readWav(it) } else null
                } else {
                    parsed = null
                }
                runOnUiThread {
                    if (parsed != null && savedKey == "custom") {
                        activeVoiceKey = "custom"
                        activeVoiceTitle = "صوت مستنسخ من تسجيلك"
                        activeVoiceSamples = parsed.first
                        activeVoiceRate = parsed.second
                        activeVoiceChannels = parsed.third
                        activeReferenceText = customTranscriptInput.text.toString()
                        customVoiceSamples = parsed.first
                        customVoiceRate = parsed.second
                        customVoiceChannels = parsed.third
                        customVoiceStatus.text = "تم استعادة التسجيل المحفوظ — 15 ثانية تقريبًا"
                    } else if (parsed != null && selected != null) {
                        activeVoiceKey = selected.key
                        activeVoiceTitle = selected.ar + " (" + selected.en + ")"
                        activeVoiceSamples = parsed.first
                        activeVoiceRate = parsed.second
                        activeVoiceChannels = parsed.third
                        activeReferenceText = selected.text
                    } else {
                        activeVoiceKey = null
                        activeVoiceTitle = "الصوت التلقائي"
                        activeVoiceSamples = null
                        activeVoiceRate = 0
                        activeVoiceChannels = 0
                        activeReferenceText = null
                    }
                    updateSelectedVoiceUi()
                    refreshPresetCards()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("تعذر استعادة اختيار الصوت السابق: " + e.message) }
            }
        }
    }

    private fun rejectIfTaskRunning():Boolean {
        if (!foregroundTaskRunning) return false
        toast("هناك عملية أخرى قيد التنفيذ. انتظر اكتمالها قبل بدء عملية جديدة.")
        return true
    }

    private fun beginLongTask(title:String, kind:String) {
        foregroundTaskRunning = true
        try {
            TalkerTaskService.start(applicationContext, title, kind)
        } catch (e: Exception) {
            toast("تعذر تشغيل الخدمة الأمامية: " + (e.message ?: "خطأ غير معروف"))
        }
    }

    private fun finishLongTask(title:String, message:String, succeeded:Boolean) {
        foregroundTaskRunning = false
        try {
            TalkerTaskService.complete(applicationContext, title, message, succeeded, appIsInBackground)
        } catch (_: Exception) {}
        if (isDestroyed) {
            try { nativeClose() } catch (_: Exception) {}
        }
    }

    private fun openCustomVoicePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,PICK_CUSTOM)
    }

    private fun selectCustomVoice() {
        val samples = customVoiceSamples
        val transcript = customTranscriptInput.text.toString().trim()
        if (samples == null) { toast("اختر تسجيل WAV أولًا"); return }
        if (transcript.isEmpty()) { customTranscriptInput.error = "اكتب النص المطابق للتسجيل"; return }
        activeVoiceKey = "custom"
        activeVoiceTitle = "صوت مستنسخ من تسجيلك"
        activeVoiceSamples = samples
        activeVoiceRate = customVoiceRate
        activeVoiceChannels = customVoiceChannels
        activeReferenceText = transcript
        persistVoiceSelection()
        updateSelectedVoiceUi()
        refreshPresetCards()
        toast("تم اختيار صوتك المستنسخ")
    }
    private fun updateSelectedVoiceUi() {
        if (::generationVoiceText.isInitialized) generationVoiceText.text = "الصوت الحالي: " + activeVoiceTitle
        if (::voicePageSelectedText.isInitialized) voicePageSelectedText.text = "الصوت الحالي: " + activeVoiceTitle
        if (::outputStatus.isInitialized && lastOutput == null) outputStatus.text = "الصوت المحدد: " + activeVoiceTitle + " — لا توجد نتيجة بعد"
    }

    private fun splitSpeechText(text:String):List<String> {
        val normalized = text.replace("\r\n","\n").replace('\r','\n')
        return normalized.split(Regex("(?<=[.!?؟؛])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun generateAudio() {
        val text = textInput.text.toString().trim()
        if (text.isEmpty()) { textInput.error = "اكتب النص أولًا"; return }
        if (rejectIfTaskRunning()) return
        if (!engineReady) { toast("حمّل النموذج وجهّز المحرك أولًا"); showPage(0); return }
        val ref = activeVoiceSamples
        val transcript = activeReferenceText
        if (ref != null && transcript.isNullOrBlank()) { toast("النص المرجعي للصوت غير متوفر"); showPage(2); return }

        val segments = if (splitIntoSeparateSentences) splitSpeechText(text) else listOf(text)
        if (segments.isEmpty()) { toast("لم يتم العثور على جمل صالحة للتوليد"); return }
        if (segments.size > 25) { toast("الحد الأقصى 25 جملة في العملية الواحدة"); return }

        val languageCode = if (generationLanguage.selectedItemPosition == 0) "arz" else "en"
        val refRate = activeVoiceRate
        val refChannels = activeVoiceChannels
        val voiceName = activeVoiceTitle
        val selectedSteps = generationSteps
        val selectedGuidance = guidanceScale
        val selectedSpeed = speakingSpeed
        val selectedDenoise = denoiseReference
        val selectedPreprocess = preprocessReference
        val selectedPostprocess = postprocessGeneratedAudio

        generateButton.isEnabled = false
        persistUiPreferences()
        beginLongTask("توليد " + segments.size + " مقطع صوتي", "media")
        generateButton.text = "جارٍ إنشاء الصوت..."
        outputStatus.text = "جارٍ تجهيز التوليد المحلي..."
        executor.execute {
            var generatedCount = 0
            var lastFile:File? = null
            var failureMessage:String? = null
            try {
                segments.forEachIndexed { index,segment ->
                    runOnUiThread {
                        outputStatus.text = if (segments.size > 1)
                            "جارٍ إنشاء المقطع " + (index+1) + " من " + segments.size
                        else "جارٍ توليد الصوت محليًا..."
                    }
                    val audio = nativeGenerate(
                        segment,languageCode,ref,refRate,refChannels,
                        if (ref != null) transcript else null,
                        selectedSteps,selectedGuidance,selectedSpeed,
                        selectedDenoise,selectedPreprocess,selectedPostprocess
                    ) ?: error(nativeLastError())
                    val rate = nativeSampleRate()
                    val out = File(filesDir,"audio-" + System.currentTimeMillis() + "-" + (index+1) + ".wav")
                    writeWav(out,audio,rate,1)
                    val duration = audio.size.toDouble() / rate.toDouble()
                    val item = GenerationRecord(
                        System.currentTimeMillis(),
                        out.name,
                        segment,
                        voiceName,
                        duration,
                        System.currentTimeMillis()
                    )
                    lastFile = out
                    generatedCount++
                    runOnUiThread {
                        lastOutput = out
                        addGenerationRecord(item)
                        outputStatus.text = "تم إنشاء " + generatedCount + " من " + segments.size +
                            " — " + out.name + " (" + out.length()/1024 + " KB)"
                        playOutputButton.isEnabled = true
                        generateButton.text = "إنشاء الصوت مرة أخرى"
                    }
                }
            } catch (e:Exception) {
                failureMessage = e.message ?: "حدث خطأ غير معروف"
                runOnUiThread {
                    outputStatus.text = "تم إنشاء " + generatedCount + " مقطع قبل الخطأ: " + failureMessage
                    toast("فشل التوليد: " + failureMessage)
                }
            } finally {
                val allSucceeded = failureMessage == null && generatedCount == segments.size
                runOnUiThread {
                    generateButton.isEnabled = engineReady
                    if (generatedCount > 0) {
                        lastOutput = lastFile
                        playOutputButton.isEnabled = true
                        refreshGenerationHistory()
                        if (allSucceeded) {
                            outputStatus.text = if (generatedCount == 1)
                                "اكتمل المقطع وحُفظ في سجل المقاطع"
                            else "اكتمل إنشاء " + generatedCount + " مقاطع مستقلة وحُفظت في السجل"
                        }
                    } else if (failureMessage != null) {
                        generateButton.text = "إعادة محاولة التوليد"
                    }
                }
                finishLongTask(
                    if (allSucceeded) "اكتمل توليد الصوت" else "تعذر إكمال التوليد",
                    if (allSucceeded) "تم حفظ " + generatedCount + " مقطع WAV مستقل."
                    else "تم إنشاء " + generatedCount + " مقطع. " + (failureMessage ?: ""),
                    allSucceeded
                )
            }
        }
    }

    private fun findLastOutput():File? =
        lastOutput?.takeIf { it.isFile } ?: generationHistory.asSequence().mapNotNull { historyFile(it) }.firstOrNull()
        ?: filesDir.listFiles()
            ?.filter { it.name.startsWith("audio-") && it.extension == "wav" }
            ?.maxByOrNull { it.lastModified() }

    private fun historyFile(item:GenerationRecord):File? {
        val name = File(item.fileName).name
        if (name != item.fileName) return null
        return File(filesDir,name).takeIf { it.isFile }
    }

    private fun addGenerationRecord(item:GenerationRecord) {
        generationHistory.removeAll { it.id == item.id }
        generationHistory.add(0,item)
        while (generationHistory.size > 80) {
            val removed = generationHistory.removeAt(generationHistory.lastIndex)
            historyFile(removed)?.delete()
            compareHistoryIds.remove(removed.id)
        }
        persistGenerationHistory()
        refreshGenerationHistory()
    }

    private fun persistGenerationHistory() {
        val array = JSONArray()
        generationHistory.forEach { item ->
            array.put(JSONObject().apply {
                put("id",item.id)
                put("file",item.fileName)
                put("text",item.text)
                put("voice",item.voice)
                put("duration",item.duration)
                put("created",item.createdAt)
            })
        }
        preferences.edit().putString("generation_history",array.toString()).apply()
    }

    private fun restoreGenerationHistory() {
        generationHistory.clear()
        try {
            val raw = preferences.getString("generation_history","[]") ?: "[]"
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val record = GenerationRecord(
                    item.optLong("id"),
                    item.optString("file"),
                    item.optString("text"),
                    item.optString("voice"),
                    item.optDouble("duration"),
                    item.optLong("created")
                )
                if (historyFile(record) != null) generationHistory.add(record)
            }
        } catch (_:Exception) {
            generationHistory.clear()
        }
        refreshGenerationHistory()
        val latest = generationHistory.firstOrNull()
        if (latest != null) {
            lastOutput = historyFile(latest)
            playOutputButton.isEnabled = lastOutput != null
            outputStatus.text = "آخر مقطع محفوظ: " + latest.text.take(65)
        }
    }

    private fun refreshGenerationHistory() {
        if (!::historyContainer.isInitialized) return
        historyContainer.removeAllViews()
        historyCountText.text = "سجل المقاطع (" + generationHistory.size + ")"
        compareHistoryButton.isEnabled = compareHistoryIds.size == 2
        compareHistoryButton.text = if (compareHistoryIds.size == 2) "مقارنة المقطعين المحددين" else "حدد مقطعين للمقارنة"
        if (generationHistory.isEmpty()) {
            historyContainer.addView(label("لم يتم إنشاء مقاطع بعد. ستظهر هنا كل النتائج بشكل منفصل.",12,muted,false,8))
            return
        }
        generationHistory.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11),dp(10),dp(11),dp(10))
                background = rounded(surface2,13)
            }
            row.addView(label(item.text,13,white,true))
            val created = SimpleDateFormat("dd/MM  HH:mm",Locale.getDefault()).format(Date(item.createdAt))
            row.addView(label(item.voice + "  •  " + formatHistoryDuration(item.duration) + "  •  " + created,11,muted,false,4))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0,dp(8),0,0)
            }
            actions.addView(historyAction("play","تشغيل") { playHistoryRecord(item) },LinearLayout.LayoutParams(0,dp(46),1f).apply { marginEnd = dp(4) })
            actions.addView(historyAction("save","حفظ") { saveHistoryRecord(item) },LinearLayout.LayoutParams(0,dp(46),1f).apply { marginEnd = dp(4) })
            actions.addView(historyAction("share","مشاركة") { shareHistoryRecord(item) },LinearLayout.LayoutParams(0,dp(46),1f).apply { marginEnd = dp(4) })
            actions.addView(historyAction("delete","حذف") { deleteHistoryRecord(item) },LinearLayout.LayoutParams(0,dp(46),1f))
            row.addView(actions)
            val compare = CheckBox(this).apply {
                text = "اختيار للمقارنة"
                textSize = 11f
                setTextColor(muted)
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                isChecked = compareHistoryIds.contains(item.id)
                setOnCheckedChangeListener { _,checked ->
                    if (checked) {
                        if (compareHistoryIds.size >= 2) compareHistoryIds.remove(compareHistoryIds.first())
                        compareHistoryIds.add(item.id)
                    } else compareHistoryIds.remove(item.id)
                    compareHistoryButton.isEnabled = compareHistoryIds.size == 2
                    compareHistoryButton.text = if (compareHistoryIds.size == 2) "مقارنة المقطعين المحددين" else "حدد مقطعين للمقارنة"
                }
            }
            row.addView(compare)
            historyContainer.addView(row,fullWidth().apply { bottomMargin = dp(8) })
        }
    }

    private fun historyAction(icon:String,title:String,action:()->Unit):View {
        val text = TextView(this).apply {
            this.text = title
            textSize = 10.5f
            setTextColor(accent2)
            gravity = Gravity.CENTER
        }
        val iconView = NavIconView(icon,accent2)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(surface,10)
            setPadding(dp(2),dp(3),dp(2),dp(3))
            addView(iconView,LinearLayout.LayoutParams(dp(17),dp(17)))
            addView(text,LinearLayout.LayoutParams(-1,-2))
            setOnClickListener { action() }
        }
    }

    private fun formatHistoryDuration(seconds:Double):String {
        val total = seconds.toInt().coerceAtLeast(0)
        return String.format(Locale.getDefault(),"%d:%02d",total/60,total%60)
    }

    private fun playHistoryRecord(item:GenerationRecord) {
        val file = historyFile(item) ?: run { toast("ملف المقطع غير موجود"); return }
        try {
            if (playingHistoryFileName == file.name && player?.isPlaying == true) {
                player?.pause()
                toast("تم إيقاف المقطع مؤقتًا")
                return
            }
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    playingHistoryFileName = null
                    if (::playOutputButton.isInitialized) playOutputButton.text = "تشغيل آخر ملف صوتي"
                }
            }
            playingHistoryFileName = file.name
            lastOutput = file
            if (::playOutputButton.isInitialized) playOutputButton.text = "إيقاف مؤقت"
        } catch (e:Exception) { toast("تعذر تشغيل المقطع: " + e.message) }
    }

    private fun playLastOutput() {
        val file = findLastOutput()
        val record = generationHistory.firstOrNull { historyFile(it)?.absolutePath == file?.absolutePath }
        if (record != null) {
            playHistoryRecord(record)
            return
        }
        if (file == null) { toast("لا يوجد ملف صوتي محفوظ بعد"); return }
        try {
            player?.release()
            player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); start() }
            lastOutput = file
            playOutputButton.text = "إيقاف مؤقت"
        } catch (e:Exception) { toast("تعذر تشغيل الصوت: " + e.message) }
    }

    private fun saveHistoryRecord(item:GenerationRecord) {
        val file = historyFile(item) ?: run { toast("ملف المقطع غير موجود"); return }
        pendingSaveFile = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE,file.name)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,SAVE_OUTPUT)
    }

    private fun saveLastOutput() {
        val file = findLastOutput() ?: run { toast("لا يوجد ملف صوتي للحفظ"); return }
        pendingSaveFile = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE,file.name)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,SAVE_OUTPUT)
    }

    private fun shareOutputFile(file:File) {
        try {
            val dir = File(cacheDir,"shared_audio")
            if (!dir.exists() && !dir.mkdirs()) error("تعذر تجهيز ملف المشاركة")
            val shared = File(dir,file.name)
            file.copyTo(shared,overwrite=true)
            val uri = Uri.Builder().scheme("content").authority("$packageName.audio").appendPath(shared.name).build()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM,uri)
                clipData = android.content.ClipData.newUri(contentResolver,file.name,uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send,"مشاركة الملف الصوتي"))
        } catch (e:Exception) { toast("تعذرت مشاركة الصوت: " + e.message) }
    }

    private fun shareHistoryRecord(item:GenerationRecord) {
        val file = historyFile(item) ?: run { toast("ملف المقطع غير موجود"); return }
        shareOutputFile(file)
    }

    private fun shareLastOutput() {
        val file = findLastOutput() ?: run { toast("لا يوجد ملف صوتي للمشاركة"); return }
        shareOutputFile(file)
    }

    private fun deleteHistoryRecord(item:GenerationRecord) {
        AlertDialog.Builder(this)
            .setTitle("حذف المقطع")
            .setMessage("هل تريد حذف هذا المقطع من السجل والذاكرة الداخلية؟")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حذف") { _,_ ->
                historyFile(item)?.delete()
                generationHistory.removeAll { it.id == item.id }
                compareHistoryIds.remove(item.id)
                if (lastOutput?.name == item.fileName) lastOutput = findLastOutput()
                persistGenerationHistory()
                refreshGenerationHistory()
                toast("تم حذف المقطع")
            }
            .show()
    }

    private fun compareSelectedHistory() {
        if (compareHistoryIds.size != 2) { toast("اختر مقطعين أولًا"); return }
        val selected = generationHistory.filter { compareHistoryIds.contains(it.id) }
        if (selected.size != 2) { toast("أحد المقطعين لم يعد موجودًا"); return }
        val names = selected.mapIndexed { index,item ->
            (if (index == 0) "A — " else "B — ") + item.text.take(48) + " (" + formatHistoryDuration(item.duration) + ")"
        }
        AlertDialog.Builder(this)
            .setTitle("مقارنة A / B")
            .setMessage("استمع إلى كل نتيجة بالتتابع وقارن النبرة والسرعة ومدى الالتزام بالنص.")
            .setItems(names.toTypedArray()) { _,which -> playHistoryRecord(selected[which]) }
            .setPositiveButton("تم",null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        appIsInBackground = false
    }

    override fun onPause() {
        persistUiPreferences()
        super.onPause()
    }

    override fun onStop() {
        appIsInBackground = true
        super.onStop()
    }

    @Deprecated("Legacy activity result for broad Android compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            PICK_CUSTOM -> executor.execute {
                try {
                    val dir = File(filesDir,"my_voices")
                    if (!dir.exists() && !dir.mkdirs()) error("تعذر إنشاء مجلد التسجيلات")
                    val file = File(dir,"custom-reference.wav")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output,64*1024) }
                    } ?: error("تعذر قراءة التسجيل")
                    val parsed = FileInputStream(file).use { readWav(it) }
                    runOnUiThread {
                        customVoiceSamples = parsed.first
                        customVoiceRate = parsed.second
                        customVoiceChannels = parsed.third
                        customVoiceStatus.text = "تم اختيار التسجيل — " + file.length()/1024 + " KB"
                    }
                } catch (e:Exception) { runOnUiThread { customVoiceStatus.text = "فشل فتح التسجيل: " + e.message } }
            }
            SAVE_OUTPUT -> {
                val file = pendingSaveFile?.takeIf { it.isFile } ?: findLastOutput() ?: return
                pendingSaveFile = null
                executor.execute {
                    try {
                        val output = contentResolver.openOutputStream(uri,"w") ?: error("تعذر فتح مكان الحفظ")
                        output.use { target -> file.inputStream().use { input -> input.copyTo(target) } }
                        runOnUiThread { toast("تم حفظ الملف الصوتي بنجاح") }
                    } catch (e:Exception) { runOnUiThread { toast("فشل حفظ الصوت: " + e.message) } }
                }
            }
            SAVE_MODEL -> downloadModelToExternal(uri)
            IMPORT_MODEL -> importModel(uri)
        }
    }

    private fun modelFile() = File(filesDir,modelName)
    private fun voiceDirectory() = File(filesDir,"reference_speakers")
    private fun voiceFile(voice:Voice) = File(voiceDirectory(),voice.file)
    private fun isWavFile(file:File):Boolean = try {
        val header = ByteArray(12)
        FileInputStream(file).use {
            it.read(header) == 12 &&
                String(header,0,4,Charsets.US_ASCII) == "RIFF" &&
                String(header,8,4,Charsets.US_ASCII) == "WAVE"
        }
    } catch (_:Exception) { false }

    private fun readWav(input:InputStream):Triple<FloatArray,Int,Int> {
        val bytes = input.readBytes()
        if (bytes.size < 44 || bytes.size > 100*1024*1024 ||
            String(bytes,0,4,Charsets.US_ASCII) != "RIFF" ||
            String(bytes,8,4,Charsets.US_ASCII) != "WAVE") {
            error("الملف ليس WAV صالحًا أو حجمه أكبر من الحد المسموح")
        }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var format = 0
        var channels = 0
        var sampleRate = 0
        var blockAlign = 0
        var bits = 0
        var dataOffset = -1
        var dataSize = 0
        while (pos.toLong()+8L <= bytes.size.toLong()) {
            val id = String(bytes,pos,4,Charsets.US_ASCII)
            val rawSize = b.getInt(pos+4).toLong() and 0xffffffffL
            val start = pos+8
            val end = start.toLong()+rawSize
            if (end > bytes.size.toLong()) error("بيانات WAV مقطوعة أو غير مكتملة")
            if (id == "fmt " && rawSize >= 16L) {
                format = b.getShort(start).toInt() and 0xffff
                channels = b.getShort(start+2).toInt() and 0xffff
                sampleRate = b.getInt(start+4)
                blockAlign = b.getShort(start+12).toInt() and 0xffff
                bits = b.getShort(start+14).toInt() and 0xffff
                if (format == 0xfffe) {
                    if (rawSize < 40L) error("امتداد WAV غير مكتمل")
                    format = b.getInt(start+24)
                }
            } else if (id == "data" && dataOffset < 0) {
                dataOffset = start
                dataSize = rawSize.toInt()
            }
            val next = end+(rawSize and 1L)
            if (next > bytes.size.toLong()) break
            pos = next.toInt()
        }
        if (dataOffset < 0 || channels !in 1..2 || sampleRate <= 0 || blockAlign <= 0) error("بيانات WAV غير مكتملة")
        if (format != 1 && format != 3) error("يدعم التطبيق WAV PCM أو Float فقط")
        if (bits !in listOf(16,24,32) || (format == 3 && bits != 32)) error("يدعم التطبيق WAV PCM 16/24/32-bit أو Float32")
        val bytesPerSample = bits/8
        if (blockAlign != channels*bytesPerSample || dataSize <= 0 || dataSize%blockAlign != 0) error("تنسيق عينات WAV غير مدعوم")
        val count = dataSize/bytesPerSample
        val samples = FloatArray(count)
        for (i in 0 until count) {
            val offset = dataOffset+i*bytesPerSample
            val value = when {
                format == 3 -> b.getFloat(offset)
                bits == 16 -> b.getShort(offset).toInt()/32768f
                bits == 24 -> {
                    var v = (bytes[offset].toInt() and 255) or
                        ((bytes[offset+1].toInt() and 255) shl 8) or
                        ((bytes[offset+2].toInt() and 255) shl 16)
                    if ((v and 0x800000) != 0) v = v or -0x1000000
                    v/8388608f
                }
                else -> b.getInt(offset)/2147483648f
            }
            samples[i] = if (value.isFinite()) value.coerceIn(-1f,1f) else 0f
        }
        return Triple(samples,sampleRate,channels)
    }

    private fun writeWav(file:File,samples:FloatArray,rate:Int,channels:Int) {
        if (rate <= 0 || channels != 1 || samples.isEmpty()) error("صيغة الصوت الناتج غير صالحة")
        val bytes = samples.size*2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36+bytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(rate)
        header.putInt(rate*channels*2)
        header.putShort((channels*2).toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(bytes)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buffer = ByteBuffer.allocate(64*1024).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                if (buffer.remaining() < 2) { out.write(buffer.array(),0,buffer.position()); buffer.clear() }
                buffer.putShort((sample.coerceIn(-1f,1f)*32767f).toInt().toShort())
            }
            if (buffer.position() > 0) out.write(buffer.array(),0,buffer.position())
            out.fd.sync()
        }
    }

    private inner class NavIconView(
        private val iconName:String,
        tint:Int
    ):View(this@MainActivity) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = 1.9f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        fun setTint(color:Int) {
            stroke.color = color
            invalidate()
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val unit = minOf(width,height).toFloat() / 24f
            canvas.save()
            canvas.translate((width - 24f*unit)/2f,(height - 24f*unit)/2f)
            canvas.scale(unit,unit)
            when (iconName) {
                "settings" -> {
                    canvas.drawCircle(12f,12f,3.1f,stroke)
                    canvas.drawCircle(12f,12f,8.1f,stroke)
                    for (i in 0 until 8) {
                        val angle = Math.PI * i / 4.0
                        canvas.drawLine(
                            12f + kotlin.math.cos(angle).toFloat()*8.4f,
                            12f + kotlin.math.sin(angle).toFloat()*8.4f,
                            12f + kotlin.math.cos(angle).toFloat()*10.3f,
                            12f + kotlin.math.sin(angle).toFloat()*10.3f,
                            stroke
                        )
                    }
                }
                "wave" -> {
                    val heights = floatArrayOf(5f,10f,15f,8f,13f,6f)
                    heights.forEachIndexed { index,h ->
                        val x = 3.5f + index*3.4f
                        canvas.drawLine(x,12f-h/2f,x,12f+h/2f,stroke)
                    }
                }
                "voice" -> {
                    canvas.drawCircle(12f,7.5f,3.4f,stroke)
                    val path = Path().apply {
                        moveTo(4f,20f)
                        cubicTo(4.5f,15.5f,8f,13.5f,12f,13.5f)
                        cubicTo(16f,13.5f,19.5f,15.5f,20f,20f)
                    }
                    canvas.drawPath(path,stroke)
                }
                "play" -> {
                    val path = Path().apply { moveTo(8f,5f); lineTo(19f,12f); lineTo(8f,19f); close() }
                    canvas.drawPath(path,stroke)
                }
                "save" -> {
                    canvas.drawRoundRect(RectF(4f,3f,20f,21f),2f,2f,stroke)
                    canvas.drawLine(8f,3f,8f,9f,stroke)
                    canvas.drawLine(16f,3f,16f,9f,stroke)
                    canvas.drawLine(8f,14f,16f,14f,stroke)
                    canvas.drawLine(8f,18f,16f,18f,stroke)
                }
                "share" -> {
                    canvas.drawCircle(6f,12f,2.3f,stroke)
                    canvas.drawCircle(18f,5f,2.3f,stroke)
                    canvas.drawCircle(18f,19f,2.3f,stroke)
                    canvas.drawLine(8f,11f,16f,6f,stroke)
                    canvas.drawLine(8f,13f,16f,18f,stroke)
                }
                "delete" -> {
                    canvas.drawRoundRect(RectF(6f,7f,18f,21f),1.5f,1.5f,stroke)
                    canvas.drawLine(4f,5f,20f,5f,stroke)
                    canvas.drawLine(9f,3f,15f,3f,stroke)
                    canvas.drawLine(10f,10f,10f,18f,stroke)
                    canvas.drawLine(14f,10f,14f,18f,stroke)
                }
            }
            canvas.restore()
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15),dp(15),dp(15),dp(15))
        background = rounded(surface,19).apply { setStroke(dp(1),Color.rgb(220,235,233)) }
    }
    private fun label(text:String,size:Int,color:Int,bold:Boolean,top:Int=0) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.RIGHT
        setPadding(0,dp(top),0,0)
    }
    private fun editField(hintText:String,lines:Int) = EditText(this).apply {
        hint = hintText
        setTextColor(white)
        setHintTextColor(muted)
        textSize = 15f
        gravity = Gravity.TOP or Gravity.RIGHT
        minLines = lines
        setPadding(dp(12),dp(12),dp(12),dp(12))
        background = rounded(field,13)
    }
    private fun primaryButton(text:String) = styledButton(text,accent,white)
    private fun secondaryButton(text:String) = styledButton(text,surface2,white)
    private fun textButton(text:String) = styledButton(text,field,accent2)
    private fun styledButton(text:String,color:Int,textColor:Int) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(textColor)
        background = rounded(color,13)
        minHeight = dp(44)
        setPadding(dp(10),dp(6),dp(10),dp(6))
    }
    private fun rounded(color:Int,radius:Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun fullWidth() = LinearLayout.LayoutParams(-1,-2)
    private fun dp(value:Int) = (value*resources.displayMetrics.density).toInt()
    private fun toast(message:String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }

    override fun onDestroy() {
        try { player?.release() } catch (_: Exception) {}
        if (!foregroundTaskRunning) {
            try { nativeClose() } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}

/** Read-only content provider for sharing generated WAV files safely. */
class TalkerAudioProvider : ContentProvider() {
    override fun onCreate():Boolean = true
    override fun getType(uri:Uri):String = "audio/wav"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor? = null
    override fun insert(uri:Uri,values:ContentValues?):Uri? = null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int = 0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int = 0
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only audio provider")
        val app = context ?: throw FileNotFoundException("Application context unavailable")
        val directory = File(app.cacheDir,"shared_audio").canonicalFile
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing audio filename")
        val target = File(directory,name).canonicalFile
        if (target.parentFile != directory || !target.isFile) throw FileNotFoundException("Audio file not found")
        return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
