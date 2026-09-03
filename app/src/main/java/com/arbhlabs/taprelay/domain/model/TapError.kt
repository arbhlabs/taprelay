package com.arbhlabs.taprelay.domain.model

/**
 * Every failure surfaced to the user is one of these. Copy is plain-language, no jargon.
 */
enum class TapError(val message: String) {
    OFFLINE("You're offline. Check your Wi-Fi or mobile data."),
    AUTH("Your Govee connection needs to be reconnected."),
    RATE_LIMIT("Slow down a moment, then tap again."),
    DEVICE_OFFLINE("That device isn't responding. It may be switched off at the wall."),
    TAG_UNREGISTERED("This tag hasn't been set up yet."),
    TAG_MALFORMED("This tag isn't a TapRelay tag."),
    NFC_DISABLED("Turn on NFC in your phone settings to use tags."),
    WRITE_INTERRUPTED("Tag moved too quickly. Hold it still against the phone."),
    TAG_READ_ONLY("This tag is locked and can't be written."),
    TAG_TOO_SMALL("This tag doesn't have enough space."),
    PROVIDER_UNAVAILABLE("That smart-home service isn't connected."),
    SERVER("The service is having a brief hiccup. Try again shortly."),
    UNKNOWN("Something went wrong. Try again.");
}

class TapException(val error: TapError) : Exception(error.message)
