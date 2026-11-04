package com.mario.buonipasto.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mario.buonipasto.R
import com.mario.buonipasto.data.Member
import com.mario.buonipasto.data.Movement
import com.mario.buonipasto.data.MovementType
import com.mario.buonipasto.data.Split
import com.mario.buonipasto.data.formatEuro
import com.mario.buonipasto.data.parseCents
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    me: Member,
    other: Member?,
    onDismiss: () -> Unit,
    onSave: (Movement) -> Unit,
) {
    val context = LocalContext.current
    var vouchers by remember { mutableIntStateOf(1) }
    var extra by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var split by remember { mutableStateOf(Split.HALF) }

    val extraCents = if (extra.isBlank()) 0L else parseCents(extra)
    val total = vouchers * me.voucherCents + (extraCents ?: 0L)
    val otherName = other?.name ?: stringResource(R.string.altra_persona)
    val splitOptions = listOf(
        Split.HALF to stringResource(R.string.split_meta),
        Split.PAYER_ONLY to stringResource(R.string.split_mia),
        Split.OTHER_ONLY to stringResource(R.string.split_di, otherName),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ios.surface,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.spesa_pagata_da, me.name), style = IosText.title3, color = Ios.textPrimary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.buoni_usati_label), style = IosText.body, color = Ios.textPrimary)
                    Text(stringResource(R.string.da_luno, formatEuro(me.voucherCents)), style = IosText.footnote, color = Ios.textSecondary)
                }
                Stepper(vouchers, onMinus = { if (vouchers > 0) vouchers -= 1 }, onPlus = { vouchers += 1 }, minusEnabled = vouchers > 0)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.extra_label), style = IosText.footnote, color = Ios.textSecondary)
                IosField(
                    value = extra,
                    onValueChange = { extra = it },
                    placeholder = "0,00",
                    error = extraCents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (extraCents == null) Text(stringResource(R.string.extra_errore), style = IosText.footnote, color = Ios.red)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.nota_label), style = IosText.footnote, color = Ios.textSecondary)
                IosField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = stringResource(R.string.nota_esempio),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.data_label), style = IosText.body, color = Ios.textPrimary, modifier = Modifier.weight(1f))
                PillButton(
                    date.format(formatoData()),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                )
            }

            Text(stringResource(R.string.di_chi_spesa), style = IosText.footnote, color = Ios.textSecondary)
            Segmented(
                options = splitOptions.map { it.second },
                selected = splitOptions.indexOfFirst { it.first == split },
                onSelect = { split = splitOptions[it].first },
            )

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = stringResource(R.string.aggiungi_spesa_da, formatEuro(total)),
                enabled = extraCents != null && total > 0,
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        Movement(
                            id = "",
                            type = MovementType.EXPENSE,
                            payerId = me.id,
                            payerName = me.name,
                            receiverName = "",
                            vouchers = vouchers,
                            voucherCents = me.voucherCents,
                            extraCents = extraCents ?: 0L,
                            split = split,
                            note = note.trim(),
                            dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            createdAt = now,
                            createdBy = me.id,
                        ),
                    )
                },
            )
        }
    }
}
