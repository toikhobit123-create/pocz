package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardCatalog
import com.example.model.CardTemplate
import com.example.model.ChampionLoadout
import com.example.model.ChampionTemplate
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.ui.components.CardTile
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.HandCardView
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.LorSecondaryMetalButton
import com.example.ui.components.RelicSlotCard
import com.example.ui.components.cardPortraitRes
import com.example.ui.components.championPortraitRes
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.interBodyStyle
import com.example.ui.components.rarityColor
import com.example.ui.components.regionAccentColor
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.EpicViolet
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.HextechGoldDeep
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.ManaBlue
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ParchmentWhite
import com.example.ui.theme.SlateBorder
import com.example.viewmodel.GameUiState

private enum class ChampionSection(val label: String) {
    OVERVIEW("TỔNG QUAN"),
    CONSTELLATION("CHÒM SAO"),
    STARTER_DECK("BỘ BÀI KHỞI ĐẦU"),
    RELICS("CỔ VẬT")
}

@Composable
fun ChampionDetailScreen(
    championId: String,
    uiState: GameUiState,
    onUpgradeStar: (String, Int) -> Unit,
    onUnlockConstellation: (String, String) -> Unit,
    onEquipRelic: (String, Int, String?) -> Unit,
    onCosmicForge: (String) -> Unit,
    onMaxChampionForTesting: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val champion = CardCatalog.findChampionOrNull(championId)
        ?: CardCatalog.champions.firstOrNull()
        ?: return
    val loadout = uiState.championLoadouts[champion.id]
        ?: uiState.championLoadouts[championId]
        ?: return
    val regionAccent = regionAccentColor(champion.region)
    val splashRes = championPortraitRes(champion.id)

    var selectedSection by remember { mutableStateOf(ChampionSection.CONSTELLATION) }
    var constellationSubTab by remember { mutableIntStateOf(0) } // 0 = Star Powers, 1 = Bonus Stars
    var relicPickerSlot by remember { mutableStateOf<Int?>(null) }
    var previewDeckCard by remember { mutableStateOf<CardTemplate?>(null) }

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionAccent
    ) {
        // Subtle Full-Bleed Champion Splash Art Atmosphere (like official LoR Path of Champions)
        if (splashRes != null) {
            Image(
                painter = painterResource(id = splashRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xB80A111F), Color(0x8A0A111F), Color(0xEE070C17))
                        )
                    )
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1120.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Sleek Top Header: Back Circle + Avatar w/ Level Badge + Name + 6 Stars + Compact Utility Pill
                ChampionDetailHeader(
                    champion = champion,
                    loadout = loadout,
                    onBack = onBack,
                    onMaxForTesting = { onMaxChampionForTesting(championId) }
                )

                if (isWide) {
                    // WIDE / LANDSCAPE LAYOUT: Left Vertical Menu | Center Clean Content | Right Champion Card + 3 Hex Relic Sockets
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Vertical Menu Rail (No boxy cards, glowing diamond active indicator)
                        Column(
                            modifier = Modifier
                                .width(156.dp)
                                .fillMaxHeight()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ChampionSection.entries.forEach { section ->
                                val active = selectedSection == section
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("champ_section_${section.name.lowercase()}")
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { selectedSection = section }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (active) HextechGoldBright else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (active) HextechGoldBright else HextechGoldDeep.copy(alpha = 0.55f),
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Text(
                                        text = section.label,
                                        style = fantasyHeadingStyle(
                                            fontSize = 12.sp,
                                            color = if (active) ParchmentWhite else Color(0xFFB3A17B)
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Center Active Pane (Star Powers / Bonus Stars / Slim Deck Strips / Overview / Relics)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ChampionSectionContent(
                                section = selectedSection,
                                constellationSubTab = constellationSubTab,
                                onConstellationSubTabChange = { constellationSubTab = it },
                                champion = champion,
                                loadout = loadout,
                                onUpgradeStar = { onUpgradeStar(championId, it) },
                                onUnlockConstellation = { onUnlockConstellation(championId, it) },
                                onOpenRelicPicker = { relicPickerSlot = it },
                                onCosmicForge = { onCosmicForge(championId) },
                                onInspectDeckCard = { previewDeckCard = it }
                            )
                        }

                        // Right Showcase: Hero Champion Card + 3 Vertical Relic Sockets Docked Beside It (Screenshot 2!)
                        ChampionCardWithRelicSockets(
                            champion = champion,
                            loadout = loadout,
                            onRelicSlotClick = { relicPickerSlot = it },
                            modifier = Modifier
                                .width(222.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                        )
                    }
                } else {
                    // COMPACT PORTRAIT LAYOUT: Underline Tabs + Clean Flat-Glass Content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChampionSection.entries.forEach { section ->
                            val active = selectedSection == section
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("champ_section_${section.name.lowercase()}")
                                    .clickable { selectedSection = section }
                                    .padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when (section) {
                                        ChampionSection.CONSTELLATION -> "CHÒM SAO"
                                        ChampionSection.STARTER_DECK -> "BỘ BÀI"
                                        else -> section.label
                                    },
                                    style = fantasyHeadingStyle(
                                        fontSize = 11.sp,
                                        color = if (active) HextechGoldBright else MutedSilver
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (active) 2.5.dp else 1.dp)
                                        .background(if (active) HextechGoldBright else SlateBorder.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        ChampionSectionContent(
                            section = selectedSection,
                            constellationSubTab = constellationSubTab,
                            onConstellationSubTabChange = { constellationSubTab = it },
                            champion = champion,
                            loadout = loadout,
                            onUpgradeStar = { onUpgradeStar(championId, it) },
                            onUnlockConstellation = { onUnlockConstellation(championId, it) },
                            onOpenRelicPicker = { relicPickerSlot = it },
                            onCosmicForge = { onCosmicForge(championId) },
                            onInspectDeckCard = { previewDeckCard = it }
                        )
                    }
                }
            }
        }
    }

    previewDeckCard?.let { card ->
        AlertDialog(
            onDismissRequest = { previewDeckCard = null },
            containerColor = ObsidianSurface,
            title = null,
            text = { CardTile(template = card) },
            confirmButton = {
                TextButton(onClick = { previewDeckCard = null }) {
                    Text("Đóng", color = HextechGoldBright)
                }
            }
        )
    }

    relicPickerSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { relicPickerSlot = null },
            containerColor = ObsidianSurface,
            title = {
                Text("Trang Bị Cổ Vật — Ô ${slot + 1}", style = fantasyHeadingStyle(16.sp, HextechGoldBright))
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedButton(
                            onClick = {
                                onEquipRelic(championId, slot, null)
                                relicPickerSlot = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tháo Cổ Vật", color = MutedSilver)
                        }
                    }
                    items(CardCatalog.relics, key = { it.id }) { relic ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    onEquipRelic(championId, slot, relic.id)
                                    relicPickerSlot = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, rarityColor(relic.rarity))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${relic.name} (${relic.rarity.displayName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = rarityColor(relic.rarity)
                                )
                                Text(
                                    text = relic.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedSilver
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { relicPickerSlot = null }) {
                    Text("Hủy", color = HextechGoldBright)
                }
            }
        )
    }
}

@Composable
private fun ChampionDetailHeader(
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    onBack: () -> Unit,
    onMaxForTesting: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular Gold-Rimmed Back Medallion
        Surface(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
                .testTag("champ_detail_back_button")
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = Color(0xCC0D1626),
            border = BorderStroke(1.4.dp, HextechGold.copy(alpha = 0.8f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = ParchmentWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Avatar with tucked Level Badge
        Box(contentAlignment = Alignment.BottomCenter) {
            ChampionAvatarBadge(
                championId = champion.id,
                name = champion.name,
                region = champion.region,
                size = 50.dp
            )
            Surface(
                modifier = Modifier.offset(y = 4.dp),
                shape = CircleShape,
                color = ObsidianBg,
                border = BorderStroke(1.2.dp, HextechGold)
            ) {
                Text(
                    text = "${loadout.championLevel}",
                    style = tabularStatStyle(9.5.sp, ParchmentWhite),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Champion Name + Title + 6 Sleek Stars
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = champion.name.uppercase(),
                    style = fantasyHeadingStyle(16.sp, HextechGoldBright),
                    maxLines = 1
                )
                Text(
                    text = "• ${loadout.fragments} Mảnh",
                    style = tabularStatStyle(11.sp, ArcaneCyan),
                    maxLines = 1
                )
            }
            Text(
                text = champion.title.uppercase(),
                style = interBodyStyle(10.5.sp, MutedSilver),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (star in 1..6) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (star <= loadout.starLevel) Color(0xFFFF7A45) else Color(0xFF474E5E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Compact Testing Max Pill (top-right, low visual footprint)
        LorSecondaryMetalButton(
            text = "Tối Đa 30★",
            onClick = onMaxForTesting,
            accentColor = EmeraldHeal,
            modifier = Modifier
                .height(36.dp)
                .testTag("max_champ_test_button")
        )
    }
}

@Composable
private fun ChampionSectionContent(
    section: ChampionSection,
    constellationSubTab: Int,
    onConstellationSubTabChange: (Int) -> Unit,
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    onUpgradeStar: (Int) -> Unit,
    onUnlockConstellation: (String) -> Unit,
    onOpenRelicPicker: (Int) -> Unit,
    onCosmicForge: () -> Unit,
    onInspectDeckCard: (CardTemplate) -> Unit
) {
    when (section) {
        ChampionSection.OVERVIEW -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = champion.archetypeSummary,
                    style = interBodyStyle(13.sp, ParchmentWhite)
                )

                // Clean Single-Row Run Baseline Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xC8111A2B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactStat("MÁU NHÀ CHÍNH", "${loadout.maxNexusHp}", ParchmentWhite)
                    CompactStat("HỒI / THẮNG", "+${loadout.victoryHeal}", EmeraldHeal)
                    CompactStat("VÀNG", "${loadout.startingGold}", HextechGoldBright)
                    CompactStat("ĐỔI LẠI", "${loadout.startingRerolls}", ArcaneCyan)
                }

                val odds = loadout.rarityOdds
                val pct = { v: Double -> String.format(java.util.Locale.US, "%.0f%%", v * 100.0) }
                Text(
                    text = "Tỉ Lệ Chọn — Thường ${pct(odds.common)} • Hiếm ${pct(odds.rare)} • Sử Thi ${pct(odds.epic)} • Huyền Thoại ${pct(odds.legendary)}",
                    style = interBodyStyle(11.sp, MutedSilver)
                )

                val unlockedPowers = champion.starPowers.filter { it.starLevel <= loadout.starLevel }
                if (unlockedPowers.isNotEmpty()) {
                    Text(
                        text = "SỨC MẠNH SAO ĐANG KÍCH HOẠT",
                        style = fantasyHeadingStyle(12.sp, HextechGoldBright),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    unlockedPowers.forEach { spec ->
                        StarPowerFlatRow(
                            title = "${spec.starLevel}★ ${spec.title}",
                            description = spec.description,
                            unlocked = true,
                            actionLabel = null,
                            costText = null,
                            actionTestTag = null,
                            onAction = null
                        )
                    }
                }
            }
        }

        ChampionSection.CONSTELLATION -> {
            // Matching Screenshot 2: Chòm Sao Của <Tướng> & subtabs SỨC MẠNH SAO | SAO THƯỞNG
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Chòm Sao Của ${champion.name}",
                    style = fantasyHeadingStyle(13.sp, HextechGoldBright),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("SỨC MẠNH SAO", "DANH SÁCH NÚT").forEachIndexed { idx, label ->
                        val selected = constellationSubTab == idx
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag(if (idx == 0) "tab_star_powers" else "tab_bonus_stars")
                                .clickable { onConstellationSubTabChange(idx) }
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                style = fantasyHeadingStyle(
                                    fontSize = 11.5.sp,
                                    color = if (selected) ParchmentWhite else Color(0xFFA08D6A)
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (selected) 2.5.dp else 1.dp)
                                    .background(if (selected) HextechGold else Color(0x448C9BB0))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (constellationSubTab == 0) {
                    val nextStarLevel = champion.nextStarPower(loadout.starLevel)?.starLevel
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(champion.starPowers, key = { it.starLevel }) { spec ->
                            val unlocked = loadout.starLevel >= spec.starLevel
                            val isNext = loadout.isUnlocked && nextStarLevel == spec.starLevel
                            val costText = if (!unlocked) {
                                buildString {
                                    append("Yêu cầu: ${spec.fragmentCost} Mảnh tướng")
                                    if (spec.starCrystalCost > 0) append(" • ${spec.starCrystalCost} Tinh Thể Sao")
                                    if (spec.novaCrystalCost > 0) append(" • ${spec.novaCrystalCost} Tinh Thể Nova")
                                }
                            } else null

                            StarPowerFlatRow(
                                title = "${spec.starLevel}★ ${spec.title}",
                                description = spec.description,
                                unlocked = unlocked,
                                actionLabel = if (isNext) "Nâng Cấp" else null,
                                costText = costText,
                                actionTestTag = if (isNext) "upgrade_star_${spec.starLevel}" else null,
                                onAction = if (isNext) { { onUpgradeStar(spec.starLevel) } } else null
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(champion.constellationNodes, key = { it.id }) { node ->
                            val unlocked = loadout.unlockedConstellationNodes.contains(node.id)
                            StarPowerFlatRow(
                                title = node.title,
                                description = node.description,
                                unlocked = unlocked,
                                actionLabel = if (!unlocked) "Mở Khóa" else null,
                                costText = if (!unlocked) "Yêu cầu: ${node.fragmentCost} Mảnh tướng • ${node.gemstoneCost} Đá Quý" else null,
                                actionTestTag = if (!unlocked) "unlock_node_${node.id}" else null,
                                onAction = if (!unlocked) { { onUnlockConstellation(node.id) } } else null
                            )
                        }
                    }
                }
            }
        }

        ChampionSection.STARTER_DECK -> {
            // Matching Screenshot 1: Bộ bài khởi đầu + Slim 1-Row Card Strips
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Bộ bài khởi đầu",
                    style = fantasyHeadingStyle(13.sp, HextechGoldBright),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(champion.baseDeck, key = { it.cardId }) { entry ->
                        val card = CardCatalog.getCard(entry.cardId)
                        SlimDeckCardStrip(
                            entry = entry,
                            card = card,
                            onClick = { onInspectDeckCard(card) }
                        )
                    }
                }
            }
        }

        ChampionSection.RELICS -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { idx ->
                    RelicSlotCard(
                        slotIndex = idx,
                        relicId = loadout.equippedRelics.getOrNull(idx),
                        unlocked = idx < loadout.unlockedRelicSlots,
                        isCosmicForged = idx < loadout.cosmicForgedSlots,
                        onClick = { onOpenRelicPicker(idx) }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LorBrassButton(
                    text = "RÈN VŨ TRỤ Ô SỬ THI (15 ĐÁ QUÝ)",
                    onClick = onCosmicForge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cosmic_forge_button"),
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Rèn Vũ Trụ", tint = DarkButtonInk, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

/**
 * Clean translucent dark-glass Star Power / Bonus Star row matching Screenshot 1 (no thick card borders).
 */
@Composable
private fun StarPowerFlatRow(
    title: String,
    description: String,
    unlocked: Boolean,
    actionLabel: String?,
    costText: String?,
    actionTestTag: String?,
    onAction: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (unlocked) Color(0xCC121D30) else Color(0x990D1422))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Circular Gold-Ringed Medallion Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (unlocked) {
                                listOf(ManaBlue.copy(alpha = 0.55f), Color(0xFF0E1B30))
                            } else {
                                listOf(Color(0xFF232C3D), Color(0xFF0B111C))
                            }
                        )
                    )
                    .border(
                        1.5.dp,
                        if (unlocked) HextechGoldBright else SlateBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (unlocked) Icons.Default.Star else Icons.Default.Lock,
                    contentDescription = title,
                    tint = if (unlocked) HextechGoldBright else MutedSilver.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = fantasyHeadingStyle(
                        fontSize = 12.5.sp,
                        color = if (unlocked) HextechGoldBright else ParchmentWhite
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = interBodyStyle(11.5.sp, if (unlocked) ParchmentWhite else MutedSilver),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (costText != null) {
                    Text(
                        text = costText,
                        style = tabularStatStyle(10.sp, ArcaneCyan)
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier)
                        .clickable(onClick = onAction),
                    shape = CircleShape,
                    color = Color(0xFF152946),
                    border = BorderStroke(1.3.dp, ArcaneCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = actionLabel,
                            style = fantasyHeadingStyle(10.5.sp, ArcaneCyan)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = actionLabel,
                            tint = ArcaneCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.7.dp, color = SlateBorder.copy(alpha = 0.45f))
    }
}

/**
 * Slim 1-Row Starter Deck Card Strip matching Screenshot 2:
 * Glowing Mana gem left | Card Name + cropped art slice | Copy count badge right.
 */
@Composable
private fun SlimDeckCardStrip(
    entry: DeckCardEntry,
    card: CardTemplate,
    onClick: () -> Unit
) {
    val artRes = cardPortraitRes(card.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .testTag("deck_strip_${entry.cardId}")
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xDD111B2E))
            .border(
                width = 1.dp,
                color = if (card.isChampion) HextechGold else SlateBorder.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cropped card artwork on the right 55% fading smoothly into dark navy on the left
            if (artRes != null) {
                Image(
                    painter = painterResource(id = artRes),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    alpha = 0.65f,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f)
                        .align(Alignment.CenterEnd)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF101A2C), Color(0xEE101A2C), Color(0x44101A2C))
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glowing Blue Mana Gem Circle
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF93E8FF), ManaBlue, Color(0xFF0A4A8A))
                            )
                        )
                        .border(1.2.dp, Color(0xFFE0F7FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${card.cost}",
                        style = tabularStatStyle(11.5.sp, ParchmentWhite)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = card.name,
                    style = fantasyHeadingStyle(
                        fontSize = 12.sp,
                        color = if (card.isChampion) HextechGoldBright else ParchmentWhite
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (entry.attachedItemIds.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Vật phẩm đã gắn",
                        tint = ArcaneCyan,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xE6090F1A),
                    border = BorderStroke(0.8.dp, HextechGold.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "x${entry.copies}",
                        style = tabularStatStyle(10.5.sp, HextechGoldBright),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Right Showcase Panel matching Screenshot 2: Champion Card + 3 Vertical Relic Hex Sockets beside it.
 */
@Composable
private fun ChampionCardWithRelicSockets(
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    onRelicSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val champCard = remember(champion.cardId) { CardCatalog.getCard(champion.cardId) }
    val previewHandCard = remember(champCard) {
        HandCardInstance(
            instanceId = "preview_${champCard.id}",
            template = champCard,
            effectiveCost = champCard.cost
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        HandCardView(
            handCard = previewHandCard,
            effectiveCost = champCard.cost,
            playable = true,
            isFocused = false,
            cardWidth = 148.dp,
            cardHeight = 216.dp,
            descFontSize = 11.sp,
            statFontSize = 15.sp,
            onClick = {}
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) { idx ->
                val unlocked = idx < loadout.unlockedRelicSlots
                val relicId = loadout.equippedRelics.getOrNull(idx)
                val relic = relicId?.let { CardCatalog.findRelicOrNull(it) }
                val isEpicForged = idx < loadout.cosmicForgedSlots
                val borderColor = when {
                    !unlocked -> SlateBorder
                    isEpicForged -> EpicViolet
                    relic != null -> rarityColor(relic.rarity)
                    else -> HextechGold
                }

                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("relic_socket_$idx")
                        .clickable(enabled = unlocked) { onRelicSlotClick(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (unlocked) Color(0xE6142138) else Color(0xCC0B111C),
                    border = BorderStroke(1.6.dp, borderColor),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                !unlocked -> Icons.Default.Lock
                                relic != null -> Icons.Default.Check
                                else -> Icons.Default.Shield
                            },
                            contentDescription = relic?.name ?: if (unlocked) "Ô Cổ Vật Trống" else "Ô Bị Khóa",
                            tint = borderColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = fantasyHeadingStyle(9.sp, MutedSilver)
        )
        Text(
            text = value,
            style = tabularStatStyle(13.sp, color)
        )
    }
}
