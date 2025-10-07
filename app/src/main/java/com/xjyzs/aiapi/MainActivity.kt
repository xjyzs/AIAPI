package com.xjyzs.aiapi

import android.content.ClipboardManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.xjyzs.aiapi.ui.theme.AIAPITheme
import com.xjyzs.aiapi.utils.FreeDropdownMenu
import com.xjyzs.aiapi.utils.InlineMarkdown
import com.xjyzs.aiapi.utils.SmallTextField
import com.xjyzs.aiapi.utils.clickVibrate
import com.xjyzs.aiapi.utils.createNewSession
import com.xjyzs.aiapi.utils.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.round

@Keep
data class Message(val role: String, val content: String)
class ChatViewModel : ViewModel() {
    var msgs = mutableStateListOf<Message>()
    var sessions = mutableStateListOf<String>()
    var isLoading by mutableStateOf(false)
    var inputMsg by mutableStateOf("")
    var currentSession by mutableStateOf("")
    var parseMd by mutableStateOf(true)
    var temperature by mutableIntStateOf(-1)
    var maxTokens by mutableStateOf("")
    var maxContext by mutableStateOf("")
    val assistantThinkingClosed = mutableStateListOf<Int>()
    var isEditing by mutableStateOf(false)
    var errorMsg by mutableStateOf("")
    var errorCode by mutableIntStateOf(0)
    var errorDialogExpanded by mutableStateOf(false)

    fun addUserMessage(content: String) {
        msgs.add(Message("user", content))
    }

    fun addSystemMessage(content: String) {
        if (content.isNotEmpty()) {
            if (msgs.isEmpty()) {
                msgs.add(Message("system", content))
            } else if (msgs.first().role == "system") {
                msgs[0] = Message("system", content)
            } else {
                msgs.add(0,Message("system", content))
            }
        }
    }

    fun updateAIMessage(content: String) {
        if (isInReasoningContext) {
            updateAIReasoningMessage(content)
            return
        }
        val newContent = if (msgs.last().content.length<=2 || msgs.last().role!="assistant"){content.replaceFirst(Regex("^\n{0,2}"), "")}else{content}
        if (msgs.isEmpty() || msgs.last().role != "assistant") {
            msgs.add(Message("assistant", content.replaceFirst(Regex("^\n{0,2}"), "")))
        } else {
            if (content.startsWith("<think>")) {
                if (msgs.last().content.isEmpty()) {
                    isInReasoningContext = true
                    val match = Regex("<think>").find(content)!!
                    updateAIReasoningMessage(
                        content.substring(
                            match.range.last + 1,
                            content.length
                        )
                    )
                    return
                }
            }
            val lastMsg = msgs.last()
            msgs[msgs.lastIndex] = lastMsg.copy(content = lastMsg.content + newContent)
        }
    }

    fun updateAIReasoningMessage(content: String) {
        val newContent = if (msgs.last().content.length<=2 || msgs.last().role!="assistant_reasoning"){content.replaceFirst(Regex("^\n{0,2}"), "")}else{content}
        if (isInReasoningContext && '>' in content) {
            if ("</think>" in msgs.last().content + content) {
                val match = Regex("</think>").find(msgs.last().content + content)
                if (match != null) {
                    msgs[msgs.lastIndex] = Message(
                        "assistant_reasoning",
                        msgs.last().content.substring(0, match.range.first)
                    )
                    isInReasoningContext = false
                    updateAIMessage(
                        (msgs.last().content+content).substring(
                            match.range.last+1,
                            msgs.last().content.length+content.length
                        )
                    )
                    return
                }
            }
        }
        if (msgs.last().role == "assistant" && msgs.last().content == "") {
            msgs.removeAt(msgs.size - 1)
        }
        if (msgs.isEmpty() || msgs.last().role != "assistant_reasoning") {
            msgs.add(Message("assistant_reasoning", newContent))
        } else {
            val lastMsg = msgs.last()
            msgs[msgs.lastIndex] = lastMsg.copy(content = lastMsg.content + newContent)
        }
    }

    fun msgsToSend(): List<Message> {
        val tmpMsgs =msgs
            .filter { it.role != "assistant_reasoning" && it.role != "system" }
            .map { it.copy() }.toMutableList()
        val tmpMsgs2= if (maxContextIsNumber()){
            tmpMsgs.subList(if (maxContextIsNumber()){max(tmpMsgs.size-maxContext.toInt(),0)}else{0},tmpMsgs.size)
        } else {
            tmpMsgs
        }
        if (msgs.first().role=="system"){
            tmpMsgs2.add(0,msgs.first())
        }
        return tmpMsgs2
            .filter { it.role != "assistant_reasoning" }
            .map { it.copy() }
    }

    fun toListWithoutSystemPrompt(): List<Message> {
        return msgs
            .filter { it.role != "system" }
            .map { it.copy() }
    }

    fun fromList(lst: MutableList<Message>) {
        msgs.clear()
        msgs.addAll(lst)
    }

    fun maxTokensIsNumber(): Boolean {
        try {
            maxTokens.toLong()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun maxContextIsNumber(): Boolean {
        try {
            maxContext.toInt()
            return true
        } catch (_: Exception) {
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
var hasLaunched=false
var isInReasoningContext=false
var cancel = false
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainUI(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val history = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    val lazyListState = rememberLazyListState(2147483647,2147483647)
    var sendImg by remember { mutableIntStateOf(1) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sessionsPref = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
    val currentConfigPref = context.getSharedPreferences("currentConfigPref", Context.MODE_PRIVATE)
    var currentConfig = currentConfigPref.getString("currentConfig", "")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val scope = rememberCoroutineScope()
    val settingsPref = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun save(){
        if (viewModel.msgs.size > if (systemPrompt.isNotEmpty()){1}else{0}) {
            history.edit {
                putString(viewModel.currentSession, Gson().toJson(viewModel.toListWithoutSystemPrompt()).toString())
            }
            sessionsPref.edit {
                putString("sessions", Gson().toJson(viewModel.sessions))
            }
        }
    }

    val distanceToBottomCurrentElement by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf 0

            val viewportHeight = layoutInfo.viewportEndOffset
            val lastItem = visibleItems.last()
            val lastItemBottom = lastItem.offset + lastItem.size
            lastItemBottom-viewportHeight
        }
    }

    LaunchedEffect(Unit) {
        viewModel.temperature=settingsPref.getInt("temperature",-1)
        viewModel.maxTokens=settingsPref.getString("maxTokens","")!!
        viewModel.maxContext=settingsPref.getString("maxContext","")!!
        viewModel.parseMd=settingsPref.getBoolean("parseMd",true)
        viewModel.sessions.addAll(Gson().fromJson(sessionsPref.getString("sessions", "[]")!!,
            object : TypeToken<List<String>>() {}.type))
        val assistantsPref = context.getSharedPreferences("assistants", Context.MODE_PRIVATE)
        currentConfig = currentConfigPref.getString("currentConfig", "")!!
        val assistants = JsonParser.parseString(
            assistantsPref.getString(
                currentConfig,
                "{'apiUrl':'','apiKey':'','model':'','systemPrompt':''}"
            )
        ).asJsonObject
        api_url = assistants.get("apiUrl").asString;api_key = assistants.get("apiKey").asString;model =
        assistants.get("model").asString;systemPrompt = assistants.get("systemPrompt").asString
        if (viewModel.sessions.isEmpty()) {
            viewModel.sessions.add("新对话" + System.currentTimeMillis().toString()+"\u200B")
        }
        viewModel.currentSession = viewModel.sessions.last()
        if (history.getString(viewModel.currentSession, "")!!.isNotEmpty()) {
            viewModel.fromList(
                Gson().fromJson(
                    history.getString(viewModel.currentSession, "[]")!!,
                    Array<Message>::class.java
                ).toMutableList()
            )
        }
        if (systemPrompt.isNotEmpty()) {
            viewModel.addSystemMessage(systemPrompt)
        }
        if (viewModel.msgs.isNotEmpty()) {
            lazyListState.scrollToItem(viewModel.msgs.lastIndex, 2147483647)
        }
    }

    // 自动滚动
    LaunchedEffect(if (viewModel.msgs.isNotEmpty()) viewModel.msgs.last().content.length else 0,viewModel.isLoading) {
        if (!lazyListState.isScrollInProgress) {
            if (lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == viewModel.msgs.lastIndex) {
                if (distanceToBottomCurrentElement < 500) {
                    lazyListState.scrollToItem(viewModel.msgs.lastIndex,2147483647)
                }
            } else if (lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == viewModel.msgs.lastIndex-1) {
                if (distanceToBottomCurrentElement < 400 && viewModel.msgs.last().content.length < 500) {
                    lazyListState.scrollToItem(viewModel.msgs.lastIndex,2147483647)
                }
            }
        }
    }
    LaunchedEffect(viewModel.isLoading) {
        // 发送图标
        sendImg = if (viewModel.isLoading) {
            0
        } else {
            1
        }
        if (hasLaunched) {
            isInReasoningContext = false
            save()
            try {
                if (viewModel.msgs.last().content.startsWith("<think>")) {
                    val match =
                        Regex("(?s)<think>(.*?)</think>").find(viewModel.msgs.last().content)
                    if (match != null) {
                        val part1 = match.groups[1]!!.value
                        val part2 = viewModel.msgs.last().content.substring(
                            match.range.last + 1,
                            viewModel.msgs.last().content.length
                        )
                        viewModel.msgs.removeAt(viewModel.msgs.lastIndex)
                        viewModel.msgs.add(Message("assistant_reasoning", part1))
                        viewModel.msgs.add(Message("assistant", part2))
                    }
                }
            } catch (_: Exception) {
            }
        }
        hasLaunched = true
    }
    LaunchedEffect(Unit) { // 自动保存
        while (true) {
            delay(2000)
            if (viewModel.isLoading) {
                try {
                    val lastMsg = viewModel.msgs.last()
                    if (lastMsg.role == "assistant" && lastMsg.content.isNotEmpty() || lastMsg.role != "assistant") {
                        save()
                    }
                }catch (_: Exception){}
            }
        }
    }
    if (viewModel.errorDialogExpanded) {
        AlertDialog(
            onDismissRequest = { viewModel.errorDialogExpanded = false },
            title = { Text("错误: ${viewModel.errorCode}") },
            text = { SelectionContainer { Column(Modifier.verticalScroll(rememberScrollState())) { Text(viewModel.errorMsg) } }},
            confirmButton = {})
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(250.dp)
            ) {
                HistoryDrawer(
                    viewModel,
                    context,
                    sessionsPref,
                    history,
                    drawerState,
                    vibrator,
                    lazyListState
                )
            }
        },
        content = {
            Scaffold(
                topBar = {
                    TopBar(
                        viewModel, context, drawerState, if (currentConfig!!.isNotEmpty()) {
                            currentConfig
                        } else {
                            "请先配置 AI API"
                        }, vibrator,settingsPref
                    )
                },
                bottomBar = {
                    MessageInputBar(
                        msg = viewModel.inputMsg,
                        onMsgChange = { viewModel.inputMsg = it },
                        onSend = {
                            viewModel.sessions.remove(viewModel.currentSession)
                            viewModel.sessions.add(viewModel.currentSession)
                            if (viewModel.isLoading) {
                                cancel = true
                            } else {
                                try {
                                    if (it.isNotEmpty()) {
                                        if (currentConfig!!.isNotEmpty() && !api_url.startsWith("/")) {
                                            if (((viewModel.msgs.isEmpty() || viewModel.msgs.last().role == "system")) && viewModel.currentSession.startsWith("新对话") && viewModel.currentSession.endsWith("\u200B")) {
                                                val withoutEnter = it.replace("\n", "")
                                                var newName = if (withoutEnter.length > 20) {
                                                    withoutEnter.substring(0, 20)
                                                } else {
                                                    withoutEnter
                                                }
                                                if (newName in viewModel.sessions) {
                                                    newName = newName + System.currentTimeMillis()
                                                        .toString()
                                                }
                                                viewModel.sessions.add(newName)
                                                viewModel.sessions.remove(viewModel.currentSession)
                                                save()
                                                viewModel.currentSession = newName
                                            }
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            viewModel.addUserMessage(it)
                                            viewModel.isEditing = false
                                            send(context, viewModel)
                                            viewModel.inputMsg = ""
                                            scope.launch {
                                                lazyListState.scrollToItem(
                                                    viewModel.msgs.lastIndex,
                                                    2147483647
                                                )
                                            }
                                        } else {
                                            Toast.makeText(
                                                context, "请先配置 AI API",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "请输入你的问题",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        sendImg = if (sendImg == 1) {
                            Icons.Default.ArrowUpward
                        } else {
                            ImageVector.vectorResource(R.drawable.ic_rectangle)
                        },
                        viewModel, vibrator, lazyListState, scope,distanceToBottomCurrentElement,history
                    )
                }
            ) { innerPadding ->
                SelectionContainer(Modifier.onSizeChanged { size ->
                    containerHeight = size.height
                }) {
                    var dialogExpanded by remember { mutableStateOf(false) }
                    var deletingMsg by remember { mutableIntStateOf(-1) }
                    if (dialogExpanded) {
                        AlertDialog(
                            onDismissRequest = { dialogExpanded = false },
                            title = { Text("永久删除消息") },
                            text = {
                                Text("删除后，该消息将不可恢复。确认删除吗？")
                            },
                            confirmButton = {
                                TextButton({
                                    dialogExpanded = false
                                    viewModel.msgs.removeAt(deletingMsg)
                                    try {
                                        if (viewModel.msgs[deletingMsg - 1].role == "assistant_reasoning") {
                                            viewModel.msgs.removeAt(deletingMsg - 1)
                                        }
                                    } catch (_: Exception) {
                                    }
                                    save()
                                }) {
                                    Text("删除")
                                }
                            },
                            dismissButton = {
                                TextButton({
                                    dialogExpanded = false
                                }) {
                                    Text("取消")
                                }
                            })
                    }
                    LazyColumn( // 消息列表
                        modifier = Modifier
                            .padding(innerPadding)
                            .onSizeChanged { size ->
                                contentHeight = size.height
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                        state = lazyListState
                    ) {
                        itemsIndexed(viewModel.msgs) { i, msg ->
                            when (msg.role) {
                                "assistant" -> {
                                    Row {
                                        Icon(
                                            Icons.Default.Api,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
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
                                                Text(msg.content, Modifier.padding(end = 16.dp))
                                            }
                                        } else {
                                            CircularProgressIndicator(Modifier.size(24.dp))
                                        }
                                    }
                                    Row(Modifier.padding(start = 24.dp, bottom = 10.dp)) {
                                        if (!viewModel.isLoading || i!=viewModel.msgs.lastIndex) {
                                            IconButton({
                                                clickVibrate(vibrator)
                                                viewModel.msgs.removeRange(
                                                    if (viewModel.msgs[i - 1].role == "assistant_reasoning") {
                                                        i - 1
                                                    } else {
                                                        i
                                                    }, viewModel.msgs.size
                                                )
                                                send(context, viewModel)
                                            }, Modifier.size(24.dp)) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            IconButton({
                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setText(
                                                    msg.content
                                                )
                                            }, Modifier.size(24.dp)) {
                                                Icon(
                                                    Icons.Default.CopyAll,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            IconButton({
                                                deletingMsg = i
                                                dialogExpanded = true
                                            }, Modifier.size(24.dp)) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }


                                "assistant_reasoning" -> {
                                    val expanded = i !in viewModel.assistantThinkingClosed
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            ImageVector.vectorResource(R.drawable.ic_deep_think),
                                            null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        if (expanded) {
                                            IconButton(
                                                { viewModel.assistantThinkingClosed.add(i) },
                                                Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    null,
                                                    Modifier.rotate(180f),
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                { viewModel.assistantThinkingClosed.remove(i) },
                                                Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                    if (expanded) {
                                        Row(Modifier.padding(end = 16.dp)) {
                                            Spacer(Modifier.size(22.dp))
                                            Text(msg.content, color = Color.Gray)
                                        }
                                    }
                                }

                                "user" -> {
                                    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp), horizontalArrangement = Arrangement.End) {
                                        Box(Modifier.background(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 16.dp,
                                            bottomEnd = 0.dp
                                        )).padding(horizontal = 12.dp, vertical = 12.dp)) {
                                            Text(msg.content)
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(end = 12.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            {
                                                clickVibrate(vibrator)
                                                if (!viewModel.isLoading) {
                                                    viewModel.inputMsg =
                                                        viewModel.msgs[i].content
                                                    viewModel.msgs.removeRange(
                                                        i,
                                                        viewModel.msgs.size
                                                    )
                                                }
                                                viewModel.isEditing = true
                                            },
                                            Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        IconButton({
                                            deletingMsg = i
                                            dialogExpanded = true
                                        }, Modifier.size(24.dp)) {
                                            Icon(
                                                Icons.Default.DeleteForever,
                                                null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }

                                else -> Row {
                                    Icon(
                                        Icons.Default.Settings,
                                        null
                                    );Text(
                                    msg.content + "\n",
                                    color = MaterialTheme.colorScheme.primary
                                )
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
    vibrator: Vibrator,
    lazyListState: LazyListState,
    scope: CoroutineScope,
    distanceToBottomCurrentElement:Int,
    history: SharedPreferences
) {
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeHeight) {
        if (viewModel.msgs.isNotEmpty()) {
            if (imeHeight > 0 && distanceToBottomCurrentElement < 100) {
                scope.launch {
                    lazyListState.scrollToItem(viewModel.msgs.lastIndex, 2147483647)
                }
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(), shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (viewModel.isEditing) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(10.dp))
                ) {
                    Row {
                        Icon(Icons.Default.Edit, null)
                        Text("正在编辑消息", Modifier.weight(1f))
                        IconButton(
                            {
                                viewModel.fromList(
                                    Gson().fromJson(
                                        history.getString(viewModel.currentSession, "[]")!!,
                                        Array<Message>::class.java
                                    ).toMutableList()
                                )
                                viewModel.addSystemMessage(systemPrompt)
                                viewModel.inputMsg = ""
                                viewModel.isEditing = false
                            },
                            Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.rotate(45f))
                        }
                    }
                }
            }
            Spacer(Modifier.size(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTextField(
                    value = msg,
                    onValueChange = onMsgChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "输入消息...",
                            style = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
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
        }
    }
}

@Composable
private fun HistoryDrawer(viewModel: ChatViewModel, context: Context, sessionsPref: SharedPreferences, history: SharedPreferences,
                          drawerState: DrawerState, vibrator: Vibrator,lazyListState: LazyListState) {
    var showHistoryMenu by remember { mutableStateOf(false) }
    var openRenameDialog by remember { mutableStateOf(false) }
    var openDelDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val buttonPositions = remember { mutableStateMapOf<Int, Offset>() }
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
                        var newName1 = newName.text
                        if (newName1 != editingSession) {
                            viewModel.sessions.remove(editingSession)
                            if (newName1 in viewModel.sessions) {
                                newName1 = newName1 + System.currentTimeMillis().toString()
                            }
                            viewModel.sessions.add(newName1)
                            sessionsPref.edit {
                                putString("sessions", Gson().toJson(viewModel.sessions))
                            }
                            history.edit {
                                putString(newName1, history.getString(editingSession, null))
                                remove(editingSession)
                            }
                            if (editingSession == viewModel.currentSession) {
                                viewModel.currentSession = newName1
                            }
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
                        sessionsPref.edit {
                            putString("sessions", Gson().toJson(viewModel.sessions))
                        }
                        history.edit {
                            remove(editingSession)
                        }
                        if (editingSession == viewModel.currentSession) {
                            createNewSession(viewModel, context, isInNewSessionCheck = false)
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    LazyColumn {
        itemsIndexed(viewModel.sessions.reversed()) { i, session ->
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (viewModel.isLoading) {
                            Toast.makeText(context, "AI 正在回答中", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.currentSession = session
                            viewModel.assistantThinkingClosed.clear()
                            viewModel.fromList(
                                Gson().fromJson(
                                    history.getString(viewModel.currentSession, "[]")!!,
                                    Array<Message>::class.java
                                ).toMutableList()
                            )
                            viewModel.addSystemMessage(systemPrompt)
                        }
                        scope.launch {
                            try {
                                lazyListState.scrollToItem(viewModel.msgs.lastIndex, 2147483647)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onLongClick = {
                        clickVibrate(vibrator)
                        expandedIndex = i
                        editingSession = session
                        newName = TextFieldValue(
                            if (session.startsWith("新对话") && session.endsWith("\u200B")) "新对话" else session,
                            selection = TextRange(0, session.length)
                        )
                        showHistoryMenu = true
                    }
                ).onGloballyPositioned { coordinates ->
                    buttonPositions[i] = coordinates.localToWindow(Offset.Zero)
                }) {
                Text(
                    if (session.startsWith("新对话") && session.endsWith("\u200B")) "新对话" else session,
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
                    maxLines = 1,
                )
            }
        }
    }
    FreeDropdownMenu(
        showHistoryMenu,
        { showHistoryMenu = false },
        IntOffset(x = 300, buttonPositions[expandedIndex]?.y?.toInt() ?: 0),
        100.dp
    ) {
        DropdownMenuItem(
            text = { Text("重命名") },
            onClick = {
                showHistoryMenu = false
                openRenameDialog = true
            }
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = {
                showHistoryMenu = false
                openDelDialog = true
            }
        )
    }
    FreeDropdownMenu(
        expanded = showHistoryMenu,
        onDismissRequest = { showHistoryMenu = false },
        offset = IntOffset(x = 300, buttonPositions[expandedIndex]?.y?.toInt() ?: 0)
    ) {
        DropdownMenuItem(
            text = { Text("重命名") },
            onClick = {
                showHistoryMenu = false
                openRenameDialog = true
            }
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = {
                showHistoryMenu = false
                openDelDialog = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(viewModel: ChatViewModel,context: Context,drawerState: DrawerState,currentConfig: String,vibrator: Vibrator,settingsPref: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var temperatureExpanded by remember { mutableStateOf(false) }
    var maxTokensExpanded by remember { mutableStateOf(false) }
    var maxContextExpanded by remember { mutableStateOf(false) }
    var temperatureChanged by remember { mutableStateOf(false) }
    var maxTokensChanged by remember { mutableStateOf(false) }
    var maxContextChanged by remember { mutableStateOf(false) }
    var temperaturePosition: Offset by remember { mutableStateOf(Offset(0F, 0F))}
    var maxTokensPosition: Offset by remember { mutableStateOf(Offset(0F, 0F))}
    var maxContextPosition: Offset by remember { mutableStateOf(Offset(0F, 0F))}
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx().toInt() }
    TopAppBar(
        navigationIcon = {
            IconButton({ scope.launch { clickVibrate(vibrator);drawerState.open() } }) {
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_msgs_list),
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Column {
                Text(
                    (if (viewModel.currentSession.startsWith("新对话") && viewModel.currentSession.endsWith(
                            "\u200B"
                        )
                    ) "新对话" else viewModel.currentSession).ifEmpty {
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
            DropdownMenu(showMenu, onDismissRequest = {
                showMenu = false
                temperatureExpanded = false
                maxTokensExpanded = false
                maxContextExpanded = false
                if (temperatureChanged) {
                    settingsPref.edit {
                        putInt("temperature", viewModel.temperature)
                    }
                    temperatureChanged = false
                }
                if (maxTokensChanged) {
                    settingsPref.edit {
                        putString("maxTokens", viewModel.maxTokens)
                    }
                    maxTokensChanged = false
                }
                if (maxContextChanged) {
                    settingsPref.edit {
                        putString("maxContext", viewModel.maxContext)
                    }
                    maxTokensChanged = false
                }
            }, offset = DpOffset(x = (-10).dp, y = 0.dp)) {
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
                        Text(
                            "温度:${
                                if (viewModel.temperature >= 0) {
                                    viewModel.temperature.toFloat() / 10
                                } else {
                                    "未设置"
                                }
                            }",
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                temperaturePosition = coordinates.localToWindow(Offset.Zero)
                            }
                        )
                    },
                    onClick = { temperatureExpanded = !temperatureExpanded }
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                "最大 token 数:${
                                    if (viewModel.maxTokensIsNumber()) {
                                        viewModel.maxTokens
                                    } else {
                                        "未设置"
                                    }
                                }",
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    maxTokensPosition = coordinates.localToWindow(Offset.Zero)
                                }
                            )
                        }
                    },
                    onClick = { maxTokensExpanded = !maxTokensExpanded }
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                "上下文数:${
                                    if (viewModel.maxContextIsNumber()) {
                                        viewModel.maxContext
                                    } else {
                                        "未设置"
                                    }
                                }",
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    maxContextPosition = coordinates.localToWindow(Offset.Zero)
                                }
                            )
                        }
                    },
                    onClick = { maxContextExpanded = !maxContextExpanded }
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("解析md", Modifier.weight(1f))
                            Checkbox(viewModel.parseMd, onCheckedChange = {
                                clickVibrate(vibrator)
                                viewModel.parseMd = !viewModel.parseMd
                                settingsPref.edit {
                                    putBoolean("parseMd", viewModel.parseMd)
                                }
                            })
                        }
                    },
                    onClick = {
                        clickVibrate(vibrator)
                        viewModel.parseMd = !viewModel.parseMd
                        settingsPref.edit {
                            putBoolean("parseMd", viewModel.parseMd)
                        }
                    }
                )
            }
        }
    )
    FreeDropdownMenu(temperatureExpanded,{temperatureExpanded=false},IntOffset(screenWidthPx-with(density){330.dp.toPx().toInt()},temperaturePosition.y.toInt()+with(density){85.dp.toPx().toInt()}), width = 150.dp) {
        Slider(value = viewModel.temperature.toFloat(), onValueChange = {
            val tmp = round(it).toInt()
            if (tmp != viewModel.temperature) {
                viewModel.temperature = tmp
                clickVibrate(vibrator)
            }
            temperatureChanged = true
        }, valueRange = -1f..20f, steps = 20,
            modifier = Modifier.height(40.dp))
    }
    FreeDropdownMenu(maxTokensExpanded,{maxTokensExpanded=false},IntOffset(screenWidthPx-with(density){280.dp.toPx().toInt()},maxTokensPosition.y.toInt()+with(density){85.dp.toPx().toInt()})) {
        SmallTextField(
            value = viewModel.maxTokens, onValueChange = {
                try {
                    viewModel.maxTokens = it
                } catch (_: Exception) {
                }
                maxTokensChanged = true
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            showFrame = false,
            showHandle = false
        )
    }
    FreeDropdownMenu(maxContextExpanded,{maxContextExpanded=false},IntOffset(screenWidthPx-with(density){280.dp.toPx().toInt()},maxContextPosition.y.toInt()+with(density){85.dp.toPx().toInt()})) {
        SmallTextField(
            value = viewModel.maxContext, onValueChange = {
                try {
                    viewModel.maxContext = it
                } catch (_: Exception) {
                }
                maxContextChanged = true
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            showFrame = false,
            showHandle = false
        )
    }
}