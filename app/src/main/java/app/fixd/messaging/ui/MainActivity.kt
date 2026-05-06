package app.fixd.messaging.ui

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.fixd.messaging.theme.FixdTheme

/**
 * The launcher activity. On first run we ask the OS to make Fixd the default
 * SMS handler  only after that is the app allowed to read/write the SMS
 * provider, send MMS, and receive new messages.
 */
class MainActivity : ComponentActivity() {

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result intentionally ignored  user choice is reflected by Telephony.Sms.getDefaultSmsPackage */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FixdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConversationListScreen(
                        isDefaultSmsApp = isDefaultSmsApp(),
                        onRequestDefault = { requestDefaultSmsRole() }
                    )
                }
            }
        }
    }

    private fun isDefaultSmsApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        }
    }
}
