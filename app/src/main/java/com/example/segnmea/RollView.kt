package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that displays the roll of the boat.
 */
class RollView @JvmOverloads constructor(
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
        style = Paint.Style.FILL
    }

    private val rollBitmap = (context.getDrawable(R.drawable.roll) as? android.graphics.drawable.BitmapDrawable)?.bitmap
    private val rollPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    /**
     * The roll of the boat in degrees.
     */
    var roll = 0f
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
        rollBitmap?.let {
            val shader = android.graphics.BitmapShader(it, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val matrix = android.graphics.Matrix()
            val scale = (radius * 2) / it.width.toFloat()
            matrix.setScale(scale, scale)
            matrix.postTranslate(centerX - (it.width * scale) / 2f, centerY - (it.height * scale) / 2f)
            shader.setLocalMatrix(matrix)
            rollPaint.shader = shader
        }
        canvas.save()
        canvas.rotate(roll, centerX, centerY)
        canvas.drawCircle(centerX, centerY, radius, rollPaint)
        canvas.restore()

        // Draw the circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)


        // Draw the roll value
        val textY = centerY + 80
        canvas.drawText("${"%.1f".format(roll)}°", centerX, textY, textPaint)

        // Draw the arrow and letter
        val arrowPath = Path()
        if (roll > 0) {
            arrowPaint.color = Color.GREEN
            arrowPaint.style = Paint.Style.STROKE
            arrowPaint.strokeWidth = 10f
            arrowPath.moveTo(centerX + 160, textY - 100)
            arrowPath.lineTo(centerX + 200, textY - 80)
            arrowPath.lineTo(centerX + 160, textY - 60)
            arrowPath.close()
            letterPaint.color = Color.GREEN
            letterPaint.textSize = 160f
            canvas.drawText("S", centerX, textY + 160, letterPaint)
        } else if (roll < 0) {
            arrowPaint.color = Color.RED
            arrowPaint.style = Paint.Style.STROKE
            arrowPaint.strokeWidth = 10f
            arrowPath.moveTo(centerX - 160, textY - 60)
            arrowPath.lineTo(centerX - 200, textY - 80)
            arrowPath.lineTo(centerX - 160, textY - 100)
            arrowPath.close()
            letterPaint.color = Color.RED
            letterPaint.textSize = 160f
            canvas.drawText("P", centerX, textY + 160, letterPaint)
        }
        canvas.drawPath(arrowPath, arrowPaint)

    }
}
