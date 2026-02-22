package com.cocode.babakcast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.cocode.babakcast.ui.main.ShareIntentViewModel
import com.cocode.babakcast.ui.navigation.NavGraph
import com.cocode.babakcast.ui.theme.BabakCastTheme
import com.cocode.babakcast.util.MediaUrlExtractor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var shareIntentViewModel: ShareIntentViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shareIntentViewModel = ViewModelProvider(this)[ShareIntentViewModel::class.java]
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            BabakCastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND != intent.action) return
        val url = extractMediaUrlFromIntent(intent)
        if (url != null) {
            shareIntentViewModel.setPendingUrl(url)
        }
    }

    /**
     * Extracts a supported media URL (YouTube or X/Twitter) from an ACTION_SEND intent.
     */
    private fun extractMediaUrlFromIntent(intent: Intent): String? {
        val text = when {
            intent.type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.data != null -> intent.data?.toString()
            else -> null
        } ?: return null
        return MediaUrlExtractor.extractFromText(text)?.url
    }
}
