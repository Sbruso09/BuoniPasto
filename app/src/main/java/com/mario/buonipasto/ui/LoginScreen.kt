package com.mario.buonipasto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mario.buonipasto.data.LoginException
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.Session
import com.mario.buonipasto.data.parseCents
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

private fun generateCode(): String {
    val random = SecureRandom()
    return (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
}

@Composable
fun LoginScreen(repository: Repository, onLoggedIn: (Session) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var voucher by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Buoni Pasto",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 32.dp),
            )
            Text(
                text = "Chi paga con i buoni, e chi deve quanto a chi.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Il tuo nome") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Codice del gruppo") },
                supportingText = { Text("Lo stesso su entrambi i telefoni.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { code = generateCode() }) {
                Text("Crea un nuovo codice")
            }
            OutlinedTextField(
                value = voucher,
                onValueChange = { voucher = it },
                label = { Text("Valore del tuo buono (€)") },
                supportingText = { Text("Se sei già nel gruppo puoi lasciarlo vuoto.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val voucherCents = if (voucher.isBlank()) null else parseCents(voucher)
                    if (voucher.isNotBlank() && (voucherCents == null || voucherCents <= 0)) {
                        error = "Scrivi il valore del buono come 8,00."
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            onLoggedIn(repository.login(code, name, voucherCents))
                        } catch (e: LoginException) {
                            error = e.message
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = "Accesso non riuscito. Controlla la connessione e riprova."
                        } finally {
                            loading = false
                        }
                    }
                },
            ) {
                Text(if (loading) "Accesso in corso" else "Entra")
            }
        }
    }
}
