package br.com.bragasaude.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import br.com.bragasaude.ui.auth.AuthViewModel
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Diversity3
import br.com.bragasaude.ui.components.BragaActionCard

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import br.com.bragasaude.ui.components.PhoneLinkDialog
import br.com.bragasaude.ui.components.WhatsAppLinkButton
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    onEditProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onAddCaregiving: () -> Unit = {},
    onNavigateToWearables: () -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val profile by profileViewModel.profile.collectAsState()
    val userFeedbacks by profileViewModel.userFeedbacks.collectAsState()
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showPhoneLinkDialog by remember { mutableStateOf(false) }

    val phoneLinkSent by profileViewModel.phoneLinkSent.collectAsState()
    val phoneLinkWa by profileViewModel.phoneLinkWa.collectAsState()
    val phoneLinkLoading by profileViewModel.phoneLinkLoading.collectAsState()
    val phoneLinkError by profileViewModel.phoneLinkError.collectAsState()
    val phoneLinkSuccess by profileViewModel.phoneLinkSuccess.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(phoneLinkSuccess) {
        if (phoneLinkSuccess) {
            Toast.makeText(context, "WhatsApp vinculado com sucesso!", Toast.LENGTH_LONG).show()
            showPhoneLinkDialog = false
            profileViewModel.resetPhoneLinkState()
        }
    }
    
    // Launcher para escolher foto da galeria
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            profileViewModel.updateAvatar(null, imageUri.toString())
        }
    }
    
    // Identificar se é cuidador
    val isViewerOnlyCaregiver = profile?.userRole == "CAREGIVER" && profile?.caregiverMode == "VIEWER_ONLY"
    
    // Pegar avatar diretamente do FirebaseAuth
    val googleAvatarUrl = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
    }

    LaunchedEffect(Unit) {
        profileViewModel.loadProfile()
    }

    val resolvedFeedbacks = remember(userFeedbacks) {
        userFeedbacks.filter { it.status == "resolved" || it.status == "resolvido_v1.2" || it.status == "implementado" }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Meu Perfil", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                    IconButton(onClick = onEditProfile) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Foto de Perfil Reativa com botão de edição
                val customPhotoUri by profileViewModel.customPhotoUri.collectAsState()
                val avatarIcon = when (profile?.avatarIdentifier) {
                    "Person" -> Icons.Default.Person
                    "Favorite" -> Icons.Default.Favorite
                    "Accessibility" -> Icons.Default.AccessibilityNew
                    "Star" -> Icons.Default.Star
                    else -> null
                }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        avatarIcon != null -> Icon(
                            avatarIcon,
                            contentDescription = "Avatar de perfil",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        !customPhotoUri.isNullOrBlank() -> AsyncImage(
                            model = customPhotoUri,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        !googleAvatarUrl.isNullOrBlank() -> AsyncImage(
                            model = googleAvatarUrl,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = {
                                android.util.Log.e("AvatarDebug", "Error loading image: ${it.result.throwable.message}")
                            }
                        )
                        else -> Text(
                            text = profile?.fullName?.take(1)?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Botão de edição sobreposto no canto inferior direito
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { 
                                showAvatarDialog = true
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar foto de perfil",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                Text(
                    text = profile?.fullName ?: "Carregando...",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Membro Braga Saúde",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (resolvedFeedbacks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Sua sugestao foi implementada!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    "Obrigado por nos ajudar a construir um Braga Saúde cada vez melhor.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Card especial para cuidadores VIEWER_ONLY
                if (isViewerOnlyCaregiver) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Button(onClick = { profileViewModel.startSelfCareSetup(onEditProfile) }) {
                                Text(if (profile?.selfCareSetupPending == true) "Continuar meu cadastro" else "Também quero cuidar de mim")
                            }
                            Text(
                                text = "Modo Cuidador Ativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Sua conta é dedicada exclusivamente ao acompanhamento e proteção do seu familiar. Você não precisa cadastrar peso, altura ou biometria própria.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Apenas Acompanhamento",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Metrics Bar para pacientes e cuidadores HYBRID
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${profile?.weight?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--"} kg",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Peso",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.LightGray.copy(alpha = 0.3f)))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${profile?.height?.toInt() ?: "--"} cm",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Altura",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.LightGray.copy(alpha = 0.3f)))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val w = profile?.weight
                                val h = profile?.height
                                val imc = if (w != null && h != null && h > 0) {
                                    val hMeters = h / 100.0
                                    String.format(java.util.Locale.US, "%.1f", w / (hMeters * hMeters))
                                } else {
                                    "--"
                                }
                                Text(
                                    text = imc,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "IMC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conexões", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    BragaActionCard(
                        title = if (profile?.userRole == "PATIENT") "Cuidar de alguém" else "Conectar familiar",
                        description = "Acompanhe quem você ama, mantendo sua própria saúde.",
                        icon = Icons.Default.Diversity3, onClick = onAddCaregiving
                    )
                    BragaActionCard(title = "Relógios e saúde", description = "Conecte seus dados e consulte as leituras salvas.", icon = Icons.Default.Watch, onClick = onNavigateToWearables)
                }
            }

            // Seção Dados Pessoais - oculta para cuidadores VIEWER_ONLY
            if (!isViewerOnlyCaregiver) {
                item {
                    Text(
                        "Dados Pessoais",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Start
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val p = profile
                            val items = listOfNotNull(
                                p?.birthDate?.takeIf { it.isNotBlank() }?.let { "Nascimento" to br.com.bragasaude.data.util.HealthFormatter.formatDisplayDate(it) },
                                p?.gender?.takeIf { it.isNotBlank() }?.let { "Gênero" to it },
                                p?.activityLevel?.takeIf { it.isNotBlank() }?.let { "Atividade Física" to it }
                            )
                            items.forEachIndexed { index, (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End
                                    )
                                }
                                if (index < items.size - 1) {
                                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                                }
                            }
                            if (items.isEmpty()) {
                                Text("Nenhum dado pessoal preenchido.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Seção Saúde - oculta para cuidadores VIEWER_ONLY
            if (!isViewerOnlyCaregiver) {
                item {
                    Text(
                        "Saúde",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Start
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val p = profile
                            val conditions = listOfNotNull(
                                if (p?.isSmoker == true) "Fumante" else null,
                                if (p?.hasDiabetes == true) "Diabetes" else null,
                                if (p?.hasHypertension == true) "Hipertensão" else null
                            )
                            
                            if (conditions.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    conditions.forEach { condition ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = condition,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
        }
    }
    
    // Diálogo de escolha de avatar/foto de perfil
    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("Personalizar Foto de Perfil", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            // Abrir galeria para escolher foto
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Escolher da Galeria")
                    }
                    
                    Text("Ou escolha um avatar:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    
                    // Grade com 4 avatares predefinidos
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf(
                            "Person" to Icons.Default.Person,
                            "Favorite" to Icons.Default.Favorite,
                            "Accessibility" to Icons.Default.AccessibilityNew,
                            "Star" to Icons.Default.Star
                        )) { (description, icon) ->
                            Surface(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clickable {
                                        profileViewModel.updateAvatar(description)
                                        showAvatarDialog = false
                                    },
                                shape = CircleShape,
                                border = BorderStroke(
                                    2.dp,
                                    if (profile?.avatarIdentifier == description) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = description,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
                            }
                            
                            if (!p?.emergencyContactName.isNullOrBlank()) {
                                if (conditions.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(16.dp))
                                }
                                Text("Contato de Emergência", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Text(p?.emergencyContactName ?: "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                if (!p?.emergencyContactRelation.isNullOrBlank()) {
                                    Text(p?.emergencyContactRelation ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                if (!p?.emergencyContactPhone.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(p?.emergencyContactPhone ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            
                            if (conditions.isEmpty() && p?.emergencyContactName.isNullOrBlank()) {
                                Text("Nenhuma informação de saúde preenchida.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // WhatsApp sempre visível: cadastrar se não tiver, alterar se já tiver cadastrado
            item {
                val userPhone = profile?.phone
                val hasPhone = !userPhone.isNullOrBlank()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable {
                            profileViewModel.resetPhoneLinkState()
                            showPhoneLinkDialog = true
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasPhone) BragaMint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (hasPhone) BragaEmerald.copy(alpha = 0.5f) else BragaMintBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (hasPhone) BragaEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "WhatsApp",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (hasPhone) "Meu WhatsApp" else "Vincular seu WhatsApp",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BragaTextPrimary
                                )
                                if (hasPhone) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BragaEmerald.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Conectado",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BragaEmerald,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (hasPhone) "$userPhone • Toque para alterar" else "Receba lembretes de exames e avisos no WhatsApp. Toque para cadastrar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextSecondary
                            )
                        }
                    }
                }
            }

            // Vínculo WhatsApp por TOTP (temporário até a Meta liberar template).
            // Toque único abre o WhatsApp com o código atual pronto para enviar.
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    WhatsAppLinkButton(
                        totpSecret = profile?.whatsappTotpSecret,
                        alreadyLinked = !profile?.whatsappPhone.isNullOrBlank()
                    )
                }
            }

            item {
                Text(
                    "Conta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Start
                )
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDeleteDialog = true
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Excluir Perfil e Conta",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { authViewModel.signOut() }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sair da Conta", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(40.dp)) }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        "Excluir Perfil e Conta?",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        "Esta ação apagará permanentemente todos os seus dados de saúde, exames, registros e metas deste dispositivo e da nuvem.\n\nSua conta será cancelada, mas você poderá criar uma nova conta com o mesmo e-mail Google a qualquer momento caso deseje retornar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            profileViewModel.deleteProfile {
                                scope.launch { authViewModel.deleteAccount() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Excluir Definitivamente", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showPhoneLinkDialog) {
            PhoneLinkDialog(
                onDismiss = {
                    showPhoneLinkDialog = false
                    profileViewModel.resetPhoneLinkState()
                },
                onSendOtp = { profileViewModel.sendPhoneLinkOtp(it) },
                onVerifyOtp = { phone, code -> profileViewModel.verifyPhoneLinkOtp(phone, code) },
                codeSent = phoneLinkSent,
                waLink = phoneLinkWa,
                onOpenWhatsApp = { waUrl ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                            setPackage("com.whatsapp")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                        context.startActivity(webIntent)
                    }
                },
                initialPhone = profile?.phone ?: "",
                isLoading = phoneLinkLoading,
                errorMessage = phoneLinkError,
                onResetStep = { profileViewModel.resetPhoneLinkState() }
            )
        }
    }
}

data class ProfileInfoItem(val label: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun ProfileInfoCard(items: List<ProfileInfoItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(item.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
