package app.drivedelta.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * One line of text that shrinks until it fits, instead of wrapping, ellipsising, or overflowing into
 * whatever is beside it.
 *
 * The three failure modes it replaces all showed up on a real phone and not on the emulator, because
 * they depend on the device's width and font scale: a "1:29:50" duration painted over the distance
 * beside it, a "Segments" tab label broke across two lines, and a segment's "1.0 km · avg 23 km/h ·
 * max 45 km/h" line lost its last figure to an ellipsis. All three are values worth a smaller font
 * and not worth a broken layout.
 *
 * Shrinking is driven by the measured overflow rather than by guessing from character counts, so it
 * is exact for any string in any language — and it stops at [minSize], past which clipping is the
 * lesser evil.
 */
@Composable
fun FittedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minSize: TextUnit = 9.sp,
) {
    // Keyed on both so a new value (or a restyle) starts again from full size rather than inheriting
    // the shrink applied to whatever was here before.
    var fitted by remember(text, style) { mutableStateOf(style) }
    Text(
        text = text,
        modifier = modifier,
        style = fitted,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && fitted.fontSize > minSize) {
                fitted = fitted.copy(fontSize = fitted.fontSize * SHRINK_STEP)
            }
        },
    )
}

private const val SHRINK_STEP = 0.92f
