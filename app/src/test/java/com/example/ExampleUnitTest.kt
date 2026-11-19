package com.example

import com.example.data.local.dao.GameDao
import com.example.data.local.entity.ChampionLoadoutEntity
import com.example.data.local.entity.PlayerMetaEntity
import com.example.data.local.entity.SavedRunEntity
import com.example.data.repository.GameRepository
import com.example.engine.CombatEngine
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.EnemyAIController
import com.example.engine.PriorityHolder
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.model.ChampionLoadout
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.model.ItemCategory
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.NodeType
import com.example.model.Rarity
import com.example.model.RunState
import com.example.model.UnitOnBoard
import com.example.ui.components.cardPortraitRes
import com.example.viewmodel.AppScreen
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeGameDao : GameDao {
    private val loadouts = MutableStateFlow<List<ChampionLoadoutEntity>>(emptyList())
    private val meta = MutableStateFlow<PlayerMetaEntity?>(null)
    private val savedRun = MutableStateFlow<SavedRunEntity?>(null)

    override fun observeChampionLoadouts(): Flow<List<ChampionLoadoutEntity>> = loadouts
    override suspend fun getChampionLoadout(id: String): ChampionLoadoutEntity? =
        loadouts.value.find { it.championId == id }

    override suspend fun upsertChampionLoadout(entity: ChampionLoadoutEntity) {
        loadouts.value = loadouts.value.filterNot { it.championId == entity.championId } + entity
    }

    override suspend fun upsertAllChampionLoadouts(entities: List<ChampionLoadoutEntity>) {
        val ids = entities.map { it.championId }.toSet()
        loadouts.value = loadouts.value.filterNot { ids.contains(it.championId) } + entities
    }

    override fun observePlayerMeta(): Flow<PlayerMetaEntity?> = meta
    override suspend fun getPlayerMeta(): PlayerMetaEntity? = meta.value
    override suspend fun upsertPlayerMeta(entity: PlayerMetaEntity) {
        meta.value = entity
    }

    override suspend fun deleteOtherChampionLoadouts(keepId: String) {
        loadouts.value = loadouts.value.filter { it.championId == keepId }
    }

    override fun observeSavedRun(): Flow<SavedRunEntity?> = savedRun
    override suspend fun getSavedRun(): SavedRunEntity? = savedRun.value
    override suspend fun upsertSavedRun(entity: SavedRunEntity) {
        savedRun.value = entity
    }

    override suspend fun clearSavedRun() {
        savedRun.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combat engine enforces keyword blocking rules for Elusive Fearsome and Brash`() {
        val elusiveAttacker = UnitOnBoard(
            instanceId = "a1",
            template = CardCatalog.getCard("card_sumpworks_mapper"),
            ownerIsPlayer = true,
            power = 1,
            health = 1,
            maxHealth = 1,
            keywords = setOf(Keyword.ELUSIVE)
        )
        val normalBlocker = UnitOnBoard(
            instanceId = "b1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        val sharpsightBlocker = UnitOnBoard(
            instanceId = "b2",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.SHARPSIGHT)
        )
        val brashAttacker = UnitOnBoard(
            instanceId = "a2",
            template = CardCatalog.getCard("card_ashe"),
            ownerIsPlayer = true,
            power = 5,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.BRASH)
        )

        assertFalse(normalBlocker.canBlockAttacker(elusiveAttacker))
        assertTrue(sharpsightBlocker.canBlockAttacker(elusiveAttacker))
        // Brash requires 3+ Health to block
        assertFalse(normalBlocker.canBlockAttacker(brashAttacker))
    }

    @Test
    fun `combat engine resolves Overwhelm Quick Attack and Deathless accurately`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val attacker = UnitOnBoard(
            instanceId = "atk_1",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = true,
            power = 5,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.OVERWHELM, Keyword.QUICK_ATTACK)
        )
        val deathlessDefender = UnitOnBoard(
            instanceId = "def_1",
            template = CardCatalog.getCard("card_rekindled_knight"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.DEATHLESS),
            hasDeathless = true
        )

        val initialState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerBoard = listOf(attacker),
            enemyBoard = listOf(deathlessDefender),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("atk_1")
        )

        val afterDeclare = CombatEngine.commitPlayerAttack(initialState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, afterDeclare.phase)
        assertEquals(PriorityHolder.ENEMY, afterDeclare.priorityHolder)

        val afterAttack = EnemyAIController.executeTurn(afterDeclare)
        // Quick Attack kills defender before return strike -> attacker unharmed
        assertEquals(3, afterAttack.playerBoard.first().health)
        // Overwhelm deals 5 - 2 = 3 excess damage to enemy Nexus (15 -> 12)
        assertEquals(12, afterAttack.enemyNexusHp)
        // Deathless revives defender with 1 HP and Stunned
        assertEquals(1, afterAttack.enemyBoard.size)
        assertEquals(1, afterAttack.enemyBoard.first().health)
        assertTrue(afterAttack.enemyBoard.first().isStunned)
        assertFalse(afterAttack.enemyBoard.first().hasDeathless)
    }

    @Test
    fun `playing Burst spell with Spellslinger and Suppression calculates effective cost and resolves immediately`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val burstCard = HandCardInstance(
            instanceId = "hc_1",
            template = CardCatalog.getCard("card_prismatic_barrier"), // base cost 2
            effectiveCost = 2
        )
        val ally = UnitOnBoard(
            instanceId = "u_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = listOf(CardCatalog.getPower("pow_spellslinger")),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 1,
            playerSpellMana = 0,
            playerHand = listOf(burstCard),
            playerBoard = listOf(ally)
        )

        assertEquals(1, state.effectivePlayCost(burstCard))
        assertTrue(state.canPlayCard(burstCard))

        val afterPlay = CombatEngine.playCardFromHand(state, "hc_1")
        assertEquals(0, afterPlay.playerMana)
        assertTrue(afterPlay.playerBoard.first().hasBarrier)
        // Lux 1★ grants +1|+1 when casting a spell
        assertEquals(3, afterPlay.playerBoard.first().power)
        assertEquals(3, afterPlay.playerBoard.first().health)
    }

    @Test
    fun `viewModel manages adventure launch DAG node progression and healer restoration`() = runTest {
        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        vm.startAdventure("adv_demacia_outskirts")
        advanceUntilIdle()

        val run = vm.uiState.value.activeRun
        assertNotNull(run)
        assertEquals(AppScreen.AdventureMap, vm.uiState.value.screen)

        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val powerNode = adv.mapNodes.first { it.id == "d_power_1" }
        assertTrue(vm.isNodeAvailable(run!!, powerNode))

        // Select Power Shrine
        vm.selectMapNode("d_power_1")
        assertEquals(AppScreen.PowerDraft, vm.uiState.value.screen)
        assertEquals(3, vm.uiState.value.powerDraftOptions.size)

        val chosenPower = vm.uiState.value.powerDraftOptions.first()
        vm.pickPowerReward(chosenPower)
        advanceUntilIdle()

        val updatedRun = vm.uiState.value.activeRun!!
        assertTrue(updatedRun.activePowerIds.contains(chosenPower.id))
        assertTrue(updatedRun.completedNodeIds.contains("d_power_1"))
        assertEquals(AppScreen.AdventureMap, vm.uiState.value.screen)
        // Branching nodes d_fight_1a and d_fight_1b both become available
        assertTrue(vm.isNodeAvailable(updatedRun, adv.mapNodes.first { it.id == "d_fight_1a" }))
        assertTrue(vm.isNodeAvailable(updatedRun, adv.mapNodes.first { it.id == "d_fight_1b" }))
    }

    @Test
    fun `playing unit passes priority to enemy and Burst spell keeps priority on player`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val unitCard = HandCardInstance(
            instanceId = "hc_unit",
            template = CardCatalog.getCard("card_cithria"), // 1-cost unit
            effectiveCost = 1
        )
        val burstCard = HandCardInstance(
            instanceId = "hc_burst",
            template = CardCatalog.getCard("card_prismatic_barrier"), // 2-cost Burst
            effectiveCost = 2
        )
        val enemyUnit = CardCatalog.getCard("card_cithria")
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 3,
            enemyMana = 2,
            playerHand = listOf(burstCard, unitCard),
            enemyHand = listOf(enemyUnit),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Burst spell resolves immediately and keeps priority on PLAYER
        val afterBurst = CombatEngine.playCardFromHand(state, "hc_burst")
        assertEquals(PriorityHolder.PLAYER, afterBurst.priorityHolder)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, afterBurst.phase)

        // Playing a unit summons it and switches priority to ENEMY
        val afterUnit = CombatEngine.playCardFromHand(afterBurst, "hc_unit")
        assertEquals(1, afterUnit.playerBoard.size)
        assertEquals(PriorityHolder.ENEMY, afterUnit.priorityHolder)

        // EnemyAIController plays its unit and returns priority to PLAYER
        val afterAi = EnemyAIController.executeTurn(afterUnit)
        assertEquals(1, afterAi.enemyBoard.size)
        assertEquals(PriorityHolder.PLAYER, afterAi.priorityHolder)
    }

    @Test
    fun `fast spell enters SPELL_STACK_PHASE and resolves LIFO when opponent passes`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"), // 2-cost Fast, deal 2
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(CombatPhase.SPELL_STACK_PHASE, onStack.phase)
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)
        assertEquals(1, onStack.spellStack.size)
        assertEquals(PriorityHolder.PLAYER, onStack.lastStackCaster)

        // AI has no reactive Fast spell -> AI passes to resolve Spell Stack LIFO
        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, resolved.phase)
        // Priority returns to opponent of lastStackCaster (PLAYER -> ENEMY)
        assertEquals(PriorityHolder.ENEMY, resolved.priorityHolder)
        // Jinx 1★ dealt 1 on play + Mystic Shot dealt 2 = 3 damage total (15 -> 12)
        assertEquals(12, resolved.enemyNexusHp)
    }

    @Test
    fun `consecutive passes advance round bank spell mana draw cards and swap attack token`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val drawCard = HandCardInstance(
            instanceId = "hc_draw",
            template = CardCatalog.getCard("card_cithria"),
            effectiveCost = 1
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 4, // up to 3 unspent Unit Mana banks into Spell Mana
            playerMaxMana = 4,
            playerSpellMana = 0,
            enemyMana = 1,
            enemyMaxMana = 1,
            enemySpellMana = 0,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerDrawPile = listOf(drawCard),
            enemyDrawPool = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0
        )

        val afterPlayerPass = CombatEngine.passPriority(state)
        assertEquals(1, afterPlayerPass.consecutivePasses)
        assertEquals(PriorityHolder.ENEMY, afterPlayerPass.priorityHolder)

        // Enemy has no cards in hand -> AI passes (consecutivePasses == 2 -> Round 2)
        val nextRound = EnemyAIController.executeTurn(afterPlayerPass)
        assertEquals(2, nextRound.round)
        assertEquals(5, nextRound.playerMana)
        assertEquals(5, nextRound.playerMaxMana)
        assertEquals(3, nextRound.playerSpellMana)
        assertEquals(1, nextRound.enemySpellMana)
        assertFalse(nextRound.playerHasAttackToken)
        assertTrue(nextRound.enemyHasAttackToken)
        assertEquals(PriorityHolder.ENEMY, nextRound.priorityHolder)
        assertEquals(1, nextRound.playerHand.size)
        assertEquals(1, nextRound.enemyHand.size)
    }

    @Test
    fun `viewModel automatically triggers EnemyAIController after 700ms delay when priority shifts to enemy`() = runTest(testDispatcher) {
        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo, aiStepDelayMs = 700L)
        advanceUntilIdle()

        vm.startAdventure("adv_demacia_outskirts")
        advanceUntilIdle()
        vm.selectMapNode("d_power_1")
        vm.pickPowerReward(vm.uiState.value.powerDraftOptions.first())
        advanceUntilIdle()

        vm.selectMapNode("d_fight_1a")
        runCurrent()

        val startCombat = vm.uiState.value.combatState
        assertNotNull(startCombat)
        assertEquals(PriorityHolder.PLAYER, startCombat!!.priorityHolder)

        // Pass priority -> ENEMY gets priority; AI should NOT execute before 700ms
        vm.passTurn()
        runCurrent()
        val afterPass = vm.uiState.value.combatState!!
        assertEquals(PriorityHolder.ENEMY, afterPass.priorityHolder)

        advanceTimeBy(699L)
        runCurrent()
        assertEquals(afterPass, vm.uiState.value.combatState)

        // At 700ms, EnemyAIController executes automatically (summons 1-cost unit -> PLAYER, or passes -> Round 2)
        advanceTimeBy(2L)
        runCurrent()
        val afterAi = vm.uiState.value.combatState!!
        assertTrue(afterAi != afterPass)
        assertTrue(afterAi.priorityHolder == PriorityHolder.PLAYER || afterAi.round == 2)

        // Once AI finishes any Round 2 opening action, priority returns to PLAYER
        advanceUntilIdle()
        assertEquals(PriorityHolder.PLAYER, vm.uiState.value.combatState!!.priorityHolder)
    }

    @Test
    fun `fast spell during attack declaration resolves stack via AI and returns priority to PLAYER`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val ally = UnitOnBoard(
            instanceId = "p1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHasAttackToken = true,
            playerBoard = listOf(ally),
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p1"),
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, onStack.phase)
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)
        assertEquals(1, onStack.spellStack.size)

        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, resolved.phase)
        assertEquals(PriorityHolder.PLAYER, resolved.priorityHolder)
        assertEquals(13, resolved.enemyNexusHp)
    }

    @Test
    fun `fast spell while defending resolves stack and keeps PLAYER in COMBAT_DECLARE_BLOCK_PHASE`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val enemyAtk = UnitOnBoard(
            instanceId = "e1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHasAttackToken = false,
            enemyHasAttackToken = false,
            playerInitiatedCombat = false,
            enemyBoard = listOf(enemyAtk),
            attackingUnitIds = listOf("e1"),
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)

        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, resolved.phase)
        assertEquals(PriorityHolder.PLAYER, resolved.priorityHolder)
        assertTrue(resolved.enemyBoard.isEmpty())
        assertEquals(30, resolved.playerNexusHp)
    }

    @Test
    fun `enemy node powers Stone Ward For Demacia Enlightened Harmony Living Shadow and Glorious Evolution work`() {
        // 1. Stone Ward adds +5 Max HP
        val demacia = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val stoneWardNode = demacia.mapNodes.first { it.id == "d_fight_1b" }
        val baseRun = RunState(
            runId = "run_1",
            adventureId = demacia.id,
            championId = "champ_garen",
            currentNodeId = "d_fight_1b",
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = listOf(DeckCardEntry("card_cithria", 4))
        )
        val loadout = ChampionLoadout("champ_garen", starLevel = 1)
        val stoneEncounter = CombatEngine.createEncounter(baseRun, loadout, demacia, stoneWardNode)
        assertEquals(stoneWardNode.enemyMaxHp + 5, stoneEncounter.enemyMaxHp)
        assertEquals(stoneWardNode.enemyMaxHp + 5, stoneEncounter.enemyNexusHp)

        // 2. For Demacia! buffs enemy board +1|+1 on even rounds
        val bossNode = demacia.mapNodes.first { it.id == "d_boss" }
        val garenEnemy = UnitOnBoard(
            instanceId = "eg_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val round1BossState = CombatState(
            encounterNode = bossNode,
            adventure = demacia,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 28,
            enemyMaxHp = 28,
            enemyBoard = listOf(garenEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )
        val round2BossState = CombatEngine.passPriority(round1BossState)
        assertEquals(2, round2BossState.round)
        assertEquals(3, round2BossState.enemyBoard.first().power)
        assertEquals(3, round2BossState.enemyBoard.first().health)

        // 3. Enlightened Harmony heals Karma Nexus +1 when enemy casts spell
        val ionia = AdventureCatalog.getAdventure("adv_ionia_woods")
        val karmaBoss = ionia.mapNodes.first { it.id == "i_boss" }
        val karmaState = CombatState(
            encounterNode = karmaBoss,
            adventure = ionia,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 30,
            enemyMaxHp = 35,
            enemyMana = 4,
            playerBoard = listOf(
                UnitOnBoard("p_1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
            ),
            enemyHand = listOf(CardCatalog.getCard("card_mystic_shot")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterKarmaCast = EnemyAIController.executeTurn(karmaState)
        assertEquals(31, afterKarmaCast.enemyNexusHp)

        // 4. Glorious Evolution grants +1|+1 and random keyword on enemy summon
        val zaun = AdventureCatalog.getAdventure("adv_zaun_foundry")
        val viktorBoss = zaun.mapNodes.first { it.id == "z_boss" }
        val viktorState = CombatState(
            encounterNode = viktorBoss,
            adventure = zaun,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 45,
            enemyMaxHp = 45,
            enemyMana = 3,
            enemyHand = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterViktorSummon = EnemyAIController.executeTurn(viktorState)
        val summoned = afterViktorSummon.enemyBoard.first()
        // Base Cithria is 2|2, +1|+1 from 3.0★ adventure, +1|+1 from Glorious Evolution -> 4|4 + extra keyword
        assertEquals(4, summoned.power)
        assertEquals(4, summoned.health)
        assertTrue(summoned.keywords.size >= 2)
    }

    @Test
    fun `wild inspiration discounts created cards concussive palm summons tail and jinx level up creates rocket`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // Wild Inspiration discounts created card by 1
        val createdSpell = HandCardInstance(
            instanceId = "hc_created",
            template = CardCatalog.getCard("card_concussive_palm"), // cost 4
            effectiveCost = 4,
            isCreated = true
        )
        val jinxBoard = UnitOnBoard(
            instanceId = "u_jinx",
            template = CardCatalog.getCard("card_jinx"),
            ownerIsPlayer = true,
            power = 4,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.QUICK_ATTACK)
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = listOf(CardCatalog.getPower("pow_wild_inspiration")),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 3,
            enemyMana = 1,
            playerHand = listOf(createdSpell),
            playerBoard = listOf(jinxBoard),
            enemyBoard = listOf(enemyUnit),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertEquals(3, state.effectivePlayCost(createdSpell))
        assertTrue(state.canPlayCard(createdSpell))

        // Playing Concussive Palm empties hand -> Jinx levels up (+4 Nexus dmg & creates Fleeting Super Mega Death Rocket!)
        val onStack = CombatEngine.playCardFromHand(state, "hc_created")
        val resolved = EnemyAIController.executeTurn(onStack)

        assertTrue(resolved.enemyBoard.first().isStunned)
        assertTrue(resolved.playerBoard.any { it.template.id == "card_tail_of_the_dragon" })
        assertTrue(resolved.playerBoard.first { it.template.id == "card_jinx" }.isLeveledUp)
        assertTrue(resolved.playerHand.any { it.template.id == "card_super_mega_rocket" && it.isCreated })
        // Enemy Nexus: 15 - 1 (Jinx 1★ on play) - 4 (Jinx Level Up) = 10
        assertEquals(10, resolved.enemyNexusHp)
    }

    @Test
    fun `slain units and resolved spells move to graveyard while survivors return to bench`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val strongAlly = UnitOnBoard(
            instanceId = "p_strong",
            template = CardCatalog.getCard("card_garen"), // 5|5
            ownerIsPlayer = true,
            power = 5,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.REGENERATION)
        )
        val fragileEnemy = UnitOnBoard(
            instanceId = "e_fragile",
            template = CardCatalog.getCard("card_cithria"), // 2|2
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val burstSpell = HandCardInstance(
            instanceId = "hc_burst",
            template = CardCatalog.getCard("card_prismatic_barrier"),
            effectiveCost = 2
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 4,
            enemyMana = 1,
            playerHasAttackToken = true,
            playerHand = listOf(burstSpell),
            playerBoard = listOf(strongAlly),
            enemyBoard = listOf(fragileEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // 1. Cast Burst spell -> effect activates -> spell goes to playerGraveyard
        state = CombatEngine.playCardFromHand(state, "hc_burst")
        assertTrue(state.playerGraveyard.any { it.id == "card_prismatic_barrier" })

        // 2. Move strongAlly from bench into Shared Battlefield attack & resolve block
        state = CombatEngine.enterAttackDeclaration(state)
        state = CombatEngine.toggleUnitAttack(state, "p_strong")
        assertEquals(listOf("p_strong"), state.attackingUnitIds)

        state = CombatEngine.commitPlayerAttack(state)
        state = state.copy(
            priorityHolder = PriorityHolder.ENEMY,
            blockPairings = mapOf("p_strong" to "e_fragile")
        )
        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = state.combatLog.toMutableList())

        // Slain enemy enters enemyGraveyard; surviving ally returns to bench (attackingUnitIds cleared)
        assertTrue(state.enemyBoard.isEmpty())
        assertTrue(state.enemyGraveyard.any { it.id == "card_cithria" })
        assertEquals(1, state.playerBoard.count { it.instanceId == "p_strong" })
    }

    @Test
    fun `anivia lv30 baseline stats starter deck and unlimited test resources match spec`() = runTest {
        val anivia = CardCatalog.getChampion("champ_anivia")
        assertEquals(9, anivia.baseDeck.size)
        assertEquals(18, anivia.baseDeck.sumOf { it.copies })
        assertEquals(listOf(2, 3, 4, 5, 6), anivia.starPowers.map { it.starLevel })

        val lv30Baseline = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 2,
            championLevel = 30
        )
        assertEquals(40, lv30Baseline.maxNexusHp)
        assertEquals(4, lv30Baseline.victoryHeal)
        assertEquals(300, lv30Baseline.startingGold)
        assertEquals(4, lv30Baseline.startingRerolls)
        assertEquals(1, lv30Baseline.startingManaBonus)
        assertEquals(0.445, lv30Baseline.rarityOdds.common, 0.0001)
        assertEquals(0.400, lv30Baseline.rarityOdds.rare, 0.0001)
        assertEquals(0.125, lv30Baseline.rarityOdds.epic, 0.0001)
        assertEquals(0.030, lv30Baseline.rarityOdds.legendary, 0.0001)

        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        assertEquals(999_999, vm.uiState.value.legendProfile.wildFragments)
        assertEquals(999_999, vm.uiState.value.legendProfile.starCrystals)
        assertEquals(999_999, vm.uiState.value.legendProfile.novaCrystals)
        assertEquals(999_999, vm.uiState.value.legendProfile.gemstones)
        assertTrue(vm.uiState.value.legendProfile.ownedRelicIds.containsAll(CardCatalog.relics.map { it.id }))

        val seededAnivia = vm.uiState.value.championLoadouts["champ_anivia"]!!
        assertTrue(seededAnivia.isUnlocked)
        assertEquals(30, seededAnivia.championLevel)

        // Upgrade Anivia star power sequentially skipping omitted 1★ (2 -> 3)
        vm.upgradeStarPower("champ_anivia", 3)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.championLoadouts["champ_anivia"]!!.starLevel)

        // Test Mode max helper jumps to 6★ Lv.30 + all constellation nodes
        vm.maxChampionForTesting("champ_anivia")
        advanceUntilIdle()
        val maxed = vm.uiState.value.championLoadouts["champ_anivia"]!!
        assertEquals(6, maxed.starLevel)
        assertEquals(30, maxed.championLevel)
        assertEquals(anivia.constellationNodes.size, maxed.unlockedConstellationNodes.size)
    }

    @Test
    fun `anivia elemental mana ramp glacial overflow caprine forge and clash of giants discount work`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val caprine = UnitOnBoard(
            instanceId = "u_cap",
            template = CardCatalog.getCard("card_flame_eyed_caprine"), // 3|2 Tough
            ownerIsPlayer = true,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        val mystics = UnitOnBoard(
            instanceId = "u_mys",
            template = CardCatalog.getCard("card_wild_mystics"), // 2|2 Overwhelm
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.OVERWHELM)
        )
        val harshWinds = HandCardInstance(
            instanceId = "hc_hw",
            template = CardCatalog.getCard("card_harsh_winds"), // 5-cost Burst Elemental
            effectiveCost = 5
        )
        val clash = HandCardInstance(
            instanceId = "hc_clash",
            template = CardCatalog.getCard("card_clash_of_giants"), // 16-cost Slow Elemental
            effectiveCost = 16
        )
        val enemyA = UnitOnBoard("e1", CardCatalog.getCard("card_cithria"), false, 5, 4, 4, emptySet())
        val enemyB = UnitOnBoard("e2", CardCatalog.getCard("card_laurent_protege"), false, 3, 4, 4, emptySet())

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 3, // Glacial Overflow + Winter's Boon II
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 9,
            playerMaxMana = 9,
            playerHand = listOf(harshWinds, clash),
            playerBoard = listOf(caprine, mystics),
            enemyBoard = listOf(enemyA, enemyB),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Playing Harsh Winds at 9 Max Mana:
        // - Frostbites 2 enemies
        // - Forges Flame-Eyed Caprine (+1|+1 -> 4|3)
        // - Winter's Boon II grants +1 empty Mana gem (Max 9 -> 10 -> Enlightened!)
        // - Wild Mystics hits Enlightened (+4|+4 -> 6|6)
        // - Clash of Giants drops cost 16 -> 15
        state = CombatEngine.playCardFromHand(state, "hc_hw")
        assertEquals(10, state.playerMaxMana)
        assertTrue(state.enemyBoard.all { it.isFrostbitten })
        val capAfter = state.playerBoard.first { it.instanceId == "u_cap" }
        assertEquals(4, capAfter.power)
        assertEquals(3, capAfter.health)
        val mysAfter = state.playerBoard.first { it.instanceId == "u_mys" }
        assertEquals(6, mysAfter.power)
        assertEquals(6, mysAfter.health)
        val clashInHand = state.playerHand.first { it.instanceId == "hc_clash" }
        assertEquals(15, state.effectivePlayCost(clashInHand))

        // Playing another Elemental spell while already Enlightened triggers Glacial Overflow (+1|+1 to all allies)
        val wings = HandCardInstance("hc_wings", CardCatalog.getCard("card_wings_of_cryophoenix"), effectiveCost = 7)
        state = state.copy(playerMana = 7, playerHand = state.playerHand + wings)
        state = CombatEngine.playCardFromHand(state, "hc_wings")
        // Both Caprine (Forged +1|+1 AND Glacial Overflow +1|+1) & Mystics (+1|+1) grew before stack resolution
        assertEquals(6, state.playerBoard.first { it.instanceId == "u_cap" }.power)
        assertEquals(7, state.playerBoard.first { it.instanceId == "u_mys" }.power)
    }

    @Test
    fun `crystal stormbird guardian follower mortal prophet endurance and eggnivia revival work`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val guardian = UnitOnBoard("u_guard", CardCatalog.getCard("card_guardian_follower"), true, 3, 4, 4, emptySet())
        val prophet = UnitOnBoard("u_proph", CardCatalog.getCard("card_mortal_prophet"), true, 0, 5, 5, emptySet())
        val anivia = UnitOnBoard("u_anivia", CardCatalog.getCard("card_anivia"), true, 3, 5, 5, setOf(Keyword.ELEMENTAL))
        val stormbird = HandCardInstance("hc_bird", CardCatalog.getCard("card_crystal_stormbird"), effectiveCost = 5)
        val frostbittenEnemy = UnitOnBoard(
            instanceId = "e_zero",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 4,
            maxHealth = 4,
            keywords = emptySet(),
            isFrostbitten = true // 0 Power -> takes double damage from Anivia 6★ Crystallize!
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 6, // Includes 4★ Endurance & 6★ Crystallize
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 9,
            playerNexusHp = 35,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 9,
            playerMaxMana = 9,
            playerHasAttackToken = true,
            playerHand = listOf(stormbird),
            playerBoard = listOf(guardian, prophet, anivia),
            enemyBoard = listOf(frostbittenEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Play Crystal Stormbird:
        // - Winter's Boon II pushes Max Mana 9 -> 10 (Anivia levels up to 4|6!)
        // - Skill deals 2 to all other units:
        //   * Guardian Follower takes 0 damage from allied skill (stays 3|4)
        //   * Mortal Prophet takes 2 -> 3 HP, Endurance +1|+1 -> 1|4, deals 1 to Enemy Nexus (20 -> 19)
        //   * Frostbitten Enemy (0 Power) takes 2 * 2 = 4 damage via 6★ Crystallize -> slain!
        state = CombatEngine.playCardFromHand(state, "hc_bird")
        assertEquals(4, state.playerBoard.first { it.instanceId == "u_guard" }.health)
        val prophAfter = state.playerBoard.first { it.instanceId == "u_proph" }
        assertEquals(1, prophAfter.power)
        assertEquals(4, prophAfter.health)
        assertEquals(19, state.enemyNexusHp)
        assertTrue(state.enemyBoard.isEmpty())
        assertTrue(state.playerBoard.first { it.instanceId == "u_anivia" }.isLeveledUp)

        // Anivia Last Breath -> Eggnivia -> Round Start hatches back into Leveled Anivia!
        val enemyStrong = UnitOnBoard("e_big", CardCatalog.getCard("card_garen"), false, 10, 10, 10, emptySet())
        state = state.copy(
            priorityHolder = PriorityHolder.PLAYER,
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            enemyBoard = listOf(enemyStrong),
            attackingUnitIds = listOf("u_anivia"),
            blockPairings = mapOf("u_anivia" to "e_big")
        )
        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = state.combatLog.toMutableList())
        val egg = state.playerBoard.find { it.instanceId == "u_anivia" }
        assertNotNull(egg)
        assertEquals("card_eggnivia", egg!!.template.id)
        assertEquals(2, egg.health)

        // Advance round -> Round Start at 10 Max Mana hatches Eggnivia into Leveled Anivia
        state = state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )
        val nextRound = CombatEngine.passPriority(state)
        val hatched = nextRound.playerBoard.find { it.instanceId == "u_anivia" }
        assertNotNull(hatched)
        assertEquals("card_anivia", hatched!!.template.id)
        assertTrue(hatched.isLeveledUp)
    }

    @Test
    fun `mana safeguards clamp bounds trigger perfected manaflow on round 1 and refill mana on item play`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia = CardCatalog.getChampion("champ_anivia")
        val runWithManaflow = RunState(
            runId = "run_mana_test",
            adventureId = adv.id,
            championId = "champ_anivia",
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 40,
            maxNexusHp = 40,
            gold = 300,
            rerollsRemaining = 4,
            revivesRemaining = 1,
            activePowerIds = listOf("pow_manaflow"),
            runDeck = anivia.baseDeck
        )
        // High starting mana sources (5★ + Lv30 + relics) are clamped to max 10
        val maxedLoadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 6,
            championLevel = 30,
            equippedRelics = listOf("relic_starforged", "relic_starforged", "relic_starforged")
        )
        val clampedEncounter = CombatEngine.createEncounter(runWithManaflow, maxedLoadout, adv, node)
        assertTrue(clampedEncounter.playerMaxMana in 1..10)
        assertTrue(clampedEncounter.playerMana in 0..clampedEncounter.playerMaxMana)

        // Round 1 with Perfected Manaflow grants +1 Mana gem immediately (base 2 -> 3)
        val baseLoadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 2,
            championLevel = 30
        )
        val round1Manaflow = CombatEngine.createEncounter(runWithManaflow, baseLoadout, adv, node)
        assertEquals(3, round1Manaflow.playerMaxMana)
        assertEquals(3, round1Manaflow.playerMana)

        // Item with refillManaOnPlay refills playerMana up to playerMaxMana
        val refillItem = ItemTemplate(
            id = "test_mana_refill",
            name = "Mana Crystal",
            rarity = Rarity.RARE,
            category = ItemCategory.ECONOMY,
            description = "Play: Refill 2 Mana",
            refillManaOnPlay = 2
        )
        val unitWithRefill = HandCardInstance(
            instanceId = "hc_refill",
            template = CardCatalog.getCard("card_wild_mystics"), // cost 2
            attachedItems = listOf(refillItem),
            effectiveCost = 2
        )
        val manaState = round1Manaflow.copy(
            playerMana = 3,
            playerMaxMana = 3,
            playerHand = listOf(unitWithRefill)
        )
        val afterPlay = CombatEngine.playCardFromHand(manaState, "hc_refill")
        assertEquals(3, afterPlay.playerMana)
        assertTrue(afterPlay.playerMana <= afterPlay.playerMaxMana)
    }

    @Test
    fun `champion roster only contains anivia stale db loadouts are cleaned and deck cards have unique art`() = runTest {
        assertEquals(listOf("champ_anivia"), CardCatalog.champions.map { it.id })

        val dao = FakeGameDao()
        val repo = GameRepository(dao, testDispatcher)
        // Simulate stale database rows from earlier multi-champion builds
        dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(ChampionLoadout(championId = "champ_jinx", isUnlocked = true)))
        dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(ChampionLoadout(championId = "champ_yasuo", isUnlocked = true)))
        repo.saveActiveRun(
            RunState(
                runId = "stale_run",
                adventureId = "adv_demacia_outskirts",
                championId = "champ_jinx",
                currentNodeId = "d_fight_1a",
                completedNodeIds = emptyList(),
                currentNexusHp = 30,
                maxNexusHp = 30,
                gold = 100,
                rerollsRemaining = 2,
                revivesRemaining = 1,
                activePowerIds = emptyList(),
                runDeck = emptyList()
            )
        )

        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        assertEquals(setOf("champ_anivia"), vm.uiState.value.championLoadouts.keys)
        assertNotNull(dao.getChampionLoadout("champ_jinx"))
        assertNotNull(dao.getChampionLoadout("champ_yasuo"))
        assertEquals("champ_anivia", vm.uiState.value.selectedChampionId)
        assertNull(dao.getSavedRun())

        // All 9 cards in Anivia's starter deck + Eggnivia token have distinct non-null illustrations
        val aniviaCardIds = CardCatalog.getChampion("champ_anivia").baseDeck.map { it.cardId } + "card_eggnivia"
        val artResources = aniviaCardIds.map { cardId ->
            val res = cardPortraitRes(cardId)
            assertNotNull("Expected illustration for $cardId", res)
            res!!
        }
        assertEquals(10, artResources.size)
        assertEquals(10, artResources.toSet().size)
    }

    @Test
    fun `zero damage and frostbitten attackers do not break barrier or spellshield`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val frostbittenAtk = UnitOnBoard(
            instanceId = "atk_frost",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 5,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.IMPACT),
            impactStacks = 1,
            isFrostbitten = true
        )
        val barrierBlocker = UnitOnBoard(
            instanceId = "blk_bar",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.BARRIER),
            hasBarrier = true
        )
        val spellShieldEnemy = UnitOnBoard(
            instanceId = "e_ss",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.SPELLSHIELD),
            hasSpellShield = true
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(frostbittenAtk),
            enemyBoard = listOf(barrierBlocker, spellShieldEnemy),
            attackingUnitIds = listOf("atk_frost"),
            blockPairings = mapOf("atk_frost" to "blk_bar"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        // 0-power Frostbitten attacker neither breaks blocker's Barrier nor deals Impact
        assertTrue(afterCombat.enemyBoard.first { it.instanceId == "blk_bar" }.hasBarrier)
        assertEquals(20, afterCombat.enemyNexusHp)

        // Direct 0-damage spell via resolveSingleSpellEffect does not break SpellShield or Barrier
        val zeroDmgSpell = CardCatalog.getCard("card_mystic_shot").copy(effectValue = 0)
        val afterZeroSpell = CombatEngine.resolveSingleSpellEffect(
            afterCombat,
            zeroDmgSpell,
            sourceIsPlayer = true,
            bonusDmg = 0,
            logs = mutableListOf()
        )
        assertTrue(afterZeroSpell.enemyBoard.first { it.instanceId == "blk_bar" }.hasBarrier)
        assertTrue(afterZeroSpell.enemyBoard.first { it.instanceId == "e_ss" }.hasSpellShield)
    }

    @Test
    fun `enemy overwhelm and double attack deal excess damage to player nexus accounting for tough`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // Enemy 6|5 Double Attack + Overwhelm vs Player 3|2 Tough
        // 1st strike: effectiveHealth = 2 + 1 (Tough) = 3 -> excess = 6 - 3 = 3 to Player Nexus, blocker dies
        // 2nd strike: blocker dead -> Overwhelm deals full 6 to Player Nexus -> total 9 damage (30 -> 21)
        val enemyDoubleOverwhelm = UnitOnBoard(
            instanceId = "e_da_ow",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = false,
            power = 6,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.DOUBLE_ATTACK, Keyword.OVERWHELM)
        )
        val playerToughBlocker = UnitOnBoard(
            instanceId = "p_tough",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        // Standard enemy 5|5 Overwhelm vs Player 3|2 Tough -> effectiveHealth = 3 -> excess = 2 (21 -> 19)
        val enemyStdOverwhelm = UnitOnBoard(
            instanceId = "e_std_ow",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = false,
            power = 5,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.OVERWHELM)
        )
        val playerToughBlocker2 = UnitOnBoard(
            instanceId = "p_tough_2",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 1,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )

        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(playerToughBlocker, playerToughBlocker2),
            enemyBoard = listOf(enemyDoubleOverwhelm, enemyStdOverwhelm),
            attackingUnitIds = listOf("e_da_ow", "e_std_ow"),
            blockPairings = mapOf("e_da_ow" to "p_tough", "e_std_ow" to "p_tough_2"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            playerInitiatedCombat = false
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = false, logs = mutableListOf())
        assertEquals(30 - 9 - 2, afterCombat.playerNexusHp)
        assertTrue(afterCombat.playerBoard.isEmpty())
    }

    @Test
    fun `fury triggers in standard combat resolution for both attacker and blocker`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val furyAttacker = UnitOnBoard(
            instanceId = "p_fury",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.FURY)
        )
        val weakBlocker = UnitOnBoard(
            instanceId = "e_weak",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val weakAttacker = UnitOnBoard(
            instanceId = "p_weak",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 1,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val furyBlocker = UnitOnBoard(
            instanceId = "e_fury",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.FURY)
        )

        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(furyAttacker, weakAttacker),
            enemyBoard = listOf(weakBlocker, furyBlocker),
            attackingUnitIds = listOf("p_fury", "p_weak"),
            blockPairings = mapOf("p_fury" to "e_weak", "p_weak" to "e_fury"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        val survivingFuryAtk = afterCombat.playerBoard.single { it.instanceId == "p_fury" }
        assertEquals(5, survivingFuryAtk.power) // 4 + 1 from Fury
        assertEquals(3, survivingFuryAtk.health) // 4 - 2 + 1 = 3

        val survivingFuryBlk = afterCombat.enemyBoard.single { it.instanceId == "e_fury" }
        assertEquals(4, survivingFuryBlk.power) // 3 + 1 from Fury
        assertEquals(4, survivingFuryBlk.health) // 4 - 1 + 1 = 4
    }

    @Test
    fun `innate barrier keyword persists after barrier breaks and round end`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val innateBarrierUnit = UnitOnBoard(
            instanceId = "p_bar",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = true,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.BARRIER, Keyword.DEATHLESS),
            hasBarrier = true,
            hasDeathless = true
        )
        val enemyHitter = UnitOnBoard(
            instanceId = "e_hit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 5,
            maxHealth = 5,
            keywords = emptySet()
        )
        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(innateBarrierUnit),
            enemyBoard = listOf(enemyHitter),
            attackingUnitIds = listOf("p_bar"),
            blockPairings = mapOf("p_bar" to "e_hit")
        )

        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        val afterHit = state.playerBoard.single { it.instanceId == "p_bar" }
        assertFalse(afterHit.hasBarrier)
        assertTrue(afterHit.keywords.contains(Keyword.BARRIER))

        // Next round clears temporary hasBarrier without stripping innate Keyword.BARRIER
        state = CombatEngine.advanceToNextRound(state.copy(playerBoard = listOf(afterHit.copy(hasBarrier = true))), mutableListOf())
        val nextRoundUnit = state.playerBoard.single { it.instanceId == "p_bar" }
        assertFalse(nextRoundUnit.hasBarrier)
        assertTrue(nextRoundUnit.keywords.contains(Keyword.BARRIER))

        // Deathless revival restores hasBarrier for units with innate Keyword.BARRIER
        val lethalShot = CardCatalog.getCard("card_mystic_shot").copy(effectValue = 5)
        val afterDeathless = CombatEngine.resolveSingleSpellEffect(
            state.copy(playerBoard = listOf(nextRoundUnit)),
            lethalShot,
            sourceIsPlayer = false,
            logs = mutableListOf()
        )
        val revived = afterDeathless.playerBoard.single { it.instanceId == "p_bar" }
        assertEquals(1, revived.health)
        assertTrue(revived.hasBarrier)
    }

    @Test
    fun `hallowed attack buff lasts only for current round and resets at round transition`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val ally = UnitOnBoard(
            instanceId = "p_hallow_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerHallowedStacks = 3,
            playerBoard = listOf(ally),
            attackingUnitIds = listOf("p_hallow_atk"),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE
        )

        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(5, state.playerBoard.single { it.instanceId == "p_hallow_atk" }.power)

        state = CombatEngine.advanceToNextRound(state, mutableListOf())
        assertEquals(2, state.round)
        assertEquals(2, state.playerBoard.single { it.instanceId == "p_hallow_atk" }.power)
    }

    @Test
    fun `enemy lifesteal heals enemy nexus and ashe frostbite aura does not buff frostbitten allies`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val enemyLifesteal = UnitOnBoard(
            instanceId = "e_ls",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.LIFESTEAL)
        )
        val enemyFrozen = UnitOnBoard(
            instanceId = "e_frz",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = emptySet(),
            isFrostbitten = true
        )
        val playerFrostbittenAtk = UnitOnBoard(
            instanceId = "p_frz",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet(),
            isFrostbitten = true
        )
        val playerHealthyAtk = UnitOnBoard(
            instanceId = "p_ok",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )

        // 1. Enemy Lifesteal unblocked strike heals Enemy Nexus (10 -> 14), not Player Nexus (20 -> 16)
        val enemyAtkState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_ashe",
            starLevel = 2,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 20,
            playerMaxHp = 30,
            enemyNexusHp = 10,
            enemyMaxHp = 20,
            enemyBoard = listOf(enemyLifesteal, enemyFrozen),
            playerBoard = listOf(playerFrostbittenAtk, playerHealthyAtk),
            attackingUnitIds = listOf("e_ls"),
            blockPairings = emptyMap(),
            playerInitiatedCombat = false
        )
        val afterEnemyAtk = CombatEngine.resolveCombatResolution(enemyAtkState, playerIsAttacking = false, logs = mutableListOf())
        assertEquals(16, afterEnemyAtk.playerNexusHp)
        assertEquals(14, afterEnemyAtk.enemyNexusHp)

        // 2. Ashe 2★ Frostbite aura (+1 per Frostbitten enemy):
        //    - p_frz is Frostbitten -> stays 0 strike power, deals 0 to enemy Nexus
        //    - p_ok is NOT Frostbitten -> gets +1 aura -> deals 3 to enemy Nexus (14 -> 11)
        val playerAtkState = afterEnemyAtk.copy(
            attackingUnitIds = listOf("p_frz", "p_ok"),
            blockPairings = emptyMap(),
            playerInitiatedCombat = true
        )
        val afterPlayerAtk = CombatEngine.resolveCombatResolution(playerAtkState, playerIsAttacking = true, logs = mutableListOf())
        assertEquals(11, afterPlayerAtk.enemyNexusHp)
    }

    @Test
    fun `strict id lookups log missing ids and findOrNull returns null`() {
        assertNull(CardCatalog.findCardOrNull("missing_card_id"))
        assertNull(CardCatalog.findItemOrNull("missing_item_id"))
        assertNull(CardCatalog.findPowerOrNull("missing_power_id"))
        assertNull(CardCatalog.findRelicOrNull("missing_relic_id"))
        assertNull(CardCatalog.findChampionOrNull("missing_champion_id"))
        assertNull(AdventureCatalog.findAdventureOrNull("missing_adventure_id"))

        val recordedCardErrors = mutableListOf<Pair<String, String>>()
        val recordedAdvErrors = mutableListOf<String>()
        CardCatalog.missingIdObserver = { kind, id -> recordedCardErrors.add(kind to id) }
        AdventureCatalog.missingIdObserver = { id -> recordedAdvErrors.add(id) }
        try {
            CardCatalog.getCard("missing_card_id")
            CardCatalog.getItem("missing_item_id")
            CardCatalog.getPower("missing_power_id")
            CardCatalog.getRelic("missing_relic_id")
            CardCatalog.getChampion("missing_champion_id")
            AdventureCatalog.getAdventure("missing_adventure_id")
        } finally {
            CardCatalog.missingIdObserver = null
            AdventureCatalog.missingIdObserver = null
        }

        assertEquals(
            listOf(
                "card" to "missing_card_id",
                "item" to "missing_item_id",
                "power" to "missing_power_id",
                "relic" to "missing_relic_id",
                "champion" to "missing_champion_id"
            ),
            recordedCardErrors
        )
        assertEquals(listOf("missing_adventure_id"), recordedAdvErrors)
    }

    @Test
    fun `celestial might and nightmare curse mutators apply in combat encounters`() {
        val targonAdv = AdventureCatalog.getAdventure("adv_targon_nightmare")
        val champ = CardCatalog.getChampion("champ_anivia")
        val loadout = ChampionLoadout(championId = champ.id, isUnlocked = true, starLevel = 1)
        val runState = RunState(
            runId = "run_mut_test",
            adventureId = targonAdv.id,
            championId = champ.id,
            currentNodeId = "d_fight_1a",
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 0,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = champ.baseDeck
        )

        // Regular node with enemyStartingMana = 1 gets bumped to 4 by mut_celestial_might
        // and does NOT get card_curse_suppression because node.type != FINAL_BOSS
        val lowManaRegularNode = AdventureCatalog.getAdventure("adv_demacia_outskirts")
            .mapNodes.first { it.id == "d_fight_1a" }
        val regularCombat = CombatEngine.createEncounter(runState, loadout, targonAdv, lowManaRegularNode)
        assertEquals(4, regularCombat.enemyMana)
        assertEquals(4, regularCombat.enemyMaxMana)
        assertTrue(regularCombat.playerHand.none { it.template.id == "card_curse_suppression" })

        // Final Boss node with mut_nightmare_curse afflicts opening hand with card_curse_suppression
        val bossNode = targonAdv.mapNodes.first { it.type == NodeType.FINAL_BOSS }
        val bossCombat = CombatEngine.createEncounter(runState, loadout, targonAdv, bossNode)
        assertEquals(4, bossCombat.enemyMana)
        assertTrue(bossCombat.playerHand.any { it.template.id == "card_curse_suppression" })
    }

    @Test
    fun `drawing from empty deck deals fatigue damage and can end game`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerEmptyState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 4,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerDrawPile = emptyList()
        )

        val afterFirstPlayerFatigue = CombatEngine.drawPlayerCard(playerEmptyState)
        assertEquals(4 - CombatEngine.EMPTY_DECK_FATIGUE_DAMAGE, afterFirstPlayerFatigue.playerNexusHp)
        assertNull(afterFirstPlayerFatigue.playerWon)
        assertTrue(afterFirstPlayerFatigue.combatLog.any { it.contains("hết", ignoreCase = true) })

        val afterLethalPlayerFatigue = CombatEngine.drawPlayerCard(afterFirstPlayerFatigue)
        assertEquals(0, afterLethalPlayerFatigue.playerNexusHp)
        assertEquals(CombatPhase.GAME_OVER, afterLethalPlayerFatigue.phase)
        assertEquals(false, afterLethalPlayerFatigue.playerWon)

        val enemyEmptyState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 20,
            playerMaxHp = 30,
            enemyNexusHp = CombatEngine.EMPTY_DECK_FATIGUE_DAMAGE,
            enemyMaxHp = 20,
            enemyDrawPool = emptyList()
        )
        val afterLethalEnemyFatigue = CombatEngine.drawEnemyCard(enemyEmptyState)
        assertEquals(0, afterLethalEnemyFatigue.enemyNexusHp)
        assertEquals(CombatPhase.GAME_OVER, afterLethalEnemyFatigue.phase)
        assertEquals(true, afterLethalEnemyFatigue.playerWon)
    }

    @Test
    fun `player and ai share fast spell response windows during attack and block phases`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerUnit = UnitOnBoard(
            instanceId = "p_unit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_unit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val playerFastShot = HandCardInstance(
            instanceId = "hc_mystic",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val playerSlowSpell = HandCardInstance(
            instanceId = "hc_slow",
            template = CardCatalog.getCard("card_golden_aegis"),
            effectiveCost = 4
        )

        // 1. Attack declaration spell-response window (COMBAT_DECLARE_ATTACK_PHASE)
        var atkWindowState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 5,
            playerMaxMana = 5,
            enemyMana = 5,
            enemyMaxMana = 5,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerHand = listOf(playerFastShot, playerSlowSpell),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")),
            attackingUnitIds = listOf("p_unit"),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertTrue(atkWindowState.canPlayCard(playerFastShot))
        assertFalse(atkWindowState.canPlayCard(playerSlowSpell))

        atkWindowState = CombatEngine.playCardFromHand(atkWindowState, "hc_mystic")
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.ENEMY, atkWindowState.priorityHolder)
        assertEquals(1, atkWindowState.spellStack.size)

        // AI responds with reactive Fast spell (Steel Tempest) and returns priority to player
        atkWindowState = EnemyAIController.executeTurn(atkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, atkWindowState.priorityHolder)
        assertEquals(2, atkWindowState.spellStack.size)

        // Player passes to resolve stack LIFO; priority returns to player in COMBAT_DECLARE_ATTACK_PHASE
        atkWindowState = CombatEngine.passPriority(atkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, atkWindowState.priorityHolder)
        assertTrue(atkWindowState.spellStack.isEmpty())
        assertTrue(atkWindowState.playerBoard.single().isStunned)
        assertTrue(atkWindowState.enemyBoard.isEmpty())

        // 2. Block declaration spell-response window (COMBAT_DECLARE_BLOCK_PHASE)
        var blkWindowState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 5,
            playerMaxMana = 5,
            enemyMana = 5,
            enemyMaxMana = 5,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerHand = listOf(playerFastShot, playerSlowSpell),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")),
            attackingUnitIds = listOf("e_unit"),
            playerInitiatedCombat = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertTrue(blkWindowState.canPlayCard(playerFastShot))
        assertFalse(blkWindowState.canPlayCard(playerSlowSpell))

        blkWindowState = CombatEngine.playCardFromHand(blkWindowState, "hc_mystic")
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.ENEMY, blkWindowState.priorityHolder)
        assertEquals(1, blkWindowState.spellStack.size)

        // AI responds with reactive Fast spell and passes priority back to defending player
        blkWindowState = EnemyAIController.executeTurn(blkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, blkWindowState.priorityHolder)
        assertEquals(2, blkWindowState.spellStack.size)

        // Defending player commits block -> resolves spell stack first, then combat resolution
        blkWindowState = CombatEngine.passPriority(blkWindowState)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, blkWindowState.priorityHolder)
        assertTrue(blkWindowState.spellStack.isEmpty())
        // Mystic Shot (2 dmg) killed e_unit (2 hp) before combat resolution hit Player Nexus
        assertEquals(30, blkWindowState.playerNexusHp)
    }

    @Test
    fun `new battle resets playerMaxMana and enemyMaxMana to match baseline capped at 10`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia = CardCatalog.getChampion("champ_anivia")
        val star1Loadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 1,
            championLevel = 1
        )
        val runState = RunState(
            runId = "run_reset_mana",
            adventureId = adv.id,
            championId = "champ_anivia",
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = anivia.baseDeck
        )

        var firstBattle = CombatEngine.createEncounter(runState, star1Loadout, adv, node)
        assertEquals(1, firstBattle.playerMaxMana)
        assertEquals(1, firstBattle.enemyMaxMana)

        // Advance first battle to Round 7 (7/7 mana)
        repeat(6) {
            firstBattle = CombatEngine.advanceToNextRound(firstBattle, mutableListOf())
        }
        assertEquals(7, firstBattle.playerMaxMana)
        assertEquals(7, firstBattle.enemyMaxMana)

        // Advance beyond Round 10 -> strictly capped at 10
        repeat(5) {
            firstBattle = CombatEngine.advanceToNextRound(firstBattle, mutableListOf())
        }
        assertEquals(10, firstBattle.playerMaxMana)
        assertEquals(10, firstBattle.enemyMaxMana)

        // Starting a fresh encounter resets playerMaxMana and enemyMaxMana to baseline (1 for 1★, 2 for 2★)
        val freshBattle = CombatEngine.createEncounter(runState, star1Loadout, adv, node)
        assertEquals(1, freshBattle.round)
        assertEquals(1, freshBattle.playerMana)
        assertEquals(1, freshBattle.playerMaxMana)
        assertEquals(1, freshBattle.enemyMana)
        assertEquals(1, freshBattle.enemyMaxMana)

        val star2Loadout = star1Loadout.copy(starLevel = 2)
        val star2Battle = CombatEngine.createEncounter(runState, star2Loadout, adv, node)
        assertEquals(2, star2Battle.playerMana)
        assertEquals(2, star2Battle.playerMaxMana)
    }

    @Test
    fun `bot attack round gives priority to AI first and blocks player attack declarations`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val playerUnit = UnitOnBoard(
            instanceId = "p_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        // Round 1 ends -> Round 2 is Bot's Attack Round (enemyHasAttackToken = true, priorityHolder = ENEMY)
        val round1End = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 1,
            playerMaxMana = 1,
            enemyMana = 1,
            enemyMaxMana = 1,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerDrawPile = listOf(
                HandCardInstance("hc_draw", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
            ),
            enemyDrawPool = listOf(CardCatalog.getCard("card_cithria")),
            enemyHand = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )

        var round2 = CombatEngine.passPriority(round1End)
        assertEquals(2, round2.round)
        assertFalse(round2.playerHasAttackToken)
        assertTrue(round2.enemyHasAttackToken)
        assertEquals(PriorityHolder.ENEMY, round2.priorityHolder)

        // Player cannot play cards or enter/toggle/commit attack while priorityHolder == ENEMY
        val playerHandCard = round2.playerHand.first()
        assertFalse(round2.canPlayCard(playerHandCard))
        assertEquals(round2, CombatEngine.enterAttackDeclaration(round2))

        // AI acts first on Bot Attack round (summons 1-cost unit -> passes priority to PLAYER)
        round2 = EnemyAIController.executeTurn(round2)
        assertEquals(2, round2.enemyBoard.size)
        assertEquals(PriorityHolder.PLAYER, round2.priorityHolder)

        // Player STILL cannot initiate an attack on Bot's attack round because playerHasAttackToken == false
        assertFalse(round2.playerHasAttackToken)
        val attemptedAttack = CombatEngine.enterAttackDeclaration(round2)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, attemptedAttack.phase)
        val forcedAttack = CombatEngine.toggleUnitAttack(
            round2.copy(phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE),
            "p_1"
        )
        assertTrue(forcedAttack.attackingUnitIds.isEmpty())
        val forcedCommit = CombatEngine.commitPlayerAttack(
            round2.copy(
                phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
                attackingUnitIds = listOf("p_1")
            )
        )
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, forcedCommit.phase)
    }

    @Test
    fun `full bot attack to player block with burst spell and AI fast response resolves cleanly`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerBlocker = UnitOnBoard(
            instanceId = "p_blk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        val enemyAttacker = UnitOnBoard(
            instanceId = "e_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )
        val playerBarrierSpell = HandCardInstance(
            instanceId = "hc_bar",
            template = CardCatalog.getCard("card_prismatic_barrier"), // 2-cost Burst
            effectiveCost = 2
        )
        val playerFastSpell = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"), // 2-cost Fast
            effectiveCost = 2
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 2,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 4,
            playerMaxMana = 4,
            enemyMana = 4,
            enemyMaxMana = 4,
            playerHasAttackToken = false,
            enemyHasAttackToken = true,
            playerInitiatedCombat = false,
            playerBoard = listOf(playerBlocker),
            enemyBoard = listOf(enemyAttacker),
            playerHand = listOf(playerBarrierSpell, playerFastSpell),
            playerDrawPile = listOf(
                HandCardInstance("hc_draw", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
            ),
            enemyHand = listOf(CardCatalog.getCard("card_mystic_shot")), // AI holds Fast burn spell
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )

        // 1. AI has Attack Token & priority -> AI declares attack with e_atk (4|4 vs 3|3)
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertFalse(state.playerInitiatedCombat)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(listOf("e_atk"), state.attackingUnitIds)

        // 2. Player assigns blocker and casts Burst Prismatic Barrier (keeps priority on PLAYER)
        state = CombatEngine.assignPlayerBlocker(state, attackerId = "e_atk", blockerId = "p_blk")
        assertEquals(mapOf("e_atk" to "p_blk"), state.blockPairings)
        state = CombatEngine.playCardFromHand(state, "hc_bar")
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.playerBoard.single().hasBarrier)
        assertTrue(state.playerCastSpellInBlock)

        // 3. Player commits block -> passes priority back to AI to respond with Fast/Burst spells
        state = CombatEngine.commitPlayerBlock(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.blockCommitted)
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)

        // 4. AI casts Mystic Shot (2 dmg) to pop Barrier -> passes priority back to PLAYER
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(1, state.spellStack.size)

        // 5. Player passes/commits on stack -> Mystic Shot pops Barrier, combat strikes trade (p_blk slain, e_atk drops to 1 HP), return to MAIN_ACTION_PHASE
        state = CombatEngine.commitPlayerBlock(state)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.spellStack.isEmpty())
        assertFalse(state.blockCommitted)
        assertEquals(30, state.playerNexusHp)
        assertTrue(state.playerBoard.isEmpty())
        assertEquals(1, state.enemyBoard.single().health)
    }

    @Test
    fun `player attack with AI reactive fast spell gives player final response window before resolving`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerAttacker = UnitOnBoard(
            instanceId = "p_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )
        val enemyDefender = UnitOnBoard(
            instanceId = "e_def",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 3,
            playerMaxMana = 3,
            enemyMana = 4,
            enemyMaxMana = 4,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerBoard = listOf(playerAttacker),
            enemyBoard = listOf(enemyDefender),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")), // 2-cost Fast stun
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_atk"),
            priorityHolder = PriorityHolder.PLAYER
        )

        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.playerInitiatedCombat)
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)

        // AI assigns blocker AND casts Steel Tempest -> yields final response window to PLAYER
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.playerInitiatedCombat)
        assertTrue(state.blockCommitted)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(1, state.spellStack.size)

        // Player clicks OK / passes -> resolves Steel Tempest (stuns p_atk) and resolves combat cleanly -> MAIN_ACTION_PHASE
        state = CombatEngine.passPriority(state)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertTrue(state.spellStack.isEmpty())
        assertTrue(state.attackingUnitIds.isEmpty())
        assertEquals(20, state.enemyNexusHp)
        assertEquals(4, state.enemyBoard.single().health)
    }
}
