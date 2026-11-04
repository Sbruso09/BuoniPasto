package com.mario.buonipasto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mario.buonipasto.R
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
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var voucher by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Ios.background)) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.padding(start = 20.dp, top = 24.dp).size(64.dp).clip(RoundedCornerShape(15.dp)).background(Ios.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_tab_buoni), null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            LargeTitle(stringResource(R.string.app_name))
            Text(
                stringResource(R.string.login_sottotitolo),
                style = IosText.body,
                color = Ios.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            GroupHeader(stringResource(R.string.login_tu))
            GroupCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                IosField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.login_nome),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(10.dp))
                IosField(
                    value = voucher,
                    onValueChange = { voucher = it },
                    placeholder = stringResource(R.string.login_buono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            GroupFooter(stringResource(R.string.login_buono_nota))
            GroupHeader(stringResource(R.string.login_gruppo))
            GroupCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                IosField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = stringResource(R.string.login_codice),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.height(10.dp))
                PillButton(stringResource(R.string.login_crea_codice), onClick = { code = generateCode() })
            }
            GroupFooter(stringResource(R.string.login_codice_nota))
            error?.let {
                Text(it, style = IosText.subhead, color = Ios.red, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            PrimaryButton(
                text = stringResource(if (loading) R.string.login_in_corso else R.string.login_entra),
                enabled = !loading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                onClick = {
                    val voucherCents = if (voucher.isBlank()) null else parseCents(voucher)
                    if (voucher.isNotBlank() && (voucherCents == null || voucherCents <= 0)) {
                        error = context.getString(R.string.login_err_formato_buono)
                        return@PrimaryButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            onLoggedIn(repository.login(code, name, voucherCents))
                        } catch (e: LoginException) {
                            error = if (e.arg != null) context.getString(e.res, e.arg) else context.getString(e.res)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = context.getString(R.string.login_err_rete)
                        } finally {
                            loading = false
                        }
                    }
                },
            )
        }
    }
}
