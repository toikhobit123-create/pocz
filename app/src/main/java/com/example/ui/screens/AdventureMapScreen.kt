package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.MapNode
import com.example.model.NodeType
import com.example.model.RunState
import com.example.ui.components.LorCarvedTopBar
import com.example.ui.components.LorMetalTone
import com.example.ui.components.LorMetallicPanel
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.RunDeckDialog
import com.example.ui.components.RunResourceHud
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.interBodyStyle
import com.example.ui.components.regionAccentColor
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.EpicViolet
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ParchmentWhite
import com.example.ui.theme.SlateBorder

@Composable
fun AdventureMapScreen(
    run: RunState,
    isNodeAvailable: (RunState, MapNode) -> Boolean,
    onSelectNode: (String) -> Unit,
    onAbandonRun: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackHome)

    var showDeck by remember { mutableStateOf(false) }
    val adventure = AdventureCatalog.getAdventure(run.adventureId)
    val regionGlow = regionAccentColor(adventure.region)
    val maxRow = adventure.mapNodes.maxOfOrNull { it.row } ?: 6
    val rowHeightDp = 104.dp
    val totalHeight = rowHeightDp * (maxRow + 1) + 40.dp

    val pulseTransition = rememberInfiniteTransition(label = "map_node_pulse")
    val activePulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_node_scale"
    )

    val mapCanvasContent: @Composable (Modifier) -> Unit = { mapModifier ->
        Box(
            modifier = mapModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                // Illuminated Cartographic Ley-Line Paths + Subtle Compass Rings
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (size.width <= 1f || size.height <= 1f) return@Canvas
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    drawCircle(
                        color = HextechGold.copy(alpha = 0.08f),
                        radius = size.width * 0.42f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = ArcaneCyan.copy(alpha = 0.07f),
                        radius = size.width * 0.28f,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )

                    val rowSpacing = size.height / (maxRow + 1).toFloat()
                    fun nodeCenter(node: MapNode): Offset {
                        val x = when (node.column) {
                            0 -> size.width * 0.22f
                            2 -> size.width * 0.78f
                            else -> size.width * 0.50f
                        }
                        val y = rowSpacing * node.row + rowSpacing * 0.5f
                        return Offset(x, y)
                    }

                    adventure.mapNodes.forEach { fromNode ->
                        val start = nodeCenter(fromNode)
                        fromNode.nextNodeIds.forEach { targetId ->
                            val toNode = adventure.mapNodes.find { it.id == targetId }
                            if (toNode != null) {
                                val end = nodeCenter(toNode)
                                val isPathActive = run.currentNodeId == fromNode.id &&
                                    isNodeAvailable(run, toNode)
                                val isPathCompleted = run.completedNodeIds.contains(fromNode.id) &&
                                    run.completedNodeIds.contains(toNode.id)

                                val path = Path().apply {
                                    moveTo(start.x, start.y)
                                    val midY = (start.y + end.y) / 2f
                                    cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                                }
                                if (isPathActive) {
                                    drawPath(
                                        path = path,
                                        color = HextechGoldBright.copy(alpha = 0.30f),
                                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = when {
                                        isPathActive -> HextechGoldBright
                                        isPathCompleted -> EmeraldHeal.copy(alpha = 0.80f)
                                        else -> SlateBorder.copy(alpha = 0.75f)
                                    },
                                    style = Stroke(
                                        width = if (isPathActive) 5f else 3.5f,
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Render rows top-down (row 0 Start -> row 6 Final Boss)
                Column(modifier = Modifier.fillMaxSize()) {
                    for (rowIndex in 0..maxRow) {
                        val nodesInRow = adventure.mapNodes.filter { it.row == rowIndex }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightDp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val left = nodesInRow.find { it.column == 0 }
                            val mid = nodesInRow.find { it.column == 1 }
                            val right = nodesInRow.find { it.column == 2 }

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (left != null) {
                                    MapNodeChip(
                                        node = left,
                                        completed = run.completedNodeIds.contains(left.id),
                                        available = isNodeAvailable(run, left),
                                        isCurrent = run.currentNodeId == left.id,
                                        activePulse = activePulse,
                                        onClick = { onSelectNode(left.id) }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (mid != null) {
                                    MapNodeChip(
                                        node = mid,
                                        completed = run.completedNodeIds.contains(mid.id),
                                        available = isNodeAvailable(run, mid),
                                        isCurrent = run.currentNodeId == mid.id,
                                        activePulse = activePulse,
                                        onClick = { onSelectNode(mid.id) }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (right != null) {
                                    MapNodeChip(
                                        node = right,
                                        completed = run.completedNodeIds.contains(right.id),
                                        available = isNodeAvailable(run, right),
                                        isCurrent = run.currentNodeId == right.id,
                                        activePulse = activePulse,
                                        onClick = { onSelectNode(right.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionGlow
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1080.dp)
                ) {
                    LorMetallicPanel(
                        modifier = Modifier
                            .width(292.dp)
                            .fillMaxHeight()
                            .padding(8.dp),
                        tone = LorMetalTone.GOLD,
                        cornerRadius = 14.dp,
                        borderWidth = 1.6.dp,
                        filigreeAlpha = 0.14f,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LorCarvedTopBar(
                                title = adventure.name,
                                subtitle = adventure.difficultyLabel,
                                onBack = onBackHome,
                                backTestTag = "map_back_home_button",
                                backDescription = "Lưu & Về Sảnh"
                            )

                            RunResourceHud(
                                run = run,
                                onOpenDeck = { showDeck = true },
                                onAbandon = onAbandonRun
                            )

                            Text(
                                text = "Chọn chặng phát sáng tiếp theo trên tuyến đường.",
                                style = interBodyStyle(11.5.sp, MutedSilver)
                            )
                        }
                    }

                    mapCanvasContent(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                ) {
                    LorCarvedTopBar(
                        title = adventure.name,
                        subtitle = "${adventure.difficultyLabel} • Đi theo tuyến đường vàng",
                        onBack = onBackHome,
                        backTestTag = "map_back_home_button",
                        backDescription = "Lưu & Về Sảnh"
                    )

                    RunResourceHud(
                        run = run,
                        onOpenDeck = { showDeck = true },
                        onAbandon = onAbandonRun,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    mapCanvasContent(Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showDeck) {
        RunDeckDialog(runDeck = run.runDeck, onDismiss = { showDeck = false })
    }
}

@Composable
private fun MapNodeChip(
    node: MapNode,
    completed: Boolean,
    available: Boolean,
    isCurrent: Boolean,
    activePulse: Float,
    onClick: () -> Unit
) {
    val accent = nodeAccent(node.type)
    val tone = when {
        available -> LorMetalTone.GOLD
        completed -> LorMetalTone.CUSTOM
        else -> LorMetalTone.CUSTOM
    }
    val customBorder = when {
        completed -> EmeraldHeal
        isCurrent -> ArcaneCyan
        else -> SlateBorder
    }

    LorMetallicPanel(
        modifier = Modifier
            .width(128.dp)
            .graphicsLayer {
                if (available) {
                    scaleX = activePulse
                    scaleY = activePulse
                }
            }
            .testTag("map_node_${node.id}"),
        tone = tone,
        customBorderColor = customBorder,
        cornerRadius = 14.dp,
        borderWidth = if (available) 2.2.dp else 1.2.dp,
        filigreeAlpha = if (available) 0.20f else 0.08f,
        showCornerFiligree = available,
        onClick = if (available) onClick else null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    (if (completed) EmeraldHeal else accent).copy(alpha = 0.35f),
                                    ObsidianBg
                                )
                            )
                        )
                        .border(1.dp, if (completed) EmeraldHeal else accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (completed) Icons.Default.CheckCircle else nodeIcon(node.type),
                        contentDescription = node.type.displayName,
                        tint = if (completed) EmeraldHeal else accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = if (isCurrent) "ĐÂY" else node.type.shortCode,
                    style = fantasyHeadingStyle(
                        fontSize = 9.5.sp,
                        color = if (available) HextechGoldBright else accent
                    )
                )
            }
            Text(
                text = node.title,
                style = fantasyHeadingStyle(
                    fontSize = 11.sp,
                    color = if (available || completed) ParchmentWhite else MutedSilver
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.type == NodeType.COMBAT || node.type == NodeType.ELITE_COMBAT ||
                node.type == NodeType.MID_BOSS || node.type == NodeType.FINAL_BOSS
            ) {
                Text(
                    text = "${node.enemyMaxHp} Máu • +${node.goldReward} Vàng",
                    style = tabularStatStyle(10.sp, if (available) HextechGoldBright else MutedSilver),
                    maxLines = 1
                )
            } else {
                Text(
                    text = node.subtitle,
                    style = interBodyStyle(10.sp, MutedSilver),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun nodeIcon(type: NodeType): ImageVector = when (type) {
    NodeType.START -> Icons.Default.Flag
    NodeType.COMBAT -> Icons.Default.SportsKabaddi
    NodeType.ELITE_COMBAT -> Icons.Default.LocalFireDepartment
    NodeType.MID_BOSS, NodeType.FINAL_BOSS -> Icons.Default.LocalFireDepartment
    NodeType.POWER_NODE -> Icons.Default.Bolt
    NodeType.HEALER_NODE -> Icons.Default.Favorite
    NodeType.SHOP_NODE -> Icons.Default.ShoppingBag
    NodeType.REINFORCEMENT_NODE -> Icons.Default.GroupAdd
    NodeType.ITEM_CHEST -> Icons.Default.Inventory2
    NodeType.NARRATIVE_EVENT -> Icons.Default.QuestionMark
}

private fun nodeAccent(type: NodeType): Color = when (type) {
    NodeType.START -> ArcaneCyan
    NodeType.COMBAT -> LegendaryAmber
    NodeType.ELITE_COMBAT, NodeType.MID_BOSS, NodeType.FINAL_BOSS -> CrimsonNexus
    NodeType.POWER_NODE -> EpicViolet
    NodeType.HEALER_NODE -> EmeraldHeal
    NodeType.SHOP_NODE -> HextechGoldBright
    NodeType.REINFORCEMENT_NODE -> ArcaneCyan
    NodeType.ITEM_CHEST -> EpicViolet
    NodeType.NARRATIVE_EVENT -> HextechGold
}
