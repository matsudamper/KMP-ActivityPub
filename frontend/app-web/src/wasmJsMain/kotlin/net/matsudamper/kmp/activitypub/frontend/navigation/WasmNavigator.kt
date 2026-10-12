package net.matsudamper.kmp.activitypub.frontend.navigation

internal class WasmNavigator(
    private val navController: NavController,
) : Navigator {
    override fun navigate(screen: Screen) {
        navController.navigateTo(screen)
    }

    override fun back() {
        navController.back()
    }

    override fun replaceAll(screen: Screen) {
        navController.replaceWith(screen)
    }
}
