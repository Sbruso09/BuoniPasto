package com.mario.buonipasto.data

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val code = prefs.getString("groupCode", null) ?: return null
        val id = prefs.getString("memberId", null) ?: return null
        val name = prefs.getString("name", null) ?: return null
        return Session(code, id, name)
    }

    fun save(session: Session) {
        prefs.edit()
            .putString("groupCode", session.groupCode)
            .putString("memberId", session.memberId)
            .putString("name", session.name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
