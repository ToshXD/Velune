/*
 * Velune - Apple Music style player layout
 * Licensed Under GPL-3.0 (same as the rest of Velune)
 */

package com.nikhil.yt.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import coil3.compose.AsyncImage
import com.nikhil.yt.R
import com.nikhil.yt.extensions.togglePlayPause
import com.nikhil.yt.innertube.toHighResThumbnail
import com.nikhil.yt.models.MediaMetadata
import com.nikhil.yt.playback.PlayerConnection
import com.nikhil.yt.ui.component.VeluneLoader
import com.nikhil.yt.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Apple Music style "Now Playing" screen.
 * Same parameters as MetroPlayerContent so it can replace it in Player.kt.
 */
@Composable
internal fun ApplePlayerContent(
    mediaMetadata: MediaMetadata,
    sliderPosition: Long?,
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    liked: Boolean,
    playerConnection: PlayerConnection,
    onToggleLike: () -> Unit,
    onExpandQueue: () -> Unit,
    onMenuClick: () -> Unit,
    context: Context,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val isLoading = playbackState == STATE_BUFFERING
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val hasDuration = durationMs > 0L && durationMs != C.TIME_UNSET

    // Local scrubbing state so the bar follows the finger smoothly.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(scrubbing) {
        if (!scrubbing && dragFraction != null) {
            delay(400)
            dragFraction = null
        }
    }

    val basePositionMs = sliderPosition ?: positionMs
    val playbackFraction =
        if (hasDuration) (basePositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownFraction = dragFraction ?: playbackFraction
    val shownPositionMs =
        if (hasDuration && dragFraction != null) (shownFraction * durationMs).toLong() else basePositionMs
    val remainingMs = if (hasDuration) (durationMs - shownPositionMs).coerceAtLeast(0L) else 0L

    // Album art shrinks while paused, like Apple Music.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "artScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                )
            )
            .padding(
                bottom = bottomPadding +
                    WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() +
                    8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Grabber handle
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(textColor.copy(alpha = 0.35f))
        )

        // Album art
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            AsyncImage(
                model = mediaMetadata.thumbnailUrl?.toHighResThumbnail(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(side)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(10.dp),
                        clip = false
                    )
                    .clip(RoundedCornerShape(10.dp))
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Title / artist + star + more
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata.title ?: "",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = mediaMetadata.artists.joinToString { it.name },
                        fontSize = 21.sp,
                        color = textColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AppleCircleButton(
                    onClick = onToggleLike,
                    description = if (liked) "Remove from favorites" else "Add to favorites",
                    background = if (liked) textColor else textColor.copy(alpha = 0.2f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.star),
                        contentDescription = null,
                        tint = if (liked) MaterialTheme.colorScheme.surface else textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                AppleCircleButton(
                    onClick = onMenuClick,
                    description = "More",
                    background = textColor.copy(alpha = 0.2f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scrubber
            AppleScrubber(
                fraction = shownFraction,
                onChange = { f ->
                    scrubbing = true
                    dragFraction = f
                },
                onFinished = { f ->
                    if (hasDuration) {
                        playerConnection.player.seekTo((f * durationMs).toLong())
                    }
                    scrubbing = false
                },
                activeColor = textColor,
                inactiveColor = textColor.copy(alpha = 0.3f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = makeTimeString(shownPositionMs),
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
                Text(
                    text = if (hasDuration) "-" + makeTimeString(remainingMs) else "",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleControlButton(
                    onClick = { playerConnection.seekToPrevious() },
                    description = "Previous",
                    modifier = Modifier.size(width = 80.dp, height = 64.dp)
                ) {
                    AppleSkipIcon(
                        forward = false,
                        tint = textColor.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                        modifier = Modifier.size(width = 44.dp, height = 32.dp)
                    )
                }
                AppleControlButton(
                    onClick = {
                        if (playbackState == STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    },
                    description = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(width = 80.dp, height = 64.dp)
                ) {
                    if (isLoading) {
                        VeluneLoader(size = 36.dp)
                    } else {
                        ApplePlayPauseIcon(
                            isPlaying = isPlaying,
                            tint = textColor,
                            modifier = Modifier.size(width = 44.dp, height = 52.dp)
                        )
                    }
                }
                AppleControlButton(
                    onClick = { playerConnection.seekToNext() },
                    description = "Next",
                    modifier = Modifier.size(width = 80.dp, height = 64.dp)
                ) {
                    AppleSkipIcon(
                        forward = true,
                        tint = textColor.copy(alpha = if (canSkipNext) 1f else 0.4f),
                        modifier = Modifier.size(width = 44.dp, height = 32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Volume (device music volume, like Apple Music)
            val audioManager = remember {
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }
            val maxVolume = remember {
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            }
            var volume by remember {
                mutableFloatStateOf(
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                )
            }
            var draggingVolume by remember { mutableStateOf(false) }
            LaunchedEffect(draggingVolume) {
                // Follow the hardware volume buttons while the user isn't dragging.
                while (!draggingVolume) {
                    volume =
                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                    delay(400)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleSpeakerLowIcon(
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                AppleScrubber(
                    fraction = volume,
                    onChange = { f ->
                        draggingVolume = true
                        volume = f
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (f * maxVolume).roundToInt(),
                            0
                        )
                    },
                    onFinished = { draggingVolume = false },
                    activeColor = textColor.copy(alpha = 0.85f),
                    inactiveColor = textColor.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(R.drawable.volume_up),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Thin rounded scrubber that thickens while dragging. */
@Composable
private fun AppleScrubber(
    fraction: Float,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 12.dp else 6.dp,
        label = "scrubberHeight"
    )
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onFinished)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnChange(f)
                        currentOnFinished(f)
                    }
                )
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnChange(last)
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnFinished(last)
                    },
                    onDragCancel = {
                        dragging = false
                        currentOnFinished(last)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        currentOnChange(last)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(inactiveColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(activeColor)
            )
        }
    }
}

/** Translucent round button (star / more) from the Apple Music header. */
@Composable
private fun AppleCircleButton(
    onClick: () -> Unit,
    description: String,
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Borderless control button with Apple's small press-down scale. */
@Composable
private fun AppleControlButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "controlPress"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Double-triangle skip icon (Apple style). */
@Composable
private fun AppleSkipIcon(
    forward: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.05f
        val mid = w / 2f
        val stroke = Stroke(width = w * 0.09f, join = StrokeJoin.Round)

        fun triangle(left: Float, right: Float): Path = Path().apply {
            if (forward) {
                moveTo(left, pad)
                lineTo(right, h / 2f)
                lineTo(left, h - pad)
            } else {
                moveTo(right, pad)
                lineTo(left, h / 2f)
                lineTo(right, h - pad)
            }
            close()
        }

        val first = triangle(pad, mid)
        val second = triangle(mid, w - pad)
        drawPath(path = first, color = tint)
        drawPath(path = first, color = tint, style = stroke)
        drawPath(path = second, color = tint)
        drawPath(path = second, color = tint, style = stroke)
    }
}

/** Big rounded play / pause glyph. */
@Composable
private fun ApplePlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.31f
            val gap = w * 0.16f
            val left = (w - (barW * 2f + gap)) / 2f
            val radius = CornerRadius(barW * 0.3f, barW * 0.3f)
            drawRoundRect(
                color = tint,
                topLeft = Offset(left, h * 0.08f),
                size = Size(barW, h * 0.84f),
                cornerRadius = radius
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(left + barW + gap, h * 0.08f),
                size = Size(barW, h * 0.84f),
                cornerRadius = radius
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.16f, h * 0.1f)
                lineTo(w * 0.94f, h * 0.5f)
                lineTo(w * 0.16f, h * 0.9f)
                close()
            }
            drawPath(path = path, color = tint)
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = w * 0.12f, join = StrokeJoin.Round)
            )
        }
    }
}

/** Plain speaker with no sound waves (left end of the volume bar). */
@Composable
private fun AppleSpeakerLowIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.10f, h * 0.38f)
            lineTo(w * 0.32f, h * 0.38f)
            lineTo(w * 0.60f, h * 0.14f)
            lineTo(w * 0.60f, h * 0.86f)
            lineTo(w * 0.32f, h * 0.62f)
            lineTo(w * 0.10f, h * 0.62f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}
