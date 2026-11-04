package com.mario.buonipasto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mario.buonipasto.R
import com.mario.buonipasto.data.DatiApp
import com.mario.buonipasto.data.Feed
import com.mario.buonipasto.data.Member
import com.mario.buonipasto.data.Movement
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.Session

const val TAB_BUONI = 0
const val TAB_LISTA = 1
const val TAB_ASSISTENTE = 2
const val TAB_IMPOSTAZIONI = 3

private val GrigioSistema = Color(0xFF8E8E93)

@Composable
fun AppRoot(
    repository: Repository,
    session: Session,
    tabRichiesta: Int,
    onTabGestita: () -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_BUONI) }
    LaunchedEffect(tabRichiesta) {
        if (tabRichiesta >= 0) {
            tab = tabRichiesta
            onTabGestita()
        }
    }

    // i dati del gruppo restano aggiornati in tutte le schede (e li legge anche l'assistente)
    val membersFlow = remember(session.groupCode) { repository.observeMembers(session.groupCode) }
    val movementsFlow = remember(session.groupCode) { repository.observeMovements(session.groupCode) }
    val membersFeed by membersFlow.collectAsStateWithLifecycle(initialValue = null as Feed<Member>?)
    val movementsFeed by movementsFlow.collectAsStateWithLifecycle(initialValue = null as Feed<Movement>?)
    LaunchedEffect(session, membersFeed, movementsFeed) {
        DatiApp.session = session
        membersFeed?.let { DatiApp.members = it.items }
        movementsFeed?.let { DatiApp.movements = it.items }
    }

    Scaffold(
        containerColor = Ios.background,
        bottomBar = { TabBar(tab) { tab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                TAB_BUONI -> HomeScreen(repository, session, membersFeed, movementsFeed)
                TAB_LISTA -> ShoppingScreen()
                TAB_ASSISTENTE -> AssistantScreen(onVaiImpostazioni = { tab = TAB_IMPOSTAZIONI })
                else -> SettingsScreen(repository, session, membersFeed?.items.orEmpty(), onLogout)
            }
        }
    }
}

@Composable
private fun TabBar(selezionata: Int, onSeleziona: (Int) -> Unit) {
    val voci = listOf(
        R.drawable.ic_tab_buoni to R.string.tab_buoni,
        R.drawable.ic_tab_lista to R.string.tab_lista,
        R.drawable.ic_tab_chat to R.string.tab_assistente,
        R.drawable.ic_tab_settings to R.string.tab_impostazioni,
    )
    Column(Modifier.fillMaxWidth().background(Ios.chrome)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp)) {
            voci.forEachIndexed { i, (icona, nome) ->
                val attiva = i == selezionata
                val colore by animateColorAsState(if (attiva) Ios.accent else GrigioSistema, label = "tab")
                val scala by animateFloatAsState(
                    if (attiva) 1.08f else 1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "rimbalzo",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSeleziona(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(icona),
                        contentDescription = stringResource(nome),
                        tint = colore,
                        modifier = Modifier.size(27.dp).scale(scala),
                    )
                }
            }
        }
    }
}
