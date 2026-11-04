package com.mario.buonipasto.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.mario.buonipasto.Lingua
import com.mario.buonipasto.R
import com.mario.buonipasto.ai.ChatStorico
import com.mario.buonipasto.ai.ChiaveAi
import com.mario.buonipasto.ai.Motore
import com.mario.buonipasto.ai.UsoAi
import com.mario.buonipasto.data.Member
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.Session
import com.mario.buonipasto.data.centsToInput
import com.mario.buonipasto.data.parseCents

@Composable
fun SettingsScreen(repository: Repository, session: Session, members: List<Member>, onLogout: () -> Unit) {
    val context = LocalContext.current
    val me = members.firstOrNull { it.id == session.memberId }
    var voucher by remember(me?.voucherCents) { mutableStateOf(me?.let { centsToInput(it.voucherCents) }.orEmpty()) }
    val cents = parseCents(voucher)
    var sceltaLingua by remember { mutableStateOf(false) }
    var chiediEsci by remember { mutableStateOf(false) }
    var chiediCancella by remember { mutableStateOf(false) }
    var versioneChiavi by remember { mutableIntStateOf(0) }
    var notificheAttive by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ios.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        LargeTitle(stringResource(R.string.tab_impostazioni), Modifier.padding(top = 6.dp))

        // ---------------------------------------------------------------- gruppo
        GroupHeader(stringResource(R.string.imp_gruppo))
        GroupCard {
            IosRow(
                title = session.name,
                subtitle = stringResource(R.string.imp_codice, session.groupCode),
                leading = { IconTile(Ios.accent, icon = Icons.Default.Person) },
            )
            RowSeparator(58.dp)
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.imp_valore_buono), style = IosText.footnote, color = Ios.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IosField(
                        value = voucher,
                        onValueChange = { voucher = it },
                        placeholder = "8,00",
                        error = cents == null || cents <= 0,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        stringResource(R.string.salva),
                        enabled = cents != null && cents > 0 && cents != me?.voucherCents,
                        onClick = {
                            val c = cents ?: return@PillButton
                            repository.updateVoucher(session.groupCode, session.memberId, c) {
                                Toast.makeText(context, it.ifBlank { context.getString(R.string.err_salvataggio) }, Toast.LENGTH_LONG).show()
                            }
                            Toast.makeText(context, R.string.imp_buono_salvato, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
        GroupFooter(stringResource(R.string.imp_buono_nota))

        // ---------------------------------------------------------------- generale
        GroupHeader(stringResource(R.string.imp_generale))
        GroupCard {
            IosRow(
                title = stringResource(R.string.lingua),
                value = Lingua.nome(context, Lingua.scelta()),
                leading = { IconTile(Ios.teal, painter = painterResource(R.drawable.ic_lingua)) },
                chevron = true,
                onClick = { sceltaLingua = true },
            )
            RowSeparator(58.dp)
            IosRow(
                title = stringResource(R.string.imp_notifiche),
                value = stringResource(if (notificheAttive) R.string.attive else R.string.da_attivare),
                leading = { IconTile(Ios.red, icon = Icons.Default.Notifications) },
                chevron = true,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                    notificheAttive = NotificationManagerCompat.from(context).areNotificationsEnabled()
                },
            )
        }

        // ---------------------------------------------------------------- assistente AI
        GroupHeader(stringResource(R.string.imp_assistente))
        ChiaviAi(context, versioneChiavi) { versioneChiavi++ }
        GroupFooter(stringResource(R.string.imp_assistente_nota))

        GroupHeader(stringResource(R.string.imp_modello))
        GroupCard(padding = PaddingValues(12.dp)) {
            val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
            var modello by remember { mutableIntStateOf(if (prefs.getString(Motore.K_MODELLO, "veloce") == "intelligente") 1 else 0) }
            Segmented(
                options = listOf(stringResource(R.string.modello_veloce), stringResource(R.string.modello_intelligente)),
                selected = modello,
                onSelect = {
                    modello = it
                    prefs.edit().putString(Motore.K_MODELLO, if (it == 1) "intelligente" else "veloce").apply()
                },
            )
        }
        val uso = UsoAi.oggi(context)
        GroupFooter(stringResource(R.string.imp_uso_oggi, uso[0], uso[1]))

        GroupHeader("")
        GroupCard {
            IosRow(title = stringResource(R.string.cancella_chat), titleColor = Ios.red, onClick = { chiediCancella = true })
            RowSeparator()
            IosRow(
                title = stringResource(R.string.rimuovi_chiavi),
                titleColor = Ios.red,
                onClick = {
                    ChiaveAi.rimuovi(context)
                    ChiaveAi.rimuovi(context, ChiaveAi.GROQ)
                    versioneChiavi++
                    Toast.makeText(context, R.string.chiavi_rimosse, Toast.LENGTH_SHORT).show()
                },
            )
        }

        // ---------------------------------------------------------------- account
        GroupHeader("")
        GroupCard {
            IosRow(title = stringResource(R.string.esci), titleColor = Ios.red, onClick = { chiediEsci = true })
        }
        GroupFooter(stringResource(R.string.imp_esci_nota))
    }

    if (sceltaLingua) {
        AlertDialog(
            onDismissRequest = { sceltaLingua = false },
            containerColor = Ios.surfaceElevated,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.lingua), style = IosText.headline, color = Ios.textPrimary) },
            text = {
                Column {
                    Lingua.CODICI.forEach { codice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sceltaLingua = false
                                    if (codice != Lingua.scelta()) Lingua.imposta(codice) // l'app si ricarica nella nuova lingua
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Lingua.nome(context, codice), style = IosText.body, color = Ios.textPrimary, modifier = Modifier.weight(1f))
                            if (codice == Lingua.scelta()) Icon(Icons.Default.Check, null, tint = Ios.accent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sceltaLingua = false }) { Text(stringResource(R.string.annulla), color = Ios.accent) }
            },
        )
    }
    if (chiediEsci) {
        IosAlert(
            title = stringResource(R.string.esci_titolo),
            text = stringResource(R.string.esci_testo, session.groupCode),
            confirm = stringResource(R.string.esci),
            dismiss = stringResource(R.string.annulla),
            destructive = true,
            onDismiss = { chiediEsci = false },
            onConfirm = {
                chiediEsci = false
                onLogout()
            },
        )
    }
    if (chiediCancella) {
        IosAlert(
            title = stringResource(R.string.cancella_chat),
            text = stringResource(R.string.nuova_chat_testo),
            confirm = stringResource(R.string.cancella),
            dismiss = stringResource(R.string.annulla),
            destructive = true,
            onDismiss = { chiediCancella = false },
            onConfirm = {
                chiediCancella = false
                Motore.ferma(context)
                ChatStorico.cancella(context)
                Motore.chatCambiata()
                Toast.makeText(context, R.string.chat_cancellata, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

/** Campi per le due chiavi gratuite: Gemini (principale) e Groq (riserva). Salvate cifrate sul telefono. */
@Composable
private fun ChiaviAi(context: Context, versione: Int, onCambiate: () -> Unit) {
    var gemini by remember { mutableStateOf("") }
    var groq by remember { mutableStateOf("") }
    val geminiOk = remember(versione) { ChiaveAi.presente(context) }
    val groqOk = remember(versione) { ChiaveAi.presente(context, ChiaveAi.GROQ) }
    GroupCard(padding = PaddingValues(16.dp)) {
        Text(stringResource(R.string.chiave_gemini), style = IosText.footnote, color = Ios.textSecondary)
        Spacer(Modifier.height(6.dp))
        IosField(gemini, { gemini = it }, stringResource(if (geminiOk) R.string.chiave_salvata_placeholder else R.string.chiave_incolla), password = true)
        Text(
            stringResource(if (geminiOk) R.string.chiave_gemini_ok else R.string.chiave_assente),
            style = IosText.footnote,
            color = if (geminiOk) Ios.green else Ios.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.chiave_gemini_link),
            style = IosText.footnote,
            color = Ios.accent,
            modifier = Modifier.padding(vertical = 6.dp).clickable { apri(context, "https://aistudio.google.com/apikey") },
        )
        RowSeparator(0.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.chiave_groq), style = IosText.footnote, color = Ios.textSecondary)
        Spacer(Modifier.height(6.dp))
        IosField(groq, { groq = it }, stringResource(if (groqOk) R.string.chiave_salvata_placeholder else R.string.chiave_incolla), password = true)
        Text(
            stringResource(if (groqOk) R.string.chiave_groq_ok else R.string.chiave_assente),
            style = IosText.footnote,
            color = if (groqOk) Ios.green else Ios.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(R.string.chiave_groq_link),
            style = IosText.footnote,
            color = Ios.accent,
            modifier = Modifier.padding(vertical = 6.dp).clickable { apri(context, "https://console.groq.com/keys") },
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            stringResource(R.string.salva_chiavi),
            enabled = gemini.isNotBlank() || groq.isNotBlank(),
            onClick = {
                if (gemini.isNotBlank() && ChiaveAi.salva(context, gemini.trim())) gemini = ""
                if (groq.isNotBlank() && ChiaveAi.salva(context, groq.trim(), ChiaveAi.GROQ)) groq = ""
                onCambiate()
                Toast.makeText(context, R.string.chiavi_salvate, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

private fun apri(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
