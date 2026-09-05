# ARBH Labs HapticSignatures

HapticSignatures is ARBH Labs' proprietary tactile-feedback subsystem for TapRelay. It gives a
completed activation a stable, recognizable feel without making vibration a prerequisite for the
action itself.

The engine builds a semantic descriptor from trigger source, physical control, action family,
stable item identity, requested/resulting transition, and execution result. Logs use a crisp double
confirmation; device actions use a compact shaped confirmation; macros use a short sequence;
failures use a clear warning. ON rises and OFF falls where amplitude control exists.

The candidate allocator is deterministic. For a new mapping it filters the bounded candidate
collection by semantic family, scores candidates with a weighted rhythm, pulse, envelope,
amplitude, and (only when verified) spatial-channel distance, then selects the maximum of each
candidate's minimum distance to existing assignments. Assignments are persisted by stable mapping
identity and capability profile, so a mapping does not wander across launches.

Android capability discovery reads the actual InputDevice vibrator manager and exposed IDs. It
never guesses motor ordering or claims trigger rumble: unvalidated channels are rendered safely in
parallel. One-channel hardware preserves identity through rhythm; phones use their vibrator;
missing/disconnected hardware is a no-op. Rendering is cancelled/coalesced before a new pattern and
all haptic exceptions are contained, so automation execution cannot fail because feedback did.
