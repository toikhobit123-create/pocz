package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.FollowerBronze
import com.example.ui.theme.FollowerSilverBright
import com.example.ui.theme.FollowerSilverDark
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.HextechGoldDeep
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.ManaBlue
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ParchmentWhite

enum class LorMetalTone {
    GOLD,
    SILVER,
    CYAN,
    CUSTOM
}

/**
 * Recessed LoR Dark & Metallic Panel with filigree overlay, 4-edge inner shadow,
 * double metallic bevel frame, and optional corner brass rivets.
 */
@Composable
fun LorMetallicPanel(
    modifier: Modifier = Modifier,
    tone: LorMetalTone = LorMetalTone.GOLD,
    customBorderColor: Color = HextechGold,
    cornerRadius: Dp = 14.dp,
    borderWidth: Dp = 1.8.dp,
    filigreeAlpha: Float = 0.14f,
    showCornerFiligree: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = when (tone) {
        LorMetalTone.GOLD -> Brush.linearGradient(
            colors = listOf(HextechGoldBright, HextechGold, HextechGoldDeep, HextechGold)
        )
        LorMetalTone.SILVER -> Brush.linearGradient(
            colors = listOf(FollowerSilverBright, FollowerSilverDark, FollowerBronze, FollowerSilverBright)
        )
        LorMetalTone.CYAN -> Brush.linearGradient(
            colors = listOf(ArcaneCyan, ManaBlue, Color(0xFF0A4A8A), ArcaneCyan)
        )
        LorMetalTone.CUSTOM -> Brush.linearGradient(
            colors = listOf(
                customBorderColor,
                customBorderColor.copy(alpha = 0.65f),
                HextechGoldDeep.copy(alpha = 0.85f),
                customBorderColor
            )
        )
    }

    val cornerAccent = when (tone) {
        LorMetalTone.GOLD -> HextechGoldBright
        LorMetalTone.SILVER -> FollowerSilverBright
        LorMetalTone.CYAN -> ArcaneCyan
        LorMetalTone.CUSTOM -> customBorderColor
    }

    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
                else Modifier
            ),
        shape = shape,
        color = ObsidianCard,
        border = BorderStroke(borderWidth, borderBrush),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A2842),
                            ObsidianCard,
                            Color(0xFF0B1323)
                        )
                    )
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_metal_filigree_tex),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = filigreeAlpha,
                modifier = Modifier.matchParentSize()
            )

            // 4-edge inner recessed shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.44f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.50f)
                            )
                        )
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f)
                            )
                        )
                    )
            )

            // Inner etched dark groove & subtle corner brackets
            Canvas(modifier = Modifier.matchParentSize()) {
                if (size.width <= 4f || size.height <= 4f) return@Canvas
                val inset = 3.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        (size.width - inset * 2).coerceAtLeast(1f),
                        (size.height - inset * 2).coerceAtLeast(1f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        (cornerRadius - 2.dp).toPx().coerceAtLeast(2f)
                    ),
                    style = Stroke(width = 1.dp.toPx())
                )
                if (showCornerFiligree) {
                    val bracketLen = minOf(12.dp.toPx(), size.minDimension * 0.18f)
                    val m = 6.dp.toPx()
                    val strokeW = 1.4.dp.toPx()
                    val c = cornerAccent.copy(alpha = 0.68f)
                    // Top-left bracket
                    drawLine(c, Offset(m, m + bracketLen), Offset(m, m), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(m, m), Offset(m + bracketLen, m), strokeW, StrokeCap.Round)
                    // Top-right bracket
                    drawLine(c, Offset(size.width - m - bracketLen, m), Offset(size.width - m, m), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(size.width - m, m), Offset(size.width - m, m + bracketLen), strokeW, StrokeCap.Round)
                    // Bottom-left bracket
                    drawLine(c, Offset(m, size.height - m - bracketLen), Offset(m, size.height - m), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(m, size.height - m), Offset(m + bracketLen, size.height - m), strokeW, StrokeCap.Round)
                    // Bottom-right bracket
                    drawLine(c, Offset(size.width - m - bracketLen, size.height - m), Offset(size.width - m, size.height - m), strokeW, StrokeCap.Round)
                    drawLine(c, Offset(size.width - m, size.height - m - bracketLen), Offset(size.width - m, size.height - m), strokeW, StrokeCap.Round)
                }
            }

            Box(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Primary LoR Burnished Gold/Brass CTA Button (#D4AF37 -> #FFD700) with beveled double rim,
 * Cinzel serif heading typography, and tactile spring press compression.
 */
@Composable
fun LorBrassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCyanVariant: Boolean = false,
    fontSize: TextUnit = 13.sp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(),
        label = "lor_brass_btn_scale"
    )

    val outerBrush = when {
        !enabled -> Brush.linearGradient(listOf(FollowerSilverDark, Color(0xFF283449)))
        isCyanVariant -> Brush.linearGradient(listOf(Color(0xFFCCF5FF), ArcaneCyan, Color(0xFF0A4A8A)))
        else -> Brush.linearGradient(listOf(Color(0xFFFFF0A6), HextechGoldBright, HextechGold, HextechGoldDeep))
    }
    val innerBrush = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0xFF233047), Color(0xFF151E2E)))
        isCyanVariant -> Brush.verticalGradient(listOf(Color(0xFF7BE4FF), ArcaneCyan, ManaBlue, Color(0xFF026AA7)))
        else -> Brush.verticalGradient(listOf(Color(0xFFFFE97D), HextechGoldBright, HextechGold, Color(0xFFA67B1B)))
    }
    val inkColor = if (enabled) DarkButtonInk else MutedSilver

    Surface(
        modifier = modifier
            .height(50.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(1.8.dp, outerBrush),
        shadowElevation = if (enabled) 10.dp else 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(innerBrush)
                .border(
                    width = 1.dp,
                    color = if (enabled) Color(0x99FFF8D6) else Color(0x338C9BB0),
                    shape = RoundedCornerShape(9.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = fantasyHeadingStyle(
                        fontSize = fontSize,
                        color = inkColor,
                        withShadow = !enabled
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Secondary Recessed Obsidian & Silver/Gold Beveled Action Button.
 */
@Composable
fun LorSecondaryMetalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = HextechGoldBright,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(),
        label = "lor_sec_btn_scale"
    )

    val borderBrush = if (enabled) {
        Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.55f), HextechGoldDeep, accentColor))
    } else {
        Brush.linearGradient(listOf(FollowerSilverDark, Color(0xFF283449)))
    }

    Surface(
        modifier = modifier
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.5.dp, borderBrush),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1F304D), Color(0xFF121C30), Color(0xFF0A111F))
                    )
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = text,
                    style = fantasyHeadingStyle(
                        fontSize = 12.sp,
                        color = if (enabled) accentColor else MutedSilver,
                        withShadow = true
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Engraved LoR Top Header Plaque with metallic gold bottom rule, recessed back medallion,
 * Cinzel serif title, and optional trailing actions.
 */
@Composable
fun LorCarvedTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    backDescription: String = "Quay Lại",
    leadingSlot: (@Composable () -> Unit)? = null,
    trailingSlot: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF192740), ObsidianSurface, Color(0xFF0C1424))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                        .then(if (backTestTag != null) Modifier.testTag(backTestTag) else Modifier)
                        .clickable(onClick = onBack),
                    shape = CircleShape,
                    color = ObsidianBg,
                    border = BorderStroke(1.3.dp, HextechGold.copy(alpha = 0.8f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDescription,
                            tint = HextechGoldBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (leadingSlot != null) {
                leadingSlot()
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = fantasyHeadingStyle(fontSize = 16.5.sp, color = HextechGoldBright),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = InterFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = ArcaneCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingSlot != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = trailingSlot
                )
            }
        }

        // Engraved horizontal gold bevel divider line with diamond center accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HextechGoldDeep,
                            HextechGoldBright,
                            HextechGoldDeep,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
