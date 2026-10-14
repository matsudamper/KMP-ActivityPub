package net.matsudamper.kmp.activitypub.android

import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Singleton

@Module
@ComponentScan("net.matsudamper.kmp.activitypub.android")
class AndroidAppModule {
    @Singleton
    fun userApi(clients: GraphQlClientSource): UserApi = UserApi(clients)
}
