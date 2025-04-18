package com.xjyzs.aiapi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xjyzs.aiapi.ui.theme.AIAPITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class SettingsViewModel: ViewModel() {
    var models = mutableStateListOf<String>()
}

class SettingsActivity : ComponentActivity() {
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigateToMainAndFinish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIAPITheme {
                val viewModel:SettingsViewModel= viewModel()
                SettingsUI(viewModel)
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }
    private fun navigateToMainAndFinish() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
}

@SuppressLint("MutableCollectionMutableState", "CommitPrefEdits")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsUI(viewModel: SettingsViewModel){
    val context = LocalContext.current
    val settingsPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val currentConfigPref = context.getSharedPreferences("currentConfigPref", Context.MODE_PRIVATE)
    var scrollState= rememberScrollState()
    var modelsExpanded by remember { mutableStateOf(false) }
    var configsExpanded by remember { mutableStateOf(false) }
    var configsList by remember { mutableStateOf(mutableListOf<String>()) }
    var apiUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var currentConfig by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        currentConfig=currentConfigPref.getString("currentConfig","")!!
        for (i in settingsPref.all) {
            configsList.add(i.key)
        }
        val settings = JsonParser.parseString(
            settingsPref.getString(
                currentConfig,
                "{'apiUrl':'','apiKey':'','model':'','systemPrompt':''}"
            )
        ).asJsonObject
        apiUrl=settings.get("apiUrl").asString;apiKey=settings.get("apiKey").asString;model=settings.get("model").asString;systemPrompt=settings.get("systemPrompt").asString
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        with(currentConfigPref.edit()) {
                            putString("currentConfig",currentConfig)
                            apply()
                        }
                        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "")
                    }
                }
            )
        }) { innerPadding ->
        Column(Modifier.fillMaxSize().wrapContentSize(Alignment.Center).padding(innerPadding).padding(30.dp).verticalScroll(scrollState)) {
            TextField(label = { Text("config") }, value = currentConfig, onValueChange = { currentConfig = it }, modifier = Modifier.fillMaxWidth(),trailingIcon = {
                IconButton(onClick = {
                configsExpanded = !configsExpanded
            }) { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "") } })
            DropdownMenu(
                expanded = configsExpanded,
                onDismissRequest = { configsExpanded = false },
            ) {
                for (i in configsList) {
                    DropdownMenuItem(
                        text = { Text(i) },
                        onClick = {
                            currentConfig=i
                            configsExpanded = false
                            val settings = JsonParser.parseString(
                                settingsPref.getString(
                                    currentConfig,
                                    "{'apiUrl':'','apiKey':'','model':'','systemPrompt':''}"
                                )
                            ).asJsonObject
                            apiUrl=settings.get("apiUrl").asString;apiKey=settings.get("apiKey").asString;model=settings.get("model").asString;systemPrompt=settings.get("systemPrompt").asString
                        }
                    )
                }
            }
            TextField(label = { Text("api_url") }, value = apiUrl, onValueChange = { apiUrl = it }, modifier = Modifier.fillMaxWidth())
            TextField(label = { Text("api_key") }, value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth())
            TextField(label = { Text("model") }, value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(),trailingIcon = { IconButton(onClick = {
                modelsExpanded = !modelsExpanded
                if (viewModel.models.isEmpty()) {
                    getModels(apiUrl, apiKey, viewModel,context)
                }
            }) { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "") } })
            DropdownMenu(
                expanded = modelsExpanded,
                onDismissRequest = { modelsExpanded = false }
            ) {
                for (i in viewModel.models) {
                    if (model.toString().lowercase() in i.lowercase()) {
                        DropdownMenuItem(
                            text = { Text(i) },
                            onClick = {
                                model = i
                                modelsExpanded = false
                            }
                        )
                    }
                }
            }
            TextField(label = { Text("system_prompt") }, value = systemPrompt, onValueChange = { systemPrompt = it }, modifier = Modifier.fillMaxWidth())
            Button({
                with(settingsPref.edit()) {
                    putString(currentConfig,Gson().toJson(mapOf("apiUrl" to apiUrl,"apiKey" to apiKey,"model" to model,"systemPrompt" to systemPrompt)))
                    apply()
                }
                with(currentConfigPref.edit()) {
                    putString("currentConfig",currentConfig)
                    apply()
                }
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("确认")
            }
        }
    }
}

private fun getModels(apiUrl: String, apiKey: String, viewModel: SettingsViewModel, context: Context) {
    var url=""
    url = if (apiUrl.endsWith("/")){
        apiUrl+"models"
    }else{
        "$apiUrl/models"
    }
    val client= OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $apiKey")
        .build()
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val response: Response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json=JsonParser.parseString(response.body?.string()).asJsonObject
                val data=json.getAsJsonArray("data")
                for (i in data){
                    viewModel.models.add(i.asJsonObject.get("id").asString)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}