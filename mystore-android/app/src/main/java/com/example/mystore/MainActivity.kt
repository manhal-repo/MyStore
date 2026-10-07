package com.example.mystore

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.launch

class StoreVM(app: Application) : AndroidViewModel(app) {
    private val prefs = RepoPrefs(app)
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var osFilter by mutableStateOf("android")
    var enabled by mutableStateOf(Repos.all.associate { it.id to prefs.isEnabled(it.id) })

    fun toggle(id: String, v: Boolean) {
        prefs.set(id, v); enabled = enabled + (id to v); load()
    }

    fun load() = viewModelScope.launch {
        loading = true; error = null
        val res = ArrayList<AppItem>(); val failed = ArrayList<String>()
        for (r in Repos.all.filter { enabled[it.id] == true }) {
            runCatching { fetchApps(r) }.onSuccess { res += it }.onFailure { failed += r.name }
        }
        runCatching { fetchCatalog(Repos.siteBase, Repos.all[0]) }.onSuccess { res += it }
        apps = res
        if (failed.isNotEmpty()) error = "تعذّر تحميل: " + failed.joinToString("، ")
        loading = false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ads.init(this)
        setContent { MaterialTheme { Surface { StoreApp(this) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreApp(activity: Activity, vm: StoreVM = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var gate by remember { mutableStateOf("loading") } // loading | ok | failed
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Pair<String, Float>?>(null) }

    fun openAd() { gate = "loading"; Ads.showAppOpen(activity) { ok -> gate = if (ok) "ok" else "failed" } }
    LaunchedEffect(Unit) { openAd(); vm.load() }

    // لا يظهر المتجر إلا بعد نجاح إعلان فتح التطبيق (App Open)
    if (gate != "ok") {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (gate == "loading") {
                    CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("جارٍ تحميل الإعلان…")
                } else {
                    Text("تعذّر عرض الإعلان. لا يمكن المتابعة بدونه.")
                    Spacer(Modifier.height(12.dp)); Button(onClick = { openAd() }) { Text("إعادة المحاولة") }
                }
            }
        }
        return
    }

    // لا يبدأ التنزيل إلا بعد ظهور الإعلان وإغلاقه بنجاح
    fun download(app: AppItem) {
        Ads.show(activity) { ok ->
            if (!ok) {
                Toast.makeText(ctx, "تعذّر عرض الإعلان، لم يبدأ التنزيل", Toast.LENGTH_LONG).show()
                return@show
            }
            if (app.os != "android") { // غير أندرويد: يُفتح الرابط في المتصفح بعد نجاح الإعلان
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@show
            }
            scope.launch {
                progress = app.name to 0f
                runCatching { Installer.download(ctx, app) { p -> progress = app.name to p } }
                    .onSuccess { Installer.install(ctx, it) }
                    .onFailure { Toast.makeText(ctx, it.message ?: "فشل التنزيل", Toast.LENGTH_LONG).show() }
                progress = null
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(Config.APP_TITLE) }) },
        bottomBar = {
            AndroidView(Modifier.fillMaxWidth(), factory = { c ->
                AdView(c).apply {
                    setAdSize(AdSize.BANNER); adUnitId = Config.BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            })
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }) { Text("التطبيقات", Modifier.padding(12.dp)) }
                Tab(tab == 1, { tab = 1 }) { Text("المستودعات", Modifier.padding(12.dp)) }
            }
            if (tab == 0) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(8.dp),
                    placeholder = { Text("ابحث عن تطبيق…") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("android" to "Android", "windows" to "Windows", "linux" to "Linux",
                        "macos" to "macOS", "ios" to "iOS", "all" to "الكل").forEach { (id, label) ->
                        FilterChip(vm.osFilter == id, { vm.osFilter = id }, { Text(label) })
                    }
                }
                if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.error?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error) }
                val list = vm.apps.filter { vm.osFilter == "all" || it.os == vm.osFilter }
                    .filter { it.name.contains(query, true) || it.summary.contains(query, true) }
                LazyColumn {
                    items(list, key = { it.repo.id + it.pkg + it.os }) { AppRow(it) { download(it) } }
                }
            } else {
                LazyColumn {
                    items(Repos.all, key = { it.id }) { r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = { Text(if (r.id == "own") "افتراضي · ${r.url}" else r.url) },
                            trailingContent = {
                                Switch(vm.enabled[r.id] == true, { vm.toggle(r.id, it) }, enabled = r.id != "own")
                            }
                        )
                    }
                    item { Text("تنبيه: فهرس F-Droid كبير وقد يستغرق تحميله وقتًا.", Modifier.padding(16.dp)) }
                }
            }
        }
    }

    progress?.let { (n, p) ->
        AlertDialog(onDismissRequest = {}, confirmButton = {},
            title = { Text("تنزيل $n") },
            text = { LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) })
    }
}

@Composable
fun AppRow(app: AppItem, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = app.iconUrl, contentDescription = app.name, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                Text(app.summary, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text("${app.repo.name} · ${app.os} · ${app.versionName} · %.1f MB".format(app.size / 1048576.0),
                    style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onDownload) { Text(if (app.os == "android") "تنزيل" else "فتح") }
        }
    }
}
