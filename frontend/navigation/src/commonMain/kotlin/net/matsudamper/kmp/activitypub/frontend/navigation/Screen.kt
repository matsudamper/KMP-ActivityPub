package net.matsudamper.kmp.activitypub.frontend.navigation

sealed interface Screen {
    data object Login : Screen

    data object Home : Screen

    data object Post : Screen
}
