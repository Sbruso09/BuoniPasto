package com.mario.buonipasto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mario.buonipasto.data.ListaSpesa
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.SessionStore
import com.mario.buonipasto.ui.AppRoot
import com.mario.buonipasto.ui.BuoniTheme
import com.mario.buonipasto.ui.LoginScreen
import com.mario.buonipasto.ui.TAB_ASSISTENTE

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_APRI_ASSISTENTE = "apri_assistente"
    }

    /** Scheda richiesta da una notifica ("Buoni Pasto ha risposto"). */
    private var tabRichiesta by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        ListaSpesa.carica(applicationContext)
        chiediPermessoNotifiche()
        if (intent?.getBooleanExtra(EXTRA_APRI_ASSISTENTE, false) == true) tabRichiesta = TAB_ASSISTENTE

        val store = SessionStore(applicationContext)
        val repository = Repository()

        setContent {
            BuoniTheme {
                var session by remember { mutableStateOf(store.load()) }
                val current = session
                if (current == null) {
                    LoginScreen(
                        repository = repository,
                        onLoggedIn = {
                            store.save(it)
                            session = it
                        },
                    )
                } else {
                    AppRoot(
                        repository = repository,
                        session = current,
                        tabRichiesta = tabRichiesta,
                        onTabGestita = { tabRichiesta = -1 },
                        onLogout = {
                            store.clear()
                            session = null
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_APRI_ASSISTENTE, false)) tabRichiesta = TAB_ASSISTENTE
    }

    /** Android 13+: senza permesso le notifiche dell'assistente non compaiono. Chiesto una volta sola. */
    private fun chiediPermessoNotifiche() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val p = getSharedPreferences("app", MODE_PRIVATE)
        if (p.getBoolean("permesso_notifiche_chiesto", false)) return
        p.edit().putBoolean("permesso_notifiche_chiesto", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
    }
}
