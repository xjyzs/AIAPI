package com.xjyzs.aiapi.utils

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun clickVibrate(vibrator: Vibrator){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        vibrator.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
            attributes
        )
    }
}

fun Context.hideKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow((this as? Activity)?.currentFocus?.windowToken, 0)
}

class MarkdownParser {
    // 代码块
    private val codeBlockRegex = """```(.*?)\n([\s\S]*?)\s*```""".toRegex()

    // 块级元素
    private val headerRegex = """^(#{1,6})\s*(.*)""".toRegex()
    private val unorderedListRegex = """^[*+-]\s+(.*)""".toRegex()
    private val orderedListRegex = """^(\d+)\.\s+(.*)""".toRegex()

    // 行内样式
    private val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    private val italicRegex = """(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""".toRegex()
    private val codeRegex = """`(.*?)`""".toRegex()
    private val deleteRegex = """~~(.*?)~~""".toRegex()

    fun parse(content: String, context: Context): List<@Composable () -> Unit> {
        val blocks = mutableListOf<@Composable () -> Unit>()

        var remainingText = content

        while (true) {
            val codeBlockMatch = codeBlockRegex.find(remainingText)

            val precedingText = if (codeBlockMatch != null) {
                remainingText.substring(0, codeBlockMatch.range.first)
            } else {
                remainingText
            }
            parseRegularText(precedingText, blocks)
            if (codeBlockMatch == null) break
            val (language, code) = codeBlockMatch.destructured
            blocks.add { CodeBlock(code, language, context = context) }

            remainingText = remainingText.substring(codeBlockMatch.range.last + 1)
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
            val cleanText = text.replace("*", "\u200B").replace("`","\u200B").replace("~","\u200B")
            append(cleanText)

            // 处理粗体
            boldRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(fontWeight = FontWeight.ExtraBold),
                    result.range.first + 2,
                    result.range.last - 1
                )
            }

            // 处理斜体
            italicRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    result.range.first + 1,
                    result.range.last
                )
            }

            // 处理删除线
            deleteRegex.findAll(text).forEach { result ->
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    result.range.first + 2,
                    result.range.last - 1
                )
            }

            // 处理代码样式
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
