package net.matsudamper.kmp.activitypub.frontend

import net.matsudamper.kmp.activitypub.frontend.logic.FrontendApiModule
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureModule
import net.matsudamper.kmp.activitypub.frontend.wasm.WasmGraphQlModule
import org.koin.core.annotation.KoinApplication

@KoinApplication(
    modules = [
        FrontendApiModule::class,
        WasmGraphQlModule::class,
        HomeFeatureModule::class,
    ],
)
internal class WebKoinApp
