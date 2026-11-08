package br.com.bragasaude.ui.home

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.data.remote.repository.NotificationCategoryFilter
import br.com.bragasaude.data.remote.repository.NotificationDisplayItem
import br.com.bragasaude.ui.MainViewModel
import br.com.bragasaude.ui.components.BragaFormSheet
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.util.BatteryOptimizationHelper
import br.com.bragasaude.ui.util.Screen

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel(context.findActivity()!!)
    val alerts by viewModel.clinicalAlerts.collectAsState()
    val readAlerts by viewModel.readAlerts.collectAsState()
    val identifiedAt by viewModel.alertIdentifiedAt.collectAsState()
    val persistedItems by viewModel.persistedNotifications.collectAsState()
    val selectedCategory by viewModel.selectedNotificationCategory.collectAsState()
    val isLoading by viewModel.notificationsLoading.collectAsState()
    val isLoadingMore by viewModel.notificationsLoadingMore.collectAsState()
    val hasMorePages by viewModel.notificationsHasMore.collectAsState()
    val syncError by viewModel.notificationsSyncError.collectAsState()
    val unavailableMessage by viewModel.unavailableDestinationMessage.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryAllowed by remember(context) { mutableStateOf(BatteryOptimizationHelper.estaLiberado(context)) }
    var batteryGuidance by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(mainViewModel) {
        mainViewModel.permissionUpdatedSignal.collect {
            viewModel.syncGrantedPermissions(context)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryAllowed = BatteryOptimizationHelper.estaLiberado(context)
                if (batteryAllowed) batteryGuidance = null
                viewModel.syncGrantedPermissions(context)
                viewModel.refreshNotifications(forceSnapshotIfEmpty = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openBatterySettings: () -> Unit = {
        val result = BatteryOptimizationHelper.pedirIgnorarOtimizacao(context)
        batteryGuidance = when (result) {
            BatteryOptimizationHelper.ResultadoLiberacao.CONFIG_APP_ABERTA ->
                "Nas configurações do Braga Saúde, abra Bateria e escolha Sem restrições, Irrestrito ou Não otimizado. O nome varia conforme o aparelho."
            BatteryOptimizationHelper.ResultadoLiberacao.INDISPONIVEL ->
                "Não foi possível abrir essa tela. Abra Configurações > Aplicativos > Braga Saúde > Bateria para permitir o uso sem restrições."
            else -> null
        }
        if (result == BatteryOptimizationHelper.ResultadoLiberacao.JA_LIBERADO) batteryAllowed = true
        batteryGuidance?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    NotificationsContent(
        alerts = alerts,
        readAlerts = readAlerts,
        identifiedAt = identifiedAt,
        batteryAllowed = batteryAllowed,
        batteryGuidance = batteryGuidance,
        onBack = onBack,
        onBatterySettings = openBatterySettings,
        onMarkAllRead = viewModel::markAlertsAsRead,
        onOpen = viewModel::markAlertAsRead,
        onDismiss = viewModel::dismissAlert,
        onNotificationPermission = mainViewModel::triggerNotificationPrompt,
        onMonitoringPermission = mainViewModel::requestPermissions,
        onNavigateToScreen = onNavigateToScreen,
        persistedItems = persistedItems,
        selectedCategory = selectedCategory,
        onSelectCategory = viewModel::selectNotificationCategory,
        showCategoryFilters = true,
        isLoading = isLoading,
        syncError = syncError,
        hasMorePages = hasMorePages,
        isLoadingMore = isLoadingMore,
        onLoadMore = viewModel::loadMoreNotifications,
        onRetrySync = { viewModel.refreshNotifications(forceSnapshotIfEmpty = true) },
        onOpenPersistedItem = { item -> viewModel.openPersistedNotification(item, onNavigateToScreen) },
        onArchivePersistedItem = viewModel::archivePersistedNotification,
        unavailableMessage = unavailableMessage,
        onDismissUnavailableMessage = viewModel::dismissUnavailableDestinationMessage
    )
}

@Composable
internal fun NotificationsContent(
    alerts: List<String>,
    readAlerts: Set<String>,
    identifiedAt: Map<String, Long>,
    batteryAllowed: Boolean,
    batteryGuidance: String?,
    onBack: () -> Unit,
    onBatterySettings: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onNotificationPermission: () -> Unit = {},
    onMonitoringPermission: () -> Unit = {},
    onNavigateToScreen: (Screen) -> Unit = {},
    persistedItems: List<NotificationDisplayItem> = emptyList(),
    selectedCategory: NotificationCategoryFilter = NotificationCategoryFilter.ALL,
    onSelectCategory: (NotificationCategoryFilter) -> Unit = {},
    showCategoryFilters: Boolean = persistedItems.isNotEmpty() || selectedCategory != NotificationCategoryFilter.ALL,
    isLoading: Boolean = false,
    syncError: String? = null,
    hasMorePages: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetrySync: () -> Unit = {},
    onOpenPersistedItem: (NotificationDisplayItem) -> Unit = {},
    onArchivePersistedItem: (NotificationDisplayItem) -> Unit = {},
    unavailableMessage: String? = null,
    onDismissUnavailableMessage: () -> Unit = {}
) {
    var selectedAlert by rememberSaveable { mutableStateOf<String?>(null) }
    val openAlert: (String) -> Unit = { alert ->
        onOpen(alert)
        selectedAlert = alert
    }
    var batteryExpanded by rememberSaveable { mutableStateOf(false) }
    var batteryDismissed by rememberSaveable { mutableStateOf(false) }

    val visibleLocalAlerts = remember(alerts, selectedCategory) {
        if (selectedCategory == NotificationCategoryFilter.ALL || selectedCategory == NotificationCategoryFilter.CARE) {
            alerts
        } else {
            emptyList()
        }
    }
    val visiblePersistedItems = remember(persistedItems, selectedCategory) {
        if (selectedCategory == NotificationCategoryFilter.ALL) {
            persistedItems
        } else {
            persistedItems.filter { it.category.equals(selectedCategory.apiValue, ignoreCase = true) }
        }
    }

    val unreadPersisted = visiblePersistedItems.filter { !it.isRead }
    val readPersisted = visiblePersistedItems.filter { it.isRead }
    val unreadLocal = visibleLocalAlerts.filter { it !in readAlerts }
    val previousLocal = visibleLocalAlerts.filter { it in readAlerts }

    val totalUnread = unreadPersisted.size + unreadLocal.size
    val totalRead = readPersisted.size + previousLocal.size
    val isCompletelyEmpty = totalUnread == 0 && totalRead == 0

    Scaffold(
        containerColor = BragaBackground,
        topBar = { EmeraldHeaderBanner(title = "Notificações", subtitle = "Seus avisos de cuidado", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!batteryAllowed && !batteryDismissed) item(key = "batteryGuidance") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryStd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Avisos em segundo plano", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { batteryExpanded = !batteryExpanded }) {
                                Icon(
                                    if (batteryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (batteryExpanded) "Recolher orientação de bateria" else "Ver orientação de bateria"
                                )
                            }
                            IconButton(onClick = { batteryDismissed = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Dispensar orientação de bateria")
                            }
                        }
                        if (batteryExpanded) {
                            Text(
                                batteryGuidance ?: "O Android pode limitar os avisos com o app fechado. Revise a bateria se os lembretes atrasarem.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = onBatterySettings) { Text("Configurar bateria") }
                        }
                    }
                }
            }

            if (showCategoryFilters) {
                item(key = "categoryFilters") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("notificationCategoryFilters")
                    ) {
                        items(NotificationCategoryFilter.entries, key = { it.name }) { filter ->
                            val selected = filter == selectedCategory
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectCategory(filter) },
                                label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("notificationFilterChip_${filter.name}")
                            )
                        }
                    }
                }
            }

            if (!syncError.isNullOrBlank()) {
                item(key = "syncError") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("notificationSyncErrorBanner")
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                syncError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = onRetrySync,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Tentar novamente")
                            }
                        }
                    }
                }
            }

            if (isLoading && isCompletelyEmpty) {
                item(key = "loading") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Carregando notificações…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (isCompletelyEmpty) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text("Tudo tranquilo por aqui", style = MaterialTheme.typography.titleLarge)
                        Text("Nenhum aviso no momento.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (totalUnread > 0) {
                item(key = "unreadHeading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Novas ($totalUnread)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Marcar todas como lidas")
                        }
                    }
                }
                items(unreadPersisted, key = { "persisted_unread_${it.id}" }) { item ->
                    PersistedNotificationCard(
                        item = item,
                        onOpen = { onOpenPersistedItem(item) },
                        onArchive = { onArchivePersistedItem(item) }
                    )
                }
                items(unreadLocal, key = { it }) { alert ->
                    NotificationCard(
                        alertDisplayText(alert).substringBefore("\n\n"),
                        alertCategory(alert),
                        true,
                        identifiedAt[alert],
                        { openAlert(alert) },
                        { onDismiss(alert) }
                    )
                }
            }

            if (totalRead > 0) {
                item(key = "readHeading") {
                    Text(
                        if (totalUnread == 0) "Histórico de avisos" else "Lidas ($totalRead)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(readPersisted, key = { "persisted_read_${it.id}" }) { item ->
                    PersistedNotificationCard(
                        item = item,
                        onOpen = { onOpenPersistedItem(item) },
                        onArchive = { onArchivePersistedItem(item) }
                    )
                }
                items(previousLocal, key = { it }) { alert ->
                    NotificationCard(
                        alertDisplayText(alert).substringBefore("\n\n"),
                        alertCategory(alert),
                        false,
                        identifiedAt[alert],
                        { openAlert(alert) },
                        { onDismiss(alert) }
                    )
                }
            }

            if (hasMorePages) {
                item(key = "loadMore") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notificationLoadMoreButton")
                    ) {
                        Text(if (isLoadingMore) "Carregando mais..." else "Carregar mais avisos")
                    }
                }
            }
        }
    }

    selectedAlert?.let { alert ->
        NotificationDetailsSheet(
            alert = alert,
            onClose = { selectedAlert = null },
            onDismiss = { onDismiss(alert); selectedAlert = null },
            onNotificationPermission = onNotificationPermission,
            onMonitoringPermission = onMonitoringPermission,
            onNavigateToScreen = onNavigateToScreen
        )
    }

    unavailableMessage?.let { msg ->
        BragaFormSheet(
            onDismissRequest = onDismissUnavailableMessage,
            title = { Text("Conteúdo indisponível") },
            text = {
                Text(
                    msg,
                    modifier = Modifier.testTag("notificationUnavailableMessage"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismissUnavailableMessage,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Entendi", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}

@Composable
private fun PersistedNotificationCard(
    item: NotificationDisplayItem,
    onOpen: () -> Unit,
    onArchive: () -> Unit
) {
    val unread = !item.isRead
    val icon = when (item.kind) {
        "FAMILY_MESSAGE" -> Icons.Default.ChatBubbleOutline
        "CONSULTATION_CREATED", "CONSULTATION_ACCEPTED", "CONSULTATION_REJECTED",
        "CONSULTATION_CANCELLED", "CONSULTATION_COMPLETED", "CONSULTATION_UPDATED" -> Icons.Default.Event
        "POST_REACTION" -> Icons.Default.ThumbUp
        "FAMILY_BINDING_ACCEPTED", "FAMILY_BINDING_REVOKED" -> Icons.Default.Groups
        "MEDICATION_LOW_STOCK", "MEDICATION_REMINDER" -> Icons.Default.Medication
        else -> Icons.Default.Favorite
    }
    val actionLabel = when (item.kind) {
        "FAMILY_MESSAGE" -> "Abrir conversa"
        "CONSULTATION_CREATED", "CONSULTATION_ACCEPTED", "CONSULTATION_REJECTED",
        "CONSULTATION_CANCELLED", "CONSULTATION_COMPLETED", "CONSULTATION_UPDATED" -> "Abrir consulta"
        "POST_REACTION" -> "Abrir publicação"
        "FAMILY_BINDING_ACCEPTED" -> "Ver família"
        "FAMILY_BINDING_REVOKED" -> "Ver detalhes"
        "MEDICATION_LOW_STOCK" -> "Ver estoque"
        "MEDICATION_REMINDER" -> "Ver horários"
        else -> "Abrir aviso"
    }
    val formattedDate = remember(item.occurredAt) {
        java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
            .format(java.util.Date(item.occurredAt))
    }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("persistedNotificationCard_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.categoryLabel, style = MaterialTheme.typography.titleSmall)
                    val statusLabel = buildString {
                        append(if (unread) "Não lido" else "Lido")
                        if (!item.personLabel.isNullOrBlank()) {
                            append(" · ")
                            append(item.personLabel)
                        }
                    }
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Arquivar aviso")
                }
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(item.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Recebido em $formattedDate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                actionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NotificationDetailsSheet(
    alert: String,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    onNotificationPermission: () -> Unit,
    onMonitoringPermission: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit
) {
    val isNotificationPermission = alert.startsWith("PERMISSION_NOTIFICATIONS:")
    val isMonitoringPermission = alert.startsWith("PERMISSION_REQUIRED:")
    val category = alertCategory(alert)
    // O primeiro parágrafo é o título; os demais preservam a orientação completa.
    val paragraphs = alertDisplayText(alert).split("\n\n", limit = 2)
    val title = when {
        isNotificationPermission || isMonitoringPermission -> paragraphs.first()
        paragraphs.size == 2 -> paragraphs.first()
        else -> null
    }
    val body = when {
        isNotificationPermission ->
            "Permita as notificações para receber lembretes e avisos de saúde mesmo com o aplicativo fechado. Toque em Configurar permissões para autorizar no Android."
        isMonitoringPermission ->
            "Permita o acesso à atividade física e à localização para acompanhar seus passos e caminhadas e atualizar seu Score. Toque em Configurar permissões para revisar os acessos no Android."
        else -> paragraphs.getOrElse(1) { paragraphs.first() }
    }
    val destination = when (category) {
        "Hidratação" -> Screen.Hydration(autoOpenDialog = true)
        "Atividade" -> Screen.Steps
        "Medicamentos" -> Screen.Reminders
        else -> null
    }
    val hasAction = isNotificationPermission || isMonitoringPermission || destination != null
    val actionLabel = when {
        isNotificationPermission || isMonitoringPermission -> "Configurar permissões"
        category == "Hidratação" -> "Registrar água"
        category == "Atividade" -> "Ver passos"
        category == "Medicamentos" -> "Ver medicamentos"
        else -> "Voltar aos avisos"
    }
    BragaFormSheet(
        onDismissRequest = onClose,
        title = { Text(category) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                title?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("notificationAlertTitle"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    body,
                    modifier = Modifier.testTag("notificationAlertBody"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = {
                        when {
                            isNotificationPermission -> onNotificationPermission()
                            isMonitoringPermission -> onMonitoringPermission()
                            destination != null -> onNavigateToScreen(destination)
                        }
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.bodyMedium)
                }
                if (hasAction) {
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Voltar aos avisos", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Dispensar aviso", style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

private fun alertDisplayText(alert: String): String =
    alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim()

private fun alertCategory(alert: String): String {
    // A orientação pode mencionar outros hábitos; a categoria acompanha o título do aviso.
    val title = alert.substringBefore("\n\n")
    return when {
        alert.startsWith("PERMISSION_") -> "Configurações"
        listOf("água", "hidrat").any { title.contains(it, ignoreCase = true) } -> "Hidratação"
        listOf("passos", "atividade", "movimento", "caminhada", "sedentar").any { title.contains(it, ignoreCase = true) } -> "Atividade"
        listOf("remédio", "medica").any { title.contains(it, ignoreCase = true) } -> "Medicamentos"
        else -> "Saúde e cuidado"
    }
}

@Composable
private fun NotificationCard(
    message: String,
    category: String,
    unread: Boolean,
    identifiedAt: Long?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            when (category) {
                                "Configurações" -> Icons.Default.Settings
                                "Hidratação" -> Icons.Default.WaterDrop
                                "Atividade" -> Icons.Default.DirectionsWalk
                                "Medicamentos" -> Icons.Default.Medication
                                else -> Icons.Default.Favorite
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (unread) "Não lido" else "Lido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dispensar aviso")
                }
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            identifiedAt?.let { timestamp ->
                val time = java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
                    .format(java.util.Date(timestamp))
                Text(
                    "Identificado em $time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Ler aviso",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
