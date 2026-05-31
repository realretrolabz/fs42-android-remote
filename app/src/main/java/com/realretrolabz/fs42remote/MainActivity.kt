package com.realretrolabz.fs42remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.realretrolabz.fs42remote.ui.theme.FS42RemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FS42RemoteTheme {
                RemoteCanvasPlaceholder()
            }
        }
    }
}

@Composable
fun RemoteCanvasPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black,
    )
    {
        Text(
            text = "FS42 Remote",
            color = Color.White,
            modifier = Modifier
                .systemBarsPadding()
                .padding(24.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RemoteCanvasPlaceholderPreview() {
    FS42RemoteTheme {
        RemoteCanvasPlaceholder()
    }
}
