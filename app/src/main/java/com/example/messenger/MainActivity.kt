package com.example.messenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    private val DEFAULT_URL = "https://my-server-ztxz.onrender.com"

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var permissionRequest: PermissionRequest? = null

    // Регистрация для выбора файлов
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        
        val data = result.data
        val results: Array<Uri>? = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    // Регистрация для запроса разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        
        if (allGranted) {
            // Все разрешения получены - передаем в WebView
            permissionRequest?.grant(permissionRequest?.resources)
            permissionRequest = null
            Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Некоторые разрешения не получены", Toast.LENGTH_LONG).show()
            permissionRequest?.deny()
            permissionRequest = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val setupLayout = findViewById<LinearLayout>(R.id.setupLayout)
        val webView = findViewById<WebView>(R.id.webView)
        val urlInput = findViewById<EditText>(R.id.serverUrlInput)
        val connectButton = findViewById<Button>(R.id.connectButton)

        // Загружаем сохраненный URL
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        var savedUrl = prefs.getString(KEY_URL, null)
        if (savedUrl.isNullOrBlank()) {
            savedUrl = DEFAULT_URL
            prefs.edit().putString(KEY_URL, savedUrl).apply()
        }
        urlInput.setText(savedUrl)

        // Настройка WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Проверяем разрешения после загрузки
                checkAndRequestPermissions()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Сохраняем запрос
                permissionRequest = request
                
                // Собираем необходимые разрешения
                val neededPermissions = mutableListOf<String>()
                
                for (resource in request.resources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_CAMERA -> {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CAMERA
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                neededPermissions.add(Manifest.permission.CAMERA)
                            }
                        }
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                }

                // Добавляем разрешения для файлов
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_MEDIA_IMAGES
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        neededPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_MEDIA_VIDEO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        neededPermissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }

                if (neededPermissions.isNotEmpty()) {
                    // Запрашиваем разрешения через приложение
                    permissionLauncher.launch(neededPermissions.toTypedArray())
                } else {
                    // Все разрешения уже есть
                    request.grant(request.resources)
                    permissionRequest = null
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

        // Автоподключение
        if (!savedUrl.isNullOrBlank()) {
            openServer(savedUrl)
        }

        connectButton.setOnClickListener {
            var url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Введите адрес сервера", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            prefs.edit().putString(KEY_URL, url).apply()
            openServer(url)
        }
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
            
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
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
