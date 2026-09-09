# TapRelay ↔ Windows relay contract

TapRelay discovers a companion on the local network with a UDP broadcast on port `38117`.
The request is `TAPRELAY_DISCOVER/1`; the companion replies with a JSON
`DiscoveryResponse` containing its HTTP host, port, display name, and whether pairing is required.
Discovery is not authentication.

Pairing is an explicit user action. TapRelay sends `POST /v1/pair` with
`{"protocol":1,"pairingCode":"..."}` and stores the returned token outside Room. All action
requests use `POST /v1/action`, an `Authorization: Bearer` token, and this JSON shape:

```json
{
  "protocol": 1,
  "eventId": "stable-per-delivery-id",
  "ownerId": "phone-installation-id",
  "action": "media.play_pause",
  "value": null
}
```

The companion must return `{"protocol":1,"relayId":"...","token":"..."}` from pairing, echo `eventId`,
reject unsupported protocol versions, and return `accepted=false`
when offline or when it cannot claim the action. `eventId` is the idempotency/diagnostic key; it
must not be reused for a later user action. Supported action names are `media.play_pause`,
`media.next`, `media.previous`, `media.volume_up`, `media.volume_down`, and `media.mute`.

LastDose remains a separate signed `ContentResolver` contract. No Windows relay is allowed to
report a LastDose event or to bypass LastDose's `LOGGED` result requirement.
