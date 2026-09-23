package com.ktele.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {

    private var client: Client? = null
    private var stage by mutableStateOf("starting")
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTelegram()
        setContent {
            MaterialTheme {
                Screen()
            }
        }
    }

    private fun startTelegram() {
        if (BuildConfig.TG_API_ID == 0) {
            stage = "error"
            message = "API keys missing in this build"
            return
        }
        try {
            System.loadLibrary("tdjni")
            client = Client.create(
                Client.ResultHandler { obj -> handleUpdate(obj) },
                null,
                null
            )
        } catch (e: Throwable) {
            stage = "error"
            message = "Telegram start error: " + e.message
        }
    }

    private fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthState(obj.authorizationState)
        }
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> stage = "phone"
            is TdApi.AuthorizationStateWaitCode -> stage = "code"
            is TdApi.AuthorizationStateWaitPassword -> stage = "password"
            is TdApi.AuthorizationStateReady -> stage = "ready"
            else -> {}
        }
    }

    private fun sendParameters() {
        val p = TdApi.SetTdlibParameters()
        p.databaseDirectory = filesDir.absolutePath + "/tdlib"
        p.useFileDatabase = true
        p.useChatInfoDatabase = true
        p.useMessageDatabase = true
        p.useSecretChats = false
        p.apiId = BuildConfig.TG_API_ID
        p.apiHash = BuildConfig.TG_API_HASH
        p.systemLanguageCode = "en"
        p.deviceModel = "Android"
        p.applicationVersion = "1.0"
        send(p)
    }

    private fun send(f: TdApi.Function<*>) {
        client?.send(f, Client.ResultHandler { r ->
            if (r is TdApi.Error) {
                message = r.message
            } else {
                message = ""
            }
        })
    }

    private fun submit(text: String) {
        when (stage) {
            "phone" -> send(TdApi.SetAuthenticationPhoneNumber(text.trim(), null))
            "code" -> send(TdApi.CheckAuthenticationCode(text.trim()))
            "password" -> send(TdApi.CheckAuthenticationPassword(text))
        }
    }

    @Composable
    private fun Screen() {
        var input by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("K-Tele Player", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            when (stage) {
                "starting" -> Text("Starting Telegram...")
                "ready" -> Text("Logged in to Telegram")
                "error" -> Text("Something went wrong")
                else -> {
                    val label = when (stage) {
                        "phone" -> "Phone number (with country code, e.g. +91...)"
                        "code" -> "Login code from Telegram"
                        else -> "Two-step verification password"
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        submit(input)
                        input = ""
                    }) {
                        Text("Next")
                    }
                }
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(message)
            }
        }
    }
}
