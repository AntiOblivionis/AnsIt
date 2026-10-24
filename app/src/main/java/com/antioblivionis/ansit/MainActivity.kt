package com.antioblivionis.ansit

import android.os.Bundle
import android.view.WindowManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var model: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        window.decorView.isForceDarkAllowed = false
        enableEdgeToEdge()
        model = ViewModelProvider(this)[MainViewModel::class.java]
        setContent { AnsItTheme { AnsItScreen(model) } }
    }

    override fun onStart() {
        super.onStart()
        model.onForeground()
    }
}
