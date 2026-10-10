/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared.backgrounds

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.morphe.manager.ui.theme.MorpheBrandBlue
import app.morphe.manager.ui.theme.MorpheBrandTeal
import app.morphe.manager.ui.theme.isDarkTheme
import kotlin.math.ceil
import kotlin.random.Random

// Glyphs of the rain: bits and the hex digits a dex dump is read in, bits weighted the heaviest
private val RANDOM_GLYPHS = listOf(
    '0', '1', '0', '1', '0', '1',
    'A', 'B', 'C', 'D', 'E', 'F',
    '2', '3', '4', '5', '6', '7', '8', '9',
    ':', ';', '/', '<', '>'
)

// Now and then a stream spells something out instead of falling as code
private val PHRASES = listOf(
    "USE MORPHE",
    "NO ADS",
    "WAKE UP",
    "PATCHED"
)

private const val PHRASE_CHANCE = 0.12f

// Every character the rain can show, each rasterized into one cell of the atlas
private val ATLAS_CHARS: List<Char> =
    (RANDOM_GLYPHS + PHRASES.flatMap { it.toList() }).filter { it != ' ' }.distinct()

private val ATLAS_INDEX: Map<Char, Int> =
    ATLAS_CHARS.withIndex().associate { (index, char) -> char to index }

private val RANDOM_GLYPH_INDICES: IntArray =
    RANDOM_GLYPHS.map { ATLAS_INDEX.getValue(it) }.toIntArray()

// A gap in a phrase, drawn as nothing at all
private const val BLANK = -1

private val GLYPH_SIZE = 14.dp

// Rows very nearly touch, while columns keep a glyph of air between them. Packed the other way
// round the rain fills the screen but stops reading as code
private const val COLUMN_STEP_RATIO = 1f
private const val ROW_STEP_RATIO = 1.02f

private const val TAIL_CELLS = 26

// Two streams per column fill the screen the way the rain is remembered. The third lands often
// enough to keep the columns out of step with each other
private const val STREAMS_PER_COLUMN = 2
private const val EXTRA_STREAM_CHANCE = 0.45f

// A glyph that changes faster than this reads as noise rather than as falling code
private const val MUTATION_INTERVAL_MS = 90f

// Skia merges blits that share a paint, so a tail is drawn in bands of one alpha rather than
// changing the paint at every glyph. Fine enough that the fade still reads as continuous
private const val ALPHA_BANDS = 16

// Same wrap as the other backgrounds use, short enough for float time to stay precise
private const val CYCLE_MS = 120000f
private const val CYCLE_FADE_MS = 1500f

/**
 * Falling columns of bytecode glyphs, the background the red pill unlocks.
 * Uses frame-based time so [speedMultiplier] changes smoothly without restarting animations.
 * On patching completion the rain surges forward and flares, then settles back into its drift.
 */
@Composable
fun MatrixBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val isDarkTheme = isDarkTheme()
    val context = LocalContext.current

    val maxAlpha = if (isDarkTheme) 0.85f else 0.5f

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.15f,
        context = context
    )

    val density = LocalDensity.current
    val glyphPx = with(density) { GLYPH_SIZE.toPx() }
    val columnStep = glyphPx * COLUMN_STEP_RATIO
    val rowStep = glyphPx * ROW_STEP_RATIO

    // Thousands of glyphs a frame is more text than a canvas will shape in a frame budget, so the
    // alphabet is rasterized once and every glyph after that is a blit out of the atlas
    val atlas = remember(glyphPx) { GlyphAtlas(glyphPx) }
    val glyphPaint = remember { android.graphics.Paint().apply { isFilterBitmap = true } }
    val source = remember { Rect() }
    val destination = remember { RectF() }

    val animatedTime = rememberAnimatedTime(speedMultiplier)

    // Completion surge: the rain jumps ahead and brightens before falling back into place
    val burstProgress = rememberCompletionPulse(patchingCompleted, riseMillis = 500, fallMillis = 1300)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columnCount = (with(density) { maxWidth.toPx() } / columnStep).toInt().coerceAtLeast(1)
        val rowCount = (with(density) { maxHeight.toPx() } / rowStep).toInt().coerceAtLeast(1)

        // Columns are dealt for the current size, so a rotation gets a fresh set instead of
        // stretching the old one across a screen it was never generated for
        val columns = remember(columnCount, rowCount) {
            List(columnCount) { index ->
                MatrixColumn(
                    seed = Random.nextInt(),
                    dimmed = index % 3 == 0,
                    streams = buildList {
                        repeat(STREAMS_PER_COLUMN) { add(randomStream()) }
                        if (Random.nextFloat() < EXTRA_STREAM_CHANCE) add(randomStream())
                    }
                )
            }
        }

        // Every column keeps its place in the brand gradient. Tinting is what the atlas costs in
        // exchange for the blit, so the filters are built per column rather than per glyph
        val tints = remember(columnCount, isDarkTheme) { ColumnTints(columnCount, isDarkTheme) }

        // Glyphs are gathered before they are drawn, so the buffer only has to hold as many of
        // them as the longest column this deal came out with
        val batches = remember(columns) {
            ColumnBatches(columns.maxOf { column -> column.streams.sumOf { it.tail } })
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val tiltX = parallaxState.tiltX.value
            val tiltY = parallaxState.tiltY.value
            val globalTime = animatedTime.value % CYCLE_MS
            val burst = burstProgress.value

            // Every column restarts when the clock wraps, so the seam is faded over instead of shown
            val cycleFade = when {
                globalTime < CYCLE_FADE_MS -> globalTime / CYCLE_FADE_MS
                globalTime > CYCLE_MS - CYCLE_FADE_MS -> (CYCLE_MS - globalTime) / CYCLE_FADE_MS
                else -> 1f
            }

            val mutationTick = (globalTime / MUTATION_INTERVAL_MS).toInt()
            val travel = rowCount + TAIL_CELLS * 2
            val parallaxX = tiltX * 40f
            val parallaxY = tiltY * 40f
            val half = atlas.cell / 2f

            drawIntoCanvas { canvas ->
                columns.forEachIndexed { columnIndex, column ->
                    val x = columnIndex * columnStep + columnStep / 2f + parallaxX
                    batches.clear()

                    column.streams.forEach { stream ->
                        // A phrase is only worth hiding if it can be read, so it keeps an even
                        // brightness and ignores the depth its column falls at
                        val depthAlpha = if (column.dimmed && stream.phrase == null) 0.6f else 1f
                        val head =
                            (globalTime * stream.fallSpeed + stream.phase * travel + burst * 12f) % travel

                        for (offset in 0 until stream.tail) {
                            val alpha = (stream.fadeAt(offset) * depthAlpha * maxAlpha * cycleFade *
                                    (1f + burst * 1.2f)).coerceIn(0f, 1f)
                            // A tail only ever dims towards its end, so the rest of it is gone too
                            if (alpha < 0.02f) break

                            val row = head.toInt() - offset
                            if (row !in 0..rowCount) continue

                            val glyph = stream.phraseGlyphAt(offset)
                                ?: RANDOM_GLYPH_INDICES[column.randomGlyphAt(row, mutationTick)]
                            if (glyph == BLANK) continue

                            val centerY = row * rowStep + rowStep / 2f + parallaxY

                            // The leading glyph carries a color of its own, and there is only one
                            // of it per stream, so it is drawn where it falls rather than banded
                            if (offset == 0) {
                                glyphPaint.colorFilter = tints.filterFor(columnIndex, head = true)
                                glyphPaint.alpha = (alpha * 255).toInt()
                                atlas.selectGlyph(glyph, source)
                                destination.set(x - half, centerY - half, x + half, centerY + half)
                                canvas.nativeCanvas.drawBitmap(atlas.bitmap, source, destination, glyphPaint)
                            } else {
                                batches.add(alphaBandOf(alpha), glyph, centerY)
                            }
                        }
                    }

                    // Glyphs of a column share its tint, so what is left after the heads goes out
                    // one alpha band at a time, the paint holding still for every blit in between
                    glyphPaint.colorFilter = tints.filterFor(columnIndex, head = false)

                    for (band in 0 until ALPHA_BANDS) {
                        val count = batches.countAt(band)
                        if (count == 0) continue

                        glyphPaint.alpha = bandAlpha(band)
                        val glyphs = batches.glyphsAt(band)
                        val centers = batches.centersAt(band)

                        for (index in 0 until count) {
                            atlas.selectGlyph(glyphs[index], source)
                            val centerY = centers[index]
                            destination.set(x - half, centerY - half, x + half, centerY + half)
                            canvas.nativeCanvas.drawBitmap(atlas.bitmap, source, destination, glyphPaint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One run of glyphs falling down a column, several of which share the same column.
 * A stream carrying a [phrase] spells it out top to bottom instead of falling as random code.
 */
private class MatrixStream(
    val phase: Float,
    val fallSpeed: Float,
    val tail: Int,
    val phrase: IntArray? = null
) {
    /** Atlas glyph this offset spells out, or null while the stream is falling as plain code. */
    fun phraseGlyphAt(offset: Int): Int? = phrase?.get(phrase.size - 1 - offset)

    /**
     * A code tail dims away towards its end. A phrase only dips, otherwise its opening letters
     * would fade out before the last ones arrive.
     */
    fun fadeAt(offset: Int): Float {
        val progress = offset.toFloat() / tail
        return if (phrase == null) (1f - progress) * (1f - progress) else 1f - progress * 0.4f
    }
}

private fun randomStream(): MatrixStream {
    val phrase = if (Random.nextFloat() < PHRASE_CHANCE) PHRASES.random().toAtlasIndices() else null

    return MatrixStream(
        phase = Random.nextFloat(),
        // A phrase falls slower than the surrounding code, long enough to be caught sight of
        fallSpeed = if (phrase != null) {
            0.002f + Random.nextFloat() * 0.002f
        } else {
            0.004f + Random.nextFloat() * 0.007f
        },
        tail = phrase?.size
            ?: (TAIL_CELLS * (0.45f + Random.nextFloat() * 0.75f)).toInt().coerceAtLeast(4),
        phrase = phrase
    )
}

private fun String.toAtlasIndices() = IntArray(length) { ATLAS_INDEX[this[it]] ?: BLANK }

/**
 * One column of the rain. Glyphs are derived from the seed and the current tick rather than
 * stored, so a column costs nothing to keep between frames however long its streams are.
 */
private class MatrixColumn(
    val seed: Int,
    val dimmed: Boolean,
    val streams: List<MatrixStream>
) {
    fun randomGlyphAt(row: Int, tick: Int): Int {
        val hash = (seed * 73856093) xor (row * 19349663) xor (tick * 83492791)
        // Masking the sign bit off, rather than taking the absolute value, keeps Int.MIN_VALUE in range
        return (hash and Int.MAX_VALUE) % RANDOM_GLYPH_INDICES.size
    }
}

/**
 * Glyphs of one column, held per alpha band so that a band can go out under a single paint.
 * Reused between frames, a column never showing more glyphs than its streams are long.
 */
private class ColumnBatches(capacity: Int) {
    private val glyphs = Array(ALPHA_BANDS) { IntArray(capacity) }
    private val centers = Array(ALPHA_BANDS) { FloatArray(capacity) }
    private val counts = IntArray(ALPHA_BANDS)

    fun clear() = counts.fill(0)

    fun add(band: Int, glyph: Int, centerY: Float) {
        val count = counts[band]
        // The capacity covers every glyph a column can hold at once, so a full band means the
        // columns were dealt again and the buffer is about to be replaced anyway
        if (count == glyphs[band].size) return

        glyphs[band][count] = glyph
        centers[band][count] = centerY
        counts[band] = count + 1
    }

    fun countAt(band: Int) = counts[band]

    fun glyphsAt(band: Int) = glyphs[band]

    fun centersAt(band: Int) = centers[band]
}

/** The band an alpha falls in, rounded to the middle of the band on the way back out. */
private fun alphaBandOf(alpha: Float) = (alpha * ALPHA_BANDS).toInt().coerceIn(0, ALPHA_BANDS - 1)

private fun bandAlpha(band: Int) = (((band + 0.5f) / ALPHA_BANDS) * 255f).toInt()

/** The alphabet rasterized side by side into one bitmap, a square cell per character. */
private class GlyphAtlas(glyphPx: Float) {
    val cell = ceil(glyphPx * 1.35f).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(cell * ATLAS_CHARS.size, cell)

    init {
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = glyphPx
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }

        // Drawn white and tinted on the way out, so one atlas serves every column color
        val baseline = cell / 2f - (paint.descent() + paint.ascent()) / 2f
        ATLAS_CHARS.forEachIndexed { index, char ->
            canvas.drawText(char.toString(), index * cell + cell / 2f, baseline, paint)
        }
    }

    fun selectGlyph(index: Int, into: Rect) = into.set(index * cell, 0, (index + 1) * cell, cell)
}

/** Per-column tints of the brand gradient, one filter for a trailing glyph and one for a head. */
private class ColumnTints(columnCount: Int, isDarkTheme: Boolean) {
    private val trail = arrayOfNulls<PorterDuffColorFilter>(columnCount)
    private val head = arrayOfNulls<PorterDuffColorFilter>(columnCount)

    init {
        for (index in 0 until columnCount) {
            val position = if (columnCount == 1) 0f else index.toFloat() / (columnCount - 1)
            // The brand gradient, read left to right across the screen the way the wordmark reads
            val brand = MorpheBrandBlue.blendTowards(MorpheBrandTeal, position)
            // The brand blue is too light to read as code on a light background
            val trailColor = if (isDarkTheme) brand else brand.blendTowards(Color.Black, 0.3f)

            trail[index] = tint(trailColor)
            head[index] = tint(
                trailColor.blendTowards(if (isDarkTheme) Color.White else Color.Black, 0.55f)
            )
        }
    }

    fun filterFor(columnIndex: Int, head: Boolean) =
        if (head) this.head[columnIndex] else trail[columnIndex]

    private fun tint(color: Color) = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
}

/** Mixes [target] into the receiver, the head of a stream being a brighter take on the accent. */
private fun Color.blendTowards(target: Color, fraction: Float) = Color(
    red = red + (target.red - red) * fraction,
    green = green + (target.green - green) * fraction,
    blue = blue + (target.blue - blue) * fraction,
    alpha = alpha
)
