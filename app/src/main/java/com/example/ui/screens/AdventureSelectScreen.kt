package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorCarvedTopBar
import com.example.ui.components.LorMetalTone
import com.example.ui.components.LorMetallicPanel
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.interBodyStyle
import com.example.ui.components.regionAccentColor
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.HextechGoldDeep
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ParchmentWhite
import com.example.viewmodel.GameUiState

@Composable
fun AdventureSelectScreen(
    uiState: GameUiState,
    onStartAdventure: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val champ = CardCatalog.getChampion(uiState.selectedChampionId)

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionAccentColor(champ.region)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
            ) {
                LorCarvedTopBar(
                    title = "PHIÊU LƯU THẾ GIỚI",
                    subtitle = "Xuất quân cùng ${champ.name}",
                    onBack = onBack,
                    backTestTag = "adv_select_back_button",
                    leadingSlot = {
                        ChampionAvatarBadge(
                            championId = champ.id,
                            name = champ.name,
                            region = champ.region,
                            size = 38.dp
                        )
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(AdventureCatalog.adventures, key = { it.id }) { adv ->
                        val completed = uiState.legendProfile.completedAdventureIds.contains(adv.id)
                        val accent = regionAccentColor(adv.region)

                        LorMetallicPanel(
                            modifier = Modifier.fillMaxWidth(),
                            tone = if (completed) LorMetalTone.CUSTOM else LorMetalTone.GOLD,
                            customBorderColor = if (completed) EmeraldHeal else accent,
                            cornerRadius = 16.dp,
                            borderWidth = 1.8.dp,
                            filigreeAlpha = 0.16f,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianBg,
                                        border = BorderStroke(1.1.dp, accent)
                                    ) {
                                        Text(
                                            text = "${adv.difficultyLabel} • ${adv.region.displayName}".uppercase(),
                                            style = tabularStatStyle(10.5.sp, accent),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (completed) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = EmeraldHeal.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, EmeraldHeal)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Đã hoàn thành", tint = EmeraldHeal, modifier = Modifier.size(14.dp))
                                                Text("ĐÃ VƯỢT ẢI", style = fantasyHeadingStyle(10.sp, EmeraldHeal))
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = adv.name,
                                    style = fantasyHeadingStyle(fontSize = 18.sp, color = HextechGoldBright)
                                )
                                Text(
                                    text = adv.description,
                                    style = interBodyStyle(12.5.sp, MutedSilver)
                                )

                                HorizontalDivider(thickness = 0.8.dp, color = HextechGoldDeep.copy(alpha = 0.45f))

                                Text(
                                    text = "Trùm Cuối: ${adv.bossName}  •  Phần Thưởng: ${adv.rewardXp} XP, ${adv.rewardFragments} Mảnh",
                                    style = tabularStatStyle(11.5.sp, ParchmentWhite)
                                )

                                if (adv.mutators.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        adv.mutators.forEach { mut ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xD91A0D14))
                                                    .padding(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Warning, contentDescription = "Hiệu Ứng Biến Đổi", tint = CrimsonNexus, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        text = "${mut.name}: ${mut.description}",
                                                        style = interBodyStyle(11.5.sp, ParchmentWhite)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                LorBrassButton(
                                    text = "BẮT ĐẦU PHIÊU LƯU",
                                    onClick = { onStartAdventure(adv.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("start_adv_${adv.id}"),
                                    leadingIcon = {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkButtonInk, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
