package com.gikoko.recap

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gikoko.recap.net.GeminiClient
import com.gikoko.recap.net.RecapPlan
import com.gikoko.recap.net.Segment
import java.io.File

class ConfirmActivity : AppCompatActivity() {

    private lateinit var titleInput: EditText
    private lateinit var chInput: EditText
    private lateinit var qInput: EditText
    private val segInputs = ArrayList<EditText>()
    private val segStarts = ArrayList<Int>()
    private val segEnds = ArrayList<Int>()
    private var useFrames = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planPath = intent.getStringExtra("plan_path") ?: ""
        if (planPath.isEmpty() || !File(planPath).exists()) {
            finish()
            return
        }
        val loaded = try {
            GeminiClient.planFromJson(File(planPath).readText())
        } catch (e: Exception) {
            Toast.makeText(this, "Plan ဖတ်မအောင်မြင်: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val plan = loaded.first
        useFrames = loaded.second
        if (plan.segments.isEmpty()) {
            Toast.makeText(this, "Segment မရှိပါ", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(Color.parseColor("#05070E"))

        val head = TextView(this)
        head.text = L.s(this, "PLAN EDITOR - တည်းဖြတ်ပါ", "PLAN EDITOR")
        head.setTextColor(Color.parseColor("#FFC940"))
        head.textSize = 16f
        head.gravity = Gravity.CENTER
        head.setTypeface(null, Typeface.BOLD)
        val hp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hp.setMargins(0, dp(24), 0, dp(6))
        outer.addView(head, hp)

        val note = TextView(this)
        note.text = L.s(this, "ခေါင်းစဉ် / စာသားတွေ ပြင်လို့ရတယ် - CONFIRM နှိပ်ရင် အသံထုတ်ပြီး recap video ဖြစ်လာမယ်", "Edit the title and captions, then CONFIRM to render the recap")
        note.setTextColor(Color.parseColor("#8A93B2"))
        note.textSize = 12f
        note.gravity = Gravity.CENTER
        val np = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        np.setMargins(dp(12), 0, dp(12), dp(10))
        outer.addView(note, np)

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        scroll.addView(root)
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tp.setMargins(dp(12), dp(4), dp(12), dp(4))

        titleInput = EditText(this)
        titleInput.setText(plan.title)
        inputStyle(titleInput)
        titleInput.hint = "Title"
        root.addView(titleInput, tp)

        root.addView(label("SEGMENTS"))
        for (i in plan.segments.indices) {
            val seg = plan.segments[i]
            root.addView(label("Segment " + (i + 1) + "  (" + seg.startSec / 60 + ":" + String.format("%02d", seg.startSec % 60) + " - " + seg.endSec / 60 + ":" + String.format("%02d", seg.endSec % 60) + ")"))
            val e = EditText(this)
            e.setText(seg.text)
            inputStyle(e)
            e.setSingleLine(false)
            e.minLines = 2
            segInputs.add(e)
            segStarts.add(seg.startSec)
            segEnds.add(seg.endSec)
            root.addView(e, tp)
        }

        root.addView(label("CHAPTERS"))
        chInput = EditText(this)
        chInput.setText(plan.chapters.joinToString("\n"))
        inputStyle(chInput)
        chInput.setSingleLine(false)
        chInput.minLines = 3
        root.addView(chInput, tp)

        root.addView(label("QUIZ"))
        qInput = EditText(this)
        qInput.setText(plan.quiz.joinToString("\n\n"))
        inputStyle(qInput)
        qInput.setSingleLine(false)
        qInput.minLines = 3
        root.addView(qInput, tp)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rp.setMargins(dp(12), dp(12), dp(12), dp(16))
        outer.addView(row, rp)
        val confirmBtn = Button(this)
        confirmBtn.text = "CONFIRM  ▶"
        confirmBtn.isAllCaps = false
        confirmBtn.setTextColor(Color.parseColor("#0A0A00"))
        confirmBtn.setBackgroundResource(R.drawable.bg_gold_btn)
        val cancelBtn = Button(this)
        cancelBtn.text = L.s(this, "ပယ်ဖျက်မည်", "CANCEL")
        cancelBtn.isAllCaps = false
        cancelBtn.setTextColor(Color.parseColor("#C9D2EC"))
        cancelBtn.setBackgroundResource(R.drawable.bg_mode_card)
        val bp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        bp.setMargins(dp(4), 0, dp(4), 0)
        row.addView(confirmBtn, bp)
        row.addView(cancelBtn, bp)

        setContentView(outer)

        cancelBtn.setOnClickListener { finish() }
        confirmBtn.setOnClickListener {
            val newTitle = titleInput.text.toString().trim().ifEmpty { plan.title }
            val segs = ArrayList<Segment>()
            for (i in segInputs.indices) {
                val t = segInputs[i].text.toString().trim()
                if (t.isNotEmpty()) segs.add(Segment(segStarts[i], segEnds[i], t))
            }
            if (segs.isEmpty()) {
                Toast.makeText(this, "Segment အားလုံးကို မဖျက်ရပါ", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val chapters = chInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val quiz = qInput.text.toString().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            try {
                File(planPath).writeText(GeminiClient.planToJson(RecapPlan(newTitle, segs, chapters, quiz), useFrames))
            } catch (e: Exception) {
                Toast.makeText(this, "Save failed: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val i = Intent(this, ProcessingActivity::class.java)
            i.putExtra("plan_path", planPath)
            i.putExtra("source", intent.getStringExtra("source") ?: "youtube")
            i.putExtra("url", intent.getStringExtra("url") ?: "")
            i.putExtra("path", intent.getStringExtra("path") ?: "")
            startActivity(i)
            finish()
        }
    }

    private fun label(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#3DE8F8"))
        tv.letterSpacing = 0.1f
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.setMargins(dp(12), dp(12), dp(12), 0)
        tv.layoutParams = p
        return tv
    }

    private fun inputStyle(e: EditText) {
        e.setBackgroundResource(R.drawable.bg_input)
        e.setTextColor(Color.parseColor("#F2F5FF"))
        e.setHintTextColor(Color.parseColor("#5A6480"))
        e.textSize = 14f
        e.setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density).toInt()
    }
}