package com.talker.offline

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bgColor = Color.rgb(12, 16, 24)
    private val surface = Color.rgb(24, 31, 43)
    private val primary = Color.rgb(117, 151, 255)
    private val muted = Color.rgb(164, 176, 196)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        window.decorView.systemUiVisibility = 0

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }

        content.addView(label("TALKER", 13, primary, true))
        content.addView(label("صوتك.. من غير إنترنت", 27, Color.WHITE, true, top = 12))
        content.addView(label(
            "نسخة Android مستقلة. سيتم تنزيل ملفات النموذج على الهاتف مرة واحدة، ثم تتم المعالجة محليًا.",
            14, muted, false, top = 10
        ))

        val modelCard = card()
        modelCard.addView(label("حالة النموذج", 16, Color.WHITE, true))
        modelCard.addView(label(
            "لم يتم تجهيز محرك Android بعد. هذه النسخة هي أساس التطبيق، ولا تولّد صوتًا حتى يتم دمج النموذج واختباره.",
            13, muted, false, top = 8
        ))
        val modelStatus = label("المحرك: غير متصل", 13, primary, true, top = 14)
        modelCard.addView(modelStatus)
        content.addView(modelCard, marginTop = 24)

        content.addView(sectionTitle("إنشاء صوت"))
        content.addView(label("اكتب النص الذي تريد تحويله إلى كلام.", 13, muted, false, top = 6))
        val textInput = android.widget.EditText(this).apply {
            hint = "اكتب النص هنا..."
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            textSize = 16f
            gravity = Gravity.TOP or Gravity.RIGHT
            minLines = 5
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(surface)
            isEnabled = false
        }
        content.addView(textInput, marginTop = 12)

        val generate = actionButton("توليد الصوت")
        generate.isEnabled = false
        content.addView(generate, marginTop = 12)

        content.addView(sectionTitle("استنساخ الصوت"))
        content.addView(label(
            "سيتم إضافة اختيار تسجيل مرجعي وتخزينه محليًا بعد نقل Audio Encoder واختباره على Android.",
            13, muted, false, top = 6
        ))
        val clone = actionButton("إضافة صوت مرجعي")
        clone.isEnabled = false
        content.addView(clone, marginTop = 12)

        content.addView(sectionTitle("السجل"))
        content.addView(label(
            "ستظهر التسجيلات التي تم إنشاؤها على هذا الهاتف هنا. التخزين سيكون محليًا.",
            13, muted, false, top = 6
        ))
        content.addView(sectionTitle("الخصوصية"))
        content.addView(label(
            "لا يوجد عنوان سيرفر أو API في تطبيق Android. صلاحية الإنترنت غير مضافة حاليًا؛ ستُضاف فقط لوظيفة تنزيل ملفات النموذج الأولية.",
            13, muted, false, top = 6
        ))

        setContentView(scroll)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(surface)
    }

    private fun sectionTitle(text: String): TextView =
        label(text, 18, Color.WHITE, true, top = 26)

    private fun label(
        text: String,
        size: Int,
        color: Int,
        bold: Boolean,
        top: Int = 0
    ) = TextView(this).apply {
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
        setBackgroundColor(primary)
    }

    private fun LinearLayout.addView(view: View, marginTop: Int) {
        addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(marginTop) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
