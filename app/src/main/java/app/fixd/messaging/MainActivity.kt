package app.fixd.messaging

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.fixd.messaging.theme.FixdTheme
import app.fixd.messaging.ui.FixdNavHost
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            FixdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val ctx = this
                    var isDefault by remember { mutableStateOf(isDefaultSmsApp(ctx)) }
                    val roleLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { isDefault = isDefaultSmsApp(ctx) }
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { /* ignore - UI re-reads state */ }
                    FixdNavHost(
                        isDefaultSmsApp = isDefault,
                        onRequestDefault = { requestDefaultSmsApp(ctx, roleLauncher::launch) },
                        onRequestPermissions = {
                            permLauncher.launch(arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.RECEIVE_MMS,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_PHONE_STATE
                            ))
                        }
                    )
                }
            }
        }
    }
}

fun isDefaultSmsApp(ctx: Context): Boolean {
    return ctx.packageName == Telephony.Sms.getDefaultSmsPackage(ctx)
}

private fun requestDefaultSmsApp(ctx: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
            launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
            return
        }
    }
    @Suppress("DEPRECATION")
    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
    launch(intent)
}
