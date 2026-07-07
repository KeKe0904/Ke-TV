package com.tvtoolbox.screensaver

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 应用内置弹窗：替代系统 AlertDialog，统一应用玻璃质感视觉风格。
 *
 * 提供三种形态：
 * 1. [showMessage]    - 标题 + 消息 + 按钮（最常见）
 * 2. [showSingleChoice] - 标题 + 单选列表 + 按钮（图床类型/主题/间隔等）
 * 3. [showInput]      - 标题 + 文本输入框 + 按钮（图床 URL）
 *
 * 视觉差异点：
 * - 玻璃质感圆角背景（dialog_glass_bg.xml）
 * - 按钮自绘，强调色主按钮 + 玻璃次按钮
 * - 单选项使用 RadioButton + 玻璃质感容器
 */
object AppDialog {

    /**
     * 显示一个简单的消息对话框。
     *
     * @param context Activity 上下文（用于 layoutInflater 和 window）
     * @param title 标题（可选）
     * @param message 消息内容
     * @param positiveText 确认按钮文案（null 则不显示）
     * @param onPositive 确认回调
     * @param negativeText 取消按钮文案（null 则不显示）
     * @param onNegative 取消回调
     */
    fun showMessage(
        context: Context,
        title: CharSequence? = null,
        message: CharSequence,
        positiveText: CharSequence? = context.getString(R.string.dialog_ok),
        onPositive: (() -> Unit)? = null,
        negativeText: CharSequence? = null,
        onNegative: (() -> Unit)? = null,
        cancelable: Boolean = true
    ): Dialog {
        return createAndShow(
            context = context,
            title = title,
            contentView = createMessageView(context, message),
            positiveText = positiveText,
            onPositive = onPositive,
            negativeText = negativeText,
            onNegative = onNegative,
            cancelable = cancelable
        )
    }

    /**
     * 显示单选列表对话框。
     *
     * @param context 上下文
     * @param title 标题
     * @param entries 选项文案列表
     * @param checkedIndex 当前选中项索引
     * @param onSelected 选择回调（传入新选中项索引）
     * @param positiveText 确认按钮文案
     * @param negativeText 取消按钮文案
     * @param instantApply 已废弃，保留参数为兼容旧调用方。当前固定走普通模式（点确认才生效）。
     */
    fun showSingleChoice(
        context: Context,
        title: CharSequence,
        entries: Array<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit,
        positiveText: CharSequence? = context.getString(R.string.dialog_ok),
        negativeText: CharSequence? = context.getString(R.string.dialog_cancel),
        @Suppress("UNUSED_PARAMETER") instantApply: Boolean = false
    ): Dialog {
        val (view, radioGroup) = createChoiceListView(context, entries, checkedIndex)
        var selected = checkedIndex
        // 普通模式（手机 + TV 都用此模式）：
        // - D-pad 上下移动焦点
        // - D-pad 中央键 / 回车键 / 点击 → check 当前 RadioButton（不关闭对话框）
        // - 按「确认」按钮 → 应用 selected + 关闭
        // 这是用户明确要求的：「点击确定才能生效，不要一滑动就默认确定」
        radioGroup.setOnCheckedChangeListener { _, id ->
            selected = id - 1
        }
        // 给每个 RadioButton 显式 OnClickListener：
        // 在 TV 上 D-pad 中央键 / 回车键 会触发 OnClickListener，
        // 让 RadioGroup.check(it.id) 选中当前 RadioButton（不关闭对话框）
        for (i in 0 until radioGroup.childCount) {
            val child = radioGroup.getChildAt(i) as? RadioButton ?: continue
            child.setOnClickListener {
                radioGroup.check(it.id)
            }
        }
        return createAndShow(
            context = context,
            title = title,
            contentView = view,
            positiveText = positiveText,
            onPositive = { onSelected(selected.coerceIn(0, entries.size - 1)) },
            negativeText = negativeText,
            onNegative = null,
            cancelable = true
        )
    }

    /**
     * 显示文本输入对话框。
     *
     * @param context 上下文
     * @param title 标题
     * @param hint 输入框提示文案
     * @param initialText 初始文本
     * @param onSave 保存回调（传入用户输入的字符串）
     */
    fun showInput(
        context: Context,
        title: CharSequence,
        hint: CharSequence,
        initialText: CharSequence = "",
        onSave: (String) -> Unit
    ): Dialog {
        val (view, editText) = createInputView(context, hint, initialText)
        val dialog = createAndShow(
            context = context,
            title = title,
            contentView = view,
            positiveText = context.getString(R.string.dialog_save),
            onPositive = {
                onSave(editText.text?.toString()?.trim() ?: "")
            },
            negativeText = context.getString(R.string.dialog_cancel),
            onNegative = null,
            cancelable = true
        )
        // 仅在手机端自动聚焦输入框并弹软键盘
        // TV 上 requestFocus + showSoftInput 会触发不存在 IME 的异常，
        // 导致 dialog 显示失败或被系统回收 → 用户感觉"点了就回首页"
        if (!FocusHelper.isTv(context)) {
            try {
                editText.requestFocus()
                editText.postDelayed({
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager)
                        .showSoftInput(editText, 0)
                }, 200)
            } catch (_: Throwable) {
                // 即使 IME 启动失败也不影响 dialog 显示
            }
        }
        // TV：默认聚焦到「保存」按钮，用户按 D-pad 上下到输入框才聚焦输入框
        // （此时系统的 IME 会被自然触发，不会异常）
        return dialog
    }

    /**
     * 显示一个带自定义视图的对话框（用于下载进度等复杂场景）。
     *
     * 调用方持有 [Dialog] 引用后，可以拿到 contentView 来动态更新内容。
     */
    fun showCustom(
        context: Context,
        title: CharSequence,
        contentView: View,
        negativeText: CharSequence? = context.getString(R.string.dialog_cancel),
        onNegative: (() -> Unit)? = null,
        cancelable: Boolean = true
    ): Dialog {
        return createAndShow(
            context = context,
            title = title,
            contentView = contentView,
            positiveText = null,
            onPositive = null,
            negativeText = negativeText,
            onNegative = onNegative,
            cancelable = cancelable
        )
    }

    // ===== 内部实现 =====

    private fun createAndShow(
        context: Context,
        title: CharSequence?,
        contentView: View,
        positiveText: CharSequence?,
        onPositive: (() -> Unit)?,
        negativeText: CharSequence?,
        onNegative: (() -> Unit)?,
        cancelable: Boolean
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildDialogView(context, dialog, title, contentView, positiveText, onPositive, negativeText, onNegative))
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)

        // 透明化窗口背景，让 dialog_glass_bg 自己显示
        // 同时加 dim 层（半透明黑），让弹窗在 TV / 任何背景下都更突出
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // dimAmount 在 TV 上尤其重要：背景变暗让弹窗前景更清晰
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            // 让弹窗宽度接近屏幕宽（手机端不被挤压，TV 上有 paddingHorizontal 24dp）
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // TV：弹窗居中而非顶部，更符合遥控器视觉中心
            setGravity(Gravity.CENTER)
        }

        // TV 关键修复：
        // 1. D-pad 中央键 / 回车键 → 触发现焦点的按钮点击
        // 2. 返回键 → 直接 dismiss dialog，避免 IME 异常时返回键被传给 Activity 导致 finish
        //    （这是"点击图床 URL 设置就回首页"的核心原因）
        val isTv = FocusHelper.isTv(context)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        val focused = dialog.window?.currentFocus
                        if (focused is TextView && focused.isClickable) {
                            focused.performClick()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (cancelable) {
                            dialog.dismiss()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            // 让 Dialog 内部正常处理焦点导航
            if (isTv && event.action == KeyEvent.ACTION_DOWN &&
                keyCode in intArrayOf(
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
                )
            ) {
                return@setOnKeyListener false
            }
            false
        }

        dialog.show()

        // TV：自动聚焦到第一个可聚焦元素（按钮或 RadioButton），方便遥控器立即操作
        // 关键修复（图床 URL 设置返回首页 bug 的真正根因）：
        // 之前用 `it is TextView && it.isClickable` 选焦点目标，
        // 但 **EditText 是 TextView 的子类**，且 EditText.isClickable 默认 true！
        // 导致 TV 上 showInput 自动聚焦 EditText → 触发 IME → TV 无 IME 异常 → dialog 失败
        // → 返回键被传给 Activity → finish() → "点了就回首页"
        // 修复：TV 自动聚焦候选明确排除 EditText，优先聚焦到按钮（保存/取消）。
        dialog.window?.decorView?.post {
            val root = dialog.window?.decorView ?: return@post
            val focusable = ArrayList<View>()
            root.addFocusables(focusable, View.FOCUS_FORWARD)
            val target = if (isTv) {
                // TV 优先聚焦顺序：
                // 1. checked RadioButton（单选对话框场景，用户按上下键从当前位置开始）
                // 2. 可点击的 Button（保存/取消按钮，**排除 EditText** 避免 IME 异常）
                // 3. 最后兜底：第一个可聚焦元素（但排除 EditText）
                focusable.firstOrNull { (it as? RadioButton)?.isChecked == true }
                    ?: focusable.firstOrNull {
                        it is TextView && it.isClickable && it !is android.widget.EditText
                    }
                    ?: focusable.firstOrNull { it !is android.widget.EditText }
            } else {
                focusable.firstOrNull()
            }
            target?.requestFocus()
        }
        return dialog
    }

    private fun buildDialogView(
        context: Context,
        dialog: Dialog,
        title: CharSequence?,
        contentView: View,
        positiveText: CharSequence?,
        onPositive: (() -> Unit)?,
        negativeText: CharSequence?,
        onNegative: (() -> Unit)?
    ): View {
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_app, null, false)
        val tvTitle = root.findViewById<TextView>(R.id.tvDialogTitle)
        val container = root.findViewById<ViewGroup>(R.id.dialogContentContainer)
        val buttonContainer = root.findViewById<LinearLayout>(R.id.dialogButtonContainer)

        if (title.isNullOrBlank()) {
            tvTitle.visibility = View.GONE
        } else {
            tvTitle.text = title
        }

        // 把外部传入的 contentView 塞进容器
        (contentView.parent as? ViewGroup)?.removeView(contentView)
        container.addView(contentView)

        // 添加按钮
        negativeText?.let { text ->
            val btn = createButton(context, text, isPrimary = false)
            btn.setOnClickListener {
                onNegative?.invoke()
                dialog.dismiss()
            }
            buttonContainer.addView(btn)
        }
        positiveText?.let { text ->
            val btn = createButton(context, text, isPrimary = true)
            btn.setOnClickListener {
                onPositive?.invoke()
                dialog.dismiss()
            }
            // 主按钮在右：negative 已 add 的话，positive 自然在右
            buttonContainer.addView(btn)
        }

        // 没按钮时隐藏按钮容器（避免空 padding）
        if (positiveText == null && negativeText == null) {
            buttonContainer.visibility = View.GONE
        }

        return root
    }

    private fun createButton(
        context: Context,
        text: CharSequence,
        isPrimary: Boolean
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (isPrimary) Color.WHITE else context.colorCompat(R.color.text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = context.getDrawableCompat(
                if (isPrimary) R.drawable.dialog_btn_primary else R.drawable.dialog_btn_secondary
            )
            val hPadding = (24 * context.resources.displayMetrics.density).toInt()
            val vPadding = (10 * context.resources.displayMetrics.density).toInt()
            setPadding(hPadding, vPadding, hPadding, vPadding)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 主按钮与次按钮间距 8dp
            lp.marginStart = (8 * context.resources.displayMetrics.density).toInt()
            layoutParams = lp
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true

            // TV 焦点态：聚焦时抬升 + 轻微放大，让用户看到当前焦点
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.elevation = 8f
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                } else {
                    v.elevation = 0f
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }
    }

    private fun createMessageView(context: Context, message: CharSequence): View {
        val tv = TextView(context).apply {
            text = message
            setTextColor(context.colorCompat(R.color.text_secondary))
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            // 多行不截断
            maxLines = Int.MAX_VALUE
        }
        return tv
    }

    private fun createChoiceListView(
        context: Context,
        entries: Array<String>,
        checkedIndex: Int
    ): Pair<View, RadioGroup> {
        val density = context.resources.displayMetrics.density
        val group = RadioGroup(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        entries.forEachIndexed { index, text ->
            val item = RadioButton(context).apply {
                this.text = text
                setTextColor(context.colorCompat(R.color.text_primary))
                textSize = 14f
                background = context.getDrawableCompat(R.drawable.dialog_choice_item_bg)
                val hPad = (16 * density).toInt()
                val vPad = (12 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
                buttonDrawable = null // 隐藏原生圆点，用背景表示选中
                id = index + 1
                isChecked = index == checkedIndex
                // TV D-pad 适配：让 RadioButton 可以被聚焦，遥控器可上下移动
                isFocusable = true
                isFocusableInTouchMode = true
                val lp = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * density).toInt()
                layoutParams = lp
            }
            group.addView(item)
        }
        return group to group
    }

    private fun createInputView(
        context: Context,
        hint: CharSequence,
        initialText: CharSequence
    ): Pair<View, TextInputEditText> {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_url_input, null, false)
        val et = view.findViewById<TextInputEditText>(R.id.etUrl)
        // 关键修复（图床 URL 设置崩溃的真正根因）：
        // 之前写 `view.findViewById<TextInputLayout>(R.id.etUrl).parent as TextInputLayout`
        // 但 R.id.etUrl 是 TextInputEditText 的 id，用这个 id 找 TextInputLayout 类型会返回 null
        // → null.parent 抛 NPE → dialog 崩溃 → 返回键被传给 Activity → finish() → "返回首页"
        // 正确写法：先 findViewById 拿到 EditText，它的 parent 才是 TextInputLayout
        val layout = et.parent as TextInputLayout
        layout.hint = hint
        et.setText(initialText)
        return view to et
    }

    // ===== 扩展 =====

    private fun Context.colorCompat(colorRes: Int): Int =
        ContextCompat.getColor(this, colorRes)

    private fun Context.getDrawableCompat(drawableRes: Int) =
        ContextCompat.getDrawable(this, drawableRes)!!
}

/**
 * 简化的入口，让 Activity 用起来更顺手
 */
fun AppCompatActivity.showAppMessage(
    title: CharSequence? = null,
    message: CharSequence,
    positiveText: CharSequence? = getString(R.string.dialog_ok),
    onPositive: (() -> Unit)? = null,
    negativeText: CharSequence? = null,
    onNegative: (() -> Unit)? = null,
    cancelable: Boolean = true
): Dialog = AppDialog.showMessage(
    context = this,
    title = title,
    message = message,
    positiveText = positiveText,
    onPositive = onPositive,
    negativeText = negativeText,
    onNegative = onNegative,
    cancelable = cancelable
)

fun AppCompatActivity.showAppSingleChoice(
    title: CharSequence,
    entries: Array<String>,
    checkedIndex: Int,
    onSelected: (Int) -> Unit,
    positiveText: CharSequence? = getString(R.string.dialog_ok),
    negativeText: CharSequence? = getString(R.string.dialog_cancel)
): Dialog = AppDialog.showSingleChoice(
    context = this,
    title = title,
    entries = entries,
    checkedIndex = checkedIndex,
    onSelected = onSelected,
    positiveText = positiveText,
    negativeText = negativeText
)

fun AppCompatActivity.showAppInput(
    title: CharSequence,
    hint: CharSequence,
    initialText: CharSequence = "",
    onSave: (String) -> Unit
): Dialog = AppDialog.showInput(
    context = this,
    title = title,
    hint = hint,
    initialText = initialText,
    onSave = onSave
)
