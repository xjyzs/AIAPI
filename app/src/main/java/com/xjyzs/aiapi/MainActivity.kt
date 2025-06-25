package com.xjyzs.aiapi

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.xjyzs.aiapi.ui.theme.AIAPITheme
import com.xjyzs.aiapi.utils.InlineMarkdown
import com.xjyzs.aiapi.utils.clickVibrate
import com.xjyzs.aiapi.utils.hideKeyboard
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
import kotlin.math.round

@Keep
data class Message(val role: String, val content: String)
@SuppressLint("MutableCollectionMutableState")
class ChatViewModel : ViewModel() {
    var msgs = mutableStateListOf<Message>()
    var sessions=mutableStateListOf<String>()
    var isLoading by mutableStateOf(false)
    var inputMsg by mutableStateOf("")
    var cancel=false
    var currentSession by mutableStateOf("")
    var parseMd by mutableStateOf(true)
    var temperature by mutableIntStateOf(-1)
    var maxTokens by mutableStateOf("")

    fun addUserMessage(content: String) {
        msgs.add(Message("user", content))
    }

    fun addSystemMessage(content: String) {
        if (content.isNotEmpty()) {
            if (msgs.isNotEmpty()) {
                msgs[0] = Message("system", content)
            } else {
                msgs.add(Message("system", content))
            }
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
            if (msgs.last().role == "assistant" && msgs.last().content == ""){
                msgs.removeAt(msgs.size-1)
            }
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
    fun maxTokensIsNumber(): Boolean{
        try {
            maxTokens.toInt()
            return true
        }catch (_: Exception){
            return false
        }
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
var systemPrompt=""
var containerHeight=0
var contentHeight=0
@SuppressLint("CommitPrefEdits")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainUI(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val history=context.getSharedPreferences("history", Context.MODE_PRIVATE)
    val scrollState = rememberScrollState()
    var sendImg by remember { mutableIntStateOf(1) }
    var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sessionsPref = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
    val currentConfigPref = context.getSharedPreferences("currentConfigPref", Context.MODE_PRIVATE)
    var currentConfig=currentConfigPref.getString("currentConfig","")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val scope=rememberCoroutineScope()

    //保存重要数据
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            if (viewModel.msgs.size>1) {
                with(history.edit()) {
                    putString(viewModel.currentSession, Gson().toJson(viewModel.toList()).toString())
                    apply()
                }
                with(sessionsPref.edit()){
                    putString("sessions",Gson().toJson(viewModel.sessions))
                    apply()
                }
            }
        }
    }
    lifecycle.addObserver(observer)

    LaunchedEffect(Unit) {
        viewModel.sessions.clear()
        viewModel.sessions.addAll(Gson().fromJson(sessionsPref.getString("sessions","[]")!!,object : TypeToken<List<String>>() {}.type))
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
        if (viewModel.sessions.isEmpty()) {
            viewModel.sessions.add("新对话"+System.currentTimeMillis().toString())
        }
        viewModel.currentSession=viewModel.sessions.last()
        if (history.getString(viewModel.currentSession,"")!!.isNotEmpty()) {
            viewModel.fromList(
                Gson().fromJson(
                    history.getString(viewModel.currentSession, "[]")!!,
                    Array<Message>::class.java
                ).toMutableList()
            )
        }
        if(systemPrompt.isNotEmpty()) {
            viewModel.addSystemMessage(systemPrompt)
        }
        delay(100)
        scrollState.animateScrollTo(contentHeight,tween(300))
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
                putString(viewModel.currentSession, Gson().toJson(viewModel.toList()).toString())
                apply()
            }
            with(sessionsPref.edit()){
                putString("sessions",Gson().toJson(viewModel.sessions))
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
                HistoryDrawer(viewModel,context,sessionsPref,history,scrollState,drawerState,vibrator)
            }
        },
        content = { Scaffold(
        topBar = {
            TopBar(viewModel,context,drawerState,if(currentConfig!!.isNotEmpty()){currentConfig}else{"请先配置 AI API"},vibrator)
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
                            if (it.isNotEmpty()) {
                                if (currentConfig!!.isNotEmpty() && !api_url.startsWith("/")) {
                                    if ((viewModel.msgs.isEmpty() || viewModel.msgs.last().role == "system") && "新对话" in viewModel.currentSession) {
                                        val withoutEnter=it.replace("\n","")
                                        var newName=if (withoutEnter.length>20){withoutEnter.substring(0,20)}else{withoutEnter}
                                        if (newName in viewModel.sessions){
                                            newName=newName+System.currentTimeMillis().toString()
                                        }
                                        viewModel.sessions.add(newName)
                                        viewModel.sessions.remove(viewModel.currentSession)
                                        with (sessionsPref.edit()){
                                            putString("sessions",Gson().toJson(viewModel.sessions))
                                            apply()
                                        }
                                        with(history.edit()) {
                                            putString(newName, Gson().toJson(viewModel.toList()).toString())
                                            remove(viewModel.currentSession)
                                            apply()
                                        }
                                        viewModel.currentSession=newName
                                    }
                                    context.hideKeyboard()
                                    viewModel.addUserMessage(it)
                                    send(context, viewModel)
                                    viewModel.inputMsg = ""
                                    scope.launch {
                                        scrollState.animateScrollTo(
                                            contentHeight,
                                            tween(300)
                                        )
                                    }
                                }else{
                                    Toast.makeText(context,"请先配置 AI API",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }else{
                                Toast.makeText(context,"请输入你的问题", Toast.LENGTH_SHORT).show()
                            }
                        }catch (e: Exception){
                            Toast.makeText(context,e.toString(), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                sendImg = if (sendImg==1){Icons.Default.ArrowUpward}
                else{ImageVector.vectorResource(R.drawable.ic_rectangle)},
                viewModel,vibrator
            )
        }
    ) { innerPadding ->
            SelectionContainer(Modifier.onSizeChanged { size ->
                containerHeight = size.height
            }) {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .onSizeChanged { size ->
                            contentHeight = size.height
                        }) {
                    viewModel.msgs.forEachIndexed { i, msg ->
                        when (msg.role) {
                            "assistant" -> {
                                Row {
                                    Icon(Icons.Default.Api, "")
                                    if (msg.content.isNotEmpty() || !viewModel.isLoading) {
                                        if (viewModel.parseMd) {
                                            InlineMarkdown(
                                                content = msg.content,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 16.dp),
                                                context
                                            )
                                        } else {
                                            Text(msg.content)
                                        }
                                    }else{
                                        CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                }
                                Row(Modifier
                                    .align(Alignment.End)
                                    .padding(end = 24.dp)) {
                                    if (viewModel.msgs[i].content.isNotEmpty() && !viewModel.isLoading) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.background)
                                                .clickable {
                                                    clickVibrate(vibrator)
                                                    viewModel.msgs.removeRange(
                                                        if (viewModel.msgs[i - 1].role == "assistant_reasoning") {
                                                            i - 1
                                                        } else {
                                                            i
                                                        }, viewModel.msgs.size
                                                    )
                                                    send(context, viewModel)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            "assistant_reasoning" -> Row {
                                Icon(
                                    Icons.Default.Api,
                                    ""
                                );Text(msg.content, color = Color.Gray)
                            }

                            "user" -> {
                                Row {
                                    Text(
                                        msg.content, Modifier
                                            .weight(1f)
                                            .wrapContentWidth(
                                                Alignment.End
                                            )
                                    );Icon(Icons.Default.AccountCircle, "")
                                }
                                Row(Modifier
                                    .align(Alignment.End)
                                    .padding(end = 24.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.background)
                                            .clickable {
                                                clickVibrate(vibrator)
                                                if (!viewModel.isLoading) {
                                                    viewModel.inputMsg =
                                                        viewModel.msgs[i].content
                                                    viewModel.msgs.removeRange(
                                                        i,
                                                        viewModel.msgs.size
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                            else -> Row {
                                Icon(
                                    Icons.Default.Settings,
                                    ""
                                );Text(msg.content + "\n")
                            }
                        }
                    }
                }
            }
        }
})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputBar(
    msg: String,
    onMsgChange: (String) -> Unit,
    onSend: (String) -> Unit,
    sendImg: ImageVector,
    viewModel: ChatViewModel,
    vibrator: Vibrator
) {
    var temperatureExpanded by remember { mutableStateOf(false) }
    var maxTokensExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(), shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = msg,
                    onValueChange = onMsgChange,
                    modifier = Modifier
                        .weight(1f).heightIn(min = 36.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 7,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(vertical = 5.dp, horizontal = 10.dp)
                        ) {
                            if (msg.isEmpty()) {
                                Text(
                                    text = "输入消息...",
                                    style = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            clickVibrate(vibrator)
                            onSend(msg)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sendImg,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Row {
                TextButton(
                    { temperatureExpanded = true },
                    Modifier.height(28.dp),
                    contentPadding = PaddingValues(5.dp),
                    shape = RectangleShape
                ) {
                    Text(
                        "温度:${
                            if (viewModel.temperature >= 0) {
                                viewModel.temperature.toFloat() / 10
                            } else {
                                "未设置"
                            }
                        }"
                    )
                }
                TextButton(
                    { maxTokensExpanded = !maxTokensExpanded },
                    Modifier.height(28.dp),
                    contentPadding = PaddingValues(5.dp),
                    shape = RectangleShape
                ) {
                    Text(
                        "最大 token 数:${
                            if (viewModel.maxTokensIsNumber()) {
                                viewModel.maxTokens
                            } else {
                                "未设置"
                            }
                        }"
                    )
                }
            }
            if (maxTokensExpanded){
                OutlinedTextField(value = viewModel.maxTokens.toString(), onValueChange = {
                    try {
                        viewModel.maxTokens = it
                    } catch (_: Exception) {
                    }
                })
            }
        }
        DropdownMenu( // 温度
            expanded = temperatureExpanded,
            onDismissRequest = { temperatureExpanded = false },
            Modifier.width(200.dp)
        ) {
            Slider(value = viewModel.temperature.toFloat(), onValueChange = {
                val tmp=round(it).toInt()
                if (tmp!=viewModel.temperature) {
                    viewModel.temperature = tmp
                    clickVibrate(vibrator)
                }
            }, valueRange = -1f..20f, steps = 20)
        }
    }
}


private fun send(
    context: Context,
    viewModel: ChatViewModel
) {
    viewModel.isLoading = true
    viewModel.cancel=false
    viewModel.updateAIMessage("")
    val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    var bodyMap=mutableMapOf(
        "model" to model,
        "messages" to viewModel.withoutReasoning(),
        "stream" to true
    )
    bodyMap.apply {
        if (viewModel.temperature >= 0) {
            put("temperature", viewModel.temperature.toFloat() / 10)
        }
        if (viewModel.maxTokensIsNumber()){
            put("max_tokens",viewModel.maxTokens.toInt())
        }
    }
    val requestBody = Gson().toJson(bodyMap)
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
                            val choices = json.getAsJsonArray("choices")
                                ?.firstOrNull()
                                ?.asJsonObject
                            val delta = choices?.getAsJsonObject("delta")
                            if (viewModel.cancel)break
                            if (delta?.get("content")?.isJsonNull == false) {
                                viewModel.updateAIMessage(delta.get("content")?.asString!!)
                            } else {
                                viewModel.updateAIReasoningMessage(delta?.get("reasoning_content")?.asString!!)
                            }
                            if (!choices.get("finish_reason").isJsonNull) {
                                if (choices.get("finish_reason").asString != "stop") {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            choices.get("finish_reason").asString,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                if (viewModel.msgs.isNotEmpty()) {
                    if (viewModel.msgs[viewModel.msgs.size-2].role == "user") {
                        viewModel.inputMsg = viewModel.msgs[viewModel.msgs.size-2].content
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                viewModel.isLoading = false
                viewModel.cancel=false
            }
        }
    }
}

@SuppressLint("CommitPrefEdits")
@Composable
private fun HistoryDrawer(viewModel: ChatViewModel, context: Context, sessionsPref: SharedPreferences, history: SharedPreferences, scrollState: ScrollState, drawerState: DrawerState, vibrator: Vibrator) {
    var showHistoryMenu by remember { mutableStateOf(false) }
    var openRenameDialog by remember { mutableStateOf(false) }
    var openDelDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    DropdownMenu(showHistoryMenu, onDismissRequest = { showHistoryMenu = false }) {
        DropdownMenuItem({ Text("重命名") }, onClick = {
            showHistoryMenu = false
            openRenameDialog = true
        })
        DropdownMenuItem({ Text("删除") }, onClick = {
            showHistoryMenu = false
            openDelDialog = true
        })
    }
    if (openRenameDialog) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { openRenameDialog = false },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    Modifier.focusRequester(focusRequester),
                    textStyle = TextStyle(fontSize = 22.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openRenameDialog = false
                        var newName1=newName.text
                        viewModel.sessions.remove(editingSession)
                        if (newName1 in viewModel.sessions){
                            newName1=newName1+System.currentTimeMillis().toString()
                        }
                        viewModel.sessions.add(newName1)
                        with(sessionsPref.edit()) {
                            putString("sessions", Gson().toJson(viewModel.sessions))
                            apply()
                        }
                        with(history.edit()) {
                            putString(newName1, history.getString(editingSession, null))
                            remove(editingSession)
                            apply()
                        }
                        if (editingSession==viewModel.currentSession){
                            viewModel.currentSession=newName1
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
    if (openDelDialog) {
        AlertDialog(
            onDismissRequest = { openDelDialog = false },
            title = { Text("永久删除对话") },
            text = { Text("删除后，该对话将不可恢复。确认删除吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDelDialog = false
                        viewModel.sessions.remove(editingSession)
                        with(sessionsPref.edit()) {
                            putString("sessions", Gson().toJson(viewModel.sessions))
                            apply()
                        }
                        with(history.edit()) {
                            remove(editingSession)
                            apply()
                        }
                        if (viewModel.sessions.isEmpty() || editingSession==viewModel.currentSession) {
                            viewModel.sessions.add("新对话" + System.currentTimeMillis().toString())
                            viewModel.currentSession = viewModel.sessions.last()
                        }
                        if (history.getString(viewModel.currentSession, "")!!.isNotEmpty()) {
                            viewModel.fromList(
                                Gson().fromJson(
                                    history.getString(viewModel.currentSession, "[]")!!,
                                    Array<Message>::class.java
                                ).toMutableList()
                            )
                        } else {
                            viewModel.msgs.clear()
                        }
                        if (systemPrompt.isNotEmpty()) {
                            viewModel.addSystemMessage(systemPrompt)
                        }
                        scope.launch {
                            scrollState.scrollTo(0)
                            delay(100)
                            scrollState.animateScrollTo(contentHeight, tween(300))
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
                    if (viewModel.isLoading) {
                        Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT).show()
                    } else {
                        clickVibrate(vibrator)
                        scope.launch { drawerState.close() }
                        createNewSession(viewModel, context)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    LazyColumn {
        itemsIndexed(viewModel.sessions.reversed()) { _, session ->
            Box(
                modifier = Modifier.combinedClickable(
                onClick = {
                    scope.launch { drawerState.close() }
                    if (viewModel.isLoading) {
                        Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.currentSession = session
                        if (history.getString(viewModel.currentSession, "")!!.isNotEmpty()) {
                            viewModel.fromList(
                                Gson().fromJson(
                                    history.getString(viewModel.currentSession, "[]")!!,
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
                            scrollState.animateScrollTo(contentHeight, tween(300))
                        }
                    }
                },
                onLongClick = {
                    clickVibrate(vibrator)
                    editingSession = session
                    newName = TextFieldValue(session, selection = TextRange(0, session.length))
                    showHistoryMenu = true
                }
            )) {
                Text(
                    session,
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (session == viewModel.currentSession) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Unspecified
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(viewModel: ChatViewModel,context: Context,drawerState: DrawerState,currentConfig: String,vibrator: Vibrator) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton({ scope.launch { clickVibrate(vibrator);drawerState.open() } }) {
                Icon(ImageVector.vectorResource(R.drawable.ic_msgs_list), null)
            }
        },
        title = {
            Column {
                Text(
                    if (viewModel.currentSession.isNotEmpty()) {
                        viewModel.currentSession
                    } else {
                        stringResource(R.string.app_name)
                    }, maxLines = 1
                )
                Text(
                    text = currentConfig,
                    color = Color.Gray,
                    fontSize = 12.sp, lineHeight = 12.sp
                )
            }
        },
        actions = {
            IconButton(onClick = { showMenu = !showMenu;clickVibrate(vibrator) }) {
                Icon(imageVector = Icons.Default.MoreVert, null)
            }
            DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("设置") },
                    onClick = {
                        showMenu = false
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                        (context as ComponentActivity).finish()
                    }
                )
                DropdownMenuItem(
                    text = { Text("开启新对话") },
                    onClick = {
                        clickVibrate(vibrator)
                        showMenu = false
                        if (viewModel.isLoading) {
                            Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            createNewSession(viewModel, context)
                        }
                    }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("解析md")
                            Checkbox(viewModel.parseMd, onCheckedChange = {
                                clickVibrate(vibrator)
                                viewModel.parseMd = !viewModel.parseMd
                            })
                        }
                    },
                    onClick = {
                        clickVibrate(vibrator)
                        viewModel.parseMd = !viewModel.parseMd
                    })
            }
        }
    )
}

private fun createNewSession(viewModel: ChatViewModel,context: Context) {
    if (viewModel.msgs.isEmpty() || viewModel.msgs.last().role == "system") {
        Toast.makeText(context, "已在新对话中", Toast.LENGTH_SHORT).show()
    } else {
        viewModel.msgs.clear()
        viewModel.addSystemMessage(systemPrompt)
        viewModel.currentSession = "新对话" + System.currentTimeMillis().toString()
        viewModel.sessions.add(viewModel.currentSession)
    }
}