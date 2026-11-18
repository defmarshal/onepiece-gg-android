package gg.opgg.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import gg.opgg.android.data.AppViewModel
import gg.opgg.android.data.CardCache
import gg.opgg.android.data.OpggApi
import gg.opgg.android.data.PrefsStore
import gg.opgg.android.data.Session
import gg.opgg.android.data.SessionStore
import gg.opgg.android.ui.AppRoot
import gg.opgg.android.ui.CardImageLoaderHolder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Debug affordance:
     * `adb shell am start -n gg.opgg.android/.MainActivity --es screen <spec>`
     *
       * Synthetic taps are blocked on this device by Android's "USB debugging
       * (Security settings)" gate, so this is how the decks and related screens
       * get reviewed without asking the user to tap. Specs: `tab:0..3`,
       * `card:<id>`, `related:<id>`, `deck:<slug>`, `deckfirst`, `builder`,
       *        `missing:<id>`, `editor:<id|new>`, `query:<search text>`.
       */
    private var screenSpec: String? = null

    private lateinit var sessions: SessionStore
    private lateinit var vm: AppViewModel

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data: Intent = result.data ?: return@registerForActivityResult
        val token = data.getStringExtra(SignInActivity.EXTRA_TOKEN).orEmpty()
        val user = data.getLongExtra(SignInActivity.EXTRA_USER, 0L)
        if (token.isBlank() || user <= 0L) return@registerForActivityResult
        lifecycleScope.launch {
            sessions.save(Session(user, token, data.getStringExtra(SignInActivity.EXTRA_NICK)), null)
            vm.onSignedIn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before setContent, and before any composable can ask for an image:
        // Coil's disk cache is opened lazily on first use, and without an
        // explicit loader that first use lands in the middle of the first grid,
        // where every tile blocks on one lock and the screen pops in at once.
        // See CardImages for the full account.
        CardImageLoaderHolder.install(applicationContext)
        screenSpec = intent?.getStringExtra("screen")
        sessions = SessionStore(applicationContext)
        val cache = CardCache(applicationContext)
        val prefs = PrefsStore(applicationContext)
        val api = OpggApi { sessions.peek()?.let { it.userId to it.token } }
        vm = AppViewModel(api, sessions, cache, prefs, applicationContext)

        setContent {
            val composed: AppViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
                }
            )
            AppRoot(
                vm = composed,
                onSignIn = { signIn.launch(Intent(this, SignInActivity::class.java)) },
                initialScreen = screenSpec,
            )
        }
    }
}
