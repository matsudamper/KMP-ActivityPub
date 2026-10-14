package net.matsudamper.kmp.activitypub.frontend

import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureModule
import org.koin.core.annotation.KoinApplication

@KoinApplication(
    modules = [
        WebAppModule::class,
        HomeFeatureModule::class,
    ],
)
internal class WebKoinApp
