/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.graphics.vector

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathNode.ArcTo
import androidx.compose.ui.graphics.vector.PathNode.Close
import androidx.compose.ui.graphics.vector.PathNode.CurveTo
import androidx.compose.ui.graphics.vector.PathNode.HorizontalTo
import androidx.compose.ui.graphics.vector.PathNode.LineTo
import androidx.compose.ui.graphics.vector.PathNode.MoveTo
import androidx.compose.ui.graphics.vector.PathNode.QuadTo
import androidx.compose.ui.graphics.vector.PathNode.ReflectiveCurveTo
import androidx.compose.ui.graphics.vector.PathNode.ReflectiveQuadTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeCurveTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeHorizontalTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeLineTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeMoveTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeQuadTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeReflectiveCurveTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeReflectiveQuadTo
import androidx.compose.ui.graphics.vector.PathNode.RelativeVerticalTo
import androidx.compose.ui.graphics.vector.PathNode.VerticalTo
import androidx.compose.ui.util.fastForEach
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal val EmptyArray = FloatArray(0)

class PathParser {
    private data class PathPoint(var x: Float = 0.0f, var y: Float = 0.0f) {
        fun reset() {
            x = 0.0f
            y = 0.0f
        }
    }

    private val nodes = mutableListOf<PathNode>()

    fun clear() {
        nodes.clear()
    }

    private val currentPoint = PathPoint()
    private val ctrlPoint = PathPoint()
    private val segmentPoint = PathPoint()
    private val reflectiveCtrlPoint = PathPoint()

    private val floatResult = FloatResult()
    private var nodeData = FloatArray(64)

    /**
     * Parses the path string to create a collection of PathNode instances with their corresponding
     * arguments
     */
    fun parsePathString(pathData: String): PathParser {
        nodes.clear()

        var start = 0
        var end = pathData.length

        // Holds the floats that describe the points for each command
        var dataCount = 0

        // Trim leading and trailing tabs and spaces
        while (start < end && pathData[start] <= ' ') start++
        while (end > start && pathData[end - 1] <= ' ') end--

        var index = start
        while (index < end) {
            var c: Char
            var command = '\u0000'

            // Look for the next command:
            //     A character that's a lower or upper case letter, but not e or E as those can be
            //      part of a float literal (e.g. 1.23e-3).
            do {
                c = pathData[index++]
                val lowerChar = c.code or 0x20
                if ((lowerChar - 'a'.code) * (lowerChar - 'z'.code) <= 0 && lowerChar != 'e'.code) {
                    command = c
                    break
                }
            } while (index < end)

            // We found a command
            if (command != '\u0000') {
                // If the command is a close command (z or Z), we don't need to extract floats,
                // and can proceed to the next command instead
                if ((command.code or 0x20) != 'z'.code) {
                    dataCount = 0

                    do {
                        // Skip any whitespace
                        while (index < end && pathData[index] <= ' ') index++

                        // Find the next float and add it to the data array if we got a valid result
                        // An invalid result could be a malformed float, or simply that we reached
                        // the end of the list of floats
                        index = FastFloatParser.nextFloat(pathData, index, end, floatResult)

                        if (floatResult.isValid) {
                            nodeData[dataCount++] = floatResult.value
                            resizeNodeData(dataCount)
                        }

                        // Skip any commas
                        while (index < end && pathData[index] == ',') index++
                    } while (index < end && floatResult.isValid)
                }

                addNodes(command, nodeData, dataCount)
            }
        }

        return this
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun resizeNodeData(dataCount: Int) {
        if (dataCount >= nodeData.size) {
            val src = nodeData
            nodeData = FloatArray(dataCount * 2)
            src.copyInto(nodeData, 0, 0, src.size)
        }
    }

    fun addPathNodes(nodes: List<PathNode>): PathParser {
        this.nodes.addAll(nodes)
        return this
    }

    fun toNodes(): List<PathNode> = nodes

    fun toPath(target: Path = Path()): Path {
        target.reset()
        currentPoint.reset()
        ctrlPoint.reset()
        segmentPoint.reset()
        reflectiveCtrlPoint.reset()

        var previousNode: PathNode? = null
        nodes.fastForEach { node ->
            if (previousNode == null) previousNode = node
            when (node) {
                is Close -> close(target)
                is RelativeMoveTo -> node.relativeMoveTo(target)
                is MoveTo -> node.moveTo(target)
                is RelativeLineTo -> node.relativeLineTo(target)
                is LineTo -> node.lineTo(target)
                is RelativeHorizontalTo -> node.relativeHorizontalTo(target)
                is HorizontalTo -> node.horizontalTo(target)
                is RelativeVerticalTo -> node.relativeVerticalTo(target)
                is VerticalTo -> node.verticalTo(target)
                is RelativeCurveTo -> node.relativeCurveTo(target)
                is CurveTo -> node.curveTo(target)
                is RelativeReflectiveCurveTo ->
                    node.relativeReflectiveCurveTo(previousNode!!.isCurve, target)
                is ReflectiveCurveTo -> node.reflectiveCurveTo(previousNode!!.isCurve, target)
                is RelativeQuadTo -> node.relativeQuadTo(target)
                is QuadTo -> node.quadTo(target)
                is RelativeReflectiveQuadTo ->
                    node.relativeReflectiveQuadTo(previousNode!!.isQuad, target)
                is ReflectiveQuadTo -> node.reflectiveQuadTo(previousNode!!.isQuad, target)
                is RelativeArcTo -> node.relativeArcTo(target)
                is ArcTo -> node.arcTo(target)
            }
            previousNode = node
        }
        return target
    }

    private fun close(target: Path) {
        currentPoint.x = segmentPoint.x
        currentPoint.y = segmentPoint.y
        ctrlPoint.x = segmentPoint.x
        ctrlPoint.y = segmentPoint.y

        target.close()
        target.moveTo(currentPoint.x, currentPoint.y)
    }

    private fun RelativeMoveTo.relativeMoveTo(target: Path) {
        currentPoint.x += dx
        currentPoint.y += dy
        target.relativeMoveTo(dx, dy)
        segmentPoint.x = currentPoint.x
        segmentPoint.y = currentPoint.y
    }

    private fun MoveTo.moveTo(target: Path) {
        currentPoint.x = x
        currentPoint.y = y
        target.moveTo(x, y)
        segmentPoint.x = currentPoint.x
        segmentPoint.y = currentPoint.y
    }

    private fun RelativeLineTo.relativeLineTo(target: Path) {
        target.relativeLineTo(dx, dy)
        currentPoint.x += dx
        currentPoint.y += dy
    }

    private fun LineTo.lineTo(target: Path) {
        target.lineTo(x, y)
        currentPoint.x = x
        currentPoint.y = y
    }

    private fun RelativeHorizontalTo.relativeHorizontalTo(target: Path) {
        target.relativeLineTo(dx, 0.0f)
        currentPoint.x += dx
    }

    private fun HorizontalTo.horizontalTo(target: Path) {
        target.lineTo(x, currentPoint.y)
        currentPoint.x = x
    }

    private fun RelativeVerticalTo.relativeVerticalTo(target: Path) {
        target.relativeLineTo(0.0f, dy)
        currentPoint.y += dy
    }

    private fun VerticalTo.verticalTo(target: Path) {
        target.lineTo(currentPoint.x, y)
        currentPoint.y = y
    }

    private fun RelativeCurveTo.relativeCurveTo(target: Path) {
        target.relativeCubicTo(
            dx1, dy1,
            dx2, dy2,
            dx3, dy3
        )
        ctrlPoint.x = currentPoint.x + dx2
        ctrlPoint.y = currentPoint.y + dy2
        currentPoint.x += dx3
        currentPoint.y += dy3
    }

    private fun CurveTo.curveTo(target: Path) {
        target.cubicTo(
            x1, y1,
            x2, y2,
            x3, y3
        )
        ctrlPoint.x = x2
        ctrlPoint.y = y2
        currentPoint.x = x3
        currentPoint.y = y3
    }

    private fun RelativeReflectiveCurveTo.relativeReflectiveCurveTo(
        prevIsCurve: Boolean,
        target: Path
    ) {
        if (prevIsCurve) {
            reflectiveCtrlPoint.x = currentPoint.x - ctrlPoint.x
            reflectiveCtrlPoint.y = currentPoint.y - ctrlPoint.y
        } else {
            reflectiveCtrlPoint.reset()
        }

        target.relativeCubicTo(
            reflectiveCtrlPoint.x, reflectiveCtrlPoint.y,
            dx1, dy1,
            dx2, dy2
        )
        ctrlPoint.x = currentPoint.x + dx1
        ctrlPoint.y = currentPoint.y + dy1
        currentPoint.x += dx2
        currentPoint.y += dy2
    }

    private fun ReflectiveCurveTo.reflectiveCurveTo(prevIsCurve: Boolean, target: Path) {
        if (prevIsCurve) {
            reflectiveCtrlPoint.x = 2 * currentPoint.x - ctrlPoint.x
            reflectiveCtrlPoint.y = 2 * currentPoint.y - ctrlPoint.y
        } else {
            reflectiveCtrlPoint.x = currentPoint.x
            reflectiveCtrlPoint.y = currentPoint.y
        }

        target.cubicTo(
            reflectiveCtrlPoint.x, reflectiveCtrlPoint.y,
            x1, y1, x2, y2
        )
        ctrlPoint.x = x1
        ctrlPoint.y = y1
        currentPoint.x = x2
        currentPoint.y = y2
    }

    private fun RelativeQuadTo.relativeQuadTo(target: Path) {
        target.relativeQuadraticBezierTo(dx1, dy1, dx2, dy2)
        ctrlPoint.x = currentPoint.x + dx1
        ctrlPoint.y = currentPoint.y + dy1
        currentPoint.x += dx2
        currentPoint.y += dy2
    }

    private fun QuadTo.quadTo(target: Path) {
        target.quadraticBezierTo(x1, y1, x2, y2)
        ctrlPoint.x = x1
        ctrlPoint.y = y1
        currentPoint.x = x2
        currentPoint.y = y2
    }

    private fun RelativeReflectiveQuadTo.relativeReflectiveQuadTo(
        prevIsQuad: Boolean,
        target: Path
    ) {
        if (prevIsQuad) {
            reflectiveCtrlPoint.x = currentPoint.x - ctrlPoint.x
            reflectiveCtrlPoint.y = currentPoint.y - ctrlPoint.y
        } else {
            reflectiveCtrlPoint.reset()
        }

        target.relativeQuadraticBezierTo(
            reflectiveCtrlPoint.x,
            reflectiveCtrlPoint.y, dx, dy
        )
        ctrlPoint.x = currentPoint.x + reflectiveCtrlPoint.x
        ctrlPoint.y = currentPoint.y + reflectiveCtrlPoint.y
        currentPoint.x += dx
        currentPoint.y += dy
    }

    private fun ReflectiveQuadTo.reflectiveQuadTo(prevIsQuad: Boolean, target: Path) {
        if (prevIsQuad) {
            reflectiveCtrlPoint.x = 2 * currentPoint.x - ctrlPoint.x
            reflectiveCtrlPoint.y = 2 * currentPoint.y - ctrlPoint.y
        } else {
            reflectiveCtrlPoint.x = currentPoint.x
            reflectiveCtrlPoint.y = currentPoint.y
        }
        target.quadraticBezierTo(
            reflectiveCtrlPoint.x,
            reflectiveCtrlPoint.y, x, y
        )
        ctrlPoint.x = reflectiveCtrlPoint.x
        ctrlPoint.y = reflectiveCtrlPoint.y
        currentPoint.x = x
        currentPoint.y = y
    }

    private fun RelativeArcTo.relativeArcTo(target: Path) {
        val arcStartX = arcStartDx + currentPoint.x
        val arcStartY = arcStartDy + currentPoint.y

        drawArc(
            target,
            currentPoint.x.toDouble(),
            currentPoint.y.toDouble(),
            arcStartX.toDouble(),
            arcStartY.toDouble(),
            horizontalEllipseRadius.toDouble(),
            verticalEllipseRadius.toDouble(),
            theta.toDouble(),
            isMoreThanHalf,
            isPositiveArc
        )
        currentPoint.x = arcStartX
        currentPoint.y = arcStartY

        ctrlPoint.x = currentPoint.x
        ctrlPoint.y = currentPoint.y
    }

    private fun ArcTo.arcTo(target: Path) {
        drawArc(
            target,
            currentPoint.x.toDouble(),
            currentPoint.y.toDouble(),
            arcStartX.toDouble(),
            arcStartY.toDouble(),
            horizontalEllipseRadius.toDouble(),
            verticalEllipseRadius.toDouble(),
            theta.toDouble(),
            isMoreThanHalf,
            isPositiveArc
        )

        currentPoint.x = arcStartX
        currentPoint.y = arcStartY

        ctrlPoint.x = currentPoint.x
        ctrlPoint.y = currentPoint.y
    }

    private fun drawArc(
        p: Path,
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        a: Double,
        b: Double,
        theta: Double,
        isMoreThanHalf: Boolean,
        isPositiveArc: Boolean
    ) {

        /* Convert rotation angle from degrees to radians */
        val thetaD = theta.toRadians()
        /* Pre-compute rotation matrix entries */
        val cosTheta = cos(thetaD)
        val sinTheta = sin(thetaD)
        /* Transform (x0, y0) and (x1, y1) into unit space */
        /* using (inverse) rotation, followed by (inverse) scale */
        val x0p = (x0 * cosTheta + y0 * sinTheta) / a
        val y0p = (-x0 * sinTheta + y0 * cosTheta) / b
        val x1p = (x1 * cosTheta + y1 * sinTheta) / a
        val y1p = (-x1 * sinTheta + y1 * cosTheta) / b

        /* Compute differences and averages */
        val dx = x0p - x1p
        val dy = y0p - y1p
        val xm = (x0p + x1p) / 2
        val ym = (y0p + y1p) / 2
        /* Solve for intersecting unit circles */
        val dsq = dx * dx + dy * dy
        if (dsq == 0.0) {
            return /* Points are coincident */
        }
        val disc = 1.0 / dsq - 1.0 / 4.0
        if (disc < 0.0) {
            val adjust = (sqrt(dsq) / 1.99999).toFloat()
            drawArc(
                p, x0, y0, x1, y1, a * adjust,
                b * adjust, theta, isMoreThanHalf, isPositiveArc
            )
            return /* Points are too far apart */
        }
        val s = sqrt(disc)
        val sdx = s * dx
        val sdy = s * dy
        var cx: Double
        var cy: Double
        if (isMoreThanHalf == isPositiveArc) {
            cx = xm - sdy
            cy = ym + sdx
        } else {
            cx = xm + sdy
            cy = ym - sdx
        }

        val eta0 = atan2(y0p - cy, x0p - cx)

        val eta1 = atan2(y1p - cy, x1p - cx)

        var sweep = eta1 - eta0
        if (isPositiveArc != (sweep >= 0)) {
            if (sweep > 0) {
                sweep -= 2 * PI
            } else {
                sweep += 2 * PI
            }
        }

        cx *= a
        cy *= b
        val tcx = cx
        cx = cx * cosTheta - cy * sinTheta
        cy = tcx * sinTheta + cy * cosTheta

        arcToBezier(
            p, cx, cy, a, b, x0, y0, thetaD,
            eta0, sweep
        )
    }

    /**
     * Converts an arc to cubic Bezier segments and records them in p.
     *
     * @param p The target for the cubic Bezier segments
     * @param cx The x coordinate center of the ellipse
     * @param cy The y coordinate center of the ellipse
     * @param a The radius of the ellipse in the horizontal direction
     * @param b The radius of the ellipse in the vertical direction
     * @param e1x E(eta1) x coordinate of the starting point of the arc
     * @param e1y E(eta2) y coordinate of the starting point of the arc
     * @param theta The angle that the ellipse bounding rectangle makes with horizontal plane
     * @param start The start angle of the arc on the ellipse
     * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
     */
    private fun arcToBezier(
        p: Path,
        cx: Double,
        cy: Double,
        a: Double,
        b: Double,
        e1x: Double,
        e1y: Double,
        theta: Double,
        start: Double,
        sweep: Double
    ) {
        var eta1x = e1x
        var eta1y = e1y
        // Taken from equations at: http://spaceroots.org/documents/ellipse/node8.html
        // and http://www.spaceroots.org/documents/ellipse/node22.html

        // Maximum of 45 degrees per cubic Bezier segment
        val numSegments = ceil(abs(sweep * 4 / PI)).toInt()

        var eta1 = start
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val cosEta1 = cos(eta1)
        val sinEta1 = sin(eta1)
        var ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1)
        var ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1)

        val anglePerSegment = sweep / numSegments
        for (i in 0 until numSegments) {
            val eta2 = eta1 + anglePerSegment
            val sinEta2 = sin(eta2)
            val cosEta2 = cos(eta2)
            val e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2)
            val e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2)
            val ep2x = (-a * cosTheta * sinEta2) - (b * sinTheta * cosEta2)
            val ep2y = (-a * sinTheta * sinEta2) + (b * cosTheta * cosEta2)
            val tanDiff2 = tan((eta2 - eta1) / 2)
            val alpha = sin(eta2 - eta1) * (sqrt(4 + 3.0 * tanDiff2 * tanDiff2) - 1) / 3
            val q1x = eta1x + alpha * ep1x
            val q1y = eta1y + alpha * ep1y
            val q2x = e2x - alpha * ep2x
            val q2y = e2y - alpha * ep2y

            // TODO (njawad) figure out if this is still necessary?
            // Adding this no-op call to workaround a proguard related issue.
            // p.relativeLineTo(0.0, 0.0)

            p.cubicTo(
                q1x.toFloat(),
                q1y.toFloat(),
                q2x.toFloat(),
                q2y.toFloat(),
                e2x.toFloat(),
                e2y.toFloat()
            )
            eta1 = eta2
            eta1x = e2x
            eta1y = e2y
            ep1x = ep2x
            ep1y = ep2y
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun addNodes(cmd: Char, args: FloatArray, count: Int) {
        cmd.addPathNodes(nodes, args, count)
    }

    private fun Double.toRadians(): Double = this / 180 * PI
}
