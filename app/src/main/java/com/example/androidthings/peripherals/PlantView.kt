package com.example.androidthings.peripherals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View

class PlantView : View {
    lateinit var plantDrawable: Drawable
    lateinit var waterDrawable: Drawable

    constructor(context: Context) : super(context) {
        sharedInit(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        sharedInit(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        sharedInit(attrs)
    }

    fun sharedInit(attrs: AttributeSet?) {
        waterDrawable = context.getDrawable(R.drawable.ic_water_in_pot)
        plantDrawable = context.getDrawable(R.drawable.ic_carrot_pot)
        val bounds = Rect(0, 0, 200.dpToPx, 200.dpToPx)
        waterDrawable.bounds = bounds
        plantDrawable.bounds = bounds
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        if (canvas != null) {
            canvas.save()
            canvas.translate((width - 200f.dpToPx) / 2f, (height - 200f.dpToPx) / 2f)

            waterDrawable.draw(canvas)
            plantDrawable.draw(canvas)
            canvas.restore()
        }
    }
}
