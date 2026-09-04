package com.arbhlabs.taprelay.domain.model

/**
 * What a trigger does when it fires. A trigger (NFC tap, controller button, in-app test, or any
 * future trigger type) resolves to an item, and the item's activation mode decides the outcome.
 *
 * A trigger may carry its own override; when it does not, the item's own mode is used.
 */
enum class ActivationMode {
    /** Run the item's configured action straight away. The original TapRelay behaviour. */
    EXECUTE,

    /** Open TapRelay on this item's full control screen instead of firing it. */
    OPEN_ITEM,

    /** Show the small contextual control surface so the choice is made in the moment. */
    QUICK_CONTROLS;

    val label: String
        get() = when (this) {
            EXECUTE -> "Execute"
            OPEN_ITEM -> "Open item"
            QUICK_CONTROLS -> "Quick Controls"
        }

    val description: String
        get() = when (this) {
            EXECUTE -> "Run the action immediately."
            OPEN_ITEM -> "Open TapRelay on this item so you can edit or run it."
            QUICK_CONTROLS -> "Show a small control panel and decide in the moment."
        }

    companion object {
        fun fromNameOrDefault(value: String?): ActivationMode =
            runCatching { value?.let { valueOf(it) } }.getOrNull() ?: EXECUTE

        /**
         * The one rule the whole app resolves activation by: a trigger's own choice wins,
         * otherwise the item decides, and an item that has never been configured executes.
         */
        fun resolve(triggerOverride: ActivationMode?, itemMode: ActivationMode?): ActivationMode =
            triggerOverride ?: itemMode ?: EXECUTE
    }
}
