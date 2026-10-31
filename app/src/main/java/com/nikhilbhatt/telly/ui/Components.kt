package com.nikhilbhatt.telly.ui

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.nikhilbhatt.telly.data.Presets
import com.nikhilbhatt.telly.data.newId
import java.io.File

val LocalHaptics = staticCompositionLocalOf { true }

@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalHaptics.current
    return remember(view, enabled) { { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }
}

/**
 * A remote button. With [repeat], holding it re-sends the key the way a physical remote does,
 * which matters for volume and arrows.
 */
@Composable
fun PressKey(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    container: Color = Palette.SurfaceHigh,
    repeat: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")
    val haptic = rememberHaptic()
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (pressed) container.lighten() else container)
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic()
                    currentOnPress()
                    if (repeat) {
                        var released = withTimeoutOrNull(450) { waitForUpOrCancellation(); true }
                        while (released == null) {
                            currentOnPress()
                            released = withTimeoutOrNull(120) { waitForUpOrCancellation(); true }
                        }
                    } else {
                        waitForUpOrCancellation()
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun IconKey(
    icon: ImageVector,
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    container: Color = Palette.SurfaceHigh,
    tint: Color = Palette.Text,
    repeat: Boolean = false,
) {
    PressKey(onPress, modifier.size(size), container = container, repeat = repeat) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

fun Color.lighten(amount: Float = 0.12f) = Color(
    red = red + (1 - red) * amount,
    green = green + (1 - green) * amount,
    blue = blue + (1 - blue) * amount,
    alpha = alpha,
)

/**
 * A logo, or a gradient with the name's initials when there is none (or it fails to load).
 * App icons [fill] the tile; channel logos are wordmarks, so they sit on a light card instead.
 */
@Composable
fun LogoArt(name: String, logo: String?, color: Long, modifier: Modifier = Modifier, textSize: Int = 22, fill: Boolean = false) {
    val base = Color(color)
    val gradient = Brush.linearGradient(listOf(base.lighten(0.18f), base, base.darken()))
    Box(
        modifier.background(if (logo.isNullOrBlank() || fill) gradient else SolidColor(LogoCard)),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Text(
                initials(name),
                color = Color.White,
                fontSize = textSize.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
            )
        }
        if (logo.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = if (logo.startsWith("/")) File(logo) else logo,
                contentDescription = name,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                modifier = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(12.dp),
                error = { Box(Modifier.fillMaxSize().background(gradient), contentAlignment = Alignment.Center) { fallback() } },
            )
        }
    }
}

private val LogoCard = Color(0xFFF4F4F6)

fun Color.darken(amount: Float = 0.25f) = Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

fun initials(name: String): String {
    val words = name.split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(if (words[0].length <= 3) 3 else 1).uppercase()
        else -> words.take(2).joinToString("") { it.first().uppercase() }
    }
}

/** A tile in the channel and app grids: tap to use, long-press to edit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridTile(
    name: String,
    logo: String?,
    color: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    subtitle: String? = null,
    fillLogo: Boolean = false,
) {
    val haptic = rememberHaptic()
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { haptic(); onClick() },
                onLongClick = { haptic(); onLongClick() },
            )
            .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(18.dp))) {
            LogoArt(name, logo, color, Modifier.fillMaxSize(), fill = fillLogo)
            if (badge != null) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Presets.palette) { c ->
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .then(if (c == selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = SectionLabel, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

/** Logo actions beside the preview in the channel and app editors. */
@Composable
fun LogoButtons(
    hasLogo: Boolean,
    finding: Boolean,
    findLabel: String,
    onFind: (() -> Unit)?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column {
        if (onFind != null) {
            TextButton(onClick = onFind, enabled = !finding) {
                if (finding) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (finding) "Finding…" else findLabel)
            }
        }
        TextButton(onClick = onPick) {
            Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pick from gallery")
        }
        extra()
        if (hasLogo) TextButton(onClick = onRemove) { Text("Remove logo") }
    }
}

/** Copies a picked image into app storage so it survives the gallery item being deleted. */
fun copyLogo(context: Context, uri: Uri, dir: File): String? = runCatching {
    val file = File(dir, newId() + ".img")
    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    file.absolutePath
}.getOrNull()

/** Readable name for an app package, using saved app tiles first. */
fun appLabel(pkg: String?, known: Map<String, String>): String? {
    if (pkg == null) return null
    known[pkg]?.let { return it }
    WELL_KNOWN[pkg]?.let { return it }
    return pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

private val WELL_KNOWN = mapOf(
    "com.netflix.ninja" to "Netflix",
    "com.google.android.youtube.tv" to "YouTube",
    "com.amazon.amazonvideo.livingroom" to "Prime Video",
    "com.google.android.tvlauncher" to "Home",
    "com.google.android.apps.tv.launcherx" to "Home",
    "com.android.tv.settings" to "Settings",
    "com.spotify.tv.android" to "Spotify",
)
