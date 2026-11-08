package app.openlive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.openlive.data.ActivateRequest
import app.openlive.data.Channel
import app.openlive.data.DEFAULT_BASE_URL
import app.openlive.data.SettingsStore
import app.openlive.data.Topic
import app.openlive.data.createApi
import app.openlive.ui.PlayerScreen
import app.openlive.ui.theme.OpenLiveTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenLiveTheme { OpenLiveApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenLiveApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val baseUrl by SettingsStore.baseUrl(context).collectAsState(initial = DEFAULT_BASE_URL)
    val savedCode by SettingsStore.activationCode(context).collectAsState(initial = "")
    val activated by SettingsStore.activated(context).collectAsState(initial = false)
    val favourites by SettingsStore.favourites(context).collectAsState(initial = emptySet())

    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    val api = remember(baseUrl) { createApi(baseUrl) }

    fun loadAll() {
        scope.launch {
            loading = true
            error = null
            try {
                topics = api.topics().topics
                channels = api.live(topic = selectedTopic).channels
            } catch (e: Exception) {
                error = "Cannot reach backend at $baseUrl — is it running? (${e.message})"
            }
            loading = false
        }
    }

    LaunchedEffect(baseUrl) { loadAll() }
    LaunchedEffect(selectedTopic) {
        if (topics.isNotEmpty() || selectedTopic != null) loadAll()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("OpenLive") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "home", onClick = { nav.navigate("home") },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = route == "search", onClick = { nav.navigate("search") },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = route == "favourites", onClick = { nav.navigate("favourites") },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = "Favourites") },
                    label = { Text("Favourites") }
                )
                NavigationBarItem(
                    selected = route == "settings", onClick = { nav.navigate("settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav, startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable("home") {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    if (!activated) {
                        Text(
                            "Not activated yet — go to Settings and enter your code. " +
                                "Demo code: OPENLIVE-DEMO-2026",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedTopic == null,
                                onClick = { selectedTopic = null },
                                label = { Text("All") }
                            )
                        }
                        items(topics) { topic ->
                            FilterChip(
                                selected = selectedTopic == topic.name,
                                onClick = { selectedTopic = topic.name },
                                label = { Text("${topic.name} (${topic.count})") }
                            )
                        }
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null -> Text(error ?: "", modifier = Modifier.padding(top = 16.dp))
                        channels.isEmpty() -> Text("No channels.", modifier = Modifier.padding(top = 16.dp))
                        else -> LazyColumn {
                            items(channels) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    isFav = channel.id in favourites,
                                    onFav = { fav ->
                                        scope.launch {
                                            SettingsStore.toggleFavourite(context, channel.id, fav)
                                        }
                                    },
                                    onClick = {
                                        selectedChannel = channel
                                        nav.navigate("player")
                                    }
                                )
                            }
                        }
                    }
                }
            }
            composable("search") {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search channels") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    searchResults = api.search(searchQuery).channels
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Search") }
                    LazyColumn {
                        items(searchResults) { channel ->
                            ChannelRow(
                                channel = channel,
                                isFav = channel.id in favourites,
                                onFav = { fav ->
                                    scope.launch {
                                        SettingsStore.toggleFavourite(context, channel.id, fav)
                                    }
                                },
                                onClick = {
                                    selectedChannel = channel
                                    nav.navigate("player")
                                }
                            )
                        }
                    }
                }
            }
            composable("favourites") {
                val favChannels = channels.filter { it.id in favourites }
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Favourites (${favChannels.size})", fontWeight = FontWeight.Bold)
                    LazyColumn {
                        items(favChannels) { channel ->
                            ChannelRow(
                                channel = channel, isFav = true,
                                onFav = { fav ->
                                    scope.launch {
                                        SettingsStore.toggleFavourite(context, channel.id, fav)
                                    }
                                },
                                onClick = {
                                    selectedChannel = channel
                                    nav.navigate("player")
                                }
                            )
                        }
                    }
                }
            }
            composable("settings") {
                SettingsScreen(
                    baseUrl = baseUrl, savedCode = savedCode, activated = activated,
                    onSave = { url, code ->
                        scope.launch {
                            SettingsStore.saveBaseUrl(context, url)
                            if (code.isNotBlank()) {
                                try {
                                    val result = createApi(url).activate(ActivateRequest(code))
                                    SettingsStore.saveActivation(context, code, result.ok)
                                } catch (e: Exception) {
                                    SettingsStore.saveActivation(context, code, false)
                                }
                            }
                        }
                    }
                )
            }
            composable("player") {
                val channel = selectedChannel
                if (channel != null) {
                    PlayerScreen(channel = channel, onBack = { nav.popBackStack() })
                } else {
                    Text("No channel selected.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    isFav: Boolean,
    onFav: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo, contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    channel.name.take(1),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(channel.name, fontWeight = FontWeight.Bold)
            Text("${channel.topic} · ${channel.country ?: ""} · ${channel.quality ?: ""}")
        }
        IconButton(onClick = { onFav(!isFav) }) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favourite"
            )
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
    }
}

@Composable
fun SettingsScreen(
    baseUrl: String,
    savedCode: String,
    activated: Boolean,
    onSave: (String, String) -> Unit
) {
    var url by remember(baseUrl) { mutableStateOf(baseUrl) }
    var code by remember(savedCode) { mutableStateOf(savedCode) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", fontWeight = FontWeight.Bold)
        Text(
            "Backend URL — your own server. Emulator: http://10.0.2.2:8000/ " +
                "Real phone on the same Wi-Fi: http://<your-PC-IP>:8000/",
            modifier = Modifier.padding(top = 12.dp)
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Backend base URL") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = code, onValueChange = { code = it },
            label = { Text("Activation code") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text(
            if (activated) "Status: activated ✓" else "Status: not activated",
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = {
                onSave(url, code)
                message = "Saved. Activation will be checked against the server."
            },
            modifier = Modifier.padding(top = 12.dp)
        ) { Text("Save & activate") }
        if (message.isNotEmpty()) Text(message, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Demo code: OPENLIVE-DEMO-2026. " +
                "Create your own codes on the server with POST /api/admin/codes.",
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
