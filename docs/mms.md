# MMS implementation notes

The outbound MMS path uses `SmsManager.sendMultimediaMessage(...)`. The PDU
builder needs to:

1. Compose a SMIL document referencing each attachment by `cid:`.
2. Encode an `m-send-req` PDU with the recipient(s), subject (optional), the
   SMIL part, and each media part.
3. Persist the serialized PDU to a content URI exposed by a FileProvider.
4. Pass that URI to `SmsManager.sendMultimediaMessage` along with a `PendingIntent`
   that the `MmsSentReceiver` listens on.

Inbound MMS is a WAP-PUSH `application/vnd.wap.mms-message` broadcast. Parse
the notification PDU, then call `SmsManager.downloadMultimediaMessage` to fetch
the full message from the carrier MMSC.

Reference: AOSP `Mms` app (`packages/apps/Mms`) and the `klinker41/android-smsmms`
library.
