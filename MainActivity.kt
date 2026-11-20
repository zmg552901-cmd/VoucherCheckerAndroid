package com.example.voucherchecker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "VoucherCheckerAndroid Ready"
            textSize = 20f
            setPadding(48, 120, 48, 48)
        }
        setContentView(textView)
    }
}
