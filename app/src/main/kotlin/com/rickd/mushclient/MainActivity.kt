package com.rickd.mushclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rickd.mushclient.ui.nav.MudNavHost
import com.rickd.mushclient.ui.theme.MushClientTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MushClientTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MudNavHost()
                }
            }
        }
    }
}
