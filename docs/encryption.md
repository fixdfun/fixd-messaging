# Fixd Messaging  End-to-End Encryption Spec

## Overview

When both sender and recipient are using Fixd Messaging and have opted in,
messages are encrypted in transit using the **Signal Protocol (Double Ratchet +
X3DH)** via the `libsignal-android` library. Carrier networks see only metadata
(sender, recipient, timestamp, approximate message size). Message content is
protected by forward-secret, break-in-recovery-capable encryption.

---

## Wire Format (FXD1)

Every Fixd-encrypted SMS body starts with the prefix `FXD1:` followed by a
single-character type tag and a Base64-encoded payload.

### Single-segment (short payloads)

```
FXD1:<type>:<base64-payload>
```

### Multi-segment (payload exceeds ~100 Base64 chars per segment)

```
FXD1:<type>:<msgId>:<seq>/<total>:<base64-chunk>
```

- `<type>`  `H` handshake, `M` message, `K` re-key
- `<msgId>`  8 hex chars, random, uniquely identifies a chunked message
- `<seq>/<total>`  1-based sequence index out of total chunk count

Non-Fixd recipients receive the raw `FXD1:...` text. This is intentionally
visible  the opt-in toggle must be enabled by the user before any FXD1
messages are sent, so plain contacts are never affected.

---

## Session Establishment (Handshake)

### Trigger

The user taps the **lock badge** in the conversation toolbar for the first
time and confirms "Enable secure messaging". This opt-in is stored per
contact in `fixd_peers` DataStore.

### Flow

```
Alice                              Bob (Fixd user)
  |                                    |
  |-- FXD1:H:<Alice PreKeyBundle> ---> |  (one or more SMS segments)
  |                                    |  Bob's SmsReceiver detects FXD1:H:
  |                                    |  Calls FixdSignal.processIncomingHandshake()
  |                                    |  SessionBuilder.process(AliceBundle)
  |                                    |  Session established on Bob's side
  | <-- FXD1:H:<Bob PreKeyBundle> --   |  Bob auto-replies with his bundle
  |                                    |
  |  FixdSignal.processIncomingHandshake(BobBundle)
  |  Session established on Alice's side
  |                                    |
  |== FXD1:M:<ciphertext> ==========> |  All subsequent messages are encrypted
  |<= FXD1:M:<ciphertext> ===========|
```

After `ESTABLISHED` state is recorded in `fixd_peers`, every outbound message
goes through `SessionCipher.encrypt()` automatically.

---

## Key Material

| Material | Generated | Stored | Persistence |
|---|---|---|---|
| Identity key pair (Ed25519) | First app launch | `fixd_signal_keys` DataStore | Permanent |
| Signed prekey | First launch + rotation | `fixd_signal_keys` DataStore | Until rotated |
| One-time prekeys (100) | First launch | `fixd_signal_keys` DataStore | Consumed on use |
| Session record | After handshake | `fixd_signal_keys` DataStore | Until reset |
| Peer fingerprint | After handshake | `fixd_peers` DataStore | Until reset |

All DataStore files live in the app's private data directory
(`/data/data/app.fixd.messaging/files/datastore/`). They are not backed up
by default (BackupAgent should exclude them  see `BackupManager`).

---

## Threat Model

| Protected | Not Protected |
|---|---|
| Message content in transit | Sender/recipient phone numbers |
| Message content at rest (plaintext stored in inbox) | Timestamp |
| Forward secrecy (each message uses a new chain key) | Message count / frequency |
| Break-in recovery (ratchet advances on each message) | Carrier-level metadata |

### Identity key change detection

If a peer's identity key changes (e.g. they reinstalled), `isTrustedIdentity()`
returns `false`. The `SmsReceiver` will fail to decrypt and display:
>  [Encrypted message  could not decrypt. Tap to reset session.]

The user can then go to **Safety Number**  **Reset Secure Session** and
initiate a new handshake.

### Safety Number verification

The Safety Number screen (`SafetyNumberScreen`) displays the peer's identity key
fingerprint in 5-character groups. Users should compare this out-of-band (phone
call, in person) to confirm no man-in-the-middle substituted a key during the
SMS handshake.

---

## Not In Scope (Phase 1)

- **Group E2E encryption**  requires Sender Keys protocol; planned for Phase 4
- **Multi-device**  one identity per phone number only
- **Server-side key directory**  all key exchange is purely SMS-based
- **Sealed sender**  requires a server intermediary
- **Prekey rotation**  one-time prekeys are generated once; rotation is Phase 2

---

## Library

`org.signal:libsignal-android:0.55.1`  
License: AGPLv3 (compatible with our MIT app since we don't modify libsignal itself)  
Source: https://github.com/signalapp/libsignal
