package com.pushpendratech.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pushpendratech.toolbox.ads.BannerAd
import com.pushpendratech.toolbox.core.CrashLog
import com.pushpendratech.toolbox.data.Category
import com.pushpendratech.toolbox.data.Prefs
import com.pushpendratech.toolbox.data.Tool
import com.pushpendratech.toolbox.data.Tools
import com.pushpendratech.toolbox.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAdsConsent()
        setContent { ToolBoxTheme { CrashReportGate { ToolBoxRoot(Prefs(applicationContext)) } } }
    }
    /** UMP consent (required for EEA/UK users; no-ops where not required). Ads still load per Google's rules if consent is declined for personalised ads. */
    private fun requestAdsConsent() {
        val params = ConsentRequestParameters.Builder().build()
        val consentInfo = UserMessagingPlatform.getConsentInformation(this)
        consentInfo.requestConsentInfoUpdate(this, params,
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { /* form dismissed / not required */ } },
            { /* consent error — continue; tools never depend on ads */ })
    }
}

/** If the previous run crashed, show the captured stack trace once, with Copy — so the exact error can be shared for a precise fix instead of guesswork. */
@Composable
private fun CrashReportGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    val report = crash
    if (report != null) {
        AlertDialog(
            onDismissRequest = { CrashLog.clear(context); crash = null },
            title = { Text("Last crash report") },
            text = {
                Text(report, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { CrashLog.clear(context); crash = null }) { Text("Dismiss") } }
        )
    }
    content()
}

private val PDF_IDS = setOf("pdf_compress","pdf_merge","pdf_split","jpg_to_pdf","pdf_to_images","pdf_rotate","pdf_reorder","pdf_delete_pages","pdf_extract","pdf_watermark","pdf_page_numbers","pdf_protect","img_to_pdf")

/** v7 PDF batch — routed to PdfToolScreen2 (ui/PdfScreens2.kt). */
private val PDF2_IDS = setOf(
    "pdf_compress_kb","pdf_unlock","pdf_to_text","pdf_ocr_text","pdf_text_to_pdf","pdf_to_word",
    "pdf_extract_images","pdf_info","pdf_edit_metadata","pdf_remove_metadata","pdf_resize_pages",
    "pdf_crop","pdf_grayscale","pdf_sign","pdf_add_image","pdf_header_footer","pdf_nup",
    "pdf_flatten","pdf_repair","pdf_reverse","pdf_insert_blank","pdf_split_size","pdf_fill_form"
)

private val TAB_ROUTES = setOf("home", "alltools", "favorites_tab", "settings_tab")

private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun ToolBoxRoot(prefs: Prefs) {
    val nav = rememberNavController()
    val favorites by prefs.favorites.collectAsState(initial = emptySet())
    val recents by prefs.recents.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val openTool: (Tool) -> Unit = { tool -> scope.launch { prefs.pushRecent(tool.id) }; nav.navigate("tool/${tool.id}") }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = currentRoute in TAB_ROUTES || currentRoute == "category/{cat}"
    Scaffold(
        bottomBar = {
            if (showTabs) {
                Column {
                    // Banner keeps its old home: bottom of Home (and the other browse tabs), above the tab bar.
                    if (currentRoute != "settings_tab") BannerAd()
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentRoute == "home",
                            onClick = { nav.switchTab("home") },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "alltools",
                            onClick = { nav.switchTab("alltools") },
                            icon = { Icon(Icons.Filled.Apps, contentDescription = "All Tools") },
                            label = { Text("All Tools") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "favorites_tab",
                            onClick = { nav.switchTab("favorites_tab") },
                            icon = { Icon(Icons.Filled.Star, contentDescription = "Favorites") },
                            label = { Text("Favorites") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "settings_tab",
                            onClick = { nav.switchTab("settings_tab") },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(favorites = favorites, recents = recents, onTool = openTool)
            }
            composable("alltools") {
                AllToolsScreen(onTool = openTool)
            }
            composable("favorites_tab") {
                FavoritesScreen(favorites.mapNotNull { id -> Tools.all.find { it.id == id } }, onTool = openTool, onBack = null)
            }
            composable("settings_tab") { SettingsScreen(onBack = null) }
            composable("category/{cat}", arguments = listOf(navArgument("cat") { type = NavType.StringType })) { entry ->
                val cat = Category.valueOf(entry.arguments!!.getString("cat")!!)
                CategoryScreen(cat, onTool = openTool, onBack = { nav.popBackStack() })
            }
            composable("tool/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val tool = Tools.byId(entry.arguments!!.getString("id")!!)
                val isFav = tool.id in favorites
                val toggle: () -> Unit = { scope.launch { prefs.toggleFavorite(tool.id) } }
                val back = { nav.popBackStack(); Unit }
                when {
                    tool.id in MORE_TOOL_IDS -> MoreToolScreen(tool, isFav, toggle, back)
                    tool.id in PDF2_IDS -> PdfToolScreen2(tool, isFav, toggle, back)
                    tool.id in PDF_IDS -> PdfToolScreen(tool, isFav, toggle, back)
                    tool.category == Category.IMAGE -> ImageToolScreen(tool, isFav, toggle, back)
                    tool.category == Category.VIDEO || tool.category == Category.AUDIO -> MediaToolScreen(tool, isFav, toggle, back)
                    tool.category == Category.TEXT -> TextToolScreen(tool, isFav, toggle, back)
                    tool.category == Category.AI -> AiToolScreen(tool, isFav, toggle, back)
                    else -> CalculatorScreen(tool, isFav, toggle, back)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(tool.name, fontWeight = FontWeight.SemiBold)
            Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tool.category.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Per-category accent for the Mendox-style home tiles. */
private fun categoryAccent(cat: Category): Color = when (cat) {
    Category.PDF -> Color(0xFFE53935)
    Category.AI -> Color(0xFF8E24AA)
    Category.IMAGE -> Color(0xFF1E88E5)
    Category.VIDEO -> Color(0xFFFB8C00)
    Category.AUDIO -> Color(0xFF00897B)
    Category.TEXT -> Color(0xFF5E35B1)
    Category.CALC -> Color(0xFF43A047)
}

/** One home tile: category-coloured rounded card, big category emoji, tool name under it. */
@Composable
private fun ToolTile(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(104.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = categoryAccent(tool.category).copy(alpha = 0.14f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(tool.category.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                tool.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Home section order (contract: PDF, AI, IMAGE, VIDEO, AUDIO, TEXT, CALC). */
private val HOME_CATEGORY_ORDER = listOf(Category.PDF, Category.AI, Category.IMAGE, Category.VIDEO, Category.AUDIO, Category.TEXT, Category.CALC)

/** Featured strip ids — resolved against the registry at render time; missing ids are skipped. */
private val FEATURED_IDS = listOf("pdf_compress_kb", "img_exact_kb", "ai_bg_remover", "pdf_merge")

@Composable
fun HomeScreen(favorites: Set<String>, recents: List<String>, onTool: (Tool) -> Unit) {
    var query by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("🧰 ToolBox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${Tools.all.size} Tools • 100% Offline", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search ${Tools.all.size} tools") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (query.isNotBlank()) {
            val results = Tools.search(query)
            if (results.isEmpty()) item { Text("No tools match \"$query\". Try \"pdf\", \"gst\", \"compress\"…") }
            items(results) { ToolRow(it) { onTool(it) } }
        } else {
            val favTools = favorites.mapNotNull { id -> Tools.all.find { it.id == id } }
            if (favTools.isNotEmpty()) {
                item { Text("⭐ Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(favTools) { ToolRow(it) { onTool(it) } }
            }
            val recentTools = recents.mapNotNull { id -> Tools.all.find { it.id == id } }
            if (recentTools.isNotEmpty()) {
                item { Text("🕘 Recently Used", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(recentTools) { ToolRow(it) { onTool(it) } }
            }
            val featured = FEATURED_IDS.mapNotNull { id -> Tools.all.find { it.id == id } }
            if (featured.isNotEmpty()) {
                item { Text("⚡ Featured", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(featured) { tool -> ToolTile(tool, Modifier.width(150.dp)) { onTool(tool) } }
                    }
                }
            }
            for (cat in HOME_CATEGORY_ORDER) {
                val tools = Tools.byCategory(cat)
                item {
                    Text("${cat.emoji} ${cat.title} • ${tools.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(tools.chunked(3)) { rowTools ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (tool in rowTools) ToolTile(tool, Modifier.weight(1f)) { onTool(tool) }
                        repeat(3 - rowTools.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** All Tools tab: every tool, one flat A–Z list, with its own search field. */
@Composable
fun AllToolsScreen(onTool: (Tool) -> Unit) {
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val tools = Tools.all
        .filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.description.lowercase().contains(needle) || it.category.title.lowercase().contains(needle) }
        .sortedBy { it.name.lowercase() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { Text("All Tools (${Tools.all.size})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search all tools") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (tools.isEmpty()) item { Text("No tools match \"$query\".") }
        items(tools) { ToolRow(it) { onTool(it) } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(cat: Category, onTool: (Tool) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val tools = Tools.byCategory(cat).filter { query.isBlank() || it.name.lowercase().contains(query.lowercase()) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("${cat.emoji} ${cat.title}") }, navigationIcon = { TextButton(onClick = onBack) { Text("← Back") } }) },
        bottomBar = { BannerAd() }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(query, { query = it }, placeholder = { Text("Search in ${cat.title}") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            if (tools.isEmpty()) item { Text("No tools found here for \"$query\".") }
            items(tools) { ToolRow(it) { onTool(it) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(favTools: List<Tool>, onTool: (Tool) -> Unit, onBack: (() -> Unit)?) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("⭐ Favorites") },
            navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("← Back") } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (favTools.isEmpty()) item { Text("No favorites yet. Open any tool and tap the ♥ icon to pin it here.") }
            items(favTools) { ToolRow(it) { onTool(it) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)?) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("← Back") } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("ToolBox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Version ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})") }
            item { Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Text("All file processing happens on your phone. Your PDFs, photos, videos and audio are never uploaded by this app — there is no server and no account. The only network use is Google AdMob ads, which may collect an advertising ID as described in the privacy policy. Favorites and recently-used tools are stored only on this device.") }
            item { Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                val counts = HOME_CATEGORY_ORDER.joinToString(", ") { "${it.title} ${Tools.byCategory(it).size}" }
                Text("${Tools.all.size} everyday tools — $counts. Built with Kotlin, Jetpack Compose, PDFBox (Apache-2.0), AndroidX Media3 (Apache-2.0) and on-device ML Kit AI.")
            }
            item { Text("Honest limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Text("• No MP3 encoder exists in Android, so audio extraction saves as M4A — never a fake .mp3.\n• PDF load/save can drop some advanced features (forms/signatures) of complex source PDFs.\n• Passport photos are sized correctly but official acceptance depends on each authority's other rules.") }
        }
    }
}
