package com.realretrolabz.fs42remote

import androidx.compose.ui.graphics.Path

fun createPolygonZone(
    label: String,
    command: RemoteCommand,
    imagePoints: List<ZonePoint>,
): DefaultRemoteZone {
    val points = imagePoints
        .take(MaxPolygonPoints)
        .map { point -> ZonePoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f)) }
    require(points.size >= MinPolygonPoints) { "Polygon needs at least $MinPolygonPoints points." }

    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    val minSize = 0.025f
    val x = minX.coerceIn(0f, 1f - minSize)
    val y = minY.coerceIn(0f, 1f - minSize)
    val w = (maxX - minX).coerceAtLeast(minSize).coerceAtMost(1f - x)
    val h = (maxY - minY).coerceAtLeast(minSize).coerceAtMost(1f - y)
    val localPoints = points.map { point ->
        ZonePoint(
            x = ((point.x - x) / w).coerceIn(0f, 1f),
            y = ((point.y - y) / h).coerceIn(0f, 1f),
        )
    }

    return DefaultRemoteZone(
        label = label,
        command = command,
        x = x,
        y = y,
        w = w,
        h = h,
        shape = ZoneShape.Polygon,
        polygonPoints = localPoints,
    ).coerceValid()
}

fun DefaultRemoteZone.containsLocalPoint(localX: Float, localY: Float, width: Float, height: Float): Boolean {
    if (width <= 0f || height <= 0f) {
        return false
    }
    return when (shape) {
        ZoneShape.Rectangle -> localX in 0f..width && localY in 0f..height
        ZoneShape.Circle -> {
            val centerX = width / 2f
            val centerY = height / 2f
            val radiusX = width / 2f
            val radiusY = height / 2f
            val dx = (localX - centerX) / radiusX
            val dy = (localY - centerY) / radiusY
            dx * dx + dy * dy <= 1f
        }
        ZoneShape.Polygon -> containsPointInPolygon(
            x = localX / width,
            y = localY / height,
            points = polygonPoints,
        )
    }
}

private fun containsPointInPolygon(x: Float, y: Float, points: List<ZonePoint>): Boolean {
    if (points.size < MinPolygonPoints) {
        return false
    }
    var inside = false
    var previousIndex = points.lastIndex
    for (index in points.indices) {
        val current = points[index]
        val previous = points[previousIndex]
        if ((current.y > y) != (previous.y > y)) {
            val intersectionX = (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x
            if (x < intersectionX) {
                inside = !inside
            }
        }
        previousIndex = index
    }
    return inside
}

fun DefaultRemoteZone.polygonPath(width: Float, height: Float): Path {
    val points = polygonPoints.take(MaxPolygonPoints)
    val path = Path()
    if (points.isEmpty()) {
        return path
    }
    path.moveTo(points.first().x * width, points.first().y * height)
    points.drop(1).forEach { point ->
        path.lineTo(point.x * width, point.y * height)
    }
    path.close()
    return path
}
