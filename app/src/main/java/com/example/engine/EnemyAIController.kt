package com.example.engine

import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard

/**
 * Section 9.4 EnemyAIController:
 * Executes one AI decision step whenever priorityHolder == PriorityHolder.ENEMY.
 * Handles Case A (SPELL_STACK_PHASE), Case B (MAIN_ACTION_PHASE), and Case C (COMBAT_DECLARE_BLOCK_PHASE).
 */
object EnemyAIController {

    fun executeTurn(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.ENEMY) {
            return state
        }
        val logs = state.combatLog.toMutableList()

        if (state.spellStack.isNotEmpty()) {
            return handleSpellStackResponse(state, logs)
        }

        return when (state.phase) {
            CombatPhase.SPELL_STACK_PHASE -> handleSpellStackResponse(state, logs)
            CombatPhase.MAIN_ACTION_PHASE -> handleMainActionPhase(state, logs)
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                if (state.playerInitiatedCombat) {
                    if (state.blockCommitted) {
                        CombatEngine.finishPlayerAttackCombat(state)
                    } else {
                        handleBlockDeclaration(state, logs)
                    }
                } else if (state.blockCommitted) {
                    val reactiveSpell = pickReactiveCombatSpell(state, playerIsAttacking = false)
                    if (reactiveSpell != null) {
                        castEnemySpell(state, reactiveSpell, logs)
                    } else {
                        CombatEngine.finishEnemyAttackCombat(state)
                    }
                } else {
                    state.copy(priorityHolder = PriorityHolder.PLAYER)
                }
            }
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            CombatPhase.COMBAT_RESOLUTION_PHASE,
            CombatPhase.GAME_OVER -> state
        }
    }

    // Case A: Responding to SPELL_STACK_PHASE
    private fun handleSpellStackResponse(state: CombatState, logs: MutableList<String>): CombatState {
        val totalMana = state.enemyMana + state.enemySpellMana
        val fastSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                it.spellSpeed == SpellSpeed.FAST &&
                it.cost <= totalMana
        }

        val playerStackThreatDmg = state.spellStack
            .filter { it.sourceIsPlayer }
            .sumOf { it.card.effectValue + it.damageBonus }

        val reactiveSpell = fastSpells.firstOrNull { spell ->
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW -> state.enemyBoard.isNotEmpty() && playerStackThreatDmg > 0
                CardEffectType.STUN_TARGET, CardEffectType.FROSTBITE_TARGET -> state.playerBoard.isNotEmpty()
                CardEffectType.DEAL_DAMAGE_ANY ->
                    state.playerBoard.any { it.health <= spell.effectValue } || state.playerNexusHp <= spell.effectValue
                CardEffectType.SINGLE_COMBAT_STRIKE ->
                    state.enemyBoard.any { e -> state.playerBoard.any { p -> e.strikePower >= p.health && e.health > p.strikePower } }
                else -> false
            }
        }

        if (reactiveSpell != null) {
            return castEnemySpell(state, reactiveSpell, logs)
        }

        // Địch bỏ qua để thực thi hàng chờ phép LIFO
        logs.add("Địch bỏ qua — đang thực thi hàng chờ phép.")
        return CombatEngine.resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
    }

    // Case B: Acting in MAIN_ACTION_PHASE
    private fun handleMainActionPhase(state: CombatState, logs: MutableList<String>): CombatState {
        // 1. Play Units / Champions / Landmarks (highest cost playable <= enemyMana while board < 6)
        if (state.enemyBoard.size < 6) {
            val playableUnits = state.enemyHand.filter {
                (it.type == CardType.UNIT || it.type == CardType.CHAMPION || it.type == CardType.LANDMARK) &&
                    it.cost <= state.enemyMana
            }
            val bestUnit = playableUnits.sortedWith(
                compareByDescending<CardTemplate> { it.isChampion }
                    .thenByDescending { it.cost }
                    .thenByDescending { it.keywords.size }
            ).firstOrNull()

            if (bestUnit != null) {
                val summoned = CombatEngine.summonEnemyUnitFromPool(state, bestUnit, bestUnit.cost, logs)
                return CombatEngine.checkGameOver(
                    summoned.copy(
                        consecutivePasses = 0,
                        priorityHolder = PriorityHolder.PLAYER,
                        combatLog = logs.takeLast(16)
                    )
                )
            }
        }

        // 2. Cast Proactive Spells (Slow, Focus, Burst, or Fast with valid targets)
        val totalMana = state.enemyMana + state.enemySpellMana
        val proactiveSpell = state.enemyHand.filter {
            it.type == CardType.SPELL && it.cost <= totalMana && isSpellWorthCasting(state, it)
        }.maxByOrNull { it.cost }

        if (proactiveSpell != null) {
            return castEnemySpell(state, proactiveSpell, logs)
        }

        // 3. Declare Attack if AI holds Attack Token and has profitable attackers
        if (state.enemyHasAttackToken) {
            val eligibleAttackers = state.enemyBoard.filter { it.canAttack }
            val chosenAttackers = eligibleAttackers.filter { atk ->
                isProfitableAiAttacker(atk, state.playerBoard)
            }.take(6)

            if (chosenAttackers.isNotEmpty()) {
                val chosenIds = chosenAttackers.map { it.instanceId }
                var nextBoard = state.enemyBoard
                if (state.enemyHallowedStacks > 0) {
                    val firstId = chosenIds.first()
                    nextBoard = nextBoard.map {
                        if (it.instanceId == firstId) it.copy(power = it.power + state.enemyHallowedStacks) else it
                    }
                    logs.add("Thiêng Liêng ban cho ${chosenAttackers.first().template.name} +${state.enemyHallowedStacks}|+0!")
                }
                if (CombatEngine.canonicalEnemyPowerKey(state.encounterNode.enemyPowerTitle) == "READY_RANKS") {
                    nextBoard = nextBoard.map {
                        if (chosenIds.contains(it.instanceId)) it.copy(power = it.power + 1) else it
                    }
                    logs.add("Sẵn Sàng Đội Hình ban cho quân tấn công của địch +1|+0!")
                }
                var newFrostbittenCount = state.enemiesFrostbittenCount
                if (state.championId == "champ_anivia" && state.starLevel >= 6) {
                    val strongest = nextBoard
                        .filter { chosenIds.contains(it.instanceId) }
                        .maxByOrNull { it.effectivePower }
                    if (strongest != null) {
                        if (!strongest.isFrostbitten) newFrostbittenCount += 1
                        nextBoard = nextBoard.map {
                            if (it.instanceId == strongest.instanceId) it.copy(isFrostbitten = true) else it
                        }
                        logs.add("Tường Băng (6★): Đóng Băng ${strongest.template.name} đang tấn công!")
                    }
                }
                logs.add("Địch tấn công với ${chosenIds.size} quân! Hãy chọn quân chặn.")
                var attackState = state.copy(
                    enemyBoard = nextBoard,
                    enemyHasAttackToken = false,
                    playerInitiatedCombat = false,
                    blockCommitted = false,
                    playerCastSpellInBlock = false,
                    enemiesFrostbittenCount = newFrostbittenCount,
                    phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
                    priorityHolder = PriorityHolder.PLAYER,
                    consecutivePasses = 0,
                    attackingUnitIds = chosenIds,
                    blockPairings = emptyMap(),
                    selectedEnemyAttackerForBlock = null,
                    combatLog = logs.takeLast(16)
                )
                // Optionally queue a Fast combat spell alongside the attack declaration
                val attackFastSpell = pickReactiveCombatSpell(attackState, playerIsAttacking = false, fastOnly = true)
                if (attackFastSpell != null) {
                    attackState = castEnemySpell(attackState, attackFastSpell, logs)
                }
                return attackState
            }
        }

        // 4. Pass Priority
        val nextPasses = state.consecutivePasses + 1
        if (nextPasses >= 2) {
            logs.add("Địch bỏ qua. Kết thúc Vòng ${state.round}.")
            return CombatEngine.advanceToNextRound(state, logs)
        }
        logs.add("Địch đã chuyển lượt.")
        return state.copy(
            consecutivePasses = nextPasses,
            priorityHolder = PriorityHolder.PLAYER,
            combatLog = logs.takeLast(16)
        )
    }

    // Case C: Defending in COMBAT_DECLARE_BLOCK_PHASE (Player declared Attackers)
    private fun handleBlockDeclaration(state: CombatState, logs: MutableList<String>): CombatState {
        val attackers = state.attackingUnitIds.mapNotNull { id ->
            state.playerBoard.find { it.instanceId == id }
        }
        val availableBlockers = state.enemyBoard.filter { it.canBlock }.toMutableList()
        val pairings = mutableMapOf<String, String>()

        // Estimate unblocked damage including Impact & Overwhelm threat
        val totalPotentialDamage = attackers.sumOf { it.strikePower + it.impactStacks }
        val isLethalThreat = totalPotentialDamage >= state.enemyNexusHp
        val lowHealthThreshold = state.enemyNexusHp <= 8

        val leveledAsheActive = state.playerBoard.any { it.template.id == "card_ashe" && it.isLeveledUp }

        for (atk in attackers) {
            val eligible = availableBlockers.filter { blk ->
                blk.canBlockAttacker(atk) && (!leveledAsheActive || blk.effectivePower > 0)
            }
            if (eligible.isEmpty()) continue

            val chosenBlocker = if (isLethalThreat) {
                // Lethal Prevention: absorb maximum damage regardless of blocker survival
                eligible.maxByOrNull { blk ->
                    val absorbed = if (atk.keywords.contains(Keyword.OVERWHELM)) minOf(atk.strikePower, blk.health) else atk.strikePower
                    absorbed * 100 + blk.health
                }
            } else {
                // Value Trade Priority
                val cleanWin = eligible.filter { blk ->
                    val atkHasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
                    val blkKills = blk.strikePower >= atk.health
                    val blkSurvives = if (atkHasFirstStrike && atk.strikePower >= blk.health && !blk.hasBarrier && !blk.hasDeathless) {
                        false
                    } else {
                        blk.hasBarrier || blk.hasDeathless || atk.strikePower < blk.health
                    }
                    blkKills && blkSurvives
                }.minByOrNull { it.template.cost }

                cleanWin ?: run {
                    val isHighThreat = atk.template.isChampion || atk.effectivePower >= 4 || atk.keywords.contains(Keyword.OVERWHELM)
                    val favorableOrEvenTrade = eligible.filter { blk ->
                        val blkKills = blk.strikePower >= atk.health
                        (blkKills && (atk.template.cost >= blk.template.cost || isHighThreat)) ||
                            (lowHealthThreshold && isHighThreat) ||
                            (blk.hasDeathless && isHighThreat)
                    }.minByOrNull { it.template.cost }

                    favorableOrEvenTrade ?: if (lowHealthThreshold) {
                        eligible.minByOrNull { it.effectivePower }
                    } else null
                }
            }

            if (chosenBlocker != null) {
                pairings[atk.instanceId] = chosenBlocker.instanceId
                availableBlockers.remove(chosenBlocker)
            }
        }

        val blockedState = state.copy(
            blockPairings = pairings,
            blockCommitted = true,
            combatLog = logs.takeLast(16)
        )

        val reactiveSpell = pickReactiveCombatSpell(blockedState, playerIsAttacking = true)
        if (reactiveSpell != null) {
            logs.add("Địch đã gán ${pairings.size} quân chặn và dùng ${reactiveSpell.name}.")
            val afterSpell = castEnemySpell(blockedState, reactiveSpell, logs)
            return afterSpell.copy(
                priorityHolder = PriorityHolder.PLAYER,
                blockCommitted = true
            )
        }

        return CombatEngine.finishPlayerAttackCombat(blockedState)
    }

    private fun pickReactiveCombatSpell(
        state: CombatState,
        playerIsAttacking: Boolean,
        fastOnly: Boolean = false
    ): CardTemplate? {
        val totalMana = state.enemyMana + state.enemySpellMana
        val combatSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                (it.spellSpeed == SpellSpeed.FAST || (!fastOnly && it.spellSpeed == SpellSpeed.BURST)) &&
                it.cost <= totalMana
        }
        return combatSpells.firstOrNull { spell ->
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW ->
                    state.enemyBoard.any { !it.hasBarrier }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO ->
                    if (playerIsAttacking) state.attackingUnitIds.isNotEmpty() else state.playerBoard.isNotEmpty()
                CardEffectType.DEAL_DAMAGE_ANY ->
                    state.playerBoard.any { it.health <= spell.effectValue } ||
                        state.playerNexusHp <= spell.effectValue ||
                        (!playerIsAttacking && state.blockCommitted && state.blockPairings.values.any { blkId ->
                            state.playerBoard.any { p -> p.instanceId == blkId && p.hasBarrier }
                        })
                CardEffectType.SINGLE_COMBAT_STRIKE ->
                    state.enemyBoard.any { e -> state.playerBoard.any { p -> e.strikePower >= p.health && e.health > p.strikePower } }
                else -> false
            }
        }
    }

    private fun castEnemySpell(state: CombatState, spell: CardTemplate, logs: MutableList<String>): CombatState {
        val spellUsed = minOf(maxOf(0, state.enemySpellMana), spell.cost)
        val unitUsed = spell.cost - spellUsed
        var s = state.copy(
            enemySpellMana = maxOf(0, state.enemySpellMana - spellUsed),
            enemyMana = maxOf(0, state.enemyMana - unitUsed),
            enemyHand = CombatEngine.removeFirstTemplate(state.enemyHand, spell.id)
        )
        if (CombatEngine.canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "ENLIGHTENED_HARMONY") {
            val healed = minOf(s.enemyMaxHp, s.enemyNexusHp + 1)
            if (healed > s.enemyNexusHp) {
                s = s.copy(enemyNexusHp = healed)
                logs.add("Hòa Hợp Khai Sáng hồi +1 Máu cho Nhà Chính địch.")
            }
        }

        return if (spell.spellSpeed == SpellSpeed.BURST || spell.spellSpeed == SpellSpeed.FOCUS) {
            s = CombatEngine.resolveSingleSpellEffect(s, spell, sourceIsPlayer = false, logs = logs)
            CombatEngine.checkGameOver(
                s.copy(
                    consecutivePasses = 0,
                    priorityHolder = PriorityHolder.ENEMY, // Keeps priority on Burst/Focus
                    combatLog = logs.takeLast(16)
                )
            )
        } else {
            val entry = SpellStackEntry(
                id = "st_${CombatEngine.generateId()}",
                sourceIsPlayer = false,
                card = spell
            )
            val nextPhase = if (s.phase == CombatPhase.MAIN_ACTION_PHASE) CombatPhase.SPELL_STACK_PHASE else s.phase
            logs.add("Địch đặt ${spell.name} (${spell.spellSpeed.displayName}) lên hàng chờ phép.")
            s.copy(
                spellStack = s.spellStack + entry,
                phase = nextPhase,
                lastStackCaster = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                priorityHolder = PriorityHolder.PLAYER,
                combatLog = logs.takeLast(16)
            )
        }
    }

    private fun isSpellWorthCasting(state: CombatState, spell: CardTemplate): Boolean {
        return when (spell.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY ->
                state.playerBoard.any { it.health <= spell.effectValue } || state.playerNexusHp <= spell.effectValue
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS,
            CardEffectType.DEAL_DAMAGE_NEXUS -> true
            CardEffectType.STUN_TARGET,
            CardEffectType.FROSTBITE_TARGET -> state.playerBoard.isNotEmpty()
            CardEffectType.GIVE_BARRIER_AND_DRAW,
            CardEffectType.RALLY_AND_SHIELD -> state.enemyBoard.isNotEmpty()
            CardEffectType.SINGLE_COMBAT_STRIKE ->
                state.enemyBoard.any { e -> state.playerBoard.any { p -> e.strikePower >= p.health && e.health > p.strikePower } }
            CardEffectType.PREDICT_DRAW,
            CardEffectType.PLANT_PUFFCAPS -> true
            else -> false
        }
    }

    private fun isProfitableAiAttacker(atk: UnitOnBoard, playerBoard: List<UnitOnBoard>): Boolean {
        val eligibleBlockers = playerBoard.filter { it.canBlockAttacker(atk) }
        // Unblocked Nexus damage or empty player board
        if (eligibleBlockers.isEmpty()) return atk.strikePower > 0 || atk.impactStacks > 0

        // Quick Attack / Double Attack can safely attack if it kills weakest/strongest blockers without dying,
        // or unit survives biggest eligible blocker, or trades evenly
        val strongestBlocker = eligibleBlockers.maxByOrNull { it.strikePower } ?: return true
        val hasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
        if (hasFirstStrike && eligibleBlockers.all { atk.strikePower >= it.health }) return true
        if (atk.health > strongestBlocker.strikePower || atk.hasBarrier || atk.hasDeathless) return true
        return atk.strikePower >= strongestBlocker.health && atk.template.cost <= strongestBlocker.template.cost
    }
}
