package com.example.segnmea

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ROTView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rotValue: Float = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maxRot = 40f

    fun setROT(value: Float) {
        // Clamp value between -40 and 40
        rotValue = value.coerceIn(-maxRot, maxRot)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f

        // Draw background
        paint.color = Color.DKGRAY
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width, height, paint)

        // Draw center marker
        paint.color = Color.WHITE
        paint.strokeWidth = 4f
        canvas.drawLine(centerX, 0f, centerX, height, paint)

        // Draw ROT bar
        // User logic:
        // "si es estribor color verde (hasta -40) si es babor color rojo (hasta 40)"
        // Interpreted as: Negative -> Green (Right/Starboard?), Positive -> Red (Left/Port?)
        // Wait, standard convention: Starboard is Green, Port is Red.
        // Usually Starboard is Positive ROT (Turning Right). Port is Negative ROT (Turning Left).
        // BUT User said: "estribor (-40)" which implies Negative is Starboard.
        // AND "babor (40)" which implies Positive is Port.
        // AND "estribor color verde", "babor color rojo".
        // So: Negative = Green, Positive = Red.

        // Let's implement visualization:
        // Center is 0.
        // -40 is Left edge? Or Right edge?
        // Usually a bar graph: -40 [       |       ] +40
        // If value is -20:        [   ====|       ]

        // Let's assume standard linear mapping: -40 (Left) ... 0 (Center) ... +40 (Right)
        // If value is negative (Green): Draw rect from Center to Left-ish
        // If value is positive (Red): Draw rect from Center to Right-ish

        // However, User said "estribor color verde (hasta -40)".
        // If I draw -40 on the Left, and call it Estribor (Starboard), that conflicts with standard "Starboard is Right".
        // Maybe User wants:
        // Left Side (-40 to 0) = Green (Estribor/Starboard) ?
        // Right Side (0 to 40) = Red (Babor/Port) ?

        // I will follow the visual implementation:
        // If rot < 0: Draw Green bar from Center towards Left (or Right depending on value mapping).
        // Let's map -40 to Left, +40 to Right.
        // Value -20: Center -> Left. Color Green.
        // Value +20: Center -> Right. Color Red.

        if (rotValue < 0) {
            paint.color = Color.GREEN
            val barLength = (Math.abs(rotValue) / maxRot) * (width / 2f)
            // Drawing from center to left? Or center to right?
            // If -40 is "Green", and we assume standard "Left is Negative", then bar goes to Left.
            canvas.drawRect(centerX - barLength, 0f, centerX, height, paint)
        } else if (rotValue > 0) {
            paint.color = Color.RED
            val barLength = (Math.abs(rotValue) / maxRot) * (width / 2f)
            canvas.drawRect(centerX, 0f, centerX + barLength, height, paint)
        }
    }
}
