package com.arbhlabs.taprelay.domain.model

import com.arbhlabs.taprelay.execution.webhook.WebhookMethod

/**
 * A starting point for the Web request editor, so the common cases are not a blank form.
 *
 * A preset only pre-fills the fields the owner would otherwise have to look up - the method, a
 * URL shape, an example body. It is not a separate action type: once saved, the item is an
 * ordinary webhook item and the same executor sends it. Nothing here stores a credential; the
 * key still goes through the editor's encrypted "Key" field.
 */
data class WebhookPreset(
    val id: String,
    val label: String,
    val method: WebhookMethod,
    /** Shown as the address hint. Blank means "type your own". */
    val urlHint: String,
    val bodyTemplate: String,
    /** One line under the address field explaining where to get the URL. */
    val help: String
) {
    companion object {
        val NTFY = WebhookPreset(
            id = "ntfy",
            label = "ntfy",
            method = WebhookMethod.POST,
            urlHint = "https://ntfy.sh/your-topic",
            bodyTemplate = "TapRelay button pressed",
            help = "Pick any topic name at ntfy.sh, subscribe to it in the ntfy app, then use that address."
        )
        val DISCORD = WebhookPreset(
            id = "discord",
            label = "Discord",
            method = WebhookMethod.POST,
            urlHint = "https://discord.com/api/webhooks/…",
            bodyTemplate = "{\"content\":\"TapRelay button pressed\"}",
            help = "In Discord: Channel settings → Integrations → Webhooks → New Webhook → Copy URL."
        )
        val HOME_ASSISTANT = WebhookPreset(
            id = "ha_webhook",
            label = "Home Assistant",
            method = WebhookMethod.POST,
            urlHint = "https://your-ha:8123/api/webhook/your-id",
            bodyTemplate = "",
            help = "In Home Assistant, add an Automation with a Webhook trigger and use its webhook id here. No token needed."
        )
        val CUSTOM = WebhookPreset(
            id = "custom",
            label = "Something else",
            method = WebhookMethod.POST,
            urlHint = "https://",
            bodyTemplate = "",
            help = "Any address that accepts an HTTP request."
        )

        val ALL = listOf(NTFY, DISCORD, HOME_ASSISTANT, CUSTOM)
    }
}
