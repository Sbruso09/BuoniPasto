package com.mario.buonipasto.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val otherName = other?.name ?: "l'altra persona"
    val splitOptions = listOf(
        Split.HALF to "A metà",
        Split.PAYER_ONLY to "Solo mia",
        Split.OTHER_ONLY to "Di $otherName",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Spesa pagata da ${me.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Buoni usati", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Da ${formatEuro(me.voucherCents)} l'uno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    enabled = vouchers > 1,
                    onClick = { vouchers -= 1 },
                ) { Text("−") }
                Text(
                    text = vouchers.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                FilledTonalButton(onClick = { vouchers += 1 }) { Text("+") }
            }

            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                label = { Text("Pagato in più con carta o contanti (€)") },
                isError = extraCents == null,
                supportingText = {
                    if (extraCents == null) Text("Scrivi l'importo come 4,50.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota, ad esempio spesa al supermercato") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                },
            ) {
                Text("Data: ${date.format(ITALIAN_DATE)}")
            }

            Text("Di chi è la spesa", style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                splitOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = split == value,
                        onClick = { split = value },
                        shape = SegmentedButtonDefaults.itemShape(index, splitOptions.size),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            Button(
                enabled = extraCents != null && total > 0,
                modifier = Modifier.fillMaxWidth(),
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
            ) {
                Text("Aggiungi spesa da ${formatEuro(total)}")
            }
        }
    }
}
