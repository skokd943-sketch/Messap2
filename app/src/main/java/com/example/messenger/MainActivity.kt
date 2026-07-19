package com.example.messenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PREFS = "messenger_prefs"
    private val KEY_URL = "server_url"

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        val data = result.data
        val results: Array<Uri>? = if (result.resultCode == RESULT_OK && data?.data != null) {
            arrayOf(data.data!!)
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    // Дожидаемся реального ответа Android о разрешениях, и только
    // после этого разрешаем (или запрещаем) доступ WebView — раньше
    // разрешение для WebView выдавалось сразу, не дожидаясь ответа
    // пользователя, из-за чего первый звонок/голосовое падали с ошибкой.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        if (request == null) return@registerForActivityResult
        val allGranted = results.values.all { it }
        if (allGranted) request.grant(request.resources) else request.deny()
    }

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
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                        needed.add(Manifest.permission.CAMERA)
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        needed.add(Manifest.permission.RECORD_AUDIO)

                    if (needed.isEmpty()) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest = request
                        permissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback = callback
                val intent = params?.createIntent()
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        fun openServer(url: String) {
            setupLayout.visibility = LinearLayout.GONE
            webView.visibility = WebView.VISIBLE
            webView.loadUrl(url)
        }

        if (!savedUrl.isNullOrBlank()) openServer(savedUrl)

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
