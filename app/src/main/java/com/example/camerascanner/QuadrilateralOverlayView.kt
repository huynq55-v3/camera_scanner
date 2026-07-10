package com.example.camerascanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point

class QuadrilateralOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: Array<Point>? = null
    private var imageWidth = 1
    private var imageHeight = 1

    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 200, 83) // Transparent green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setPoints(points: Array<Point>?, imageWidth: Int, imageHeight: Int) {
        this.points = points
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = points ?: return
        if (pts.size < 4) return

        val viewWidth = width
        val viewHeight = height

        // Scale factors from analyzed image space to view space
        val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = viewHeight.toFloat() / imageHeight.toFloat()

        val p1x = pts[0].x.toFloat() * scaleX
        val p1y = pts[0].y.toFloat() * scaleY
        val p2x = pts[1].x.toFloat() * scaleX
        val p2y = pts[1].y.toFloat() * scaleY
        val p3x = pts[2].x.toFloat() * scaleX
        val p3y = pts[2].y.toFloat() * scaleY
        val p4x = pts[3].x.toFloat() * scaleX
        val p4y = pts[3].y.toFloat() * scaleY

        val path = Path().apply {
            moveTo(p1x, p1y)
            lineTo(p2x, p2y)
            lineTo(p3x, p3y)
            lineTo(p4x, p4y)
            close()
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)

        canvas.drawCircle(p1x, p1y, 16f, cornerPaint)
        canvas.drawCircle(p2x, p2y, 16f, cornerPaint)
        canvas.drawCircle(p3x, p3y, 16f, cornerPaint)
        canvas.drawCircle(p4x, p4y, 16f, cornerPaint)
    }
}
