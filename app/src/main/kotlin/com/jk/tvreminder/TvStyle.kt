package com.jk.tvreminder

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

object TvStyle {
    val background = Color.rgb(16, 25, 35)
    val panel = Color.rgb(31, 46, 60)
    val accent = Color.rgb(99, 224, 196)
    val muted = Color.rgb(176, 194, 208)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun card(context: Context, color: Int = panel, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(context, 12).toFloat()
        if (stroke) setStroke(dp(context, 3), accent)
    }
    fun focusBackground(context: Context) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), card(context, Color.rgb(42, 73, 85), true))
        addState(intArrayOf(android.R.attr.state_pressed), card(context, Color.rgb(42, 73, 85), true))
        addState(intArrayOf(), card(context))
    }
    fun text(context: Context, value: String, size: Float = 18f, secondary: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(if (secondary) muted else Color.WHITE)
        if (size >= 28) typeface = Typeface.DEFAULT_BOLD
    }
    fun button(context: Context, title: String, click: (View) -> Unit) = Button(context).apply {
        text = title
        textSize = 18f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = focusBackground(context)
        setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8))
        minHeight = dp(context, 52)
        setOnClickListener(click)
    }
}
