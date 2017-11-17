package com.example.androidthings.peripherals

import android.animation.ArgbEvaluator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaterLevelView : View {
    var value: Float = 0.0f
        set(value) {
            field = value
            invalidate()
        }
    val evaluator = ArgbEvaluator()
    private val paint = Paint()
    private val whitePaint = Paint()
    private val strokePaint = Paint()

    constructor(context: Context) : super(context) {
        sharedInit(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        sharedInit(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        sharedInit(attrs)
    }

    private fun sharedInit(attrs: AttributeSet?) {
        val styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.WaterLevelView)
        try {
            value = styledAttributes.getFloat(R.styleable.WaterLevelView_value, 0.0f)
        } finally {
            styledAttributes.recycle()
        }

        paint.color = context.getColor(R.color.water)
        whitePaint.color = Color.WHITE
        strokePaint.color = Color.BLACK
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1f.dpToPx
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)

        if (canvas != null) {
            canvas.drawRect(0.0f, 0f, width.toFloat(), height * (1f - value), whitePaint)
            canvas.drawRect(0.0f, height - height * value, width.toFloat(), height.toFloat(), paint)
            canvas.drawRect(0.5f.dpToPx, 0.5f.dpToPx, width.toFloat() - 1f.dpToPx, height.toFloat() - 1f.dpToPx, strokePaint)
            canvas.save()
            canvas.translate(0f, height / 8f)
            canvas.drawLine(0f, 0f, width / 4f, 0f, strokePaint)
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 4f, 0f, strokePaint)
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 4f, 0f, strokePaint)
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 4f, 0f, strokePaint)
            canvas.restore()
            canvas.save()
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 3f, 0f, strokePaint)
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 3f, 0f, strokePaint)
            canvas.translate(0f, height / 4f)
            canvas.drawLine(0f, 0f, width / 3f, 0f, strokePaint)
            canvas.restore()
        }
    }

}