package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.model.HandCardInstance
import com.example.model.UnitOnBoard
import com.example.ui.components.CardTile
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.EpicViolet
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ParchmentWhite
import com.example.ui.theme.SlateBorder

data class CombatEmoteOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

val defaultCombatEmotes = listOf(
    CombatEmoteOption("gg", "Đánh Hay Lắm!", Icons.Default.ThumbUp),
    CombatEmoteOption("watch", "Xem Này!", Icons.Default.Bolt),
    CombatEmoteOption("shield", "Giữ Vững Đội Hình!", Icons.Default.Shield),
    CombatEmoteOption("star", "Tính Toán Cả Đấy!", Icons.Default.Star),
    CombatEmoteOption("spark", "Bùm!", Icons.Default.AutoAwesome),
    CombatEmoteOption("cheer", "Trận Hay Quá!", Icons.Default.EmojiEmotions)
)

@Composable
fun HandCardInspectOverlay(
    card: HandCardInstance,
    canPlay: Boolean,
    effectiveCost: Int,
    detailOnly: Boolean,
    minWidth: Dp,
    maxWidth: Dp,
    buttonHeight: Dp,
    offsetY: Dp,
    padH: Dp,
    padV: Dp,
    descFontSize: TextUnit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(60f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = offsetY)
                .widthIn(min = minWidth, max = maxWidth)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.8.dp, if (canPlay) ArcaneCyan else HextechGold),
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = padH, vertical = padV),
                verticalArrangement = Arrangement.spacedBy(padV)
            ) {
                CardTile(
                    template = card.template,
                    effectiveCost = effectiveCost,
                    bonusPower = card.bonusPower,
                    bonusHealth = card.bonusHealth,
                    attachedItems = card.attachedItems,
                    extraKeywords = card.extraKeywords,
                    isFleeting = card.isFleeting,
                    playable = canPlay
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(padH)
                ) {
                    if (!detailOnly) {
                        Button(
                            enabled = canPlay,
                            onClick = onPlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight)
                                .testTag("confirm_play_card_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canPlay) ArcaneCyan else SlateBorder,
                                contentColor = DarkButtonInk
                            )
                        ) {
                            Text(
                                text = if (canPlay) "Ra Bài ($effectiveCost Năng lượng)" else "Không Thể Ra Bài",
                                fontSize = descFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(buttonHeight)
                    ) {
                        Text("Đóng", color = MutedSilver, fontSize = descFontSize)
                    }
                }
            }
        }
    }
}

@Composable
fun UnitInspectOverlay(
    unit: UnitOnBoard,
    sidePadding: Dp,
    minWidth: Dp,
    maxWidth: Dp,
    padH: Dp,
    padV: Dp,
    descFontSize: TextUnit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(65f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(if (unit.ownerIsPlayer) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = sidePadding)
                .widthIn(min = minWidth, max = maxWidth)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface.copy(alpha = 0.97f),
            border = BorderStroke(1.8.dp, HextechGoldBright),
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = padH, vertical = padV),
                verticalArrangement = Arrangement.spacedBy(padV * 0.8f)
            ) {
                CardTile(
                    template = unit.template,
                    effectiveCost = unit.template.cost,
                    bonusPower = unit.strikePower - unit.template.basePower,
                    bonusHealth = unit.health - unit.template.baseHealth,
                    attachedItems = unit.attachedItems,
                    extraKeywords = unit.keywords,
                    playable = true
                )
                if (unit.equippedGearName != null) {
                    Text(
                        text = "Trang bị: ${unit.equippedGearName}",
                        fontSize = descFontSize,
                        color = HextechGoldBright
                    )
                }
                if (unit.isStunned || unit.isFrostbitten || unit.hasBarrier || unit.hasSpellShield) {
                    val statuses = buildList {
                        if (unit.hasBarrier) add("Lá Chắn")
                        if (unit.hasSpellShield) add("Khiên Phép")
                        if (unit.isFrostbitten) add("Đóng Băng")
                        if (unit.isStunned) add("Choáng")
                    }
                    Text(
                        text = "Trạng thái: ${statuses.joinToString()}",
                        fontSize = descFontSize,
                        color = ArcaneCyan
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Đóng", color = HextechGoldBright, fontSize = descFontSize)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotePickerOverlay(
    safeStartPadding: Dp,
    bottomPadding: Dp,
    descFontSize: TextUnit,
    onSelectEmote: (CombatEmoteOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(62f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = safeStartPadding, bottom = bottomPadding)
                .widthIn(max = 320.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.5.dp, HextechGold),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Gửi Biểu Cảm",
                    fontSize = descFontSize,
                    fontWeight = FontWeight.Bold,
                    color = HextechGoldBright
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    defaultCombatEmotes.forEach { emote ->
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectEmote(emote) },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBg,
                            border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = emote.icon,
                                    contentDescription = emote.label,
                                    tint = HextechGoldBright,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = emote.label,
                                    fontSize = descFontSize,
                                    color = ParchmentWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraveyardDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "Nghĩa Địa (${combat.playerGraveyard.size + combat.enemyGraveyard.size})",
                color = HextechGoldBright
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = "Nghĩa Địa Của Bạn (${combat.playerGraveyard.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = ArcaneCyan
                    )
                }
                if (combat.playerGraveyard.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có đồng minh tử trận hoặc phép được thi triển.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSilver
                        )
                    }
                } else {
                    items(combat.playerGraveyard.reversed()) { card ->
                        Text(
                            text = "• ${card.name} (${card.type.displayName}, ${card.cost} NL)",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentWhite
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nghĩa Địa Kẻ Địch (${combat.enemyGraveyard.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = CrimsonNexus
                    )
                }
                if (combat.enemyGraveyard.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có kẻ địch bị tiêu diệt hoặc phép được thi triển.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSilver
                        )
                    }
                } else {
                    items(combat.enemyGraveyard.reversed()) { card ->
                        Text(
                            text = "• ${card.name} (${card.type.displayName}, ${card.cost} NL)",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentWhite
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = HextechGoldBright)
            }
        }
    )
}

@Composable
fun MatchInfoDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = "Trạng Thái Trận Đấu — Vòng ${combat.round}",
                color = HextechGoldBright
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = "${combat.encounterNode.enemyName} — ${combat.encounterNode.enemyPowerTitle}",
                        style = MaterialTheme.typography.titleSmall,
                        color = CrimsonNexus
                    )
                    Text(
                        text = combat.encounterNode.enemyPowerDescription.ifBlank { combat.encounterNode.subtitle },
                        style = MaterialTheme.typography.bodySmall,
                        color = ParchmentWhite
                    )
                }
                if (combat.activePowers.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = SlateBorder)
                        Text(
                            text = "Sức Mạnh Đang Kích Hoạt",
                            style = MaterialTheme.typography.titleSmall,
                            color = ArcaneCyan
                        )
                    }
                    items(combat.activePowers) { power ->
                        Text(
                            text = "• ${power.name}: ${power.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentWhite
                        )
                    }
                }
                if (combat.equippedRelics.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = SlateBorder)
                        Text(
                            text = "Cổ Vật Đã Trang Bị",
                            style = MaterialTheme.typography.titleSmall,
                            color = LegendaryAmber
                        )
                    }
                    items(combat.equippedRelics) { relic ->
                        Text(
                            text = "• ${relic.name}: ${relic.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentWhite
                        )
                    }
                }
                if (combat.mutators.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = SlateBorder)
                        Text(
                            text = "Hiệu Ứng Cuộc Phiêu Lưu",
                            style = MaterialTheme.typography.titleSmall,
                            color = EpicViolet
                        )
                    }
                    items(combat.mutators) { mut ->
                        Text(
                            text = "• ${mut.name}: ${mut.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentWhite
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = HextechGoldBright)
            }
        }
    )
}

@Composable
fun EnemyPowerDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = combat.encounterNode.enemyPowerTitle.ifBlank { combat.encounterNode.enemyName },
                color = CrimsonNexus
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = combat.encounterNode.enemyPowerDescription.ifBlank { combat.encounterNode.subtitle },
                    color = ParchmentWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (combat.mutators.isNotEmpty()) {
                    HorizontalDivider(color = SlateBorder)
                    combat.mutators.forEach { mut ->
                        Text(
                            text = "${mut.name}: ${mut.description}",
                            color = ArcaneCyan,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = HextechGoldBright)
            }
        }
    )
}

@Composable
fun CombatLogDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Nhật Ký Giao Tranh — Vòng ${combat.round}", color = HextechGoldBright) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(combat.combatLog.takeLast(16).reversed()) { entry ->
                    Text("• $entry", style = MaterialTheme.typography.bodySmall, color = ParchmentWhite)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = HextechGoldBright)
            }
        }
    )
}

@Composable
fun ForfeitConfirmDialog(
    onConfirmForfeit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Từ Bỏ Cuộc Phiêu Lưu?", color = CrimsonNexus) },
        text = { Text("Rời trận giữa chừng sẽ hủy bỏ hành trình hiện tại.", color = ParchmentWhite) },
        confirmButton = {
            Button(
                onClick = onConfirmForfeit,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonNexus)
            ) {
                Text("Đầu Hàng")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ở Lại", color = ArcaneCyan)
            }
        }
    )
}

@Composable
fun CombatGameOverDialog(
    combat: CombatState,
    revivesRemaining: Int,
    onVictoryContinue: () -> Unit,
    onUseRevive: () -> Unit,
    onAcceptDefeat: () -> Unit
) {
    if (combat.phase != CombatPhase.GAME_OVER) return
    val won = combat.playerWon == true
    AlertDialog(
        onDismissRequest = {},
        containerColor = ObsidianSurface,
        title = {
            Text(
                text = if (won) "CHIẾN THẮNG!" else "NHÀ CHÍNH BỊ PHÁ HỦY",
                style = MaterialTheme.typography.headlineMedium,
                color = if (won) HextechGoldBright else CrimsonNexus
            )
        },
        text = {
            Text(
                text = if (won) {
                    "Bạn đã đánh bại ${combat.encounterNode.enemyName} và nhận +${combat.encounterNode.goldReward} Vàng!"
                } else if (revivesRemaining > 0) {
                    "Nhà Chính đã sụp đổ, nhưng bạn còn $revivesRemaining Lần Hồi Sinh! Khôi phục 50% máu Nhà Chính và đấu lại trận này."
                } else {
                    "Nhà Chính đã sụp đổ và bạn không còn Lần Hồi Sinh nào."
                },
                color = ParchmentWhite,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            if (won) {
                Button(
                    onClick = onVictoryContinue,
                    modifier = Modifier.testTag("combat_victory_continue_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = HextechGold, contentColor = DarkButtonInk)
                ) {
                    Text("Nhận Phần Thưởng")
                }
            } else if (revivesRemaining > 0) {
                Button(
                    onClick = onUseRevive,
                    modifier = Modifier.testTag("use_revive_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldHeal, contentColor = DarkButtonInk)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hồi Sinh (50% Máu)")
                }
            } else {
                Button(
                    onClick = onAcceptDefeat,
                    modifier = Modifier.testTag("accept_defeat_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonNexus)
                ) {
                    Text("Kết Thúc")
                }
            }
        },
        dismissButton = {
            if (!won && revivesRemaining > 0) {
                TextButton(onClick = onAcceptDefeat) {
                    Text("Chấp Nhận Thua", color = MutedSilver)
                }
            }
        }
    )
}
