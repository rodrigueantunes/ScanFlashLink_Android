package com.antunes.scanflashlink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class BarcodeOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var barcodeRect: Rect? = null
    private val paint = Paint().apply {
        color = Color.GREEN  // Couleur de surbrillance
        style = Paint.Style.STROKE
        strokeWidth = 8f     // Épaisseur du contour
    }

    fun setBarcodeRect(rect: Rect?) {
        barcodeRect = rect
        invalidate() // Redessiner la vue
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barcodeRect?.let {
            canvas.drawRect(it, paint)
        }
    }
}
