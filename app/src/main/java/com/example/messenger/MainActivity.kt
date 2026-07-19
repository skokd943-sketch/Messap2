package com.example.messenger

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val PREFS = "messenger_prefs"
    private val KEY_URL = "server_url"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val setupLayout = findViewById<LinearLayout>(R.id.setupLayout)
        val webView = findViewById<WebView>(R.id.webView)
        val urlInput = findViewById<EditText>(R.id.serverUrlInput)
        val connectButton = findViewById<Button>(R.id.connectButton)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_URL, null)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        fun openServer(url: String) {
            setupLayout.visibility = LinearLayout.GONE
            webView.visibility = WebView.VISIBLE
            webView.loadUrl(url)
        }

        if (!savedUrl.isNullOrBlank()) {
            openServer(savedUrl)
        }

        connectButton.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Введите адрес сервера", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http")) url = "https://$url"
            prefs.edit().putString(KEY_URL, url).apply()
            openServer(url)
        }
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(R.id.webView)
        if (webView.visibility == WebView.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
