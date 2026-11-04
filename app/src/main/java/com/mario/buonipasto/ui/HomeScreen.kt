package com.mario.buonipasto.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mario.buonipasto.data.Feed
import com.mario.buonipasto.data.Member
import com.mario.buonipasto.data.Movement
import com.mario.buonipasto.data.MovementType
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.Session
import com.mario.buonipasto.data.Split
import com.mario.buonipasto.data.centsToInput
import com.mario.buonipasto.data.formatEuro
import com.mario.buonipasto.data.netCents
import com.mario.buonipasto.data.parseCents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val ITALIAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
private val ITALIAN_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

private fun Movement.localDate(): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repository: Repository, session: Session, onLogout: () -> Unit) {
    val context = LocalContext.current
    val showError: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    val membersFlow = remember(session.groupCode) { repository.observeMembers(session.groupCode) }
    val movementsFlow = remember(session.groupCode) { repository.observeMovements(session.groupCode) }
    val membersFeed by membersFlow.collectAsStateWithLifecycle(initialValue = null as Feed<Member>?)
    val movementsFeed by movementsFlow.collectAsStateWithLifecycle(initialValue = null as Feed<Movement>?)

    var adding by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Movement?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    val members = membersFeed?.items.orEmpty()
    val movements = movementsFeed?.items.orEmpty()
    val feedError = membersFeed?.error ?: movementsFeed?.error
    val me = members.firstOrNull { it.id == session.memberId }
    val other = members.firstOrNull { it.id != session.memberId }
    val loaded = membersFeed != null && movementsFeed != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ciao, ${session.name}", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                },
            )
        },
        floatingActionButton = {
            if (me != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Aggiungi spesa") },
                )
            }
        },
    ) { padding ->
        when {
            feedError != null -> Message(
                text = "I dati non si caricano: $feedError\n\nControlla la connessione e le regole di Firestore.",
                modifier = Modifier.padding(padding),
            )

            !loaded || me == null -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> {
                val monthMovements = movements.filter { YearMonth.from(it.localDate()) == month }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        BalancePanel(
                            members = members,
                            me = me,
                            other = other,
                            movements = movements,
                            groupCode = session.groupCode,
                            onSettle = { settling = true },
                        )
                    }
                    item {
                        MonthHeader(
                            month = month,
                            onPrevious = { month = month.minusMonths(1) },
                            onNext = { month = month.plusMonths(1) },
                        )
                    }
                    item { MonthSummary(members, monthMovements) }
                    if (monthMovements.isEmpty()) {
                        item {
                            Text(
                                text = "Nessuna spesa in questo mese. Aggiungine una con il pulsante in basso.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(monthMovements, key = { it.id }) { movement ->
                        MovementRow(
                            movement = movement,
                            color = personColor(members.indexOfFirst { it.id == movement.payerId }.coerceAtLeast(0)),
                            otherNameFor = { payerId -> members.firstOrNull { it.id != payerId }?.name ?: "l'altra persona" },
                            canDelete = movement.createdBy == session.memberId,
                            onDelete = { toDelete = movement },
                        )
                    }
                }
            }
        }
    }

    if (adding && me != null) {
        AddExpenseSheet(
            me = me,
            other = other,
            onDismiss = { adding = false },
            onSave = {
                repository.add(session.groupCode, it, showError)
                month = YearMonth.from(it.localDate())
                adding = false
            },
        )
    }

    if (settling && me != null && other != null) {
        val net = netCents(movements, me.id)
        val debtor = if (net > 0) other else me
        val creditor = if (net > 0) me else other
        val amount = abs(net)
        AlertDialog(
            onDismissRequest = { settling = false },
            title = { Text("Segna come saldato") },
            text = { Text("${debtor.name} ha dato ${formatEuro(amount)} a ${creditor.name}? Il saldo tornerà a zero.") },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    repository.add(
                        session.groupCode,
                        Movement(
                            id = "",
                            type = MovementType.SETTLEMENT,
                            payerId = debtor.id,
                            payerName = debtor.name,
                            receiverName = creditor.name,
                            vouchers = 0,
                            voucherCents = 0,
                            extraCents = amount,
                            split = Split.OTHER_ONLY,
                            note = "",
                            dateMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            createdAt = now,
                            createdBy = me.id,
                        ),
                        showError,
                    )
                    month = YearMonth.now()
                    settling = false
                }) { Text("Segna come saldato") }
            },
            dismissButton = {
                TextButton(onClick = { settling = false }) { Text("Annulla") }
            },
        )
    }

    toDelete?.let { movement ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare questa voce?") },
            text = { Text("${formatEuro(movement.totalCents)} del ${movement.localDate().format(ITALIAN_DATE)}. Il saldo verrà ricalcolato.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(session.groupCode, movement.id, showError)
                    toDelete = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Annulla") }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            me = me,
            groupCode = session.groupCode,
            onDismiss = { showSettings = false },
            onSaveVoucher = { cents ->
                repository.updateVoucher(session.groupCode, session.memberId, cents, showError)
                showSettings = false
            },
            onLogout = {
                showSettings = false
                onLogout()
            },
        )
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun BalancePanel(
    members: List<Member>,
    me: Member,
    other: Member?,
    movements: List<Movement>,
    groupCode: String,
    onSettle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
    ) {
        if (other == null) {
            Text(
                text = "Manca la seconda persona",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text("Deve installare l'app ed entrare con il codice $groupCode.")
            return@Column
        }

        val net = netCents(movements, me.id)
        val headline = when {
            net > 0 -> "${other.name} ti deve"
            net < 0 -> "Devi a ${other.name}"
            else -> "Siete pari"
        }
        Text(text = headline, style = MaterialTheme.typography.titleLarge)
        if (net != 0L) {
            Text(
                text = formatEuro(abs(net)),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(Modifier.height(12.dp))
        val firstNet = netCents(movements, members[0].id)
        BalanceBeam(
            firstNetCents = firstNet,
            leftColor = personColor(0),
            rightColor = personColor(1),
            pivotColor = MaterialTheme.colorScheme.outline,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(members[0].name, color = personColor(0), fontWeight = FontWeight.Bold)
            Text(members[1].name, color = personColor(1), fontWeight = FontWeight.Bold)
        }

        if (net != 0L) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSettle, modifier = Modifier.fillMaxWidth()) {
                Text("Segna come saldato")
            }
        }
    }
}

/** A scale that leans towards whoever has paid more for the other person. Full tilt from 50 €. */
@Composable
private fun BalanceBeam(firstNetCents: Long, leftColor: Color, rightColor: Color, pivotColor: Color) {
    val target = -(firstNetCents / 5000f).coerceIn(-1f, 1f) * 10f
    val degrees by animateFloatAsState(targetValue = target, label = "beam")

    Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val weight = 12.dp.toPx()
        val beamHalf = size.width / 2 - weight

        val pivot = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x - 12.dp.toPx(), size.height)
            lineTo(center.x + 12.dp.toPx(), size.height)
            close()
        }
        drawPath(pivot, pivotColor)

        rotate(degrees = degrees, pivot = center) {
            drawLine(
                color = pivotColor,
                start = Offset(center.x - beamHalf, center.y),
                end = Offset(center.x + beamHalf, center.y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(leftColor, weight, Offset(center.x - beamHalf, center.y))
            drawCircle(rightColor, weight, Offset(center.x + beamHalf, center.y))
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = month.format(ITALIAN_MONTH).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPrevious) { Text("Precedente") }
        TextButton(onClick = onNext) { Text("Successivo") }
    }
}

@Composable
private fun MonthSummary(members: List<Member>, monthMovements: List<Movement>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        members.forEachIndexed { index, member ->
            val paid = monthMovements.filter { it.type == MovementType.EXPENSE && it.payerId == member.id }
            val vouchers = paid.sumOf { it.vouchers }
            val word = if (vouchers == 1) "buono" else "buoni"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(personColor(index)),
                )
                Spacer(Modifier.width(8.dp))
                Text("${member.name} ha pagato ${formatEuro(paid.sumOf { it.totalCents })} con $vouchers $word")
            }
        }
    }
}

@Composable
private fun MovementRow(
    movement: Movement,
    color: Color,
    otherNameFor: (String) -> String,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val title: String
    val detail: String
    if (movement.type == MovementType.SETTLEMENT) {
        title = "${movement.payerName} ha dato i soldi a ${movement.receiverName}"
        detail = "Saldo pareggiato"
    } else {
        title = movement.note.ifBlank { "Spesa di ${movement.payerName}" }
        val word = if (movement.vouchers == 1) "buono" else "buoni"
        val extra = if (movement.extraCents > 0) " e ${formatEuro(movement.extraCents)} in più" else ""
        val split = when (movement.split) {
            Split.HALF -> "a metà"
            Split.PAYER_ONLY -> "solo di ${movement.payerName}"
            Split.OTHER_ONLY -> "solo di ${otherNameFor(movement.payerId)}"
        }
        detail = "${movement.payerName}: ${movement.vouchers} $word$extra, $split"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(6.dp).height(72.dp).background(color))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                movement.localDate().format(ITALIAN_DATE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatEuro(movement.totalCents),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, end = if (canDelete) 0.dp else 16.dp),
        )
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Elimina voce")
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    me: Member?,
    groupCode: String,
    onDismiss: () -> Unit,
    onSaveVoucher: (Long) -> Unit,
    onLogout: () -> Unit,
) {
    var voucher by remember { mutableStateOf(me?.let { centsToInput(it.voucherCents) }.orEmpty()) }
    val cents = parseCents(voucher)
    val valid = cents != null && cents > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Codice del gruppo: $groupCode")
                OutlinedTextField(
                    value = voucher,
                    onValueChange = { voucher = it },
                    label = { Text("Valore del tuo buono (€)") },
                    supportingText = { Text("Vale per le prossime spese. Quelle già inserite non cambiano.") },
                    isError = !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                TextButton(onClick = onLogout) { Text("Esci") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { cents?.let(onSaveVoucher) }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
