package app.fixd.messaging.mms

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Stub MMS sender. Fully PDU-encoding outbound MMS messages and uploading them
 * to the carrier MMSC is non-trivial; this scaffold delegates to the platform
 * SmsManager.sendMultimediaMessage(...) flow which the implementor wires up
 * with a content URI of the prepared MMS PDU. See docs/mms.md for the spec.
 */
object MmsSender {
    fun send(ctx: Context, address: String, body: String, attachments: List<Uri>) {
        Log.i("FixdMms", "Queue MMS to=$address parts=${attachments.size} bodyLen=${body.length}")
        // TODO: build SMIL + PDU, write to a content provider URI, then call
        //   SmsManager.getDefault().sendMultimediaMessage(ctx, contentUri, null, null, sentIntent)
    }
}
