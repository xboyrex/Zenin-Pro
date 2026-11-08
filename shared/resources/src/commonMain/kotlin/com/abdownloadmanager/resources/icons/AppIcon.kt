package com.abdownloadmanager.resources.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ABDMIcons.AppIcon: ImageVector
    get() {
        if (_AppIcon != null) {
            return _AppIcon!!
        }
        _AppIcon = ImageVector.Builder(
            name = "AppIcon",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).apply {
            // Outer Zen'in Globe Sphere
            path(
                fill = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF00C6FF),
                        0.5f to Color(0xFF3A6EFF),
                        1f to Color(0xFF8A2BE2)
                    ),
                    start = Offset(4f, 4f),
                    end = Offset(44f, 44f)
                ),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(24f, 2f)
                curveTo(11.85f, 2f, 2f, 11.85f, 2f, 24f)
                curveTo(2f, 36.15f, 11.85f, 46f, 24f, 46f)
                curveTo(36.15f, 46f, 46f, 36.15f, 46f, 24f)
                curveTo(46f, 11.85f, 36.15f, 2f, 24f, 2f)
                close()
            }
            // Stylized Z-Ribbon Swoop
            path(
                fill = SolidColor(Color(0xFFF4F8FF)),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(14f, 14f)
                horizontalLineTo(34f)
                lineTo(34f, 19f)
                lineTo(20.5f, 29f)
                horizontalLineTo(34f)
                verticalLineTo(34f)
                horizontalLineTo(14f)
                verticalLineTo(29f)
                lineTo(27.5f, 19f)
                horizontalLineTo(14f)
                close()
            }
        }.build()

        return _AppIcon!!
    }

@Suppress("ObjectPropertyName")
private var _AppIcon: ImageVector? = null
