package com.example.engine

import com.example.model.AdventureConfig
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.ChampionLoadout
import com.example.model.HandCardInstance
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.MapNode
import com.example.model.NodeType
import com.example.model.PowerTemplate
import com.example.model.RelicTemplate
import com.example.model.RunState
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object CombatEngine {

    internal const val EMPTY_DECK_FATIGUE_DAMAGE = 2

    private val roundHallowedBuffs = ConcurrentHashMap<String, Int>()

    private fun hallowedKey(encounterId: String, round: Int, unitId: String): String =
        "$encounterId:$round:$unitId"

    private fun nextId(): String = UUID.randomUUID().toString().take(8)

    internal fun canonicalEnemyPowerKey(title: String): String = when (title) {
        "Ready Ranks", "Sẵn Sàng Đội Hình" -> "READY_RANKS"
        "Stone Ward", "Phòng Hộ Đá" -> "STONE_WARD"
        "Prismatic Shield", "Khiên Lăng Kính" -> "PRISMATIC_SHIELD"
        "For Demacia!", "Vì Demacia!" -> "FOR_DEMACIA"
        "Unseen Path", "Lối Đi Vô Hình" -> "UNSEEN_PATH"
        "Tempest Training", "Huấn Luyện Cuồng Phong" -> "TEMPEST_TRAINING"
        "Living Shadow", "Phân Thân Bóng Tối" -> "LIVING_SHADOW"
        "Enlightened Harmony", "Hòa Hợp Khai Sáng" -> "ENLIGHTENED_HARMONY"
        "Volatile Munitions", "Đạn Dược Bất Ổn" -> "VOLATILE_MUNITIONS"
        "Hextech Upgrade", "Nâng Cấp Hextech" -> "HEXTECH_UPGRADE"
        "Rocket Barrage", "Mưa Tên Lửa" -> "ROCKET_BARRAGE"
        "Glorious Evolution", "Tiến Hóa Huy Hoàng" -> "GLORIOUS_EVOLUTION"
        "Blazing Dawn", "Bình Minh Rực Cháy" -> "BLAZING_DAWN"
        "Undying Terror", "Nỗi Kinh Hoàng Bất Tử" -> "UNDYING_TERROR"
        "Endless Winter", "Mùa Đông Vô Tận" -> "ENDLESS_WINTER"
        "Star Forger", "Đấng Sáng Tạo Sao" -> "STAR_FORGER"
        else -> title
    }

    fun createEncounter(
        runState: RunState,
        loadout: ChampionLoadout,
        adventure: AdventureConfig,
        node: MapNode
    ): CombatState {
        val powers = runState.activePowerIds.mapNotNull { id ->
            CardCatalog.findPowerOrNull(id) ?: run { CardCatalog.getPower(id); null }
        }
        val relics = loadout.equippedRelics.mapNotNull { id ->
            id?.let { CardCatalog.findRelicOrNull(it) ?: run { CardCatalog.getRelic(it); null } }
        }

        // Expand RunState.runDeck into individual HandCardInstances
        val expandedDeck = mutableListOf<HandCardInstance>()
        runState.runDeck.forEach { entry ->
            val tmpl = CardCatalog.findCardOrNull(entry.cardId) ?: run {
                CardCatalog.getCard(entry.cardId)
                return@forEach
            }
            val items = entry.attachedItemIds.mapNotNull { itemId ->
                CardCatalog.findItemOrNull(itemId) ?: run { CardCatalog.getItem(itemId); null }
            }
            val costRed = items.sumOf { it.costReduction }
            val bonusPow = items.sumOf { it.bonusPower }
            val bonusHp = items.sumOf { it.bonusHealth }
            val itemKeywords = items.mapNotNull { it.grantedKeyword }.toSet()

            repeat(entry.copies) {
                expandedDeck.add(
                    HandCardInstance(
                        instanceId = "hc_${nextId()}",
                        template = tmpl,
                        attachedItems = items,
                        effectiveCost = maxOf(0, tmpl.cost - costRed),
                        bonusPower = bonusPow,
                        bonusHealth = bonusHp,
                        extraKeywords = itemKeywords,
                        isFleeting = tmpl.keywords.contains(Keyword.FLEETING)
                    )
                )
            }
        }
        expandedDeck.shuffle()

        // Farsight Alteration: pull Farsight cards to top so they are drawn in opening hand
        val farsights = expandedDeck.filter { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val others = expandedDeck.filterNot { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val orderedDeck = farsights + others

        val openingHand = orderedDeck.take(4)
        val remainingDeck = orderedDeck.drop(4)

        val startingMana = (1 +
            loadout.startingManaBonus +
            (if (loadout.starLevel >= 5) 1 else 0) +
            relics.sumOf { it.startingManaBonus }).coerceIn(1, 10)

        val startingSpellMana = if (powers.any { it.id == "pow_sorcery" }) 3 else 0
        val celestialStartMana = if (adventure.mutators.any { it.id == "mut_celestial_might" }) 4 else 1
        val enemyStartMana = maxOf(1, node.enemyStartingMana, celestialStartMana).coerceIn(1, 10)

        val enemyPool = node.enemyDeckCardIds.mapNotNull { id ->
            CardCatalog.findCardOrNull(id) ?: run { CardCatalog.getCard(id); null }
        }.ifEmpty { listOf(CardCatalog.getCard("card_cithria")) }
        val openingEnemyHand = List(4) { enemyPool.random() }

        val initialLog = mutableListOf(
            "Chạm trán: ${node.title} vs ${node.enemyName}",
            "Năng Lượng Khởi Điểm: Bạn $startingMana | Địch $enemyStartMana"
        )
        if (adventure.mutators.any { it.id == "mut_celestial_might" }) {
            initialLog.add("Hiệu Ứng [Hiểm Họa Vũ Trụ]: Địch khởi đầu với $enemyStartMana Năng Lượng!")
        }
        if (node.enemyPowerTitle.isNotBlank()) {
            initialLog.add("Sức Mạnh Địch [${node.enemyPowerTitle}]: ${node.enemyPowerDescription}")
        }

        val enemyPowerKey = canonicalEnemyPowerKey(node.enemyPowerTitle)
        val effectiveEnemyMaxHp = node.enemyMaxHp + (if (enemyPowerKey == "STONE_WARD") 5 else 0)
        val initialEnemyHallowed = if (enemyPowerKey == "UNDYING_TERROR") 2 else 0

        var state = CombatState(
            encounterNode = node,
            adventure = adventure,
            championId = loadout.championId,
            starLevel = loadout.starLevel,
            equippedRelics = relics,
            activePowers = powers,
            mutators = adventure.mutators,
            round = 1,
            playerNexusHp = runState.currentNexusHp,
            playerMaxHp = runState.maxNexusHp,
            enemyNexusHp = effectiveEnemyMaxHp,
            enemyMaxHp = effectiveEnemyMaxHp,
            playerMana = startingMana,
            playerMaxMana = startingMana,
            playerSpellMana = startingSpellMana,
            enemyMana = enemyStartMana,
            enemyMaxMana = enemyStartMana,
            enemySpellMana = 0,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            enemyHallowedStacks = initialEnemyHallowed,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0,
            playerHand = openingHand,
            playerDrawPile = remainingDeck,
            enemyHand = openingEnemyHand,
            enemyDrawPool = enemyPool,
            combatLog = initialLog
        )

        roundHallowedBuffs.keys.removeIf { it.startsWith("${node.id}:") }

        // Apply Game Start / Round 1 Star Powers & Powers
        state = applyRoundStartHooks(state, isGameStart = true)
        return state
    }

    private fun applyRoundStartHooks(state: CombatState, isGameStart: Boolean): CombatState {
        var s = state
        val logs = s.combatLog.toMutableList()

        // Mutator: Spirit Breeze
        if (s.mutators.any { it.id == "mut_spirit_breeze" }) {
            s = s.copy(
                playerSpellMana = minOf(3, s.playerSpellMana + 1),
                enemySpellMana = minOf(3, s.enemySpellMana + 1)
            )
        }

        // Mutator: Dư Âm Ác Mộng
        if (s.encounterNode.type == NodeType.FINAL_BOSS &&
            s.mutators.any { it.id == "mut_nightmare_curse" } &&
            s.playerHand.size < 10 &&
            (isGameStart || (s.round % 2 == 0 && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) != "STAR_FORGER"))
        ) {
            s = s.copy(
                playerHand = s.playerHand + createHandInstance(
                    CardCatalog.getCard("card_curse_suppression"),
                    isCreated = false
                )
            )
            logs.add("Dư Âm Ác Mộng yểm Lời Nguyền Áp Chế lên tay bạn!")
        }

        // Power: Sorcery
        if (s.activePowers.any { it.id == "pow_sorcery" }) {
            s = s.copy(playerSpellMana = 3)
        }

        // Power: Sinh Lực Dồi Dào
        if (s.activePowers.any { it.id == "pow_vitality" }) {
            val healed = minOf(s.playerMaxHp, s.playerNexusHp + 2)
            if (healed > s.playerNexusHp) {
                s = s.copy(playerNexusHp = healed)
                logs.add("Sinh Lực Dồi Dào hồi +2 Máu cho Nhà Chính.")
            }
        }

        // Power: Thống Trị
        if (s.activePowers.any { it.id == "pow_domination" }) {
            s = s.copy(playerHasAttackToken = true)
        }

        // Power: Dòng Chảy Năng Lượng Hoàn Mỹ
        if (s.activePowers.any { it.id == "pow_manaflow" }) {
            s = gainPlayerManaGem(s, empty = false, logs, "Dòng Chảy Năng Lượng Hoàn Mỹ")
        }

        // Sức Mạnh Sao Của Tướng
        when (s.championId) {
            "champ_jinx" -> {
                if (isGameStart && s.starLevel >= 2 && s.playerHand.size < 10) {
                    val rocket = createHandInstance(CardCatalog.getCard("card_mystic_shot"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + rocket)
                    logs.add("Jinx 2★ tạo Phát Bắn Thần Bí Chóng Tàn.")
                }
            }
            "champ_lux" -> {
                if (s.starLevel >= 2) {
                    s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
                }
                if (s.starLevel >= 6) {
                    s = s.copy(
                        playerHasAttackToken = true,
                        playerBoard = s.playerBoard.map { it.copy(hasBarrier = true) }
                    )
                    logs.add("Lux 6★ Tập Hợp & ban Lá Chắn cho đồng minh!")
                }
            }
            "champ_yasuo" -> {
                if (s.starLevel >= 1 && s.enemyBoard.isNotEmpty()) {
                    val target = if (s.starLevel >= 3) {
                        s.enemyBoard.maxByOrNull { it.effectivePower }
                    } else {
                        s.enemyBoard.minByOrNull { it.effectivePower }
                    }
                    if (target != null) {
                        s = stunEnemyUnit(s, target.instanceId, logs)
                    }
                }
                if (s.starLevel >= 3 && s.playerHand.size < 10) {
                    val tempest = createHandInstance(CardCatalog.getCard("card_steel_tempest"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + tempest)
                }
            }
            "champ_garen" -> {
                if (s.starLevel >= 6 || s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp }) {
                    s = s.copy(playerHasAttackToken = true)
                    if (!isGameStart && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp }) {
                        logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
                    }
                }
            }
            "champ_ashe" -> {
                if (s.starLevel >= 1 && s.enemyBoard.isNotEmpty()) {
                    if (s.starLevel >= 6) {
                        val newlyFrostbitten = s.enemyBoard.count { !it.isFrostbitten }
                        s = s.copy(
                            enemyBoard = s.enemyBoard.map { it.copy(isFrostbitten = true) },
                            enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                        )
                        logs.add("Ashe 6★ Đóng Băng TẤT CẢ kẻ địch!")
                    } else {
                        val strongest = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                            ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                        if (strongest != null) {
                            val newlyFrostbitten = if (!strongest.isFrostbitten) 1 else 0
                            s = s.copy(
                                enemyBoard = s.enemyBoard.map {
                                    if (it.instanceId == strongest.instanceId) it.copy(isFrostbitten = true) else it
                                },
                                enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                            )
                            logs.add("Sức Mạnh Sao Ashe Đóng Băng ${strongest.template.name}.")
                        }
                    }
                }
                if (s.starLevel >= 3) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { topAlly ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) {
                                it.copy(power = it.power + 2, health = it.health + 2, maxHealth = it.maxHealth + 2)
                            } else it
                        })
                        logs.add("Ashe 3★ ban +2|+2 cho ${topAlly.template.name}!")
                    }
                }
            }
            "champ_anivia" -> {
                if (isGameStart && s.starLevel >= 2) {
                    logs.add("Anivia 2★ Băng Giá Tràn Trề kích hoạt (+1 Năng Lượng Khởi Điểm).")
                }
            }
        }

        // Hồi sinh Trứng Anivia vào Đầu Vòng Đấu khi đạt Khai Sáng
        if (!isGameStart && s.playerMaxMana >= 10 && s.playerBoard.any { it.template.id == "card_eggnivia" }) {
            val aniviaTmpl = CardCatalog.getCard("card_anivia")
            s = s.copy(
                playerBoard = s.playerBoard.map { u ->
                    if (u.template.id == "card_eggnivia") {
                        val bonusPow = aniviaTmpl.levelUpPowerBonus + s.equippedRelics.sumOf { it.bonusPower }
                        val bonusHp = aniviaTmpl.levelUpHealthBonus + s.equippedRelics.sumOf { it.bonusHealth }
                        val newPow = aniviaTmpl.basePower + bonusPow
                        val newHp = aniviaTmpl.baseHealth + bonusHp
                        u.copy(
                            template = aniviaTmpl,
                            power = newPow,
                            health = newHp,
                            maxHealth = newHp,
                            keywords = aniviaTmpl.keywords + s.equippedRelics.mapNotNull { it.grantedKeyword },
                            isLeveledUp = true
                        )
                    } else u
                }
            )
            logs.add("Trứng Anivia nở thành Anivia Thăng Cấp!")
        }

        // Garen Thăng Cấp
        if (s.championId != "champ_garen" && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp }) {
            s = s.copy(playerHasAttackToken = true)
            logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
        }

        // Người Máy Đạn Đạo
        if (!isGameStart && s.playerBoard.any { it.template.id == "card_ballistic_bot" } && s.playerHand.size < 10) {
            val ignition = createHandInstance(CardCatalog.getCard("card_ignition"), isFleeting = true)
            s = s.copy(playerHand = s.playerHand + ignition)
            logs.add("Người Máy Đạn Đạo tạo Tia Lửa Chóng Tàn.")
        }

        // Relic: Archangel's Staff (if leveled or on board)
        if (s.playerBoard.any { u -> u.template.isChampion } &&
            s.equippedRelics.any { it.id == "relic_archangels_staff" }
        ) {
            s = s.copy(playerSpellMana = 3)
        }

        // Relic: Grand General's Counterplan
        if (!isGameStart &&
            s.playerBoard.any { it.template.isChampion } &&
            s.equippedRelics.any { it.id == "relic_grand_generals" } &&
            s.playerHand.size < 10
        ) {
            val spellId = when (s.championId) {
                "champ_jinx" -> "card_get_excited"
                "champ_lux" -> "card_prismatic_barrier"
                "champ_yasuo" -> "card_steel_tempest"
                "champ_garen" -> "card_single_combat"
                "champ_anivia" -> "card_harsh_winds"
                else -> "card_flash_freeze"
            }
            s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard(spellId), isFleeting = true))
        }

        // Sức Mạnh Địch Đầu Vòng Đấu
        if (!isGameStart) {
            when (canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle)) {
                "FOR_DEMACIA" -> {
                    if (s.round % 2 == 0 && s.enemyBoard.isNotEmpty()) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                        })
                        logs.add("Vì Demacia! Quân địch nhận +1|+1.")
                    }
                }
                "TEMPEST_TRAINING" -> {
                    if (s.round % 2 == 0) {
                        s.playerBoard.minByOrNull { it.effectivePower }?.let { weakest ->
                            s = s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == weakest.instanceId) {
                                    it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN)
                                } else it
                            })
                            logs.add("Huấn Luyện Cuồng Phong làm Choáng ${weakest.template.name} của bạn!")
                        }
                    }
                }
                "PRISMATIC_SHIELD" -> {
                    val topEnemy = s.enemyBoard.maxByOrNull { it.effectivePower }
                    if (topEnemy != null) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == topEnemy.instanceId) it.copy(hasBarrier = true) else it
                        })
                        logs.add("Lux Trùm Phụ ban Lá Chắn cho ${topEnemy.template.name}.")
                    }
                }
                "ROCKET_BARRAGE" -> {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
                "ENDLESS_WINTER" -> {
                    val topAlly = s.playerBoard.maxByOrNull { it.effectivePower }
                    if (topAlly != null) {
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) it.copy(isFrostbitten = true) else it
                        })
                        logs.add("Ashe Trùm Phụ Đóng Băng ${topAlly.template.name}!")
                    }
                }
                "STAR_FORGER" -> {
                    s = s.copy(enemyBoard = s.enemyBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    })
                    if (s.round % 2 == 0 && s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_curse_suppression"),
                                isCreated = false
                            )
                        )
                        logs.add("Aurelion Sol yểm Lời Nguyền Áp Chế lên tay bạn!")
                    }
                }
            }
        }

        // Đếm Ngược Địa Danh
        val tickedBoard = mutableListOf<UnitOnBoard>()
        var extraDraws = 0
        var extraSpellMana = 0
        s.playerBoard.forEach { u ->
            if (u.template.type == CardType.LANDMARK && u.countdownRemaining > 0) {
                val rem = u.countdownRemaining - 1
                if (rem <= 0) {
                    extraDraws += 2
                    extraSpellMana += 2
                    logs.add("${u.template.name} hoàn tất Đếm Ngược: Rút 2 lá & +2 Năng Lượng Phép!")
                } else {
                    tickedBoard.add(u.copy(countdownRemaining = rem))
                }
            } else {
                tickedBoard.add(u)
            }
        }
        s = s.copy(
            playerBoard = tickedBoard,
            playerSpellMana = minOf(3, s.playerSpellMana + extraSpellMana),
            combatLog = logs.takeLast(16)
        )
        repeat(extraDraws) { s = drawPlayerCard(s) }
        s = applyEnlightenedBoardBuffs(s, logs)
        s = checkChampionLevelUps(s, logs)

        return checkGameOver(s.copy(combatLog = logs.takeLast(16)))
    }

    private fun createHandInstance(
        template: CardTemplate,
        isFleeting: Boolean = false,
        isCreated: Boolean = true
    ): HandCardInstance {
        return HandCardInstance(
            instanceId = "hc_${nextId()}",
            template = template,
            effectiveCost = template.cost,
            isFleeting = isFleeting || template.keywords.contains(Keyword.FLEETING),
            isCreated = isCreated
        )
    }

    fun drawPlayerCard(state: CombatState): CombatState {
        if (state.playerDrawPile.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài của bạn đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính.")
            val afterFatigue = dealDamageToPlayerNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        val topCard = state.playerDrawPile.first()
        val remainingPile = state.playerDrawPile.drop(1)
        val logs = state.combatLog.toMutableList()
        var s = state.copy(playerDrawPile = remainingPile)

        // Check Puffcap Traps
        if (topCard.hasPuffcapTrap > 0) {
            s = dealDamageToPlayerNexus(s, topCard.hasPuffcapTrap, logs)
            logs.add("Bẫy Nấm Độc gây ${topCard.hasPuffcapTrap} sát thương!")
        }

        // Check Chime Boons
        if (topCard.hasChimeBoon && s.playerHand.isNotEmpty()) {
            val idx = Random.nextInt(s.playerHand.size)
            val updatedHand = s.playerHand.toMutableList()
            val chosen = updatedHand[idx]
            updatedHand[idx] = chosen.copy(bonusPower = chosen.bonusPower + 1, bonusHealth = chosen.bonusHealth + 1)
            s = s.copy(playerHand = updatedHand)
            logs.add("Chuông Vũ Trụ ban +1|+1 cho ${chosen.template.name}!")
        }

        if (s.playerHand.size < 10) {
            s = s.copy(playerHand = s.playerHand + topCard, combatLog = logs.takeLast(16))
        } else {
            logs.add("Đầy tay! Tiêu hủy ${topCard.template.name}.")
            s = s.copy(combatLog = logs.takeLast(16))
        }
        return s
    }

    fun playCardFromHand(state: CombatState, handCardId: String): CombatState {
        val card = state.playerHand.find { it.instanceId == handCardId } ?: return state
        if (!state.canPlayCard(card)) return state

        val cost = state.effectivePlayCost(card)
        val isSpellLike = card.template.type == CardType.SPELL || card.template.type == CardType.CURSE

        var newMana = state.playerMana
        var newSpellMana = state.playerSpellMana
        if (isSpellLike) {
            val spellUsed = minOf(maxOf(0, newSpellMana), cost)
            newSpellMana = maxOf(0, newSpellMana - spellUsed)
            newMana = maxOf(0, newMana - (cost - spellUsed))
        } else {
            newMana = maxOf(0, newMana - cost)
        }

        val logs = state.combatLog.toMutableList()
        val itemManaRefill = card.attachedItems.sumOf { it.refillManaOnPlay }
        if (itemManaRefill > 0) {
            newMana = minOf(state.playerMaxMana, newMana + itemManaRefill)
            logs.add("${card.template.name} hồi +$itemManaRefill Năng Lượng.")
        }

        var s = state.copy(
            playerMana = newMana,
            playerSpellMana = newSpellMana,
            playerHand = state.playerHand.filterNot { it.instanceId == handCardId },
            playerCardsPlayedThisRound = state.playerCardsPlayedThisRound + 1,
            combatLog = logs
        )

        // Jinx 1★ / 3★ Power: Playing a card deals burn damage
        if (s.championId == "champ_jinx" && s.starLevel >= 1) {
            s = dealDamageToEnemyNexus(s, 1, logs)
            if (s.starLevel >= 3 && s.enemyBoard.isNotEmpty()) {
                val target = s.enemyBoard.random()
                s = damageUnit(s, target.instanceId, isEnemy = true, damage = 1, logs = logs)
            }
            logs.add("Sức Mạnh Sao Jinx gây 1 sát thương!")
        }

        // Play: Draw 1 item or card hook
        if (card.attachedItems.any { it.onPlayDrawOne } ||
            card.template.id == "card_zaunite_urchin" ||
            card.template.id == "card_shadow_assassin"
        ) {
            s = drawPlayerCard(s)
        }

        // Mutator: Glorious Assembly
        if (s.mutators.any { it.id == "mut_glorious_assembly" } && isSpellLike) {
            s = s.copy(playerBoard = s.playerBoard.map { it.copy(power = it.power + 1) })
        }

        var nextPriority = PriorityHolder.PLAYER
        var nextPhase = s.phase
        var nextLastCaster = s.lastStackCaster

        when (card.template.type) {
            CardType.UNIT, CardType.CHAMPION, CardType.LANDMARK -> {
                val prevSize = s.playerBoard.size
                s = summonPlayerUnit(s, card, logs)
                val summonedStormbirdId = if (card.template.id == "card_crystal_stormbird" && s.playerBoard.size > prevSize) {
                    s.playerBoard.lastOrNull { it.template.id == "card_crystal_stormbird" }?.instanceId
                } else null

                if (summonedStormbirdId != null) {
                    s = onPlayerSkillOrElementalPlayed(s, logs, "Chim Bão Pha Lê")
                    val skillDmg = 2 + s.equippedRelics.sumOf { it.spellSkillDamageBonus }
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = skillDmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                    s.playerBoard.toList().forEach { ally ->
                        if (ally.instanceId != summonedStormbirdId) {
                            s = damageUnit(s, ally.instanceId, isEnemy = false, damage = skillDmg, isSpell = true, fromAllyEffect = true, logs = logs)
                        }
                    }
                    logs.add("Kỹ Năng Chim Bão Pha Lê gây $skillDmg sát thương lên TẤT CẢ bài quân khác!")
                }
                if (card.template.effectType == CardEffectType.FROSTBITE_TARGET) {
                    s.enemyBoard.maxByOrNull { it.effectivePower }?.let { target ->
                        s = s.copy(
                            enemyBoard = s.enemyBoard.map {
                                if (it.instanceId == target.instanceId) it.copy(isFrostbitten = true) else it
                            },
                            enemiesFrostbittenCount = s.enemiesFrostbittenCount + 1
                        )
                        logs.add("${card.template.name} Đóng Băng ${target.template.name}.")
                    }
                }
                if (card.template.id == "card_brightsteel_protector") {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                }
                // Rule 9.3: Summoning Unit/Landmark passes priority immediately to opponent
                nextPriority = PriorityHolder.ENEMY
            }
            CardType.EQUIPMENT -> {
                val host = s.playerBoard.maxByOrNull { it.effectivePower }
                if (host != null) {
                    s = s.copy(playerBoard = s.playerBoard.map {
                        if (it.instanceId == host.instanceId) {
                            it.copy(
                                power = it.power + card.template.basePower,
                                health = it.health + card.template.baseHealth,
                                maxHealth = it.maxHealth + card.template.baseHealth,
                                keywords = it.keywords + card.template.keywords,
                                equippedGearName = card.template.name
                            )
                        } else it
                    })
                    logs.add("Trang bị ${card.template.name} cho ${host.template.name}!")
                }
                // Rule 9.3: Equipment passes priority immediately to opponent
                nextPriority = PriorityHolder.ENEMY
            }
            CardType.SPELL, CardType.CURSE -> {
                s = s.copy(
                    playerSpellsManaSpent = s.playerSpellsManaSpent + cost,
                    playerSpellsPlayedThisRound = s.playerSpellsPlayedThisRound + 1
                )

                if (card.template.keywords.contains(Keyword.ELEMENTAL)) {
                    s = onPlayerSkillOrElementalPlayed(s, logs, card.template.name)
                }

                // Lux Star Power 1★: Spell grants strongest ally +1|+1
                if (s.championId == "champ_lux" && s.starLevel >= 1) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) {
                                it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                            } else it
                        })
                    }
                }
                // Lux 3★: Every 6 spell mana -> Rally + Final Spark
                if (s.championId == "champ_lux" && s.starLevel >= 3 && s.playerSpellsManaSpent >= 6) {
                    s = s.copy(
                        playerSpellsManaSpent = s.playerSpellsManaSpent - 6,
                        playerHasAttackToken = true
                    )
                    if (s.playerHand.size < 10) {
                        s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_final_spark"), true))
                    }
                    logs.add("Lux 3★ kích hoạt: Tập Hợp & tạo Cầu Vồng Tối Thượng!")
                }

                val spellDmgBonus = s.equippedRelics.sumOf { it.spellSkillDamageBonus }
                // Rule 9.3: Burst/Focus resolve immediately and KEEP priority; Fast/Slow enter stack and pass priority
                if (card.template.spellSpeed == SpellSpeed.BURST || card.template.spellSpeed == SpellSpeed.FOCUS) {
                    s = resolveSingleSpellEffect(s, card.template, sourceIsPlayer = true, bonusDmg = spellDmgBonus, logs = logs)
                    nextPriority = PriorityHolder.PLAYER
                } else {
                    val entry = SpellStackEntry(
                        id = "st_${nextId()}",
                        sourceIsPlayer = true,
                        card = card.template,
                        damageBonus = spellDmgBonus
                    )
                    s = s.copy(spellStack = s.spellStack + entry)
                    nextLastCaster = PriorityHolder.PLAYER
                    if (nextPhase == CombatPhase.MAIN_ACTION_PHASE) {
                        nextPhase = CombatPhase.SPELL_STACK_PHASE
                    }
                    nextPriority = PriorityHolder.ENEMY
                    logs.add("Đặt ${card.template.name} (${card.template.spellSpeed.displayName}) lên hàng chờ phép.")
                }
            }
        }

        // Check Augment triggers for created/spell cards
        if (isSpellLike) {
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (u.keywords.contains(Keyword.AUGMENT)) u.copy(power = u.power + 1) else u
            })
        }

        val castDuringBlock = isSpellLike && s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        s = checkChampionLevelUps(s, logs)
        s = s.copy(
            phase = nextPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = nextLastCaster,
            playerCastSpellInBlock = s.playerCastSpellInBlock || castDuringBlock,
            combatLog = logs.takeLast(16)
        )
        return checkGameOver(s)
    }

    private fun summonPlayerUnit(
        state: CombatState,
        card: HandCardInstance,
        logs: MutableList<String>,
        forceEphemeral: Boolean = false
    ): CombatState {
        if (state.playerBoard.size >= 6) return state

        var s = state
        val tmpl = card.template
        val itemKeywords = card.extraKeywords
        var keywords = tmpl.keywords + itemKeywords
        if (forceEphemeral) keywords = keywords + Keyword.EPHEMERAL

        if (s.equippedRelics.any { it.id == "relic_troll_kings_crown" }) {
            keywords = keywords + Keyword.OVERWHELM
        }

        // Hextech Fabricator random keyword
        if (card.attachedItems.any { it.grantRandomKeywordOnSummon }) {
            keywords = keywords + Keyword.sharablePool.random()
        }

        var power = tmpl.basePower + card.bonusPower
        var health = tmpl.baseHealth + card.bonusHealth
        if (tmpl.isChampion) {
            power += s.equippedRelics.sumOf { it.bonusPower }
            health += s.equippedRelics.sumOf { it.bonusHealth }
            s.equippedRelics.mapNotNull { it.grantedKeyword }.forEach { keywords = keywords + it }
        }
        if (s.championId == "champ_garen" && s.starLevel >= 2) {
            power += 1; health += 1
        }
        if (s.championId == "champ_garen" && s.starLevel >= 4) {
            keywords = keywords + Keyword.TOUGH
        }
        if (s.championId == "champ_lux" && s.starLevel >= 4) {
            health += 2; keywords = keywords + Keyword.SHARPSIGHT
        }
        if (s.championId == "champ_jinx" && s.starLevel >= 4) {
            health += 1; keywords = keywords + Keyword.IMPACT
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 4) {
            power += 1; health += 1
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 2 && keywords.contains(Keyword.QUICK_ATTACK)) {
            power += 1
        }

        // Daybreak bonus
        if (keywords.contains(Keyword.DAYBREAK) && s.playerCardsPlayedThisRound == 1) {
            power += 1; health += 1
            keywords = keywords + Keyword.CHALLENGER
            logs.add("Hừng Đông kích hoạt trên ${tmpl.name} (+1|+1 & Thách Đấu)!")
        }

        // Spirit stack bonus
        val spiritCount = tmpl.spiritStacks + card.attachedItems.sumOf { it.spiritStacks }
        power += spiritCount
        health += spiritCount

        val impactCount = tmpl.impactStacks + card.attachedItems.sumOf { it.impactStacks } +
            (if (s.championId == "champ_jinx" && s.starLevel >= 4) 1 else 0)

        // Track Evolve unique positive keywords
        val newSeenKeywords = s.playerUniqueKeywordsSeen + keywords.filter { it.isSharablePositive }
        if (keywords.contains(Keyword.EVOLVE) && newSeenKeywords.size >= 6) {
            power += 2; health += 2
        }

        var enlightenedApplied = false
        if (tmpl.id == "card_wild_mystics" && s.playerMaxMana >= 10) {
            power += 4
            health += 4
            enlightenedApplied = true
            logs.add("Thầy Tu Hoang Dã (Khai Sáng) triệu hồi với chỉ số 6|6!")
        }

        // Power: Evolution (+1|+1 per positive keyword)
        if (s.activePowers.any { it.id == "pow_evolution" }) {
            val kwCount = keywords.count { it.isSharablePositive }
            power += kwCount; health += kwCount
        }

        val unit = UnitOnBoard(
            instanceId = "u_${nextId()}",
            template = tmpl,
            ownerIsPlayer = true,
            power = power,
            health = health,
            maxHealth = health,
            keywords = keywords,
            impactStacks = impactCount,
            spiritStacks = spiritCount,
            hasBarrier = keywords.contains(Keyword.BARRIER),
            hasSpellShield = keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = keywords.contains(Keyword.DEATHLESS),
            attachedItems = card.attachedItems,
            countdownRemaining = tmpl.countdownRounds,
            hasEnlightenedBonusApplied = enlightenedApplied
        )

        // Attune
        if (keywords.contains(Keyword.ATTUNE)) {
            s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
        }

        // Power: Sharing is Caring
        var updatedBoard = s.playerBoard
        if (s.activePowers.any { it.id == "pow_sharing_is_caring" }) {
            val sharable = keywords.filter { it.isSharablePositive }.toSet()
            updatedBoard = updatedBoard.map { existing ->
                existing.copy(keywords = existing.keywords + sharable)
            }
        }

        s = s.copy(
            playerBoard = updatedBoard + unit,
            playerUniqueKeywordsSeen = newSeenKeywords
        )
        logs.add("Triệu hồi ${tmpl.name} (${unit.power}|${unit.health}).")

        // Power: Trifarian Might (5+ Power strikes weakest enemy)
        if (s.activePowers.any { it.id == "pow_trifarian_might" } && unit.effectivePower >= 5) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = damageUnit(s, weakest.instanceId, isEnemy = true, damage = unit.effectivePower, logs = logs)
                logs.add("Sức Mạnh Tam Cực: ${tmpl.name} đánh ${weakest.template.name} gây ${unit.effectivePower} sát thương!")
            }
        }

        // Relic: Stalker's Blade
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "relic_stalkers_blade" }) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = damageUnit(s, weakest.instanceId, isEnemy = true, damage = unit.effectivePower, logs = logs)
                logs.add("Kiếm Truy Sát: ${tmpl.name} đánh ${weakest.template.name}!")
            }
        }

        // Shadow Totem or Stabilize Ephemeral Copy
        if (!forceEphemeral && (card.attachedItems.any { it.onSummonEphemeralCopy } ||
                (tmpl.isChampion && s.activePowers.any { it.id == "pow_stabilize" }))
        ) {
            if (s.playerBoard.size < 6) {
                s = summonPlayerUnit(s, card, logs, forceEphemeral = true)
            }
        }

        return s
    }

    fun resolveSpellStack(state: CombatState): CombatState {
        if (state.spellStack.isEmpty()) return state
        var s = state
        val logs = s.combatLog.toMutableList()
        // LIFO stack resolution (top of stack resolves first)
        for (entry in state.spellStack.reversed()) {
            s = resolveSingleSpellEffect(s, entry.card, entry.sourceIsPlayer, entry.damageBonus, logs)
        }
        val returnPhase = if (s.phase == CombatPhase.SPELL_STACK_PHASE) {
            CombatPhase.MAIN_ACTION_PHASE
        } else {
            s.phase
        }
        val nextPriority = when (returnPhase) {
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> PriorityHolder.PLAYER
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ->
                if (s.playerInitiatedCombat) PriorityHolder.ENEMY else PriorityHolder.PLAYER
            else -> s.lastStackCaster?.opponent() ?: s.priorityHolder
        }
        s = s.copy(
            spellStack = emptyList(),
            phase = returnPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )
        s = checkChampionLevelUps(s, logs)
        s = checkGameOver(s)
        if (s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && s.blockCommitted && s.playerWon == null) {
            return if (s.playerInitiatedCombat) {
                finishPlayerAttackCombat(s)
            } else {
                finishEnemyAttackCombat(s)
            }
        }
        return s
    }

    internal fun resolveSingleSpellEffect(
        state: CombatState,
        card: CardTemplate,
        sourceIsPlayer: Boolean,
        bonusDmg: Int = 0,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val mult = if (sourceIsPlayer && s.championId == "champ_jinx" && s.starLevel >= 6) 2 else 1
        val dmg = (card.effectValue + bonusDmg) * mult

        when (card.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY -> {
                if (sourceIsPlayer) {
                    val target = s.enemyBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = true, damage = dmg, isSpell = true, logs = logs)
                    } else {
                        s = dealDamageToEnemyNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} gây $dmg sát thương.")
                } else {
                    val target = s.playerBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = false, damage = dmg, isSpell = true, logs = logs)
                    } else {
                        s = dealDamageToPlayerNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} của địch gây $dmg sát thương.")
                }
            }
            CardEffectType.DEAL_DAMAGE_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên Nhà Chính của bạn.")
                }
            }
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = maxOf(2, dmg / 2), isSpell = true, logs = logs)
                    }
                    logs.add("${card.name} bắn $dmg lên Nhà Chính & toàn bộ quân địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = maxOf(2, dmg / 2), isSpell = true, logs = logs)
                    }
                    logs.add("${card.name} của địch bắn $dmg lên Nhà Chính & quân của bạn!")
                }
            }
            CardEffectType.STUN_TARGET -> {
                if (sourceIsPlayer) {
                    s.enemyBoard.maxByOrNull { it.effectivePower }?.let { target ->
                        s = stunEnemyUnit(s, target.instanceId, logs)
                    }
                } else {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { target ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == target.instanceId) it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN) else it
                        })
                        logs.add("${card.name} của địch làm Choáng ${target.template.name}.")
                    }
                }
                if (card.id == "card_concussive_palm") {
                    val tailTmpl = CardCatalog.getCard("card_tail_of_the_dragon")
                    if (sourceIsPlayer && s.playerBoard.size < 6) {
                        s = summonPlayerUnit(s, createHandInstance(tailTmpl), logs)
                    } else if (!sourceIsPlayer && s.enemyBoard.size < 6) {
                        s = summonEnemyUnitFromPool(s, tailTmpl, spendMana = 0, logs = logs)
                    }
                }
            }
            CardEffectType.FROSTBITE_TARGET -> {
                if (sourceIsPlayer) {
                    s.enemyBoard.maxByOrNull { it.effectivePower }?.let { target ->
                        s = s.copy(
                            enemyBoard = s.enemyBoard.map {
                                if (it.instanceId == target.instanceId) it.copy(isFrostbitten = true) else it
                            },
                            enemiesFrostbittenCount = s.enemiesFrostbittenCount + 1
                        )
                        logs.add("${card.name} Đóng Băng ${target.template.name}.")
                    }
                } else {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { target ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == target.instanceId) it.copy(isFrostbitten = true) else it
                        })
                        logs.add("${card.name} của địch Đóng Băng ${target.template.name}.")
                    }
                }
            }
            CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                if (sourceIsPlayer) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = drawPlayerCard(s)
                    logs.add("${card.name} ban Lá Chắn và rút 1 lá.")
                } else {
                    s.enemyBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = drawEnemyCard(s)
                    logs.add("${card.name} của địch ban Lá Chắn và rút 1 lá.")
                }
            }
            CardEffectType.RALLY_AND_SHIELD -> {
                if (sourceIsPlayer) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = s.copy(playerHasAttackToken = true)
                    logs.add("Khiên Vàng Tập Hợp kích hoạt Tập Hợp!")
                } else {
                    s.enemyBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = s.copy(enemyHasAttackToken = true)
                    logs.add("Khiên Vàng Tập Hợp của địch kích hoạt Tập Hợp!")
                }
            }
            CardEffectType.SINGLE_COMBAT_STRIKE -> {
                val ally = if (sourceIsPlayer) s.playerBoard.maxByOrNull { it.strikePower }
                    else s.playerBoard.minByOrNull { it.health }
                val enemy = if (sourceIsPlayer) s.enemyBoard.minByOrNull { it.health }
                    else s.enemyBoard.maxByOrNull { it.strikePower }
                if (ally != null && enemy != null) {
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = ally.strikePower, logs = logs)
                    s = damageUnit(s, ally.instanceId, isEnemy = false, damage = enemy.strikePower, logs = logs)
                    logs.add("Đấu Tay Đôi: ${ally.template.name} & ${enemy.template.name} giao đấu!")
                }
            }
            CardEffectType.PREDICT_DRAW -> {
                if (sourceIsPlayer) {
                    s = drawPlayerCard(s)
                    logs.add("Thủ Thuật Thời Gian Tiên Đoán và rút 1 lá.")
                } else {
                    s = drawEnemyCard(s)
                    logs.add("Thủ Thuật Thời Gian của địch rút 1 lá.")
                }
            }
            CardEffectType.PLANT_CHIMES -> {
                if (sourceIsPlayer) {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasChimeBoon = true)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    if (s.playerBoard.isNotEmpty()) {
                        val top = s.playerBoard.first()
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                        })
                    }
                    logs.add("Gieo ${card.effectValue} Chuông vào bộ bài!")
                }
            }
            CardEffectType.PLANT_PUFFCAPS -> {
                if (sourceIsPlayer) {
                    logs.add("Gieo ${card.effectValue} Bẫy Nấm Độc vào bộ bài địch!")
                    s = dealDamageToEnemyNexus(s, 1, logs)
                } else {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasPuffcapTrap = updatedPile[idx].hasPuffcapTrap + 1)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    logs.add("Địch gieo ${card.effectValue} Nấm Độc vào bộ bài bạn!")
                }
            }
            CardEffectType.FROSTBITE_TWO -> {
                if (sourceIsPlayer && s.enemyBoard.isNotEmpty()) {
                    val targets = s.enemyBoard
                        .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                        .take(2)
                    val targetIds = targets.map { it.instanceId }.toSet()
                    val newlyFrostbitten = targets.count { !it.isFrostbitten }
                    s = s.copy(
                        enemyBoard = s.enemyBoard.map {
                            if (targetIds.contains(it.instanceId)) it.copy(isFrostbitten = true) else it
                        },
                        enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                    )
                    logs.add("${card.name} Đóng Băng ${targets.joinToString(", ") { it.template.name }}.")
                } else if (!sourceIsPlayer && s.playerBoard.isNotEmpty()) {
                    val targets = s.playerBoard
                        .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                        .take(2)
                    val targetIds = targets.map { it.instanceId }.toSet()
                    s = s.copy(playerBoard = s.playerBoard.map {
                        if (targetIds.contains(it.instanceId)) it.copy(isFrostbitten = true) else it
                    })
                    logs.add("${card.name} của địch Đóng Băng ${targets.size} bài quân.")
                }
            }
            CardEffectType.WINGS_OF_CRYOPHOENIX -> {
                if (sourceIsPlayer) {
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                    if (s.playerMaxMana >= 10) {
                        val healed = minOf(s.playerMaxHp, s.playerNexusHp + 3)
                        s = s.copy(playerNexusHp = healed)
                        logs.add("Đôi Cánh Phượng Hoàng Băng (Khai Sáng): Gây $dmg lên tất cả kẻ địch & hồi +3 Máu Nhà Chính!")
                    } else {
                        s.playerBoard.toList().forEach { ally ->
                            s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                        }
                        logs.add("Đôi Cánh Phượng Hoàng Băng gây $dmg lên TẤT CẢ bài quân.")
                    }
                } else {
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, logs = logs)
                    }
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                }
            }
            CardEffectType.SUMMON_TWO_GIANTS -> {
                val count = maxOf(1, card.effectValue)
                if (sourceIsPlayer) {
                    repeat(count) {
                        if (s.playerBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonPlayerUnit(s, createHandInstance(giant), logs)
                        }
                    }
                    logs.add("Khổng Lồ Chạm Trán triệu hồi các tùy tùng Khổng Lồ Freljord!")
                } else {
                    repeat(count) {
                        if (s.enemyBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonEnemyUnitFromPool(s, giant, spendMana = 0, logs = logs)
                        }
                    }
                }
            }
            CardEffectType.PURGE_SUPPRESSION_CURSE, CardEffectType.PURGE_SHACKLES_CURSE, CardEffectType.PURGE_TERROR_CURSE -> {
                logs.add("Đã hóa giải Lời Nguyền ${card.name} khỏi tay.")
            }
            else -> {}
        }
        s = if (sourceIsPlayer) {
            s.copy(playerGraveyard = s.playerGraveyard + card)
        } else {
            s.copy(enemyGraveyard = s.enemyGraveyard + card)
        }
        return s
    }

    // --- ATTACK & BLOCK DECLARATION ---

    fun enterAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.MAIN_ACTION_PHASE ||
            !state.playerHasAttackToken ||
            state.spellStack.isNotEmpty() ||
            state.attackingUnitIds.isNotEmpty() ||
            state.playerBoard.none { it.canAttack }
        ) return state
        return state.copy(
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap()
        )
    }

    fun cancelAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.spellStack.isNotEmpty()
        ) return state
        return state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap()
        )
    }

    fun toggleUnitAttack(state: CombatState, unitId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        val unit = state.playerBoard.find { it.instanceId == unitId } ?: return state
        if (!unit.canAttack) return state

        val current = state.attackingUnitIds.toMutableList()
        if (current.contains(unitId)) {
            current.remove(unitId)
        } else {
            current.add(unitId)
        }
        return state.copy(attackingUnitIds = current)
    }

    fun commitPlayerAttack(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.attackingUnitIds.isEmpty()
        ) return state

        var s = state
        val logs = s.combatLog.toMutableList()
        val attackers = s.attackingUnitIds.mapNotNull { id -> s.playerBoard.find { it.instanceId == id } }

        // Check Scout Rally
        val allScout = attackers.all { it.keywords.contains(Keyword.SCOUT) }
        val scoutRally = allScout && !s.scoutUsedThisRound

        // Hallowed bonus for first attacker (scoped to current round)
        if (s.playerHallowedStacks > 0 && attackers.isNotEmpty()) {
            val firstId = attackers.first().instanceId
            val key = hallowedKey(s.encounterNode.id, s.round, firstId)
            roundHallowedBuffs[key] = (roundHallowedBuffs[key] ?: 0) + s.playerHallowedStacks
            s = s.copy(playerBoard = s.playerBoard.map {
                if (it.instanceId == firstId) it.copy(power = it.power + s.playerHallowedStacks) else it
            })
            logs.add("Thiêng Liêng ban cho ${attackers.first().template.name} +${s.playerHallowedStacks}|+0!")
        }

        // Guinsoo's Rageblade, Ashe & Anivia Attack triggers
        val skillDmgBonus = s.equippedRelics.sumOf { it.spellSkillDamageBonus }
        attackers.forEach { atk ->
            if (atk.template.isChampion && s.equippedRelics.any { it.id == "relic_guinsoos_rageblade" }) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == atk.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                })
            }
            if (atk.template.id == "card_ashe") {
                val target = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                if (target != null) {
                    val newlyFrostbitten = if (!target.isFrostbitten) 1 else 0
                    s = s.copy(
                        enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == target.instanceId) it.copy(isFrostbitten = true) else it
                        },
                        enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                    )
                    logs.add("Ashe Tấn Công: Đóng Băng ${target.template.name}.")
                }
            }
            if (atk.template.id == "card_anivia") {
                s = onPlayerSkillOrElementalPlayed(s, logs, "Bão Băng Giá")
                val stormDmg = (if (atk.isLeveledUp) 2 else 1) + skillDmgBonus
                s.enemyBoard.toList().forEach { enemy ->
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = stormDmg, isSpell = true, fromAllyEffect = true, logs = logs)
                }
                s = dealDamageToEnemyNexus(s, stormDmg, logs)
                logs.add("Bão Băng Giá gây $stormDmg sát thương lên toàn bộ kẻ địch & Nhà Chính địch!")
                if (s.playerWon != null) return checkGameOver(s.copy(combatLog = logs.takeLast(16)))
            }
        }

        // Resolve any queued Fast spells before transitioning to defender's block phase
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }

        logs.add("Tuyên bố tấn công với ${attackers.size} quân! Địch đang chọn quân chặn...")
        return s.copy(
            playerHasAttackToken = false,
            scoutUsedThisRound = s.scoutUsedThisRound || allScout,
            pendingScoutRally = scoutRally,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.ENEMY,
            consecutivePasses = 0,
            blockPairings = emptyMap(),
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
    }

    fun assignPlayerBlocker(state: CombatState, attackerId: String, blockerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted
        ) return state
        val attacker = state.enemyBoard.find { it.instanceId == attackerId } ?: return state
        val blocker = state.playerBoard.find { it.instanceId == blockerId } ?: return state
        if (!blocker.canBlockAttacker(attacker)) return state

        val updated = state.blockPairings.toMutableMap()
        val prevKey = updated.entries.find { it.value == blockerId }?.key
        if (prevKey != null) updated.remove(prevKey)
        updated[attackerId] = blockerId
        return state.copy(blockPairings = updated, selectedEnemyAttackerForBlock = null)
    }

    fun removePlayerBlocker(state: CombatState, attackerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted
        ) return state
        val updated = state.blockPairings.toMutableMap()
        updated.remove(attackerId)
        return state.copy(blockPairings = updated)
    }

    fun commitPlayerBlock(state: CombatState): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE || state.priorityHolder != PriorityHolder.PLAYER) return state
        if (state.playerInitiatedCombat) {
            return finishPlayerAttackCombat(state)
        }
        // If Player cast Burst/Fast spells during block and hasn't yielded a post-block window to AI yet
        if (state.spellStack.isEmpty() && state.playerCastSpellInBlock && !state.blockCommitted) {
            val logs = state.combatLog.toMutableList()
            logs.add("Đã chốt đội hình chặn — Địch có thể phản ứng.")
            return state.copy(
                blockCommitted = true,
                priorityHolder = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        }
        return finishEnemyAttackCombat(state)
    }

    internal fun finishPlayerAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = true, logs = logs)
        val scoutRally = s.pendingScoutRally
        val postCombatPriority = if (scoutRally) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        return checkGameOver(
            s.copy(
                playerHasAttackToken = scoutRally,
                pendingScoutRally = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = postCombatPriority,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun finishEnemyAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = false, logs = logs)
        return checkGameOver(
            s.copy(
                enemyHasAttackToken = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = PriorityHolder.PLAYER,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun resolveCombatResolution(
        state: CombatState,
        playerIsAttacking: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val attackers = s.attackingUnitIds.toList()
        val playerFrostbiteAura = if (s.championId == "champ_ashe" && s.starLevel >= 2) {
            s.enemyBoard.count { it.isFrostbitten }
        } else 0

        for (atkId in attackers) {
            val atk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId } ?: continue
            if (atk.isStunned) continue
            val blkId = s.blockPairings[atkId]
            val blk = blkId?.let { id -> (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == id } }

            val atkStrikePower = atk.strikePower +
                (if (playerIsAttacking && !atk.isFrostbitten && !atk.keywords.contains(Keyword.FORMIDABLE)) playerFrostbiteAura else 0)
            val blkStrikePower = (blk?.strikePower ?: 0) +
                (if (!playerIsAttacking && blk != null && !blk.isFrostbitten && !blk.keywords.contains(Keyword.FORMIDABLE)) playerFrostbiteAura else 0)

            // Impact damage on attack strike (0-power Frostbitten units do not strike)
            if (atk.impactStacks > 0 && atkStrikePower > 0) {
                if (playerIsAttacking) {
                    s = dealDamageToEnemyNexus(s, atk.impactStacks, logs)
                } else {
                    s = dealDamageToPlayerNexus(s, atk.impactStacks, logs)
                }
                logs.add("${atk.template.name} Tác Động gây ${atk.impactStacks} sát thương Nhà Chính!")
            }

            if (blk == null) {
                // Unblocked strike directly to Nexus
                val dmg = atkStrikePower
                if (dmg > 0) {
                    val strikes = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1
                    repeat(strikes) {
                        if (playerIsAttacking) {
                            s = dealDamageToEnemyNexus(s, dmg, logs)
                        } else {
                            s = dealDamageToPlayerNexus(s, dmg, logs)
                        }
                        if (atk.keywords.contains(Keyword.LIFESTEAL)) {
                            s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dmg)
                        }
                    }
                    logs.add("${atk.template.name} đánh thẳng Nhà Chính gây ${dmg * strikes} sát thương!")
                }
            } else {
                // Combat between Attacker and Blocker
                val hasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
                val strikesCount = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1

                if (hasFirstStrike) {
                    val atkDmg = atkStrikePower
                    if (atkDmg > 0) {
                        val blkHadBarrier = blk.hasBarrier
                        val effectiveHealth = blk.health + if (blk.keywords.contains(Keyword.TOUGH)) 1 else 0
                        s = damageUnit(s, blk.instanceId, isEnemy = playerIsAttacking, damage = atkDmg, logs = logs)
                        if (atk.keywords.contains(Keyword.LIFESTEAL) && !blkHadBarrier) {
                            val dealt = if (blk.keywords.contains(Keyword.TOUGH)) maxOf(0, atkDmg - 1) else atkDmg
                            s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dealt)
                        }
                        if (atk.keywords.contains(Keyword.OVERWHELM) && !blkHadBarrier && atkDmg > effectiveHealth) {
                            val excess = atkDmg - effectiveHealth
                            if (playerIsAttacking) s = dealDamageToEnemyNexus(s, excess, logs)
                            else s = dealDamageToPlayerNexus(s, excess, logs)
                        }
                    }

                    val survivingBlk = (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == blk.instanceId }
                    if (survivingBlk != null) {
                        val blkDmg = survivingBlk.strikePower +
                            (if (!playerIsAttacking && !survivingBlk.isFrostbitten && !survivingBlk.keywords.contains(Keyword.FORMIDABLE)) playerFrostbiteAura else 0)
                        if (strikesCount == 2 && atkDmg > 0) {
                            val survHadBarrier = survivingBlk.hasBarrier
                            val survEffectiveHealth = survivingBlk.health + if (survivingBlk.keywords.contains(Keyword.TOUGH)) 1 else 0
                            s = damageUnit(s, survivingBlk.instanceId, isEnemy = playerIsAttacking, damage = atkDmg, logs = logs)
                            if (atk.keywords.contains(Keyword.LIFESTEAL) && !survHadBarrier) {
                                val dealt2 = if (survivingBlk.keywords.contains(Keyword.TOUGH)) maxOf(0, atkDmg - 1) else atkDmg
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dealt2)
                            }
                            if (atk.keywords.contains(Keyword.OVERWHELM) && !survHadBarrier && atkDmg > survEffectiveHealth) {
                                val excess2 = atkDmg - survEffectiveHealth
                                if (playerIsAttacking) s = dealDamageToEnemyNexus(s, excess2, logs)
                                else s = dealDamageToPlayerNexus(s, excess2, logs)
                            }
                        }
                        if (!survivingBlk.isStunned && blkDmg > 0) {
                            val atkHadBarrier = atk.hasBarrier
                            s = damageUnit(s, atk.instanceId, isEnemy = !playerIsAttacking, damage = blkDmg, logs = logs)
                            if (survivingBlk.keywords.contains(Keyword.LIFESTEAL) && !atkHadBarrier) {
                                val blkDealt = if (atk.keywords.contains(Keyword.TOUGH)) maxOf(0, blkDmg - 1) else blkDmg
                                s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = blkDealt)
                            }
                        }
                        val blkAfterSecond = (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == blk.instanceId }
                        if (blkAfterSecond == null) {
                            s = applyKillBonuses(s, atk.instanceId, isPlayerKiller = playerIsAttacking, logs = logs)
                        }
                        val atkAfterReturn = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atk.instanceId }
                        if (atkAfterReturn == null) {
                            s = applyKillBonuses(s, survivingBlk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                        }
                    } else {
                        s = applyKillBonuses(s, atk.instanceId, isPlayerKiller = playerIsAttacking, logs = logs)
                        if (strikesCount == 2 && atkDmg > 0 && atk.keywords.contains(Keyword.OVERWHELM)) {
                            if (playerIsAttacking) s = dealDamageToEnemyNexus(s, atkDmg, logs)
                            else s = dealDamageToPlayerNexus(s, atkDmg, logs)
                            if (atk.keywords.contains(Keyword.LIFESTEAL)) {
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = atkDmg)
                            }
                        }
                    }
                } else {
                    val atkDmg = atkStrikePower
                    val blkDmg = blkStrikePower
                    val blkHadBarrier = blk.hasBarrier
                    val atkHadBarrier = atk.hasBarrier
                    val effectiveHealth = blk.health + if (blk.keywords.contains(Keyword.TOUGH)) 1 else 0

                    if (atkDmg > 0) {
                        s = damageUnit(s, blk.instanceId, isEnemy = playerIsAttacking, damage = atkDmg, logs = logs)
                        if (atk.keywords.contains(Keyword.LIFESTEAL) && !blkHadBarrier) {
                            val dealt = if (blk.keywords.contains(Keyword.TOUGH)) maxOf(0, atkDmg - 1) else atkDmg
                            s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dealt)
                        }
                        if (atk.keywords.contains(Keyword.OVERWHELM) && !blkHadBarrier && atkDmg > effectiveHealth) {
                            val excess = atkDmg - effectiveHealth
                            if (playerIsAttacking) s = dealDamageToEnemyNexus(s, excess, logs)
                            else s = dealDamageToPlayerNexus(s, excess, logs)
                        }
                    }
                    if (blkDmg > 0) {
                        s = damageUnit(s, atk.instanceId, isEnemy = !playerIsAttacking, damage = blkDmg, logs = logs)
                        if (blk.keywords.contains(Keyword.LIFESTEAL) && !atkHadBarrier) {
                            val blkDealt = if (atk.keywords.contains(Keyword.TOUGH)) maxOf(0, blkDmg - 1) else blkDmg
                            s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = blkDealt)
                        }
                    }

                    val blkSurvived = (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == blk.instanceId }
                    if (blkSurvived == null) {
                        s = applyKillBonuses(s, atk.instanceId, isPlayerKiller = playerIsAttacking, logs = logs)
                    }
                    val atkSurvived = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atk.instanceId }
                    if (atkSurvived == null) {
                        s = applyKillBonuses(s, blk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                    }
                }
            }

            // Sức Mạnh Địch: Phân Thân Bóng Tối (Zed Trùm Phụ)
            if (!playerIsAttacking && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "LIVING_SHADOW") {
                val survivingAtk = s.enemyBoard.find { it.instanceId == atkId }
                if (survivingAtk != null && !survivingAtk.keywords.contains(Keyword.EPHEMERAL)) {
                    s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                        if (u.instanceId == atkId) {
                            u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                        } else u
                    })
                    logs.add("Phân Thân Bóng Tối cường hóa ${survivingAtk.template.name} (+1|+1)!")
                }
            }

            // Ephemeral unit dies after striking
            val postBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
            val postAtk = postBoard.find { it.instanceId == atkId }
            if (postAtk != null && postAtk.keywords.contains(Keyword.EPHEMERAL)) {
                s = removeUnit(s, postAtk.instanceId, isEnemy = !playerIsAttacking, logs = logs)
            }
        }

        // Garen 1★/3★ strike scaling
        if (s.championId == "champ_garen" && s.starLevel >= 1 && playerIsAttacking) {
            val bonus = if (s.starLevel >= 3) 2 else 1
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (attackers.contains(u.instanceId)) {
                    u.copy(power = u.power + bonus, health = u.health + bonus, maxHealth = u.maxHealth + bonus)
                } else u
            })
            if (s.starLevel >= 3 && attackers.isNotEmpty() && s.playerHand.size < 10) {
                s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_single_combat"), isFleeting = true))
                logs.add("Garen 3★ tạo Đấu Tay Đôi Chóng Tàn!")
            }
        }

        return checkGameOver(s)
    }

    private fun applyKillBonuses(
        state: CombatState,
        killerId: String,
        isPlayerKiller: Boolean,
        logs: MutableList<String>
    ): CombatState {
        val board = if (isPlayerKiller) state.playerBoard else state.enemyBoard
        val updated = board.map { u ->
            if (u.instanceId == killerId && u.keywords.contains(Keyword.FURY)) {
                logs.add("${u.template.name} kích hoạt Cuồng Nộ (+1|+1)!")
                u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
            } else u
        }
        return if (isPlayerKiller) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
    }

    private fun applyLifesteal(state: CombatState, forPlayer: Boolean, dmg: Int): CombatState {
        if (dmg <= 0) return state
        return if (forPlayer) {
            state.copy(playerNexusHp = minOf(state.playerMaxHp, state.playerNexusHp + dmg))
        } else {
            state.copy(enemyNexusHp = minOf(state.enemyMaxHp, state.enemyNexusHp + dmg))
        }
    }

    private fun damageUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        damage: Int,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String>
    ): CombatState {
        if (damage <= 0) return state
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state

        if (!isEnemy && target.template.id == "card_guardian_follower" && fromAllyEffect) {
            logs.add("${target.template.name} miễn nhiễm sát thương từ bài phép/Kỹ Năng đồng minh.")
            return state
        }

        if (isSpell && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        if (target.hasBarrier) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasBarrier = false) else it
            }
            logs.add("Lá Chắn của ${target.template.name} đã đỡ sát thương!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        val afterTough = if (target.keywords.contains(Keyword.TOUGH)) maxOf(0, damage - 1) else damage
        val crystallizeMult = if (isEnemy && target.effectivePower == 0 && state.championId == "champ_anivia" && state.starLevel >= 6) 2 else 1
        val actualDmg = afterTough * crystallizeMult
        val newHp = target.health - actualDmg

        if (newHp <= 0) {
            return removeUnit(state, unitId, isEnemy, logs)
        } else {
            var newPow = target.power
            var updatedMaxHp = target.maxHealth
            var finalHp = newHp

            if (isEnemy && state.activePowers.any { it.id == "pow_enfeebling_strike" }) {
                newPow = maxOf(0, newPow - actualDmg)
            }
            if (!isEnemy && actualDmg > 0 && state.championId == "champ_anivia" && state.starLevel >= 4) {
                newPow += 1
                finalHp += 1
                updatedMaxHp += 1
                logs.add("Bền Bỉ (4★): ${target.template.name} sống sót qua sát thương và nhận +1|+1!")
            }

            val updated = board.map {
                if (it.instanceId == unitId) it.copy(health = finalHp, maxHealth = updatedMaxHp, power = newPow) else it
            }
            var s = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)

            if (actualDmg > 0 && target.template.id == "card_mortal_prophet") {
                if (!isEnemy) {
                    s = dealDamageToEnemyNexus(s, 1, logs)
                    logs.add("Tiên Tri Chết Chóc sống sót: Gây 1 sát thương lên Nhà Chính địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
            }
            return s
        }
    }

    private fun removeUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        // Deathless revival
        if (target.hasDeathless) {
            val revived = target.copy(
                health = 1,
                hasDeathless = false,
                hasBarrier = target.keywords.contains(Keyword.BARRIER),
                isStunned = true,
                keywords = (target.keywords - Keyword.DEATHLESS) + Keyword.STUN
            )
            val updated = board.map { if (it.instanceId == unitId) revived else it }
            logs.add("${target.template.name} hồi sinh nhờ Bất Tử (1 Máu, Choáng)!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        // Anivia Last Breath: Transform into Eggnivia (0|2)
        if (!isEnemy && target.template.id == "card_anivia") {
            roundHallowedBuffs.remove(hallowedKey(s.encounterNode.id, s.round, unitId))
            val eggTmpl = CardCatalog.getCard("card_eggnivia")
            val egg = UnitOnBoard(
                instanceId = target.instanceId,
                template = eggTmpl,
                ownerIsPlayer = true,
                power = eggTmpl.basePower,
                health = eggTmpl.baseHealth,
                maxHealth = eggTmpl.baseHealth,
                keywords = eggTmpl.keywords,
                attachedItems = target.attachedItems
            )
            logs.add("Trăng Trối Anivia: Biến đổi thành Trứng Anivia (0|2)!")
            return s.copy(playerBoard = s.playerBoard.map { if (it.instanceId == unitId) egg else it })
        }

        if (!isEnemy) {
            roundHallowedBuffs.remove(hallowedKey(s.encounterNode.id, s.round, unitId))
        }
        val updated = board.filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            s.copy(enemyBoard = updated, enemyGraveyard = s.enemyGraveyard + target.template)
        } else {
            s.copy(playerBoard = updated, playerGraveyard = s.playerGraveyard + target.template)
        }
        logs.add("${target.template.name} đã bị hạ gục.")

        if (target.keywords.contains(Keyword.HALLOWED)) {
            s = if (!isEnemy) s.copy(playerHallowedStacks = s.playerHallowedStacks + 1)
            else s.copy(enemyHallowedStacks = s.enemyHallowedStacks + 1)
        }

        return s
    }

    private fun stunEnemyUnit(state: CombatState, unitId: String, logs: MutableList<String>): CombatState {
        var s = state
        val target = s.enemyBoard.find { it.instanceId == unitId } ?: return s
        s = s.copy(
            enemyBoard = s.enemyBoard.map {
                if (it.instanceId == unitId) it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN) else it
            },
            enemiesStunnedCount = s.enemiesStunnedCount + 1
        )
        logs.add("Đã làm Choáng ${target.template.name}.")

        val skillBonus = s.equippedRelics.sumOf { it.spellSkillDamageBonus }
        val yasuos = s.playerBoard.filter { it.template.id == "card_yasuo" }
        yasuos.forEach { yasuo ->
            val dmg = (if (yasuo.isLeveledUp) yasuo.effectivePower else 2) + skillBonus
            s = damageUnit(s, unitId, isEnemy = true, damage = dmg, logs = logs)
            logs.add("Yasuo chém ${target.template.name} đang Choáng gây $dmg sát thương!")
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 6) {
            s = damageUnit(s, unitId, isEnemy = true, damage = 5 + skillBonus, logs = logs)
        }
        s = s.copy(playerBoard = s.playerBoard.map {
            if (it.template.id == "card_fae_blader") it.copy(power = it.power + 2) else it
        })
        return s
    }

    private fun dealDamageToEnemyNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        var s = state.copy(
            enemyNexusHp = maxOf(0, state.enemyNexusHp - dmg),
            playerPlunderActive = true
        )
        if (s.activePowers.any { it.id == "pow_raiding_party" } && s.playerHand.isNotEmpty()) {
            val firstUnitIdx = s.playerHand.indexOfFirst {
                it.template.type == CardType.UNIT || it.template.type == CardType.CHAMPION
            }
            if (firstUnitIdx >= 0) {
                val updatedHand = s.playerHand.toMutableList()
                val card = updatedHand[firstUnitIdx]
                updatedHand[firstUnitIdx] = card.copy(bonusPower = card.bonusPower + 1, bonusHealth = card.bonusHealth + 1)
                s = s.copy(playerHand = updatedHand)
            }
        }
        return checkGameOver(s)
    }

    private fun dealDamageToPlayerNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        val s = state.copy(playerNexusHp = maxOf(0, state.playerNexusHp - dmg))
        return checkGameOver(s)
    }

    // --- PRIORITY PASS & ROUND TRANSITION ---

    fun passPriority(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.PLAYER) return state
        val logs = state.combatLog.toMutableList()

        // Rule 9.2: Passing during DECLARE_BLOCK with 0+ blockers commits blocks
        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
            return commitPlayerBlock(state)
        }

        // Rule 9.2: Passing while Spell Stack is non-empty resolves stack LIFO and transfers priority
        if (state.spellStack.isNotEmpty()) {
            logs.add("Bạn đã bỏ qua — đang thực thi hàng chờ phép.")
            return resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
        }

        // Rule 9.3: Passing in MAIN_ACTION_PHASE increments consecutivePasses; 2 passes ends round
        val newPasses = state.consecutivePasses + 1
        if (newPasses >= 2) {
            logs.add("Cả hai bên đều bỏ qua. Kết thúc Vòng ${state.round}.")
            return advanceToNextRound(state, logs)
        }

        logs.add("Bạn đã chuyển lượt.")
        return state.copy(
            consecutivePasses = newPasses,
            priorityHolder = PriorityHolder.ENEMY,
            combatLog = logs.takeLast(16)
        )
    }

    internal fun drawEnemyCard(state: CombatState): CombatState {
        if (state.enemyDrawPool.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài địch đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính địch.")
            val afterFatigue = dealDamageToEnemyNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        if (state.enemyHand.size >= 10) return state
        return state.copy(enemyHand = state.enemyHand + state.enemyDrawPool.random())
    }

    internal fun summonEnemyUnitFromPool(state: CombatState, chosen: CardTemplate, spendMana: Int, logs: MutableList<String>): CombatState {
        var bonusPow = 0
        var bonusHp = 0
        val extraKw = mutableSetOf<Keyword>()
        if (state.adventure.starRating >= 3.0f) {
            bonusPow += 1; bonusHp += 1
        }
        when (canonicalEnemyPowerKey(state.encounterNode.enemyPowerTitle)) {
            "VOLATILE_MUNITIONS" -> extraKw.add(Keyword.IMPACT)
            "UNSEEN_PATH" -> if (chosen.keywords.contains(Keyword.ELUSIVE)) bonusPow += 1
            "GLORIOUS_EVOLUTION" -> {
                bonusPow += 1; bonusHp += 1
                extraKw.add(Keyword.sharablePool.random())
            }
            "BLAZING_DAWN" -> {
                bonusPow += 1; bonusHp += 1
            }
        }

        val enemyUnit = UnitOnBoard(
            instanceId = "eu_${nextId()}",
            template = chosen,
            ownerIsPlayer = false,
            power = chosen.basePower + bonusPow,
            health = chosen.baseHealth + bonusHp,
            maxHealth = chosen.baseHealth + bonusHp,
            keywords = chosen.keywords + extraKw,
            impactStacks = chosen.impactStacks + (if (extraKw.contains(Keyword.IMPACT)) 1 else 0),
            hasBarrier = chosen.keywords.contains(Keyword.BARRIER),
            hasSpellShield = chosen.keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = chosen.keywords.contains(Keyword.DEATHLESS)
        )
        logs.add("Địch triệu hồi ${chosen.name} (${enemyUnit.power}|${enemyUnit.health}).")
        return state.copy(
            enemyMana = maxOf(0, state.enemyMana - spendMana),
            enemyHand = removeFirstTemplate(state.enemyHand, chosen.id),
            enemyBoard = state.enemyBoard + enemyUnit
        )
    }

    internal fun removeFirstTemplate(hand: List<CardTemplate>, cardId: String): List<CardTemplate> {
        val idx = hand.indexOfFirst { it.id == cardId }
        if (idx < 0) return hand
        return hand.toMutableList().apply { removeAt(idx) }
    }

    internal fun advanceToNextRound(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        val cleanPlayerBoard = s.playerBoard.filterNot { it.keywords.contains(Keyword.EPHEMERAL) }.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            val hallowedBuff = roundHallowedBuffs.remove(hallowedKey(s.encounterNode.id, s.round, u.instanceId)) ?: 0
            u.copy(
                power = maxOf(0, u.power - hallowedBuff),
                health = healedHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                keywords = u.keywords - Keyword.STUN
            )
        }
        roundHallowedBuffs.keys.removeIf { it.startsWith("${s.encounterNode.id}:${s.round}:") }
        val cleanEnemyBoard = s.enemyBoard.filterNot { it.keywords.contains(Keyword.EPHEMERAL) }.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            u.copy(
                health = healedHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                keywords = u.keywords - Keyword.STUN
            )
        }

        val cleanHand = s.playerHand.filterNot { it.isFleeting }

        val nextRound = s.round + 1
        val newPlayerMax = (s.playerMaxMana + 1).coerceIn(1, 10)
        val newEnemyMax = (s.enemyMaxMana + 1).coerceIn(1, 10)
        val playerSpellBank = minOf(3, maxOf(0, s.playerSpellMana) + maxOf(0, s.playerMana))
        val enemySpellBank = minOf(3, maxOf(0, s.enemySpellMana) + maxOf(0, s.enemyMana))

        val playerGetsToken = nextRound % 2 == 1
        val roundInitiator = if (playerGetsToken) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        logs.add("--- Vòng $nextRound (Lượt Tấn Công: ${if (playerGetsToken) "Bạn" else "Địch"}) ---")

        s = s.copy(
            round = nextRound,
            playerMana = newPlayerMax,
            playerMaxMana = newPlayerMax,
            playerSpellMana = playerSpellBank,
            enemyMana = newEnemyMax,
            enemyMaxMana = newEnemyMax,
            enemySpellMana = enemySpellBank,
            playerHasAttackToken = playerGetsToken,
            enemyHasAttackToken = !playerGetsToken,
            scoutUsedThisRound = false,
            pendingScoutRally = false,
            playerInitiatedCombat = playerGetsToken,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            playerBoard = cleanPlayerBoard,
            enemyBoard = cleanEnemyBoard,
            playerHand = cleanHand,
            playerCardsPlayedThisRound = 0,
            playerSpellsPlayedThisRound = 0,
            playerPlunderActive = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            selectedEnemyAttackerForBlock = null,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = roundInitiator,
            consecutivePasses = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )

        s = drawPlayerCard(s)
        s = drawEnemyCard(s)
        s = applyRoundStartHooks(s, isGameStart = false)
        return s
    }

    internal fun checkChampionLevelUps(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state
        var rallyTriggered = false
        val newlyLeveledIds = mutableListOf<String>()
        val updatedBoard = s.playerBoard.map { u ->
            if (u.template.isChampion && !u.isLeveledUp) {
                val shouldLevel = when (u.template.id) {
                    "card_jinx" -> s.playerHand.isEmpty() || s.playerSpellsPlayedThisRound >= 2
                    "card_lux" -> s.playerSpellsManaSpent >= 6
                    "card_yasuo" -> s.enemiesStunnedCount >= 3 || s.round >= 3
                    "card_ashe" -> s.enemiesFrostbittenCount >= 3 || s.round >= 3
                    "card_garen" -> s.round >= 3
                    "card_anivia" -> s.playerMaxMana >= 10
                    else -> s.round >= 3
                }
                if (shouldLevel) {
                    logs.add("★ ${u.template.name} ĐÃ THĂNG CẤP! (+${u.template.levelUpPowerBonus}|+${u.template.levelUpHealthBonus})")
                    newlyLeveledIds.add(u.template.id)
                    if (s.equippedRelics.any { it.id == "relic_crownguard" }) rallyTriggered = true
                    u.copy(
                        isLeveledUp = true,
                        power = u.power + u.template.levelUpPowerBonus,
                        health = u.health + u.template.levelUpHealthBonus,
                        maxHealth = u.maxHealth + u.template.levelUpHealthBonus
                    )
                } else u
            } else u
        }
        s = s.copy(playerBoard = updatedBoard)
        if (rallyTriggered) {
            s = s.copy(playerHasAttackToken = true)
            logs.add("Di Sản Hoàng Tộc kích hoạt Tập Hợp!")
        }
        for (champId in newlyLeveledIds) {
            when (champId) {
                "card_jinx" -> {
                    s = dealDamageToEnemyNexus(s, 4, logs)
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_super_mega_rocket"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Jinx Thăng Cấp gây 4 sát thương Nhà Chính & tạo Tên Lửa Đạn Đạo Siêu Khủng Khiếp!")
                }
                "card_lux" -> {
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_final_spark"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Lux Thăng Cấp tạo Cầu Vồng Tối Thượng 0 tiêu hao!")
                }
            }
        }
        return s
    }

    internal fun onPlayerSkillOrElementalPlayed(
        state: CombatState,
        logs: MutableList<String>,
        sourceName: String
    ): CombatState {
        var s = state.copy(playerElementalAndSkillsPlayed = state.playerElementalAndSkillsPlayed + 1)

        // Hỏa Nhân Caprine: Tôi Luyện (+1|+1)
        if (s.playerBoard.any { it.template.id == "card_flame_eyed_caprine" }) {
            s = s.copy(
                playerBoard = s.playerBoard.map { u ->
                    if (u.template.id == "card_flame_eyed_caprine") {
                        u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                    } else u
                }
            )
            logs.add("Hỏa Nhân Caprine được Tôi Luyện (+1|+1) từ $sourceName!")
        }

        // Anivia 3★ Ân Huệ Mùa Đông II: Nhận 1 ngọc năng lượng rỗng
        if (s.championId == "champ_anivia" && s.starLevel >= 3) {
            s = gainPlayerManaGem(s, empty = true, logs, "Ân Huệ Mùa Đông II")
        }
        return s
    }

    internal fun gainPlayerManaGem(
        state: CombatState,
        empty: Boolean,
        logs: MutableList<String>,
        sourceLabel: String = "Tăng Ngọc Năng Lượng"
    ): CombatState {
        var s = state
        if (s.playerMaxMana >= 10) {
            // Anivia 2★ Băng Giá Tràn Trề: Nếu được Khai Sáng, ban +1|+1 cho đồng minh
            if (s.championId == "champ_anivia" && s.starLevel >= 2 && s.playerBoard.isNotEmpty()) {
                s = s.copy(
                    playerBoard = s.playerBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    }
                )
                logs.add("Băng Giá Tràn Trề (Khai Sáng): Toàn bộ đồng minh nhận +1|+1!")
            }
            return s
        }
        val newMax = minOf(10, s.playerMaxMana + 1)
        val newCurrent = if (empty) minOf(newMax, s.playerMana) else minOf(newMax, s.playerMana + 1)
        s = s.copy(playerMaxMana = newMax, playerMana = newCurrent)
        logs.add("$sourceLabel: Nhận +1 ngọc năng lượng${if (empty) " rỗng" else ""} (Tối đa: $newMax).")
        if (newMax >= 10) {
            logs.add("Đạt KHAI SÁNG (10 Năng Lượng Tối Đa)!")
            s = applyEnlightenedBoardBuffs(s, logs)
            s = checkChampionLevelUps(s, logs)
        }
        return s
    }

    private fun applyEnlightenedBoardBuffs(state: CombatState, logs: MutableList<String>): CombatState {
        if (state.playerMaxMana < 10) return state
        var changed = false
        val updated = state.playerBoard.map { u ->
            if (u.template.id == "card_wild_mystics" && !u.hasEnlightenedBonusApplied) {
                changed = true
                logs.add("Thầy Tu Hoang Dã đạt Khai Sáng (+4|+4)!")
                u.copy(
                    power = u.power + 4,
                    health = u.health + 4,
                    maxHealth = u.maxHealth + 4,
                    hasEnlightenedBonusApplied = true
                )
            } else u
        }
        return if (changed) state.copy(playerBoard = updated) else state
    }

    internal fun checkGameOver(state: CombatState): CombatState {
        return when {
            state.enemyNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = true)
            state.playerNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = false)
            else -> state
        }
    }

    internal fun generateId(): String = nextId()
}
