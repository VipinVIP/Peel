package dev.vipi.peel

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vipi.peel.ui.AboutScreen
import dev.vipi.peel.ui.PeelTheme

class AboutActivity : ComponentActivity() {
    companion object {
        const val REPO_URL = "https://github.com/VipinVIP/Peel"
        const val AUTHOR = "VipinVIP"
        const val AUTHOR_URL = "https://github.com/VipinVIP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            PeelTheme {
                AboutScreen(version = version, onBack = ::finish, onOpen = ::openUrl)
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
