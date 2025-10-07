package com.xjyzs.aiapi.utils

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xjyzs.aiapi.ChatViewModel
import com.xjyzs.aiapi.api_key
import com.xjyzs.aiapi.api_url
import com.xjyzs.aiapi.cancel
import com.xjyzs.aiapi.model
import com.xjyzs.aiapi.systemPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

fun clickVibrate(vibrator: Vibrator){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
            attributes
        )
    }
}

@Composable
fun FreeDropdownMenu(expanded: Boolean,
                     onDismissRequest: () -> Unit,
                     offset: IntOffset = IntOffset(0,0),
                     width: Dp = 100.dp,
                     content: @Composable (() -> Unit)) {
    if (expanded) {
        Popup(
            alignment = Alignment.TopStart,
            offset = offset,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            val transition = updateTransition(targetState = expanded, label = "menuTransition")
            val scale by transition.animateFloat(
                transitionSpec = { tween(durationMillis = 120, easing = LinearOutSlowInEasing) },
                label = "scale"
            ) { if (it) 1f else 0.8f }

            val alpha by transition.animateFloat(
                transitionSpec = { tween(durationMillis = 120) },
                label = "alpha"
            ) { if (it) 1f else 0f }
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .shadow(
                        elevation = 8.dp,
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .width(width)
            ) { content() }
        }
    }
}

@Composable
fun SmallTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier= Modifier, placeholder: @Composable (() -> Unit)? = null,keyboardOptions: KeyboardOptions= KeyboardOptions.Default,showFrame: Boolean=true,showHandle: Boolean=true) {
    val selectionColors = TextSelectionColors(
        handleColor = if (showHandle) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        backgroundColor = MaterialTheme.colorScheme.primary
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .heightIn(min = 36.dp),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 7,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = if (showFrame) {
                        Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(vertical = 5.dp, horizontal = 10.dp)
                    } else {
                        modifier.padding(vertical = 5.dp, horizontal = 10.dp)
                    }
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        placeholder()
                    }
                    innerTextField()
                }
            }
        )
    }
}

class MarkdownParser {
    // 代码块
    private val codeBlockRegex = """```(.*?)\n([\s\S]*?)\s*```""".toRegex()
    // 分界线
    private val dividerRegex = "(?m)^\\s{0,3}(?:-{3,}|\\*{3,})\\s*$".toRegex()

    // 块级元素
    private val headerRegex = """^(#{1,6})\s*(.*)""".toRegex()
    private val unorderedListRegex = """^[*+-]\s+(.*)""".toRegex()
    private val orderedListRegex = """^(\d+)\.\s+(.*)""".toRegex()

    // 行内样式
    private val boldRegex = """(\*\*|__)(.+?)\1""".toRegex()
    private val italicRegex = """(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""".toRegex()
    private val codeRegex = """`(.+?)`""".toRegex()
    private val deleteRegex = """~~(.+?)~~""".toRegex()
    private val underLineRegex = """\+\+(.+?)\+\+""".toRegex()

    fun parse(content: String, context: Context): List<@Composable () -> Unit> {
        val blocks = mutableListOf<@Composable () -> Unit>()

        var remainingText = content

        while (remainingText.isNotEmpty()) {
            val codeBlockMatch = codeBlockRegex.find(remainingText)
            val dividerMatch = dividerRegex.find(remainingText)

            val nextMatch = listOfNotNull(codeBlockMatch, dividerMatch)
                .minByOrNull { it.range.first }

            if (nextMatch == null) {
                parseRegularText(remainingText, blocks)
                break
            }

            val precedingText = remainingText.substring(0, nextMatch.range.first)
            parseRegularText(precedingText, blocks)

            if (nextMatch == codeBlockMatch) {
                val (language, code) = codeBlockMatch.destructured
                blocks.add { CodeBlock(code, language, context = context) }
            } else if (nextMatch == dividerMatch) {
                blocks.add { HorizontalDivider() }
            }

            remainingText = remainingText.substring(nextMatch.range.last + 1)
        }

        return blocks
    }

    private fun parseRegularText(text: String, blocks: MutableList<@Composable () -> Unit>) {
        text.split("\n").forEach { line ->
            when {
                line.matches(headerRegex) -> parseHeader(line)?.let { blocks.add(it) }
                line.matches(unorderedListRegex) -> parseListItem(line)?.let { blocks.add(it) }
                line.matches(orderedListRegex) -> parseOrderedListItem(line)?.let {
                    blocks.add(it)
                }

                else -> blocks.add { ParseInlineText(line) }
            }
        }
    }

    @Composable
    fun CodeBlock(
        code: String,
        language: String = "",
        modifier: Modifier = Modifier,
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
        cornerRadius: Dp = 8.dp,
        context: Context
    ) {
        val typography = MaterialTheme.typography

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setText(
                        AnnotatedString(code)
                    )
                },
            shape = RoundedCornerShape(cornerRadius),
            color = backgroundColor
        ) {
            Column {
                if (language.isNotBlank()) {
                    Text(
                        text = language,
                        style = typography.labelSmall,
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp, end = 12.dp)
                            .align(Alignment.Start),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = highlightSyntax(code, language),
                    style = typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    fun highlightSyntax(code: String, language: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)

            val lc = language.lowercase()
            if (lc == "kotlin" || lc == "java") {
                val rules = listOf(
                    Rule("""val|var|class""".toRegex(), SpanStyle(color = Color(0xFFFF9800))),
                )
                rules.forEach { rule ->
                    rule.regex.findAll(code).forEach { match ->
                        addStyle(rule.style, match.range.first, match.range.last + 1)
                    }
                }
            } else if (lc == "python") {
                val rules = listOf(
                    Rule("""print""".toRegex(), SpanStyle(color = Color(0xFF16659B)))
                )
                rules.forEach { rule ->
                    rule.regex.findAll(code).forEach { match ->
                        addStyle(rule.style, match.range.first, match.range.last + 1)
                    }
                }
            }
            val rules = listOf(
                Rule("""\b\d+(\.\d+)?\b""".toRegex(), SpanStyle(color = Color(0xFF16659B))), // 数字
                Rule(
                    """return |def |fun |try|except |except:|finally|with | as |if |else |import """.toRegex(),
                    SpanStyle(color = Color(0xFFFF9800))
                ),
                Rule("""@.*?\n""".toRegex(), SpanStyle(color = Color(0xFFD5C430))),
                Rule("""".*?"|'.*?'""".toRegex(), SpanStyle(color = Color(0xFF067D17))), // 字符串
                Rule("""(?<![:/])(//|#).*""".toRegex(), SpanStyle(color = Color(0xFF9E9E9E))), // 注释
                Rule(
                    """/\*.*?\*/""".toRegex(RegexOption.DOT_MATCHES_ALL),
                    SpanStyle(color = Color(0xFF9E9E9E))
                ), // 注释
            )
            rules.forEach { rule ->
                rule.regex.findAll(code).forEach { match ->
                    addStyle(rule.style, match.range.first, match.range.last + 1)
                }
            }
        }
    }

    private data class Rule(
        val regex: Regex,
        val style: SpanStyle
    )

    private fun parseHeader(line: String): (@Composable () -> Unit)? {
        val (hashes, text) = headerRegex.find(line)?.destructured ?: return null
        val level = hashes.length.coerceIn(1, 6)
        return {
            Text(
                text = parseInlineStyles(text),
                fontSize = when (level) {
                    1 -> 24.sp
                    else -> (24 - level * 1).sp
                },
                fontWeight = FontWeight.Bold
            )
        }
    }

    private fun parseListItem(line: String): (@Composable () -> Unit)? {
        val (content) = unorderedListRegex.find(line)?.destructured ?: return null
        return {
            Row(verticalAlignment = Alignment.Top) {
                Text("• ", fontSize = 16.sp)
                Text(parseInlineStyles(content))
            }
        }
    }

    private fun parseOrderedListItem(line: String): (@Composable () -> Unit)? {
        val (num, content) = orderedListRegex.find(line)?.destructured ?: return null
        return {
            Row(verticalAlignment = Alignment.Top) {
                Text("$num. ", fontSize = 16.sp)
                Text(parseInlineStyles(content))
            }
        }
    }

    @Composable
    private fun ParseInlineText(text: String) {
        Text(parseInlineStyles(text))
    }

    @Composable
    private fun parseInlineStyles(text: String): AnnotatedString {
        val annotatedString = buildAnnotatedString {
            val singleRegex = listOf(
                "(\\*|_)(.+?)\\1",
                "(`)(.+?)\\1"
            )
            val doubleRegex = listOf(
                "(\\*\\*|__)(.+?)\\1",
                "(~~)(.+?)\\1",
                "(\\+\\+)(.+?)\\1"
            )
            var cleanText = text
            for (i in doubleRegex) {
                cleanText = cleanText.replace(Regex(i)) { matchResult ->
                    val content = matchResult.groups[2]?.value ?: ""
                    "\u200B\u200B$content\u200B\u200B"
                }
            }
            for (i in singleRegex) {
                cleanText = cleanText.replace(Regex(i)) { matchResult ->
                    val content = matchResult.groups[2]?.value ?: ""
                    "\u200B$content\u200B"
                }
            }


            // 粗体
            boldRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(fontWeight = FontWeight.ExtraBold),
                    result.range.first + 2,
                    result.range.last - 1
                )
            }

            // 斜体
            italicRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    result.range.first + 1,
                    result.range.last
                )
            }

            // 删除线
            deleteRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    result.range.first + 2,
                    result.range.last - 1
                )
            }

            // 下划线
            underLineRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    result.range.first + 1,
                    result.range.last
                )
            }
            append(cleanText)

            // 代码
            codeRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(
                        background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        fontFamily = FontFamily.Monospace
                    ),
                    result.range.first + 1,
                    result.range.last
                )
            }
        }
        return annotatedString
    }
}

@Composable
fun InlineMarkdown(content: String, modifier: Modifier = Modifier,context: Context) {
    val parser = remember { MarkdownParser() }
    val blocks = remember(content) { parser.parse(content, context) }

    Column(modifier) {
        blocks.forEach { block ->
            Row {
                block()
            }
            Text("\n", lineHeight = 0.sp)
        }
    }
}


fun createNewSession(viewModel: ChatViewModel,context: Context,isInNewSessionCheck: Boolean =true) {
    if (isInNewSessionCheck && (viewModel.msgs.isEmpty() || viewModel.msgs.last().role == "system")) {
        Toast.makeText(context, "已在新对话中", Toast.LENGTH_SHORT).show()
    } else {
        viewModel.msgs.clear()
        viewModel.addSystemMessage(systemPrompt)
        if (viewModel.sessions.isNotEmpty() && viewModel.sessions.last().startsWith("新对话") && viewModel.sessions.last().endsWith("\u200B")){
            viewModel.currentSession = viewModel.sessions.last()
        }else {
            viewModel.currentSession = "新对话" + System.currentTimeMillis().toString() + "\u200B"
            viewModel.sessions.add(viewModel.currentSession)
        }
    }
}


fun send(
    context: Context,
    viewModel: ChatViewModel
) {
    viewModel.isLoading = true
    cancel = false
    val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    val bodyMap = mutableMapOf(
        "model" to model,
        "messages" to viewModel.msgsToSend(),
        "stream" to true
    )
    bodyMap.apply {
        if (viewModel.temperature >= 0) {
            put("temperature", viewModel.temperature.toFloat() / 10)
        }
        if (viewModel.maxTokensIsNumber()) {
            put("max_tokens", viewModel.maxTokens.toLong())
        }
    }
    val requestBody = Gson().toJson(bodyMap)
        .toRequestBody("application/json".toMediaTypeOrNull())

    val request = Request.Builder()
        .url(api_url)
        .post(requestBody)
        .addHeader("Authorization", "Bearer $api_key")
        .build()

    viewModel.updateAIMessage("")
    viewModel.viewModelScope.launch(Dispatchers.IO) {
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "错误: ${response.code}", Toast.LENGTH_SHORT).show()
                    viewModel.errorMsg=response.body.string()
                    viewModel.errorDialogExpanded=true
                    viewModel.errorCode=response.code
                    viewModel.isLoading = false
                }
                return@launch
            }

            response.body.byteStream().use { stream ->
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
                            if (cancel) break
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
            response.close()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "错误: ${e.message}", Toast.LENGTH_SHORT).show()
                if (viewModel.msgs.isNotEmpty()) {
                    if (viewModel.msgs.first().role == "assistant" && viewModel.msgs.first().content.isEmpty() && viewModel.msgs[1].role == "user") {
                        viewModel.inputMsg = viewModel.msgs[1].content
                        viewModel.msgs.removeRange(0,2)
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                viewModel.isLoading = false
                cancel = false
            }
        }
    }
}