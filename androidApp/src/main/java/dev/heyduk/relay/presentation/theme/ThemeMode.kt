package dev.heyduk.relay.presentation.theme

/** User-selectable theme mode, persisted to DataStore. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
