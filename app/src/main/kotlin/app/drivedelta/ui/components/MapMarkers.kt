package app.drivedelta.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.drivedelta.ui.theme.DdMapBase
import app.drivedelta.ui.theme.DdSurfaceElevated
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * DriveDelta's map pins: a dark disc ringed in an accent colour with a glyph inside it and a short
 * tail pointing at the coordinate. Anchor them at (0.5, 1.0) so the tail sits on the point.
 *
 * They exist because the default red Google teardrop says nothing — the same pin for the start of a
 * drive, the end of it and a saved place. A pin here carries the meaning it needs: a saved place
 * shows the emoji the user picked for it in the place editor, and a plain start or end shows a
 * start/finish glyph, so the two ends of a drive are told apart at a glance rather than by tapping.
 *
 * Rasterising is not free — [BitmapDescriptorFactory] needs a bitmap — so every pin is remembered on
 * its inputs. Redrawing per recomposition would allocate a bitmap per frame while the map animates.
 */
@Composable
fun rememberMapPin(glyph: String, accent: Color): BitmapDescriptor {
    val density = LocalDensity.current
    return remember(glyph, accent, density) { buildPin(density, glyph, accent) }
}

/** Glyphs used when a drive's endpoint isn't a saved place, so there is no emoji to show. */
object MapPinGlyphs {
    const val START = "▶"
    const val FINISH = "⚑"
}

private fun buildPin(density: Density, glyph: String, accent: Color): BitmapDescriptor {
    val px = { dp: Float -> with(density) { dp.dp.toPx() } }
    val discRadius = px(21f)
    val ring = px(2.5f)
    val tailHeight = px(9f)
    val tailHalfWidth = px(7f)
    val width = (discRadius * 2 + ring).toInt().coerceAtLeast(1)
    val height = (discRadius * 2 + ring + tailHeight).toInt().coerceAtLeast(1)

    val bitmap: Bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    val centreX = width / 2f
    val centreY = discRadius + ring / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Tail first, so the disc paints over where the two meet.
    paint.color = accent.toArgb()
    canvas.drawPath(
        Path().apply {
            moveTo(centreX - tailHalfWidth, centreY + discRadius - ring)
            lineTo(centreX + tailHalfWidth, centreY + discRadius - ring)
            lineTo(centreX, height.toFloat())
            close()
        },
        paint,
    )

    // Accent ring, then the dark face it surrounds.
    canvas.drawCircle(centreX, centreY, discRadius, paint)
    paint.color = DdSurfaceElevated.toArgb()
    canvas.drawCircle(centreX, centreY, discRadius - ring, paint)

    // The glyph. Emoji carry their own colour; the fallback arrow/flag takes the accent. Centring
    // uses the font's own ascent/descent rather than the text bounds, so a flag and an emoji sit at
    // the same height instead of each finding its own.
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent.toArgb()
        textSize = px(21f)
        textAlign = Paint.Align.CENTER
    }
    val metrics = text.fontMetrics
    canvas.drawText(glyph, centreX, centreY - (metrics.ascent + metrics.descent) / 2f, text)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * A flat disc for a point that should read as part of the map rather than as a pin on top of it —
 * used for the driver's own position. Anchor at (0.5, 0.5).
 */
@Composable
fun rememberMapDot(fill: Color, diameter: Float = 46f): BitmapDescriptor {
    val density = LocalDensity.current
    return remember(fill, diameter, density) {
        val sizePx = with(density) { diameter.dp.toPx() }.toInt().coerceAtLeast(1)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val centre = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = fill.copy(alpha = 0.22f).toArgb()
        canvas.drawCircle(centre, centre, centre, paint)
        paint.color = DdMapBase.toArgb()
        canvas.drawCircle(centre, centre, centre * 0.62f, paint)
        paint.color = fill.toArgb()
        canvas.drawCircle(centre, centre, centre * 0.46f, paint)
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
