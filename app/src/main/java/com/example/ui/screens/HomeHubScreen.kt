package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.KeywordGlossaryDialog
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.LorSecondaryMetalButton
import com.example.ui.components.championPortraitRes
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.interBodyStyle
import com.example.ui.components.regionAccentColor
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.HextechGoldDeep
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ParchmentWhite
import com.example.ui.theme.SlateBorder
import com.example.viewmodel.GameUiState

@Composable
fun HomeHubScreen(
    uiState: GameUiState,
    onSelectChampion: (String) -> Unit,
    onUnlockChampion: (String) -> Unit,
    onOpenChampionDetail: (String) -> Unit,
    onOpenAdventures: () -> Unit,
    onQuickBattle: () -> Unit = {},
    onResumeRun: () -> Unit,
    onAbandonRun: () -> Unit,
    onGrantUnlimitedTestResources: () -> Unit,
    onOpenStretchDisplay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showGlossary by remember { mutableStateOf(false) }

    val selectedChamp = CardCatalog.findChampionOrNull(uiState.selectedChampionId)
        ?: CardCatalog.champions.firstOrNull()
        ?: return
    val selectedLoadout = uiState.championLoadouts[selectedChamp.id]
    val selectedAccent = regionAccentColor(selectedChamp.region)
    val selectedPortrait = championPortraitRes(selectedChamp.id)

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = selectedAccent
    ) {
        // Atmospheric Full-Bleed Champion Backdrop (no stacked bulky boxes)
        if (selectedPortrait != null) {
            Image(
                painter = painterResource(id = selectedPortrait),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xC80A111F), Color(0x880A111F), Color(0xF2070C17))
                        )
                    )
            )
        }

        val topCompactBar: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CON ĐƯỜNG ANH HÙNG",
                        style = fantasyHeadingStyle(15.sp, HextechGoldBright),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Huyền Thoại Cấp ${uiState.legendProfile.legendLevel} • ${uiState.legendProfile.wildFragments} Mảnh Vạn Năng • ${uiState.legendProfile.starCrystals} Tinh Thể Sao",
                            style = tabularStatStyle(10.5.sp, ArcaneCyan),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = {
                                (uiState.legendProfile.legendPoints.toFloat() /
                                    uiState.legendProfile.pointsToNextLevel.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .width(64.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = HextechGoldBright,
                            trackColor = ObsidianBg
                        )
                    }
                }

                // Sleek Compact Utility Icon Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactUtilityPill(
                        label = "∞",
                        tint = EmeraldHeal,
                        testTag = "grant_unlimited_resources_button",
                        onClick = onGrantUnlimitedTestResources
                    )
                    CompactUtilityPill(
                        icon = Icons.Default.SelfImprovement,
                        contentDescription = "Nghỉ Giãn Cơ",
                        tint = EmeraldHeal,
                        testTag = "open_stretch_button",
                        onClick = onOpenStretchDisplay
                    )
                    CompactUtilityPill(
                        icon = Icons.Default.AutoStories,
                        contentDescription = "Sổ Tay Từ Khóa",
                        tint = ArcaneCyan,
                        testTag = "open_glossary_button",
                        onClick = { showGlossary = true }
                    )
                }
            }
        }

        val savedRunBar: @Composable () -> Unit = {
            if (uiState.savedRun != null) {
                val savedChampName = CardCatalog.findChampionOrNull(uiState.savedRun.championId)?.name
                    ?: uiState.savedRun.championId
                val savedAdvName = AdventureCatalog.findAdventureOrNull(uiState.savedRun.adventureId)?.name
                    ?: uiState.savedRun.adventureId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xD8111F36))
                        .border(1.2.dp, ArcaneCyan.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HÀNH TRÌNH ĐANG CHƠI • $savedChampName ($savedAdvName)",
                            style = fantasyHeadingStyle(11.5.sp, ArcaneCyan),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Máu ${uiState.savedRun.currentNexusHp}/${uiState.savedRun.maxNexusHp} • ${uiState.savedRun.gold} Vàng • ${uiState.savedRun.battlesWon} Thắng",
                            style = tabularStatStyle(10.5.sp, MutedSilver)
                        )
                    }
                    Text(
                        text = "Bỏ",
                        style = fantasyHeadingStyle(11.sp, CrimsonNexus),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("abandon_saved_run_button")
                            .clickable(onClick = onAbandonRun)
                            .padding(horizontal = 6.dp, vertical = 12.dp)
                    )
                    LorBrassButton(
                        text = "TIẾP TỤC",
                        onClick = onResumeRun,
                        isCyanVariant = true,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("resume_run_button"),
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Tiếp tục", tint = DarkButtonInk, modifier = Modifier.size(15.dp))
                        }
                    )
                }
            }
        }

        val rosterStrip: @Composable () -> Unit = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(CardCatalog.champions, key = { it.id }) { champ ->
                    val loadout = uiState.championLoadouts[champ.id]
                    val isSelected = champ.id == uiState.selectedChampionId
                    val unlocked = loadout?.isUnlocked == true
                    val stars = loadout?.starLevel ?: 0

                    Column(
                        modifier = Modifier
                            .width(74.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("roster_champ_${champ.id}")
                            .clickable { onSelectChampion(champ.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            ChampionAvatarBadge(
                                championId = champ.id,
                                name = champ.name,
                                region = champ.region,
                                size = if (isSelected) 58.dp else 50.dp
                            )
                            Surface(
                                modifier = Modifier.offset(y = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) HextechGoldBright else if (unlocked) SlateBorder else CrimsonNexus
                                )
                            ) {
                                if (unlocked) {
                                    Text(
                                        text = "${stars}★",
                                        style = tabularStatStyle(9.5.sp, if (isSelected) HextechGoldBright else Color(0xFFFF7A45)),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.5.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Chưa mở khóa",
                                        tint = CrimsonNexus,
                                        modifier = Modifier.padding(3.dp).size(11.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = champ.name,
                            style = fantasyHeadingStyle(
                                fontSize = 11.sp,
                                color = if (isSelected) HextechGoldBright else MutedSilver
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val championShowcase: @Composable (Boolean) -> Unit = { compactWide ->
            if (selectedLoadout != null) {
                val activeStarPower = selectedChamp.starPowers.lastOrNull { it.starLevel <= selectedLoadout.starLevel }
                val equippedCount = selectedLoadout.equippedRelics.count { it != null }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Open Clean Champion Header (No nested card-inside-card!)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChampionAvatarBadge(
                            championId = selectedChamp.id,
                            name = selectedChamp.name,
                            region = selectedChamp.region,
                            size = 50.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedChamp.name.uppercase(),
                                style = fantasyHeadingStyle(18.sp, HextechGoldBright),
                                maxLines = 1
                            )
                            Text(
                                text = "${selectedChamp.title} • Cấp ${selectedLoadout.championLevel}",
                                style = interBodyStyle(11.5.sp, selectedAccent),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                for (star in 1..6) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (star <= selectedLoadout.starLevel) Color(0xFFFF7A45) else Color(0xFF474E5E),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        if (compactWide) {
                            LorSecondaryMetalButton(
                                text = "SAO & BỘ BÀI",
                                onClick = { onOpenChampionDetail(selectedChamp.id) },
                                accentColor = HextechGoldBright,
                                modifier = Modifier.testTag("champion_mastery_button"),
                                leadingIcon = {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = HextechGoldBright, modifier = Modifier.size(15.dp))
                                }
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.8.dp, color = HextechGoldDeep.copy(alpha = 0.55f))

                    // Clean 1-line Stats & Star Power Summary (Flat-glass strip, zero bloat)
                    Text(
                        text = "Máu ${selectedLoadout.maxNexusHp}  •  +${selectedLoadout.victoryHeal}/Trận Thắng  •  ${selectedLoadout.startingGold} Vàng  •  ${selectedLoadout.startingRerolls} Đổi Lại  •  Cổ Vật $equippedCount/${selectedLoadout.unlockedRelicSlots}",
                        style = tabularStatStyle(11.sp, ParchmentWhite),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (activeStarPower != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xB80E182B))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${activeStarPower.starLevel}★ ${activeStarPower.title.uppercase()}",
                                style = fantasyHeadingStyle(11.sp, HextechGoldBright)
                            )
                            Text(
                                text = activeStarPower.description,
                                style = interBodyStyle(11.sp, MutedSilver),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!compactWide) {
                        LorSecondaryMetalButton(
                            text = "CHÒM SAO, BỘ BÀI & CỔ VẬT ($equippedCount/${selectedLoadout.unlockedRelicSlots})",
                            onClick = { onOpenChampionDetail(selectedChamp.id) },
                            accentColor = HextechGoldBright,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("champion_mastery_button"),
                            leadingIcon = {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = HextechGoldBright, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    if (selectedLoadout.isUnlocked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LorBrassButton(
                                text = "PHIÊU LƯU",
                                onClick = onOpenAdventures,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("embark_adventure_button"),
                                leadingIcon = {
                                    Icon(Icons.Default.Explore, contentDescription = "Phiêu Lưu", tint = DarkButtonInk, modifier = Modifier.size(18.dp))
                                }
                            )
                            LorBrassButton(
                                text = "ĐẤU NHANH",
                                onClick = onQuickBattle,
                                isCyanVariant = true,
                                fontSize = 11.5.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_battle_button"),
                                leadingIcon = {
                                    Icon(Icons.Default.SportsKabaddi, contentDescription = "Đấu Nhanh", tint = DarkButtonInk, modifier = Modifier.size(17.dp))
                                }
                            )
                        }
                    } else {
                        LorBrassButton(
                            text = "MỞ KHÓA TƯỚNG (${selectedChamp.unlockFragmentCost} MẢNH)",
                            onClick = { onUnlockChampion(selectedChamp.id) },
                            isCyanVariant = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_champion_button"),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = "Mở khóa", tint = DarkButtonInk, modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (maxWidth >= 600.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1080.dp)
                ) {
                    topCompactBar()
                    HorizontalDivider(thickness = 1.dp, color = HextechGoldDeep.copy(alpha = 0.65f))
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            savedRunBar()
                            Text(
                                text = "DANH SÁCH TƯỚNG",
                                style = fantasyHeadingStyle(12.sp, HextechGoldBright)
                            )
                            rosterStrip()
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Top
                        ) {
                            championShowcase(true)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        topCompactBar()
                        HorizontalDivider(thickness = 1.dp, color = HextechGoldDeep.copy(alpha = 0.65f))
                    }
                    if (uiState.savedRun != null) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                savedRunBar()
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DANH SÁCH TƯỚNG",
                                style = fantasyHeadingStyle(12.sp, HextechGoldBright)
                            )
                            rosterStrip()
                        }
                    }
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            championShowcase(false)
                        }
                    }
                }
            }
        }
    }

    if (showGlossary) {
        KeywordGlossaryDialog(onDismiss = { showGlossary = false })
    }
}

@Composable
private fun CompactUtilityPill(
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = Color(0xCC0D1626),
            border = BorderStroke(1.1.dp, tint.copy(alpha = 0.7f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (label != null) {
                    Text(
                        text = label,
                        style = tabularStatStyle(14.sp, tint)
                    )
                }
            }
        }
    }
}
