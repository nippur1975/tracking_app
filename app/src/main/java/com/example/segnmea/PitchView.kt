package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that displays the pitch of the boat.
 */
class PitchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 160f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }


    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pitchBitmap = (context.getDrawable(R.drawable.pitch) as? android.graphics.drawable.BitmapDrawable)?.bitmap
    private val pitchPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The pitch of the boat in degrees.
     */
    var pitch = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f)

        // Draw the background image
        pitchBitmap?.let {
            val shader = android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val matrix = android.graphics.Matrix()
            val scale = (radius * 2) / it.width.toFloat()
            matrix.setScale(scale, scale)
            matrix.postTranslate(centerX - (it.width * scale) / 2f, centerY - (it.height * scale) / 2f)
            shader.setLocalMatrix(matrix)
            pitchPaint.shader = shader
        }
        canvas.save()
        canvas.rotate(pitch, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, pitchPaint)
        canvas.restore()

        // Draw the circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)



        // Draw the pitch value
        val textY = centerY + 80
        canvas.drawText("${"%.1f".format(pitch)}°", centerX, textY, textPaint)

        // Draw the arrow
        val arrowPath = Path()
        if (pitch > 0) {
            arrowPaint.color = Color.WHITE
            arrowPath.moveTo(centerX - 240, textY - 80)
            arrowPath.lineTo(centerX - 200, textY - 120)
            arrowPath.lineTo(centerX - 160, textY - 80)
            arrowPath.close()
        } else if (pitch < 0) {
            arrowPaint.color = Color.WHITE
            arrowPath.moveTo(centerX - 240, textY - 120)
            arrowPath.lineTo(centerX - 200, textY - 80)
            arrowPath.lineTo(centerX - 160, textY - 120)
            arrowPath.close()
        }
        canvas.drawPath(arrowPath, arrowPaint)


    }
}
