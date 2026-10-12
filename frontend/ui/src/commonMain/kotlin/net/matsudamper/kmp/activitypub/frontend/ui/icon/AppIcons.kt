package net.matsudamper.kmp.activitypub.frontend.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * パスは Material Icons（Apache License 2.0）の Filled から写したもの
 */
object AppIcons {
    val ArrowBack: ImageVector by lazy {
        materialIcon(
            name = "ArrowBack",
            pathData = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
            autoMirror = true,
        )
    }

    val Edit: ImageVector by lazy {
        materialIcon(
            name = "Edit",
            pathData = "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25z" +
                "M20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0" +
                "l-1.83,1.83 3.75,3.75 1.83,-1.83z",
            autoMirror = false,
        )
    }

    val Logout: ImageVector by lazy {
        materialIcon(
            name = "Logout",
            pathData = "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5z" +
                "M4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z",
            autoMirror = true,
        )
    }

    private fun materialIcon(
        name: String,
        pathData: String,
        autoMirror: Boolean,
    ): ImageVector {
        return ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = autoMirror,
            ).addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
            ).build()
    }
}
