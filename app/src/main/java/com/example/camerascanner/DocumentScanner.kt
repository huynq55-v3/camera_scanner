package com.example.camerascanner

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import java.util.Arrays

object DocumentScanner {

    fun scanDocument(srcBmp: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val edged = Mat()

        // Step 1: Preprocess
        Imgproc.cvtColor(srcBmp, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edged, 75.0, 200.0)

        // Step 2: Find contours
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var docContour: MatOfPoint2f? = null
        var maxArea = 0.0

        for (cnt in contours) {
            val cnt2f = MatOfPoint2f(*cnt.toArray())
            val perimeter = Imgproc.arcLength(cnt2f, true)
            val approx = MatOfPoint2f()
            
            Imgproc.approxPolyDP(cnt2f, approx, 0.02 * perimeter, true)

            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(cnt)
                if (area > maxArea) {
                    docContour = approx
                    maxArea = area
                }
            }
        }

        // Fallback: return original if contour not found
        if (docContour == null) {
            gray.release()
            blurred.release()
            edged.release()
            hierarchy.release()
            return srcBmp.clone()
        }

        // Step 3: Sort corners
        val srcPoints = sortPoints(docContour.toArray())

        // Calculate size of target warped image
        val widthA = Math.sqrt(Math.pow(srcPoints[2].x - srcPoints[3].x, 2.0) + Math.pow(srcPoints[2].y - srcPoints[3].y, 2.0))
        val widthB = Math.sqrt(Math.pow(srcPoints[1].x - srcPoints[0].x, 2.0) + Math.pow(srcPoints[1].y - srcPoints[0].y, 2.0))
        val maxWidth = Math.max(widthA, widthB).toInt()

        val heightA = Math.sqrt(Math.pow(srcPoints[1].y - srcPoints[2].y, 2.0) + Math.pow(srcPoints[1].x - srcPoints[2].x, 2.0))
        val heightB = Math.sqrt(Math.pow(srcPoints[0].y - srcPoints[3].y, 2.0) + Math.pow(srcPoints[0].x - srcPoints[3].x, 2.0))
        val maxHeight = Math.max(heightA, heightB).toInt()

        val dstPoints = arrayOf(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        // Step 4: Perspective Transform
        val srcMat = MatOfPoint2f(*srcPoints)
        val dstMat = MatOfPoint2f(*dstPoints)

        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        val output = Mat()
        Imgproc.warpPerspective(srcBmp, output, perspectiveTransform, Size(maxWidth.toDouble(), maxHeight.toDouble()))

        // Cleanup
        gray.release()
        blurred.release()
        edged.release()
        hierarchy.release()
        srcMat.release()
        dstMat.release()
        perspectiveTransform.release()
        
        return output
    }

    fun sortPoints(pts: Array<Point>): Array<Point> {
        val sortedPoints = Array(4) { Point() }

        // Find Top-Left (min sum x+y) and Bottom-Right (max sum x+y)
        val sumComparator = Comparator<Point> { p1, p2 -> java.lang.Double.compare(p1.x + p1.y, p2.x + p2.y) }
        Arrays.sort(pts, sumComparator)
        sortedPoints[0] = pts[0] // Top-Left
        sortedPoints[2] = pts[3] // Bottom-Right

        // Find Top-Right (max difference x-y) and Bottom-Left (min difference x-y)
        val diffComparator = Comparator<Point> { p1, p2 -> java.lang.Double.compare(p1.x - p1.y, p2.x - p2.y) }
        Arrays.sort(pts, diffComparator)
        sortedPoints[1] = pts[3] // Top-Right
        sortedPoints[3] = pts[0] // Bottom-Left

        return sortedPoints
    }

    fun detectDocumentContour(srcBmp: Mat): Array<Point>? {
        val gray = Mat()
        val blurred = Mat()
        val edged = Mat()

        Imgproc.cvtColor(srcBmp, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edged, 75.0, 200.0)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edged, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        var docContour: MatOfPoint2f? = null
        var maxArea = 0.0

        for (cnt in contours) {
            val cnt2f = MatOfPoint2f(*cnt.toArray())
            val perimeter = Imgproc.arcLength(cnt2f, true)
            val approx = MatOfPoint2f()
            
            Imgproc.approxPolyDP(cnt2f, approx, 0.02 * perimeter, true)

            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(cnt)
                if (area > maxArea) {
                    docContour = approx
                    maxArea = area
                }
            }
        }

        val result = docContour?.let { sortPoints(it.toArray()) }

        gray.release()
        blurred.release()
        edged.release()
        hierarchy.release()

        return result
    }
}
