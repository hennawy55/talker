package com.talker.offline

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        init { System.loadLibrary("audiocpp"); System.loadLibrary("talker_jni") }
        private const val PICK_CUSTOM = 41
        private const val SAVE_OUTPUT = 42
        private const val SAVE_MODEL = 43
        private const val IMPORT_MODEL = 44
    }

    private external fun nativeLoad(modelPath: String): String?
    private external fun nativeGenerate(text: String, language: String, voice: FloatArray?, voiceRate: Int, voiceChannels: Int, referenceText: String?): FloatArray?
    private external fun nativeLastError(): String
    private external fun nativeSampleRate(): Int
    private external fun nativeClose()

    private data class Voice(val key:String,val ar:String,val en:String,val gender:String,val file:String,val text:String)
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
    private val voiceBaseUrl = "https://raw.githubusercontent.com/MohammedAly22/VoiceTuT-TTS/main/reference_speakers/"
    private val bg = Color.rgb(10,14,23)
    private val surface = Color.rgb(22,29,43)
    private val surface2 = Color.rgb(30,39,57)
    private val field = Color.rgb(16,23,36)
    private val accent = Color.rgb(111,143,255)
    private val accent2 = Color.rgb(68,206,178)
    private val muted = Color.rgb(157,171,195)
    private val white = Color.rgb(244,247,255)

    private lateinit var pages:List<View>
    private lateinit var navLabels:List<TextView>
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

    private var modelLoading = false
    private var modelDownloading = false
    private var engineReady = false
    private var player:MediaPlayer? = null
    private var lastOutput:File? = null
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

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
        refreshModelStatus()
        refreshVoiceLibraryStatus()
        if (isValidModel(modelFile(),true)) loadEngine()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
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
            background = rounded(Color.rgb(24,63,62),50)
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
        val icons = listOf("⚙","◉","♫")
        val items = mutableListOf<TextView>()
        titles.forEachIndexed { index,title ->
            val item = TextView(this).apply {
                text = icons[index] + "\n" + title
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(3),dp(7),dp(3),dp(7))
                setTextColor(muted)
                setOnClickListener { showPage(index) }
            }
            nav.addView(item,LinearLayout.LayoutParams(0,dp(52),1f))
            items.add(item)
        }
        navLabels = items
        val navWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(8),dp(14),dp(9))
            setBackgroundColor(bg)
            addView(nav,LinearLayout.LayoutParams(-1,-2))
        }
        root.addView(navWrap)
        setContentView(root)
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
        addSection(content,"إنشاء الصوت","اكتب النص، اختر اللغة والصوت، ثم أنشئ ملف WAV.")
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

        val output = card()
        output.addView(label("النتيجة",18,white,true))
        outputStatus = label("لم يتم إنشاء ملف صوتي بعد",13,muted,false,8)
        output.addView(outputStatus)
        playOutputButton = primaryButton("تشغيل آخر ملف صوتي")
        playOutputButton.setOnClickListener { playLastOutput() }
        playOutputButton.isEnabled = false
        output.addView(playOutputButton,fullWidth().apply { topMargin = dp(12) })
        val save = secondaryButton("حفظ الصوت في الملفات")
        save.setOnClickListener { saveLastOutput() }
        output.addView(save,fullWidth().apply { topMargin = dp(8) })
        val share = secondaryButton("مشاركة الصوت")
        share.setOnClickListener { shareLastOutput() }
        output.addView(share,fullWidth().apply { topMargin = dp(8) })
        content.addView(output,fullWidth().apply { topMargin = dp(14) })

        val tip = card()
        tip.addView(label("نصيحة",14,white,true))
        tip.addView(label("للاستنساخ، اختر صوتًا جاهزًا من صفحة الأصوات أو أضف تسجيلك الخاص مع النص المطابق له. اترك الاختيار على الصوت التلقائي لاستخدام الصوت الافتراضي للمحرك.",13,muted,false,7))
        content.addView(tip,fullWidth().apply { topMargin = dp(12) })
        return wrapPage(content)
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
        library.addView(label("عينات WAV أصلية من مستودع VoiceTut. تُنزّل مرة واحدة داخل التطبيق لتعمل دون إنترنت بعد ذلك.",12,muted,false,7))
        voiceLibraryStatus = label("مكتبة الأصوات غير منزّلة",13,accent,true,12)
        library.addView(voiceLibraryStatus)
        voiceLibraryProgress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 19
            progressTintList = android.content.res.ColorStateList.valueOf(accent2)
        }
        library.addView(voiceLibraryProgress,fullWidth().apply { topMargin = dp(8) })
        voiceLibraryButton = primaryButton("تنزيل مكتبة الأصوات الـ19")
        voiceLibraryButton.setOnClickListener { downloadVoiceLibrary() }
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
        val pick = secondaryButton("اختيار تسجيل WAV من الهاتف")
        pick.setOnClickListener { openCustomVoicePicker() }
        custom.addView(pick,fullWidth().apply { topMargin = dp(10) })
        customVoiceStatus = label("لم يتم اختيار تسجيل خاص",12,muted,false,7)
        custom.addView(customVoiceStatus)
        customTranscriptInput = editField("النص المطابق للتسجيل المرجعي",3)
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
        addView(content,ScrollView.LayoutParams(-1,-2))
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
            item.setTextColor(if (active) accent else muted)
            item.background = if (active) rounded(Color.rgb(34,47,76),16) else rounded(surface,16)
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
        if (modelDownloading) return
        modelDownloading = true
        modelProgress.progress = 0
        modelStatus.text = "جارٍ تنزيل الملف وحفظ نسخة خارج التطبيق..."
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
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
                external = contentResolver.openOutputStream(uri,"w") ?: error("تعذر فتح مكان الحفظ الخارجي")
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { local ->
                        val buffer = ByteArray(1024*1024)
                        var received = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            local.write(buffer,0,count)
                            external.write(buffer,0,count)
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
                external.flush()
                external.close()
                external = null
                validateModelFile(temp)
                promoteModelFile(temp)
                runOnUiThread {
                    modelDownloading = false
                    modelStatus.text = "اكتمل التنزيل — النسخة الخارجية محفوظة أيضًا"
                    refreshModelStatus()
                    loadEngine()
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
            } finally { connection?.disconnect() }
        }
    }

    private fun importModel(uri:Uri) {
        if (modelDownloading) return
        modelDownloading = true
        modelStatus.text = "جارٍ استيراد ملف النموذج..."
        modelProgress.progress = 0
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
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
                runOnUiThread {
                    modelDownloading = false
                    refreshModelStatus()
                    modelStatus.text = "تم استيراد النموذج بنجاح"
                    loadEngine()
                }
            } catch (e:Exception) {
                temp.delete()
                runOnUiThread {
                    modelDownloading = false
                    modelStatus.text = "فشل الاستيراد: " + (e.message ?: "ملف غير صالح")
                    refreshModelStatus()
                    toast("تأكد أن الملف هو VoiceTut Q8_0 GGUF كامل")
                }
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
        if (modelDownloading || modelLoading || !isValidModel(modelFile(),true)) {
            if (!modelLoading && !modelDownloading) engineStatus.text = "استورد ملف النموذج أو نزّله أولًا"
            return
        }
        modelLoading = true
        engineReady = false
        modelStatus.text = "النموذج محفوظ — جارٍ تجهيز المحرك"
        engineStatus.text = "جارٍ تحميل النموذج في الذاكرة..."
        generateButton.isEnabled = false
        modelDownloadButton.isEnabled = false
        modelImportButton.isEnabled = false
        executor.execute {
            val error = try { nativeClose(); nativeLoad(modelFile().absolutePath) }
                catch (e:Exception) { e.message ?: "تعذر تشغيل مكتبة المحرك" }
            runOnUiThread {
                modelLoading = false
                engineReady = error == null
                engineStatus.text = if (engineReady) "المحرك جاهز — التوليد يعمل محليًا" else "تعذر تحميل المحرك: " + error
                generateButton.isEnabled = engineReady
                modelDownloadButton.isEnabled = true
                modelImportButton.isEnabled = true
                refreshModelStatus()
            }
        }
    }

    private fun refreshVoiceLibraryStatus() {
        val count = voices.count { voiceFile(it).isFile && voiceFile(it).length() > 1000L }
        voiceLibraryStatus.text = "تم تجهيز " + count + " من " + voices.size + " صوت"
        voiceLibraryProgress.progress = count
        voiceLibraryButton.text = if (count == voices.size) "مكتبة الأصوات جاهزة — 19/19" else "تنزيل مكتبة الأصوات الـ19"
    }
    private fun downloadVoiceLibrary() {
        val missing = voices.filter { !voiceFile(it).isFile || voiceFile(it).length() < 1000L }
        if (missing.isEmpty()) { refreshVoiceLibraryStatus(); toast("مكتبة الأصوات جاهزة بالفعل"); return }
        voiceLibraryButton.isEnabled = false
        voiceLibraryStatus.text = "جارٍ تنزيل عينات الأصوات..."
        executor.execute {
            try {
                val directory = voiceDirectory()
                if (!directory.exists() && !directory.mkdirs()) error("تعذر إنشاء مجلد الأصوات")
                missing.forEachIndexed { index,voice ->
                    val target = voiceFile(voice)
                    val temp = File(directory,voice.key + ".wav.part")
                    var connection:HttpURLConnection? = null
                    try {
                        connection = (URL(voiceBaseUrl + voice.file).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 20000
                            readTimeout = 60000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent","Talker-Android")
                        }
                        if (connection.responseCode !in 200..299) error("تعذر تنزيل " + voice.en)
                        connection.inputStream.use { input ->
                            FileOutputStream(temp).use { output -> input.copyTo(output,64*1024); output.fd.sync() }
                        }
                        if (temp.length() < 1000L || !isWavFile(temp)) error("عينة " + voice.en + " غير صالحة")
                        if (target.exists()) target.delete()
                        if (!temp.renameTo(target)) error("تعذر حفظ " + voice.en)
                    } finally { connection?.disconnect() }
                    val done = index + 1
                    runOnUiThread {
                        voiceLibraryProgress.progress = (voices.size-missing.size+done).coerceIn(0,voices.size)
                        voiceLibraryStatus.text = "تم تنزيل " + (voices.size-missing.size+done) + " من " + voices.size
                    }
                }
                runOnUiThread {
                    voiceLibraryButton.isEnabled = true
                    refreshVoiceLibraryStatus()
                    refreshPresetCards()
                    toast("تم تجهيز الأصوات المرجعية الـ19")
                }
            } catch (e:Exception) {
                runOnUiThread {
                    voiceLibraryButton.isEnabled = true
                    refreshVoiceLibraryStatus()
                    voiceLibraryStatus.text = "تعذر إكمال المكتبة: " + e.message
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
        updateSelectedVoiceUi()
        refreshPresetCards()
        toast("تم اختيار الصوت التلقائي")
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
        updateSelectedVoiceUi()
        refreshPresetCards()
        toast("تم اختيار صوتك المستنسخ")
    }
    private fun updateSelectedVoiceUi() {
        if (::generationVoiceText.isInitialized) generationVoiceText.text = "الصوت الحالي: " + activeVoiceTitle
        if (::voicePageSelectedText.isInitialized) voicePageSelectedText.text = "الصوت الحالي: " + activeVoiceTitle
        if (::outputStatus.isInitialized && lastOutput == null) outputStatus.text = "الصوت المحدد: " + activeVoiceTitle + " — لا توجد نتيجة بعد"
    }

    private fun generateAudio() {
        val text = textInput.text.toString().trim()
        if (text.isEmpty()) { textInput.error = "اكتب النص أولًا"; return }
        if (!engineReady) { toast("حمّل النموذج وجهّز المحرك أولًا"); showPage(0); return }
        val ref = activeVoiceSamples
        val transcript = activeReferenceText
        if (ref != null && transcript.isNullOrBlank()) { toast("النص المرجعي للصوت غير متوفر"); showPage(2); return }
        generateButton.isEnabled = false
        generateButton.text = "جارٍ إنشاء الصوت..."
        outputStatus.text = "جارٍ توليد الصوت محليًا..."
        val languageCode = if (generationLanguage.selectedItemPosition == 0) "arz" else "en"
        val refRate = activeVoiceRate
        val refChannels = activeVoiceChannels
        executor.execute {
            try {
                val audio = nativeGenerate(text,languageCode,ref,refRate,refChannels,if (ref != null) transcript else null)
                    ?: error(nativeLastError())
                val rate = nativeSampleRate()
                val out = File(filesDir,"audio-${System.currentTimeMillis()}.wav")
                writeWav(out,audio,rate,1)
                lastOutput = out
                runOnUiThread {
                    generateButton.text = "إنشاء الصوت مرة أخرى"
                    outputStatus.text = "اكتمل إنشاء الملف — " + out.name + " (" + out.length()/1024 + " KB)"
                    playOutputButton.isEnabled = true
                    toast("تم إنشاء الصوت وحفظه داخل Talker")
                }
            } catch (e:Exception) {
                runOnUiThread {
                    generateButton.text = "إعادة محاولة التوليد"
                    outputStatus.text = "فشل إنشاء الصوت: " + e.message
                    toast("فشل التوليد: " + e.message)
                }
            } finally { runOnUiThread { generateButton.isEnabled = engineReady } }
        }
    }

    private fun findLastOutput():File? = lastOutput?.takeIf { it.isFile } ?: filesDir.listFiles()
        ?.filter { it.name.startsWith("audio-") && it.extension == "wav" }
        ?.maxByOrNull { it.lastModified() }

    private fun playLastOutput() {
        val file = findLastOutput()
        if (file == null) { toast("لا يوجد ملف صوتي محفوظ بعد"); return }
        try {
            if (player != null && player?.isPlaying == true) {
                player?.pause()
                playOutputButton.text = "متابعة تشغيل الصوت"
                return
            }
            if (player != null) {
                try { player?.start(); playOutputButton.text = "إيقاف مؤقت"; return } catch (_:Exception) {}
            }
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    runOnUiThread { playOutputButton.text = "تشغيل آخر ملف صوتي" }
                }
            }
            playOutputButton.text = "إيقاف مؤقت"
            lastOutput = file
        } catch (e:Exception) { toast("تعذر تشغيل الصوت: " + e.message) }
    }
    private fun saveLastOutput() {
        val file = findLastOutput()
        if (file == null) { toast("لا يوجد ملف صوتي للحفظ"); return }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE,file.name)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent,SAVE_OUTPUT)
    }
    private fun shareLastOutput() {
        val file = findLastOutput()
        if (file == null) { toast("لا يوجد ملف صوتي للمشاركة"); return }
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
                val file = findLastOutput() ?: return
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

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15),dp(15),dp(15),dp(15))
        background = rounded(surface,19)
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
        try { player?.release(); nativeClose() } catch (_:Exception) {}
        executor.shutdownNow()
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
