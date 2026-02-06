package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that displays a compass.
 */
class CompassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val compassBitmap = (context.getDrawable(R.drawable.compass_rose) as? android.graphics.drawable.BitmapDrawable)?.bitmap
    private val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The rotation of the compass in degrees.
     */
    var compassRotation = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f)

        // Draw the compass bitmap
        compassBitmap?.let {
            val shader = android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val matrix = android.graphics.Matrix()
            val scale = (radius * 2) / it.width.toFloat() * 1.33f
            matrix.setScale(scale, scale)
            matrix.postTranslate(centerX - (it.width * scale) / 2f, centerY - (it.height * scale) / 2f)
            shader.setLocalMatrix(matrix)
            compassPaint.shader = shader
        }

        // Rotate the canvas and draw the compass
        canvas.save()
        canvas.rotate(-compassRotation, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, compassPaint)
        canvas.restore()
    }
}
