package com.mario.buonipasto.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mario.buonipasto.ai.GiorniDieta
import com.mario.buonipasto.ai.ModificaArticolo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mario.buonipasto.R
import com.mario.buonipasto.ai.Dieta
import com.mario.buonipasto.data.Articolo
import com.mario.buonipasto.data.ListaSpesa
import com.mario.buonipasto.data.Reparti

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen() {
    val context = LocalContext.current
    val articoli by ListaSpesa.articoli.collectAsStateWithLifecycle()
    val dieta by Dieta.stato.collectAsStateWithLifecycle()
    var nuovo by remember { mutableStateOf("") }
    var chiediSvuota by remember { mutableStateOf(false) }

    val scegliPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // giorni della settimana e persone, poi la lettura
        if (uri != null) GiorniDieta.daPdf(context, uri)
    }

    Column(Modifier.fillMaxSize().background(Ios.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LargeTitle(stringResource(R.string.titolo_lista), Modifier.weight(1f))
            if (articoli.isNotEmpty()) {
                Box(
                    Modifier.size(38.dp).premibile { condividi(context, articoli) }.clip(CircleShape).background(Ios.fillTertiary),
                    Alignment.Center,
                ) { Icon(Icons.Default.Share, stringResource(R.string.condividi), tint = Ios.accent, modifier = Modifier.size(20.dp)) }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            // ------------------------------------------------------------ importa dieta
            item {
                GroupCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Ios.red, painter = painterResource(R.drawable.ic_pdf), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dieta_titolo), style = IosText.headline, color = Ios.textPrimary)
                            Text(stringResource(R.string.dieta_sottotitolo), style = IosText.footnote, color = Ios.textSecondary)
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    if (dieta.inCorso) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Ios.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(dieta.messaggio.orEmpty(), style = IosText.subhead, color = Ios.textSecondary)
                        }
                    } else {
                        PrimaryButton(stringResource(R.string.dieta_scegli), onClick = { scegliPdf.launch(arrayOf("application/pdf")) })
                    }
                    if (!dieta.inCorso && dieta.messaggio != null) {
                        Spacer(Modifier.size(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                dieta.messaggio.orEmpty(),
                                style = IosText.subhead,
                                color = if (dieta.errore) Ios.red else Ios.green,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.chiudi),
                                tint = Ios.textTertiary,
                                modifier = Modifier.size(20.dp).premibile { Dieta.chiudiMessaggio() },
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------ aggiungi a mano
            item {
                GroupHeader(stringResource(R.string.lista_aggiungi))
                GroupCard(padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val aggiungi = {
                            val n = nuovo.trim()
                            if (n.isNotEmpty()) {
                                ListaSpesa.aggiungi(context, listOf(Articolo(nome = n.replaceFirstChar { it.uppercase() })))
                                nuovo = ""
                            }
                        }
                        IosField(
                            value = nuovo,
                            onValueChange = { nuovo = it },
                            placeholder = stringResource(R.string.lista_esempio),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(38.dp).premibile(nuovo.isNotBlank()) { aggiungi() }.clip(CircleShape)
                                .background(if (nuovo.isNotBlank()) Ios.accent else Ios.fillTertiary),
                            Alignment.Center,
                        ) { Icon(Icons.Default.Add, stringResource(R.string.lista_aggiungi), tint = Color.White) }
                    }
                }
            }

            // ------------------------------------------------------------ lista per reparto
            if (articoli.isEmpty()) {
                item { EmptyState(stringResource(R.string.lista_vuota), stringResource(R.string.lista_vuota_testo)) }
            } else {
                val daPrendere = articoli.count { !it.preso }
                item {
                    Text(
                        pluralStringResource(R.plurals.lista_da_prendere, daPrendere, daPrendere),
                        style = IosText.subhead,
                        color = Ios.textSecondary,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp),
                    )
                }
                for (rep in Reparti.CHIAVI) {
                    // prima quelli da prendere, poi quelli gia' nel carrello
                    val voci = articoli.filter { it.reparto == rep }.sortedBy { it.preso }
                    if (voci.isEmpty()) continue
                    item(key = "rep_$rep") {
                        GroupHeader(Reparti.nome(context, rep))
                        GroupCard {
                            voci.forEachIndexed { i, a ->
                                if (i > 0) RowSeparator(52.dp)
                                RigaArticolo(
                                    a,
                                    onSpunta = { ListaSpesa.segna(context, a.id, !a.preso) },
                                    onTieni = { ModificaArticolo.mostra(context, a) },
                                )
                            }
                        }
                    }
                }
                item {
                    GroupFooter(stringResource(R.string.lista_tieni_premuto))
                    GroupHeader("")
                    GroupCard {
                        IosRow(
                            stringResource(R.string.lista_togli_presi),
                            titleColor = if (articoli.any { it.preso }) Ios.accent else Ios.textTertiary,
                            onClick = { ListaSpesa.togliPresi(context) },
                        )
                        RowSeparator()
                        IosRow(stringResource(R.string.lista_svuota), titleColor = Ios.red, onClick = { chiediSvuota = true })
                    }
                }
            }
        }
    }

    if (chiediSvuota) {
        IosAlert(
            title = stringResource(R.string.lista_svuota),
            text = stringResource(R.string.lista_svuota_testo),
            confirm = stringResource(R.string.lista_svuota),
            dismiss = stringResource(R.string.annulla),
            destructive = true,
            onDismiss = { chiediSvuota = false },
            onConfirm = {
                ListaSpesa.svuota(context)
                chiediSvuota = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RigaArticolo(a: Articolo, onSpunta: () -> Unit, onTieni: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSpunta, onLongClick = onTieni)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (a.preso) Ios.accent else Color.Transparent)
                .border(1.5.dp, if (a.preso) Ios.accent else Ios.textTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (a.preso) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.nome,
                style = IosText.body.copy(textDecoration = if (a.preso) TextDecoration.LineThrough else null),
                color = if (a.preso) Ios.textTertiary else Ios.textPrimary,
            )
            if (a.fonte.isNotBlank()) Text(a.fonte, style = IosText.caption, color = Ios.textTertiary)
        }
        if (a.quantita.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(a.quantita, style = IosText.subhead, color = if (a.preso) Ios.textTertiary else Ios.textSecondary)
        }
    }
}

/** Condivide la lista come testo (WhatsApp, Note, ...). */
private fun condividi(c: Context, articoli: List<Articolo>) {
    val testo = buildString {
        for (rep in Reparti.CHIAVI) {
            val voci = articoli.filter { it.reparto == rep && !it.preso }
            if (voci.isEmpty()) continue
            append(Reparti.nome(c, rep)).append('\n')
            voci.forEach { append("☐ ").append(it.nome).append(if (it.quantita.isNotBlank()) " — ${it.quantita}" else "").append('\n') }
            append('\n')
        }
    }.trim()
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, testo)
    runCatching { c.startActivity(Intent.createChooser(i, null)) }
}
