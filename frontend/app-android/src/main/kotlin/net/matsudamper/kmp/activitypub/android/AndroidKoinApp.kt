package net.matsudamper.kmp.activitypub.android

import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureModule
import org.koin.core.annotation.KoinApplication

@KoinApplication(
    modules = [
        HomeFeatureModule::class,
        AndroidAppModule::class,
    ],
)
internal class AndroidKoinApp
