package net.matsudamper.kmp.activitypub.frontend.navigation

internal class WasmNavigator(
    private val navController: NavController,
) : Navigator {
    override suspend fun navigate(screen: Screen) {
        navController.navigateTo(screen)
    }

    override suspend fun back() {
        navController.back()
    }
}
