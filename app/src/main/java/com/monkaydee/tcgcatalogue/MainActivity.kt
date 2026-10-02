package com.monkaydee.tcgcatalogue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monkaydee.tcgcatalogue.ui.AppNav
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as TcgApp).repository
        setContent {
            TcgTheme {
                AppNav(repository)
            }
        }
    }
}
