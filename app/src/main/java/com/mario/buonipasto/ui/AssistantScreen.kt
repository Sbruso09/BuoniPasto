package com.mario.buonipasto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mario.buonipasto.R
import com.mario.buonipasto.ai.ChatStorico
import com.mario.buonipasto.ai.Motore
import kotlinx.coroutines.delay

/** Testo dell'assistente: **grassetto** ed elenchi con "- " diventano grassetto e punti elenco. */
fun formattaAi(testo: String): AnnotatedString = buildAnnotatedString {
    val righe = testo.replace("\r", "").split("\n")
    righe.forEachIndexed { i, rigaOriginale ->
        var riga = rigaOriginale
        val t = riga.trimStart()
        if (t.startsWith("- ") || t.startsWith("* ")) riga = "•  " + t.substring(2)
        riga = riga.replace(Regex("^#{1,6}\\s*"), "")
        var j = 0
        var grassetto = false
        while (j < riga.length) {
            val k = riga.indexOf("**", j)
            val pezzo = if (k < 0) riga.substring(j) else riga.substring(j, k)
            if (grassetto) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(pezzo) } else append(pezzo)
            if (k < 0) break
            grassetto = !grassetto
            j = k + 2
        }
        if (i < righe.size - 1) append("\n")
    }
}

private data class Suggerimento(val etichetta: Int, val testo: Int, val completare: Boolean)

private val SUGGERIMENTI = listOf(
    Suggerimento(R.string.chip_prodotto_l, R.string.chip_prodotto_t, true),
    Suggerimento(R.string.chip_mese_l, R.string.chip_mese_t, false),
    Suggerimento(R.string.chip_chi_l, R.string.chip_chi_t, false),
    Suggerimento(R.string.chip_buoni_l, R.string.chip_buoni_t, false),
    Suggerimento(R.string.chip_lista_l, R.string.chip_lista_t, false),
    Suggerimento(R.string.chip_economico_l, R.string.chip_economico_t, true),
    Suggerimento(R.string.chip_confronto_l, R.string.chip_confronto_t, true),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssistantScreen(onVaiImpostazioni: () -> Unit) {
    val context = LocalContext.current
    val stato by Motore.stato.collectAsStateWithLifecycle()
    var messaggi by remember { mutableStateOf(ChatStorico.leggi(context)) }
    LaunchedEffect(stato.versione) { messaggi = ChatStorico.leggi(context) }
    var domanda by remember { mutableStateOf("") }
    var chiediNuova by remember { mutableStateOf(false) }
    val chiavi = remember { Motore.chiaviComplete(context) }
    val lista = rememberLazyListState()

    // mentre guardi la chat la risposta si vede qui: niente notifica
    DisposableEffect(Unit) {
        Motore.chatVisibile = true
        Motore.togliNotifica(context)
        onDispose { Motore.chatVisibile = false }
    }

    var adesso by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stato.inCorso) {
        while (stato.inCorso) {
            adesso = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elementi = messaggi.size + if (stato.inCorso) 1 else 0
    LaunchedEffect(elementi, stato.parziale.length / 80) {
        if (elementi > 0) lista.animateScrollToItem(elementi - 1)
    }

    Column(Modifier.fillMaxSize().background(Ios.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LargeTitle(stringResource(R.string.tab_assistente), Modifier.weight(1f))
            if (messaggi.isNotEmpty()) {
                Box(Modifier.size(38.dp).premibile { chiediNuova = true }.clip(CircleShape).background(Ios.fillTertiary), Alignment.Center) {
                    Icon(Icons.Default.Create, stringResource(R.string.nuova_chat), tint = Ios.accent, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (!chiavi) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Ios.surfaceElevated), Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tab_chat), null, tint = Ios.accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.ai_chiavi_titolo), style = IosText.title3, color = Ios.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.ai_chiavi_testo), style = IosText.subhead, color = Ios.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.vai_impostazioni), onVaiImpostazioni, Modifier.padding(horizontal = 24.dp))
            }
            return@Column
        }

        Box(Modifier.weight(1f)) {
            if (messaggi.isEmpty() && !stato.inCorso) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Ios.accent), Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_tab_chat), null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.ai_intro), style = IosText.subhead, color = Ios.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            LazyColumn(
                state = lista,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(messaggi) { i, m ->
                    val stesso = i > 0 && messaggi[i - 1].ruolo == m.ruolo
                    Bolla(m, Modifier.padding(top = if (stesso) 0.dp else 8.dp), context)
                }
                if (stato.inCorso) {
                    item {
                        val testo = if (stato.parziale.isNotEmpty()) null else {
                            val sec = (adesso - stato.inizio) / 1000
                            (stato.fase.ifEmpty { "•••" }) + if (sec >= 3) "  ·  $sec s" else ""
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Box(
                                Modifier
                                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.8f).dp)
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(Ios.surfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                if (testo != null) Text(testo, style = IosText.body, color = Ios.textSecondary, modifier = Modifier.alpha(0.85f))
                                else Text(formattaAi(stato.parziale), style = IosText.body, color = Ios.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // barra di input con i suggerimenti
        Column(Modifier.fillMaxWidth().background(Ios.chrome)) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.separator))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 6.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SUGGERIMENTI.forEach { s ->
                    val testo = stringResource(s.testo)
                    PillButton(stringResource(s.etichetta), onClick = {
                        if (s.completare) domanda = testo
                        else if (!stato.inCorso) Motore.avvia(context, testo)
                    })
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = domanda,
                    onValueChange = { domanda = it },
                    textStyle = IosText.body.copy(color = Ios.textPrimary),
                    cursorBrush = SolidColor(Ios.accent),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                    decorationBox = { campo ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(Ios.surfaceElevated)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (domanda.isEmpty()) Text(stringResource(R.string.ai_scrivi), style = IosText.body, color = Ios.textTertiary)
                            campo()
                        }
                    },
                )
                Spacer(Modifier.size(8.dp))
                val puoInviare = stato.inCorso || domanda.isNotBlank()
                Box(
                    Modifier
                        .padding(bottom = 1.dp)
                        .size(36.dp)
                        .premibile(puoInviare) {
                            if (stato.inCorso) {
                                Motore.ferma(context)
                            } else {
                                val d = domanda.trim()
                                if (d.isNotEmpty()) {
                                    domanda = ""
                                    Motore.avvia(context, d)
                                }
                            }
                        }
                        .clip(CircleShape)
                        .background(if (puoInviare) Ios.accent else Ios.fillTertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (stato.inCorso) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                    } else {
                        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.invia), tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    if (chiediNuova) {
        IosAlert(
            title = stringResource(R.string.nuova_chat),
            text = stringResource(R.string.nuova_chat_testo),
            confirm = stringResource(R.string.nuova_chat),
            dismiss = stringResource(R.string.annulla),
            destructive = true,
            onDismiss = { chiediNuova = false },
            onConfirm = {
                chiediNuova = false
                Motore.ferma(context)
                ChatStorico.cancella(context)
                Motore.chatCambiata()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bolla(m: ChatStorico.Messaggio, modifier: Modifier, context: Context) {
    val utente = m.ruolo == ChatStorico.UTENTE
    val errore = m.ruolo == ChatStorico.ERRORE
    val copiato = stringResource(R.string.copiato)
    Box(modifier.fillMaxWidth(), contentAlignment = if (utente) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.8f).dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (utente) Ios.accent else Ios.surfaceElevated)
                .combinedClickable(onClick = {}, onLongClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Buoni Pasto", m.testo))
                    Toast.makeText(context, copiato, Toast.LENGTH_SHORT).show()
                })
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            when {
                utente -> Text(m.testo, style = IosText.body, color = Color.White)
                errore -> Text(m.testo, style = IosText.body, color = Ios.red)
                else -> Text(formattaAi(m.testo), style = IosText.body, color = Ios.textPrimary)
            }
        }
    }
}
