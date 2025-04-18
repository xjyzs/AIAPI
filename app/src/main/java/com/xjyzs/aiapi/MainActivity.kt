package com.xjyzs.aiapi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.xjyzs.aiapi.ui.theme.AIAPITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


data class Message(val role: String, val content: String)
@SuppressLint("MutableCollectionMutableState")
class ChatViewModel : ViewModel() {
    var msgs = mutableStateListOf<Message>()
    var isLoading by mutableStateOf(false)
    var inputMsg by mutableStateOf("")
    var cancel=false

    fun addUserMessage(content: String) {
        msgs.add(Message("user", content))
    }

    fun addSystemMessage(content: String) {
        if (msgs.isNotEmpty()) {
            msgs[0] = Message("system", content)
        }else{
            msgs.add(Message("system",content))
        }
    }

    fun delMessage() {
        if (msgs[msgs.size - 1].role == "user") {
            msgs.removeAt(msgs.size - 1)
        }
    }

    fun updateAIMessage(content: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (msgs.isEmpty() || msgs.last().role != "assistant") {
                msgs.add(Message("assistant", content))
            } else {
                val lastMsg = msgs.last()
                msgs[msgs.lastIndex] = lastMsg.copy(content = lastMsg.content + content)
            }
        }
    }

    fun updateAIReasoningMessage(content: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (msgs.isEmpty() || msgs.last().role != "assistant_reasoning") {
                msgs.add(Message("assistant_reasoning", content))
            } else {
                val lastMsg = msgs.last()
                msgs[msgs.lastIndex] = lastMsg.copy(content = lastMsg.content + content)
            }
        }
    }

    fun withoutReasoning(): List<Message> {
        return msgs
            .filter { it.role != "assistant_reasoning" }
            .map { it.copy() }
    }
    fun toList(): List<Message>{
        return msgs
            .map { it.copy() }
    }
    fun fromList(lst: MutableList<Message>){
        msgs.clear()
        msgs.addAll(lst)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIAPITheme {
                val viewModel: ChatViewModel = viewModel()
                MainUI(viewModel)
            }
        }
    }
}
var api_url = ""
var api_key = ""
var model=""
var sessions=mutableListOf<String>()
@SuppressLint("CommitPrefEdits")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainUI(viewModel: ChatViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showHistoryMenu by remember { mutableStateOf(false) }
    var openRenameDialog by remember { mutableStateOf(false) }
    var openDelDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val history=context.getSharedPreferences("history", Context.MODE_PRIVATE)
    val scrollState = rememberScrollState()
    var contentHeight by remember { mutableIntStateOf(0) }
    var sendImg by remember { mutableIntStateOf(1) }
    var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessionsPref = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
    var currentSession by remember { mutableStateOf("") }
    val currentConfigPref = context.getSharedPreferences("currentConfigPref", Context.MODE_PRIVATE)
    var currentConfig=currentConfigPref.getString("currentConfig","")
    var containerHeight by remember { mutableIntStateOf(0) }
    var systemPrompt by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val settingsPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        var configsList = mutableListOf<String>()
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
        api_url=settings.get("apiUrl").asString;api_key=settings.get("apiKey").asString;model=settings.get("model").asString;systemPrompt=settings.get("systemPrompt").asString
        api_url = if (api_url.endsWith("/")) {api_url+"chat/completions"} else {"$api_url/chat/completions"}
        sessions= Gson().fromJson(sessionsPref.getString("sessions","[]")!!,object : TypeToken<MutableList<String>>() {}.type)
        if (sessions.isEmpty()) {
            sessions.add(System.currentTimeMillis().toString())
        }
        currentSession=sessions.last()
        if (history.getString(currentSession,"")!!.isNotEmpty()) {
            viewModel.fromList(
                Gson().fromJson(
                    history.getString(currentSession, "[]")!!,
                    Array<Message>::class.java
                ).toMutableList()
            )
        }
        if(systemPrompt.isNotEmpty()) {
            viewModel.addSystemMessage(systemPrompt)
        }
        delay(100)
        scrollState.animateScrollTo(contentHeight,tween(500))
    }
    LaunchedEffect(if (viewModel.msgs.isNotEmpty())viewModel.msgs.last().content.length else 0) {
        if (contentHeight - scrollState.value - containerHeight < 10) {
            scrollState.scrollTo(contentHeight)
        }
    }
    LaunchedEffect(viewModel.isLoading) {
        sendImg = if (viewModel.isLoading){ 0 }else { 1 }
        if (viewModel.msgs.size>1) {
            with(history.edit()) {
                putString(currentSession, Gson().toJson(viewModel.toList()).toString())
                apply()
            }
            with(sessionsPref.edit()){
                putString("sessions",Gson().toJson(sessions))
                apply()
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(250.dp)
            ) {
                DropdownMenu(showHistoryMenu, onDismissRequest = {showHistoryMenu=false}) {
                    DropdownMenuItem({ Text("重命名") },onClick = {
                        showHistoryMenu=false
                        openRenameDialog=true
                    })
                    DropdownMenuItem({ Text("删除") }, onClick = {
                        showHistoryMenu=false
                        openDelDialog=true
                    })
                }
                if (openRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { openRenameDialog = false },
                        title = { Text("重命名") },
                        text = { OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it }
                        )},
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    openRenameDialog = false
                                    sessions.remove(currentSession)
                                    sessions.add(newName)
                                    with (sessionsPref.edit()){
                                        putString("sessions",Gson().toJson(sessions))
                                        apply()
                                    }
                                    with(history.edit()) {
                                        remove(currentSession)
                                        currentSession=newName
                                        putString(currentSession, Gson().toJson(viewModel.toList()).toString())
                                        apply()
                                    }
                                }
                            ) { Text("完成") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { openRenameDialog = false }
                            ) { Text("取消") }
                        }
                    )
                }
                if (openDelDialog){
                    AlertDialog(
                        onDismissRequest = { openDelDialog = false },
                        title = { Text("永久删除对话") },
                        text = { Text("删除后，该对话将不可恢复。确认删除吗？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    openDelDialog = false
                                    sessions.remove(currentSession)
                                    with (sessionsPref.edit()){
                                        putString("sessions",Gson().toJson(sessions))
                                        apply()
                                    }
                                    if (sessions.isEmpty()) {
                                        sessions.add(System.currentTimeMillis().toString())
                                    }
                                    currentSession=sessions.last()
                                    if (history.getString(currentSession, "")!!.isNotEmpty()) {
                                        viewModel.fromList(
                                            Gson().fromJson(
                                                history.getString(currentSession, "[]")!!,
                                                Array<Message>::class.java
                                            ).toMutableList()
                                        )
                                    } else {
                                        viewModel.msgs.clear()
                                        if (systemPrompt.isNotEmpty()) {
                                            viewModel.addSystemMessage(systemPrompt)
                                        }
                                    }
                                    if (systemPrompt.isNotEmpty()) {
                                        viewModel.addSystemMessage(systemPrompt)
                                    }
                                    scope.launch {
                                        scrollState.scrollTo(0)
                                        delay(100)
                                        scrollState.animateScrollTo(contentHeight, tween(500))
                                    }
                                }
                            ) { Text("删除") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    openDelDialog = false
                                }
                            ) { Text("取消") }
                        }
                    )
                }
                Row(Modifier.padding(16.dp)) {
                    Text("对话记录", fontSize = 24.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                scope.launch { drawerState.close() }
                                viewModel.msgs.clear()
                                viewModel.addSystemMessage(systemPrompt)
                                currentSession = System.currentTimeMillis().toString()
                                sessions.add(currentSession)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp))
                    }
                }
                LazyColumn {
                    itemsIndexed(sessions.reversed()) { _,session ->
                        Box(modifier = Modifier.combinedClickable(
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (viewModel.isLoading) {
                                    Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT).show()
                                } else {
                                    currentSession = session
                                    if (history.getString(currentSession, "")!!.isNotEmpty()) {
                                        viewModel.fromList(
                                            Gson().fromJson(
                                                history.getString(currentSession, "[]")!!,
                                                Array<Message>::class.java
                                            ).toMutableList()
                                        )
                                    } else {
                                        viewModel.msgs.clear()
                                        if (systemPrompt.isNotEmpty()) {
                                            viewModel.addSystemMessage(systemPrompt)
                                        }
                                    }
                                    if (systemPrompt.isNotEmpty()) {
                                        viewModel.addSystemMessage(systemPrompt)
                                    }
                                    scope.launch {
                                        scrollState.scrollTo(0)
                                        delay(100)
                                        scrollState.animateScrollTo(contentHeight, tween(500))
                                    }
                                }
                            },
                            onLongClick = {
                                editingSession=session
                                newName=session
                                showHistoryMenu=true
                            }
                        )){
                            if (session==currentSession) {
                                Text(
                                    session,
                                    Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }else{
                                Text(
                                    session,
                                    Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        },
        content = { Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton({scope.launch { drawerState.open() }}) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_msgs_list),"")
                    }
                },
                title = { Text(stringResource(R.string.app_name)+"-"+currentConfig) },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = ""
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                showMenu = false
                                val intent=Intent(context,SettingsActivity::class.java)
                                context.startActivity(intent)
                                (context as ComponentActivity).finish()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("开启新对话") },
                            onClick = {
                                showMenu = false
                                if (viewModel.isLoading) {
                                    Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    viewModel.msgs.clear()
                                    viewModel.addSystemMessage(systemPrompt)
                                    currentSession = System.currentTimeMillis().toString()
                                    sessions.add(currentSession)
                                }
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                msg = viewModel.inputMsg,
                onMsgChange = { viewModel.inputMsg = it },
                onSend = {
                    if (viewModel.isLoading){
                        viewModel.cancel=true
                    }else {
                        try {
                            viewModel.addUserMessage(it)
                            send(context, viewModel)
                            viewModel.inputMsg = ""
                            scope.launch { scrollState.animateScrollTo(contentHeight, tween(300)) }
                        }catch (e: Exception){
                            Toast.makeText(context,e.toString(), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                sendImg = if (sendImg==1){Icons.Default.ArrowUpward}else{ImageVector.vectorResource(R.drawable.ic_rectangle)}
            )
        }
    ) { innerPadding ->
        SelectionContainer(Modifier.onSizeChanged{size->
            containerHeight=size.height
        }) {
            Column(modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .onSizeChanged { size ->
                    contentHeight = size.height
                }) {
                for (msg in viewModel.msgs){
                    when (msg.role) {
                        "assistant" -> Row {Icon(Icons.Default.Api,"");Text(msg.content+"\n")}
                        "assistant_reasoning" -> Row {Icon(Icons.Default.Api,"");Text(msg.content+"\n", color = Color.Gray)}
                        "user" -> Row {Text(msg.content+"\n", Modifier
                            .weight(1f)
                            .wrapContentWidth(
                                Alignment.End
                            ));Icon(Icons.Default.AccountCircle,"")}
                        else -> Row {Icon(Icons.Default.Settings,"");Text(msg.content+"\n")}
                    }
                }
            }
        }
    }
})
}

@Composable
fun MessageInputBar(
    msg: String,
    onMsgChange: (String) -> Unit,
    onSend: (String) -> Unit,
    sendImg: ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(), shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = msg,
                onValueChange = onMsgChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息...") }
            )
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onSend(msg) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = sendImg,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp))
            }
        }
    }
}


private fun send(
    context: Context,
    viewModel: ChatViewModel
) {
    viewModel.isLoading = true
    val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    val requestBody = Gson().toJson(
        mapOf(
            "model" to model,
            "messages" to viewModel.withoutReasoning(),
            "stream" to true
        )
    )
        .toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url(api_url)
        .post(requestBody)
        .addHeader("Authorization", "Bearer $api_key")
        .build()

    viewModel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${response.code}", Toast.LENGTH_SHORT).show()
                    viewModel.isLoading=false
                }
                return@launch
            }

            response.body?.byteStream()?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    //解析
                    while (reader.readLine().also { line = it } != null) {
                        try {
                            val cleanLine = line?.removePrefix("data: ")?.trim()
                            val json = JsonParser.parseString(cleanLine).asJsonObject
                            val delta = json.getAsJsonArray("choices")
                                ?.firstOrNull()
                                ?.asJsonObject
                                ?.getAsJsonObject("delta")
                            if (viewModel.cancel)break
                            if (delta?.get("content")?.isJsonNull == false) {
                                viewModel.updateAIMessage(delta.get("content")?.asString!!)
                            } else {
                                viewModel.updateAIReasoningMessage(delta?.get("reasoning_content")?.asString!!)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                println(e.toString())
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                if (viewModel.msgs.isNotEmpty()) {
                    if (viewModel.msgs.last().role == "user") {
                        viewModel.inputMsg = viewModel.msgs.last().content
                    }
                }
                viewModel.delMessage()
            }
        } finally {
            withContext(Dispatchers.Main) {
                viewModel.isLoading = false
                viewModel.cancel=false
            }
        }
    }
}