package gg.opgg.android

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import gg.opgg.android.ui.theme.OpggTheme
import kotlinx.coroutines.delay

/**
 * Signs in by loading the real onepiece.gg site in a WebView and letting the
 * site's own JavaScript run the Google/social login and the DotGG token
 * exchange (wp-admin/admin-ajax.php?action=dotgg_token_exchange). When that
 * exchange completes it writes dotgg_user / dotgg_token / dotgg_nick into
 * localStorage, which we read out and hand back to the caller.
 *
 * This deliberately does not try to reimplement the site's auth: the API has no
 * social login endpoint and its password login needs a site identifier the
 * server does not disclose to us.
 */
class SignInActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var resultSent = false

    @Volatile
    private var credentials: Triple<Long, String, String?>? = null

    @Volatile
    private var wpSessionSeen = false

    private var reloads = 0

    /**
     * Main-frame load failure, if any. Offline used to leave the user staring at
     * the browser's bare "Web page not available" page with no way forward; the
     * overlay below offers a retry instead.
     */
    private var pageError by mutableStateOf<String?>(null)

    private val sessionCallback = ValueCallback<String> { json ->
        val raw = json?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?: return@ValueCallback
        val parts = raw.split('|')
        if (parts.size < 2) return@ValueCallback
        if (parts[0] == "TOK") parseToken(parts)?.let { credentials = it } else if (parts[1] == "1") wpSessionSeen = true
    }

    private fun parseToken(parts: List<String>): Triple<Long, String, String?>? {
        if (parts.size < 3) return null
        val user = parts[1].toLongOrNull() ?: return null
        val token = parts[2]
        if (token.isBlank() || user <= 0L) return null
        val nick = parts.getOrNull(3)?.takeIf { it.isNotBlank() && it != "null" }
        return Triple(user, token, nick)
    }

    /**
     * Let every http(s) page load inside the WebView like a normal browser, and
     * hand only the schemes the WebView cannot render (mail, tel, market, intent)
     * to the system. Returning true for ordinary web URLs would silently swallow
     * navigation, which looks identical to a button that does nothing.
     */
    private fun handleUrl(url: android.net.Uri): Boolean {
        val scheme = url.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") return false
        // Hand off to whatever app owns mailto:/tel:/market:. Returning true
        // means "handled" — the inverse of this silently asked the WebView to
        // load a mailto: URL, which it cannot do.
        return runCatching {
            startActivity(
                Intent(android.content.Intent.ACTION_VIEW, url).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 DotGGApp/1.0"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(request.url)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    // A new navigation clears the previous failure. Sub-frame
                    // and redirect loads land here too, which is fine: the
                    // error overlay only ever follows a main-frame failure.
                    super.onPageStarted(view, url, favicon)
                    pageError = null
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    // Only the main frame matters - an ad pixel failing to load
                    // must not hide a login form that did load.
                    if (request.isForMainFrame) {
                        pageError = error.description?.toString()?.takeIf { it.isNotBlank() }
                            ?: "The sign-in page did not load."
                    }
                }
            }
            // OAuth popups: window.open() is otherwise dropped, and a login that
            // vanishes is indistinguishable from one that is silently failing.
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    val popup = WebView(view.context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val handled = handleUrl(r.url)
                                if (!handled) v.loadUrl(r.url.toString())
                                v.destroy()
                                return true
                            }
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport == null) {
                        popup.destroy()
                        return false
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
        }
        webView = wv
        // The site's dedicated login page, not the homepage: the user signs in
        // against a form, not by hunting the Log in button in the busy front
        // page. returl sends the site back to the SPA after auth, where its own
        // JavaScript performs the token exchange we poll localStorage for.
        wv.loadUrl(LOGIN_URL)

        setContent {
            OpggTheme {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "Sign in to OnePiece.gg",
                        color = Color(0xFFEE6DCA),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                    Text(
                        "Sign in with Google (or email) on the page below. " +
                            "This app never sees your password - it only reads the " +
                            "session token the site issues afterwards.",
                        color = Color(0xFFB0BAC5),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (pageError == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { wv },
                            modifier = Modifier.fillMaxSize().testTag("signin_webview"),
                        )
                        val failure = pageError
                        if (failure != null) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF14161A))
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Can't reach OnePiece.gg",
                                    color = Color(0xFFEE6DCA),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "$failure\n\nCheck your connection - mobile data or " +
                                        "Wi-Fi - then try again.",
                                    color = Color(0xFFB0BAC5),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                                )
                                Button(onClick = {
                                    pageError = null
                                    wv.loadUrl(LOGIN_URL)
                                }) { Text("Retry") }
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    while (!resultSent) {
                        // The site performs the token exchange on its own once you are
                        // signed in; we just poll localStorage for the result.
                        wv.evaluateJavascript(READ_SESSION_JS, sessionCallback)
                        delay(1000)
                        nudgeExchange(wv)
                        val found = credentials
                        if (found != null) {
                            resultSent = true
                            setResult(
                                Activity.RESULT_OK,
                                Intent()
                                    .putExtra(EXTRA_USER, found.first)
                                    .putExtra(EXTRA_TOKEN, found.second)
                                    .putExtra(EXTRA_NICK, found.third),
                            )
                            finish()
                            return@LaunchedEffect
                        }
                    }
                }
            }
        }
    }

    /** Returns (userId, token, nick) once the site has stored a DotGG session, else null. */
    private fun parseSession(json: String?): Triple<Long, String, String?>? {
        val raw = json?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\")
            ?: return null
        val parts = raw.split('|')
        if (parts.size < 2) return null
        val token = parts[1]
        val user = parts[0].toLongOrNull() ?: return null
        if (token.isBlank() || user <= 0L) return null
        val nick = parts.getOrNull(2)?.takeIf { it.isNotBlank() && it != "null" }
        return Triple(user, token, nick)
    }

    /** Re-runs the site's token exchange by reloading the SPA. */
    private fun nudgeExchange(wv: WebView) {
        if (!wpSessionSeen || resultSent || reloads >= 6) return
        reloads++
        wv.post { wv.loadUrl("https://onepiece.gg/") }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_USER = "user"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_NICK = "nick"

        /** The DotGG login page; `returl` returns to the SPA that mints the token. */
        private const val LOGIN_URL = "https://dotgg.gg/login/?returl=https://onepiece.gg/"

        private val READ_SESSION_JS = """
            (function(){try{
                var t=localStorage.getItem('dotgg_token')||'';
                if(t) return 'TOK|'+localStorage.getItem('dotgg_user')+'|'+t+'|'+(localStorage.getItem('dotgg_nick')||'');
                var wp=(document.cookie.indexOf('wordpress_logged_in')>=0)?'1':'0';
                return 'WP|'+wp;
            }catch(e){return ''}})()
        """.trimIndent()
    }
}
