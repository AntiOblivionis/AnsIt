package com.antioblivionis.ansit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Public identity only. No profile requests, SDKs or credentials on this screen. */
internal object AboutLinks {
    const val AUTHOR = "AntiOblivionis"
    const val GITHUB = "https://github.com/AntiOblivionis"
    const val HOME = "https://id.tgwgroup.ltd"
    const val REPOSITORY = "https://github.com/AntiOblivionis/AnsIt"
    const val WEBSITE = "https://ansit.tgwgroup.ltd"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(onBack: () -> Unit, onOpenLink: ((String) -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    var failedLink by remember { mutableStateOf(false) }
    val open: (String) -> Unit = { url ->
        try { if (onOpenLink != null) onOpenLink(url) else uriHandler.openUri(url) }
        catch (_: Exception) { failedLink = true }
    }
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("关于 AnsIt") }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("about_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
            }
        })
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).testTag("about_screen"), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_launcher_flat), "AnsIt 图标", modifier = Modifier.size(88.dp))
                    Text("AnsIt", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("屏幕里的问题，随时解答", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("版本 " + BuildConfig.VERSION_NAME, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("开发者", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.github_avatar), "AntiOblivionis 的 GitHub 头像",
                                modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(AboutLinks.AUTHOR, style = MaterialTheme.typography.titleLarge, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text("保持思考，无限进步。", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AboutLink("个人主页", "id.tgwgroup.ltd", "about_home", { open(AboutLinks.HOME) })
                        AboutLink("GitHub", "github.com/AntiOblivionis", "about_github", { open(AboutLinks.GITHUB) })
                    }
                }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("项目", style = MaterialTheme.typography.titleMedium)
                        AboutLink("官方网站", "ansit.tgwgroup.ltd", "about_website", { open(AboutLinks.WEBSITE) })
                        HorizontalDivider()
                        AboutLink("代码仓库", "AntiOblivionis/AnsIt", "about_repository", { open(AboutLinks.REPOSITORY) })
                    }
                }
                Text("基于 Root 与 LSPosed，在系统界面识别电池区域长按。截图仅发送到你配置的模型接口；每次识屏都是独立会话。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    if (failedLink) AlertDialog(onDismissRequest = { failedLink = false },
        title = { Text("暂时无法打开链接") }, text = { Text("请检查设备是否安装了可打开 HTTPS 链接的浏览器。") },
        confirmButton = { TextButton(onClick = { failedLink = false }) { Text("知道了") } })
}

@Composable
private fun AboutLink(title: String, address: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title + " ↗", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(address, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
