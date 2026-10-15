package net.matsudamper.kmp.activitypub.android.session

internal interface SessionLocalStore {
    suspend fun read(): SessionState?

    suspend fun write(state: SessionState)
}

internal interface SessionBackupStore {
    suspend fun restore(): SessionCookie?

    suspend fun save(session: SessionCookie?)
}
