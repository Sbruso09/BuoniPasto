package com.mario.buonipasto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mario.buonipasto.data.Repository
import com.mario.buonipasto.data.SessionStore
import com.mario.buonipasto.ui.BuoniTheme
import com.mario.buonipasto.ui.HomeScreen
import com.mario.buonipasto.ui.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    HomeScreen(
                        repository = repository,
                        session = current,
                        onLogout = {
                            store.clear()
                            session = null
                        },
                    )
                }
            }
        }
    }
}
