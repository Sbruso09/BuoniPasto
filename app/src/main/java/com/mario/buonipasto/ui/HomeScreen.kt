package com.mario.buonipasto.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mario.buonipasto.ai.Ponte
import com.mario.buonipasto.Lingua
import com.mario.buonipasto.R
import com.mario.buonipasto.data.Feed
import com.mario.buonipasto.data.Member
import com.mario.buonipasto.data.Movement
import com.mario.buonipasto.data.MovementType
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.Session
import com.mario.buonipasto.data.Split
import com.mario.buonipasto.data.formatEuro
import com.mario.buonipasto.data.netCents
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

fun formatoData(): DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Lingua.locale())
private fun formatoMese(): DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Lingua.locale())

fun Movement.localDate(): LocalDate =
    Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HomeScreen(
    repository: Repository,
    session: Session,
    membersFeed: Feed<Member>?,
    movementsFeed: Feed<Movement>?,
) {
    val context = LocalContext.current
    val erroreSalvataggio = stringResource(R.string.err_salvataggio)
    val showError: (String) -> Unit = {
        Toast.makeText(context, if (it.isBlank()) erroreSalvataggio else it, Toast.LENGTH_LONG).show()
    }

    var adding by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Movement?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    val members = membersFeed?.items.orEmpty()
    val movements = movementsFeed?.items.orEmpty()
    val feedError = membersFeed?.error ?: movementsFeed?.error
    val me = members.firstOrNull { it.id == session.memberId }
    val other = members.firstOrNull { it.id != session.memberId }
    val loaded = membersFeed != null && movementsFeed != null

    Column(Modifier.fillMaxSize().background(Ios.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                LargeTitle(stringResource(R.string.titolo_buoni))
                Text(
                    stringResource(R.string.ciao, session.name),
                    style = IosText.subhead,
                    color = Ios.textSecondary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp).padding(top = 0.dp),
                )
            }
            if (me != null) {
                Box(
                    Modifier.size(38.dp).premibile { adding = true }.clip(CircleShape).background(Ios.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.aggiungi_spesa), tint = Color.White)
                }
            }
        }

        when {
            feedError != null -> EmptyState(
                stringResource(R.string.err_caricamento_titolo),
                stringResource(R.string.err_caricamento, feedError),
            )

            !loaded || me == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ios.accent)
            }

            else -> {
                val monthMovements = movements.filter { YearMonth.from(it.localDate()) == month }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                ) {
                    item {
                        BalancePanel(members, me, other, movements, session.groupCode) { settling = true }
                    }
                    item {
                        MonthHeader(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
                    }
                    item { MonthSummary(members, monthMovements) }
                    item { GroupHeader(stringResource(R.string.movimenti)) }
                    if (monthMovements.isEmpty()) {
                        item {
                            GroupCard {
                                Text(
                                    stringResource(R.string.nessuna_spesa_mese),
                                    style = IosText.subhead,
                                    color = Ios.textSecondary,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    } else {
                        item {
                            GroupCard {
                                monthMovements.forEachIndexed { i, movement ->
                                    if (i > 0) RowSeparator(start = 60.dp)
                                    MovementRow(
                                        movement = movement,
                                        color = personColor(members.indexOfFirst { it.id == movement.payerId }.coerceAtLeast(0)),
                                        otherNameFor = { payerId -> members.firstOrNull { it.id != payerId }?.name ?: "" },
                                        canDelete = movement.createdBy == session.memberId,
                                        onDelete = { toDelete = movement },
                                    )
                                }
                            }
                            GroupFooter(stringResource(R.string.tieni_premuto_elimina))
                        }
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
        IosAlert(
            title = stringResource(R.string.segna_saldato),
            text = stringResource(R.string.segna_saldato_testo, debtor.name, formatEuro(amount), creditor.name),
            confirm = stringResource(R.string.segna_saldato),
            dismiss = stringResource(R.string.annulla),
            onDismiss = { settling = false },
            onConfirm = {
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
            },
        )
    }

    toDelete?.let { movement ->
        IosAlert(
            title = stringResource(R.string.elimina_voce_titolo),
            text = stringResource(R.string.elimina_voce_testo, formatEuro(movement.totalCents), movement.localDate().format(formatoData())),
            confirm = stringResource(R.string.elimina),
            dismiss = stringResource(R.string.annulla),
            destructive = true,
            onDismiss = { toDelete = null },
            onConfirm = {
                repository.delete(session.groupCode, movement.id, showError)
                toDelete = null
            },
        )
    }
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
    GroupCard(padding = PaddingValues(20.dp)) {
        if (other == null) {
            Text(stringResource(R.string.manca_seconda), style = IosText.title3, color = Ios.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.manca_seconda_testo, groupCode), style = IosText.subhead, color = Ios.textSecondary)
            return@GroupCard
        }
        val net = netCents(movements, me.id)
        val headline = when {
            net > 0 -> stringResource(R.string.ti_deve, other.name)
            net < 0 -> stringResource(R.string.devi_a, other.name)
            else -> stringResource(R.string.siete_pari)
        }
        Text(headline.uppercase(), style = IosText.groupHeader, color = Ios.textSecondary)
        Text(
            text = if (net != 0L) formatEuro(abs(net)) else "0,00 €",
            style = IosText.largeTitle.copy(fontWeight = FontWeight.Bold),
            color = when {
                net > 0 -> Ios.green
                net < 0 -> Ios.orange
                else -> Ios.textPrimary
            },
        )
        if (me.voucherCents > 0 && net != 0L) {
            val buoni = abs(net).toDouble() / me.voucherCents
            Text(
                stringResource(R.string.circa_buoni, String.format(Lingua.locale(), "%.1f", buoni)),
                style = IosText.footnote,
                color = Ios.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        BalanceBeam(netCents(movements, members[0].id), personColor(0), personColor(1), Ios.fillSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(members[0].name, color = personColor(0), style = IosText.subhead.copy(fontWeight = FontWeight.SemiBold))
            Text(members[1].name, color = personColor(1), style = IosText.subhead.copy(fontWeight = FontWeight.SemiBold))
        }
        if (net != 0L) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.segna_saldato), onSettle)
        }
    }
}

/** Bilancia che pende verso chi ha pagato di più per l'altro. Inclinazione massima da 50 €. */
@Composable
private fun BalanceBeam(firstNetCents: Long, leftColor: Color, rightColor: Color, pivotColor: Color) {
    val target = -(firstNetCents / 5000f).coerceIn(-1f, 1f) * 10f
    val degrees by animateFloatAsState(targetValue = target, label = "beam")
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val weight = 10.dp.toPx()
        val beamHalf = size.width / 2 - weight
        val pivot = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x - 11.dp.toPx(), size.height)
            lineTo(center.x + 11.dp.toPx(), size.height)
            close()
        }
        drawPath(pivot, pivotColor)
        rotate(degrees = degrees, pivot = center) {
            drawLine(pivotColor, Offset(center.x - beamHalf, center.y), Offset(center.x + beamHalf, center.y), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(leftColor, weight, Offset(center.x - beamHalf, center.y))
            drawCircle(rightColor, weight, Offset(center.x + beamHalf, center.y))
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = month.format(formatoMese()).replaceFirstChar { it.titlecase(Lingua.locale()) },
            style = IosText.title3,
            color = Ios.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(36.dp).premibile(onClick = onPrevious).clip(CircleShape).background(Ios.fillTertiary), Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowLeft, stringResource(R.string.mese_precedente), tint = Ios.accent)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(36.dp).premibile(onClick = onNext).clip(CircleShape).background(Ios.fillTertiary), Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowRight, stringResource(R.string.mese_successivo), tint = Ios.accent)
        }
    }
}

@Composable
private fun MonthSummary(members: List<Member>, monthMovements: List<Movement>) {
    GroupCard {
        members.forEachIndexed { index, member ->
            if (index > 0) RowSeparator(start = 40.dp)
            val paid = monthMovements.filter { it.type == MovementType.EXPENSE && it.payerId == member.id }
            val vouchers = paid.sumOf { it.vouchers }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(personColor(index)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(member.name, style = IosText.body, color = Ios.textPrimary)
                    Text(pluralStringResource(R.plurals.buoni_usati, vouchers, vouchers), style = IosText.footnote, color = Ios.textSecondary)
                }
                Text(formatEuro(paid.sumOf { it.totalCents }), style = IosText.headline, color = Ios.textPrimary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
        title = stringResource(R.string.rimborso_titolo, movement.payerName, movement.receiverName)
        detail = stringResource(R.string.saldo_pareggiato)
    } else {
        title = movement.note.ifBlank { stringResource(R.string.spesa_di, movement.payerName) }
        val buoni = pluralStringResource(R.plurals.buoni_n, movement.vouchers, movement.vouchers)
        val extra = if (movement.extraCents > 0) stringResource(R.string.e_in_piu, formatEuro(movement.extraCents)) else ""
        val split = when (movement.split) {
            Split.HALF -> stringResource(R.string.a_meta)
            Split.PAYER_ONLY -> stringResource(R.string.solo_di, movement.payerName)
            Split.OTHER_ONLY -> stringResource(R.string.solo_di, otherNameFor(movement.payerId))
        }
        detail = "$buoni$extra · $split"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { Ponte.Tocco(movement).invoke() }, onLongClick = if (canDelete) onDelete else null)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = IosText.body, color = Ios.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = IosText.footnote, color = Ios.textSecondary, maxLines = 2)
            Text(movement.localDate().format(formatoData()), style = IosText.caption, color = Ios.textTertiary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatEuro(movement.totalCents),
            style = IosText.headline,
            color = if (movement.type == MovementType.SETTLEMENT) Ios.green else Ios.textPrimary,
        )
    }
}
