package br.com.bragasaude.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.util.HealthFormatter

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onProfileSaved: () -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = onBack != null) {
        showLeaveDialog = true
    }
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Sair da edição?") },
            text = { Text("As alterações ainda não salvas serão descartadas. Seus dados já salvos serão mantidos.") },
            confirmButton = {
                TextButton(enabled = !isLoading, onClick = { showLeaveDialog = false; onBack?.invoke() }) { Text("Sair sem salvar") }
            },
            dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Continuar editando") } }
        )
    }
    val saveError by viewModel.saveError.collectAsState()
    LaunchedEffect(saveError) {
        saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val isCaregiver = profile?.userRole == "CAREGIVER" && profile?.caregiverMode == "VIEWER_ONLY" && profile?.selfCareSetupPending != true

    var fullName by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var isSmoker by rememberSaveable { mutableStateOf(false) }
    var activityLevel by rememberSaveable { mutableStateOf("Sedentário") }
    
    var hasDiabetes by rememberSaveable { mutableStateOf(false) }
    var diabetesType by rememberSaveable { mutableStateOf("Tipo 2") }
    var foodAllergies by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var customFoodRestrictions by rememberSaveable { mutableStateOf("") }
    var hasHypertension by rememberSaveable { mutableStateOf(false) }
    var hasThyroid by rememberSaveable { mutableStateOf(false) }

    val allergyOptions = listOf(
        "Lactose e Derivados do Leite",
        "Glúten / Doença Celíaca",
        "Frutos do Mar e Crustáceos",
        "Castanhas e Amendoim",
        "Ovos",
        "Peixes"
    )
    val diabetesTypes = listOf("Pré-diabetes", "Tipo 2", "Tipo 1", "LADA / Outro", "Não sei ao certo")

    var emergencyName by rememberSaveable { mutableStateOf("") }
    var emergencyRelation by rememberSaveable { mutableStateOf("") }
    var emergencyPhone by rememberSaveable { mutableStateOf("") }

    var sleepStart by rememberSaveable { mutableStateOf("22:00") }
    var sleepEnd by rememberSaveable { mutableStateOf("06:00") }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )

    var showSleepStartTimePicker by rememberSaveable { mutableStateOf(false) }
    var showSleepEndTimePicker by rememberSaveable { mutableStateOf(false) }

    val activityLevels = listOf("Sedentário", "Levemente Ativo", "Moderadamente Ativo", "Muito Ativo")
    val genders = listOf("Masculino", "Feminino", "Outro", "Prefiro não dizer")

    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    var hydratedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profile?.id) {
        if (profile?.id == hydratedProfileId) return@LaunchedEffect
        profile?.let {
            hydratedProfileId = it.id
            fullName = it.fullName ?: ""
            birthDate = HealthFormatter.formatDisplayDate(it.birthDate).filter { c -> c.isDigit() }.take(8)
            gender = it.gender ?: ""
            weight = it.weight?.toString()?.replace(".", ",") ?: ""
            height = it.height?.toString()?.replace(".", ",") ?: ""
            isSmoker = it.isSmoker
            activityLevel = it.activityLevel ?: "Sedentário"
            hasDiabetes = it.hasDiabetes
            diabetesType = it.diabetesType ?: "Tipo 2"
            foodAllergies = it.foodAllergies?.toSet() ?: emptySet()
            customFoodRestrictions = it.customFoodRestrictions ?: ""
            hasHypertension = it.hasHypertension
            hasThyroid = it.hasThyroidIssue
            emergencyName = it.emergencyContactName ?: ""
            emergencyRelation = it.emergencyContactRelation ?: ""
            emergencyPhone = it.emergencyContactPhone?.filter { c -> c.isDigit() }?.take(11) ?: ""
            sleepStart = it.sleepStartTime ?: "22:00"
            sleepEnd = it.sleepEndTime ?: "06:00"
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = it
                        val year = cal.get(Calendar.YEAR)
                        val month = cal.get(Calendar.MONTH) + 1
                        val day = cal.get(Calendar.DAY_OF_MONTH)
                        birthDate = "%02d%02d%04d".format(day, month, year)
                    }
                    showDatePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showSleepStartTimePicker) {
        val parsedHour = sleepStart.split(":").getOrNull(0)?.toIntOrNull() ?: 22
        val parsedMinute = sleepStart.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = parsedHour.coerceIn(0, 23),
            initialMinute = parsedMinute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showSleepStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    sleepStart = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showSleepStartTimePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepStartTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Hora de Dormir", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showSleepEndTimePicker) {
        val parsedHour = sleepEnd.split(":").getOrNull(0)?.toIntOrNull() ?: 6
        val parsedMinute = sleepEnd.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = parsedHour.coerceIn(0, 23),
            initialMinute = parsedMinute.coerceIn(0, 59),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showSleepEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    sleepEnd = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showSleepEndTimePicker = false
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepEndTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Hora de Acordar", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            if (onBack != null) {
                TopAppBar(title = { Text("Meu cadastro") }, navigationIcon = {
                    TextButton(onClick = { showLeaveDialog = true }) { Text("Voltar") }
                })
            }
        },
        bottomBar = {
            Box(Modifier.padding(24.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Button(
                        onClick = {
                            if (birthDate.isNotBlank() && !HealthFormatter.isValidDate(birthDate)) {
                                Toast.makeText(context, "Por favor, digite uma data de nascimento válida (DD/MM/AAAA).", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (emergencyPhone.isNotBlank() && !HealthFormatter.isValidPhone(emergencyPhone)) {
                                Toast.makeText(context, "Por favor, digite o telefone de emergência completo com DDD (ex: (11) 98765-4321).", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val parsedWeight = if (!isCaregiver) HealthFormatter.parseDouble(weight) else profile?.weight
                            if (!isCaregiver && (parsedWeight == null || parsedWeight !in 20.0..350.0)) {
                                Toast.makeText(context, "Por favor, digite um peso válido entre 20 e 350 kg (ex: 70,5).", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val parsedHeight = if (!isCaregiver) HealthFormatter.parseDouble(height) else profile?.height
                            if (!isCaregiver && (parsedHeight == null || (parsedHeight !in 50.0..250.0 && parsedHeight !in 0.5..2.5))) {
                                Toast.makeText(context, "Por favor, digite uma altura válida (ex: 170 cm ou 1,70 m).", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            // APP-8: perfil chega ao ViewModel via ProfileInput
                            // em vez do overload monolítico de ~28 parâmetros.
                            val input = ProfileInput(
                                name = fullName,
                                birthDate = HealthFormatter.formatDateInput(birthDate),
                                gender = gender,
                                activityLevel = activityLevel,
                                height = parsedHeight?.let { if (it <= 2.5) it * 100 else it },
                                weight = parsedWeight,
                                isSmoker = if (!isCaregiver) isSmoker else (profile?.isSmoker ?: false),
                                hasDiabetes = if (!isCaregiver) hasDiabetes else (profile?.hasDiabetes ?: false),
                                diabetesType = if (!isCaregiver) { if (hasDiabetes) diabetesType else null } else profile?.diabetesType,
                                foodAllergies = if (!isCaregiver) foodAllergies.toList() else (profile?.foodAllergies ?: emptyList()),
                                customFoodRestrictions = if (!isCaregiver) customFoodRestrictions.ifBlank { null } else profile?.customFoodRestrictions,
                                hasHypertension = if (!isCaregiver) hasHypertension else (profile?.hasHypertension ?: false),
                                hasThyroid = if (!isCaregiver) hasThyroid else (profile?.hasThyroidIssue ?: false),
                                sleepStart = HealthFormatter.normalizeTime(sleepStart, "22:00"),
                                sleepEnd = HealthFormatter.normalizeTime(sleepEnd, "06:00"),
                                emergencyName = emergencyName,
                                emergencyRelation = emergencyRelation,
                                emergencyPhone = HealthFormatter.formatPhoneInput(emergencyPhone)
                            )
                            viewModel.saveProfile(input, onComplete = onProfileSaved)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        enabled = fullName.isNotBlank() && profile != null
                    ) {
                        Text(if (profile?.selfCareSetupPending == true) "Ativar autocuidado" else "Salvar perfil")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                br.com.bragasaude.ui.components.ShieldEcgIcon(sizeDp = 64.dp)
                Spacer(Modifier.height(8.dp))
                Text("Bem-vindo(a)!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Complete seu perfil para personalizarmos sua experiência.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }

            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Dados Pessoais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Nome Completo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                        )
                        
                        OutlinedTextField(
                            value = birthDate,
                            onValueChange = {
                                // D-INP1: estado guarda apenas dígitos; a máscara é
                                // renderizada pelo VisualTransformation (sem saltar cursor).
                                birthDate = it.filter { c -> c.isDigit() }.take(8)
                            },
                            label = { Text("Data de Nascimento") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            visualTransformation = DateMaskTransformation(),
                            trailingIcon = { 
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Selecionar Data") 
                                }
                            },
                            placeholder = { Text("DD/MM/AAAA") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next)
                        )
                        
                        Column {
                            Text("Gênero", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                genders.take(2).forEach { option ->
                                    FilterChip(
                                        selected = gender == option,
                                        onClick = { gender = option },
                                        label = { Text(option) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                genders.drop(2).forEach { option ->
                                    FilterChip(
                                        selected = gender == option,
                                        onClick = { gender = option },
                                        label = { Text(option) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        
                        if (!isCaregiver) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = weight,
                                    onValueChange = { weight = it },
                                    label = { Text("Peso (kg)") },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                    placeholder = { Text("70,5") }
                                )
                                OutlinedTextField(
                                    value = height,
                                    onValueChange = { height = it },
                                    label = { Text("Altura (cm)") },
                                    modifier = Modifier.weight(1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                    placeholder = { Text("175") }
                                )
                            }
                        }
                    }
                }
            }

            if (!isCaregiver) {
                item {
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Estilo de Vida e Saúde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        Column {
                            Text("Nível de Atividade", style = MaterialTheme.typography.labelLarge)
                            Column {
                                activityLevels.forEach { level ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(selected = activityLevel == level, onClick = { activityLevel = level })
                                        Text(level, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        
                        Column {
                            Text("Saúde e Hábitos", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSmoker, onCheckedChange = { isSmoker = it })
                                Text("Sou fumante", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasDiabetes, onCheckedChange = { hasDiabetes = it })
                                Text("Tenho Diabetes", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasDiabetes) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Qual o tipo de diabetes?",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        diabetesTypes.forEach { type ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { diabetesType = type }
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                RadioButton(
                                                    selected = diabetesType == type,
                                                    onClick = { diabetesType = type }
                                                )
                                                Text(type, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasHypertension, onCheckedChange = { hasHypertension = it })
                                Text("Tenho Hipertensão", style = MaterialTheme.typography.bodyMedium)
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            // Seção Alergias & Restrições Alimentares
                            Text(
                                "Alergias & Restrições Alimentares",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Alimentos marcados serão 100% excluídos das suas sugestões e lista de compras:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(6.dp))

                            allergyOptions.forEach { allergy ->
                                val isChecked = foodAllergies.contains(allergy)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            foodAllergies = if (isChecked) foodAllergies - allergy else foodAllergies + allergy
                                        }
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            foodAllergies = if (checked) foodAllergies + allergy else foodAllergies - allergy
                                        }
                                    )
                                    Text(allergy, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customFoodRestrictions,
                                onValueChange = { customFoodRestrictions = it },
                                label = { Text("Outros alimentos que você não come") },
                                placeholder = { Text("Ex: alho, pimenta, coentro") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
                item {
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Perfil de Cuidador", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Dados clínicos, corporais e metas nutricionais são preenchidos e acompanhados diretamente na conta do seu familiar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Contato de Emergência", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = emergencyName,
                            onValueChange = { emergencyName = it },
                            label = { Text("Nome do Contato") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = emergencyRelation,
                            onValueChange = { emergencyRelation = it },
                            label = { Text("Grau de Parentesco") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = emergencyPhone,
                            onValueChange = {
                                // D-INP1: estado guarda apenas dígitos; a máscara é
                                // renderizada pelo VisualTransformation (sem saltar cursor).
                                emergencyPhone = it.filter { c -> c.isDigit() }.take(11)
                            },
                            label = { Text("Telefone (com DDD)") },
                            placeholder = { Text("(11) 98765-4321") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            visualTransformation = PhoneMaskTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF3F4F6))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ciclo de Sono (Não incomodar)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = sleepStart,
                                onValueChange = { sleepStart = HealthFormatter.formatTimeInput(it) },
                                label = { Text("Hora de Dormir") },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { showSleepStartTimePicker = true }) {
                                        Icon(Icons.Default.AccessTime, contentDescription = "Selecionar Horário de Dormir")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                placeholder = { Text("22:00") }
                            )
                            OutlinedTextField(
                                value = sleepEnd,
                                onValueChange = { sleepEnd = HealthFormatter.formatTimeInput(it) },
                                label = { Text("Hora de Acordar") },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { showSleepEndTimePicker = true }) {
                                        Icon(Icons.Default.AccessTime, contentDescription = "Selecionar Horário de Acordar")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                placeholder = { Text("06:00") }
                            )
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
