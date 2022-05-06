package com.alphagot.nightmarememo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.io.File

object NMConfig {
    var prefix: Component = Component.text("[").color(TextColor.color(210, 210, 210))
        .append(
            Component.text("NightmareMemo").color(TextColor.color(230, 0, 230))
        )
        .append(
            Component.text("] ").color(TextColor.color(210, 210, 210))
        )
}