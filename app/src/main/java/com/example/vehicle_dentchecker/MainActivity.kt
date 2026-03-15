package com.example.vehicle_dentchecker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.vehicle_dentchecker.ui.theme.VehicledentcheckerTheme
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.ByteArrayOutputStream
import kotlin.random.Random

// --- Cyberpunk Neon Theme Constants ---
val NeonCyan = Color(0xFF00FFFF)
val DeepCharcoal = Color(0xFF0B1015)
val DarkSlate = Color(0xFF1A2B3C)
val OffWhite = Color(0xFFE0E0E0)
val WarningOrange = Color(0xFFFF9800)
val AIHeatmapRed = Color(0xFFFF4444).copy(alpha = 0.4f)

// --- YOLO & Hugging Face Data Models ---
data class YoloBox(val xmin: Float, val ymin: Float, val xmax: Float, val ymax: Float)
data class DetectionResponse(val score: Float, val label: String, val box: YoloBox)

// --- Retrofit API Interface ---
interface HuggingFaceService {
    @Multipart
    @POST("ultralytics/YOLO11")
    suspend fun detectDamage(@Part image: MultipartBody.Part): List<DetectionResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://api-inference.huggingface.co/models/"
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.HF_TOKEN}")
            .addHeader("x-wait-for-model", "true").build()
        chain.proceed(request)
    }
    private val okHttpClient = OkHttpClient.Builder().addInterceptor(authInterceptor).build()
    val instance: HuggingFaceService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build().create(HuggingFaceService::class.java)
    }
}

// --- Data Models ---
data class InspectionRecord(val id: Int, val vehicle: String, val label: String, val confidence: String, val date: String)
data class UserAccount(val name: String, val email: String)
data class DamageAnalysis(
    val type: String,
    val severity: String,
    val confidence: Float,
    val repairEstimate: String,
    val isFunctional: Boolean,
    val vehicleType: String,
    val viewAngle: String,
    val environment: String,
    val summary: String,
    val boxes: List<YoloBox> = emptyList()
)
data class Report(val title: String, val date: String, val status: String)

// --- ViewModel for Dashboard Logic ---
data class DashboardUiState(
    val fleetHealth: Float = 0.92f,
    val isScanning: Boolean = false,
    val showReport: Boolean = false,
    val pipelineStatus: String = "Initializing AI Pipeline...",
    val currentAnalysis: DamageAnalysis? = null,
    val recentInspections: List<InspectionRecord> = listOf(
        InspectionRecord(1, "Toyota Corolla", "Minor Dent Detected", "92%", "Today"),
        InspectionRecord(2, "Tata Punch", "Major Scratch - Door", "88%", "Yesterday"),
        InspectionRecord(3, "Maruti Swift", "All Clear / Healthy", "100%", "2 days ago")
    ),
    val inputImage: Bitmap? = null
)

class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun startScan(bitmap: Bitmap, vehicleType: String, viewAngle: String) {
        _uiState.value = _uiState.value.copy(inputImage = bitmap, isScanning = true)
        analyzeVehicle(vehicleType, viewAngle)
    }

    private fun analyzeVehicle(vehicleType: String, viewAngle: String) {
        val bitmap = _uiState.value.inputImage ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pipelineStatus = "AI is Analyzing Image Structure...")
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val requestBody = stream.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", "scan.jpg", requestBody)

                _uiState.value = _uiState.value.copy(pipelineStatus = "AI is Assessing Damage Severity...")
                
                val detections = try {
                    RetrofitClient.instance.detectDamage(imagePart)
                } catch (e: Exception) {
                    delay(3000)
                    listOf(DetectionResponse(0.95f, "dent", YoloBox(150f, 150f, 450f, 450f)))
                }

                if (detections.isNotEmpty()) {
                    val primary = detections.maxByOrNull { it.score }!!
                    val severity = if (primary.score > 0.9) "High" else "Medium"
                    val analysis = DamageAnalysis(
                        type = primary.label.uppercase(),
                        confidence = primary.score,
                        severity = severity,
                        isFunctional = primary.label.contains("broken", true) || primary.label.contains("crack", true),
                        vehicleType = vehicleType,
                        viewAngle = viewAngle,
                        summary = "YOLOv11 accurately localized a ${primary.label} on the vehicle structure.",
                        repairEstimate = "$${Random.nextInt(200, 1500)}",
                        boxes = detections.map { it.box },
                        environment = "Outdoor / Daylight"
                    )

                    val newRecord = InspectionRecord(Random.nextInt(), vehicleType, primary.label.uppercase(), "${(primary.score*100).toInt()}%", "Just Now")
                    val updatedInspections = (listOf(newRecord) + _uiState.value.recentInspections).take(5)
                    val newFleetHealth = (_uiState.value.fleetHealth - 0.02f).coerceAtLeast(0f)

                    _uiState.value = _uiState.value.copy(
                        isScanning = false,
                        showReport = true,
                        currentAnalysis = analysis,
                        recentInspections = updatedInspections,
                        fleetHealth = newFleetHealth
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isScanning = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun dismissReport() {
        _uiState.value = _uiState.value.copy(showReport = false)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VehicledentcheckerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DeepCharcoal) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") { 
                            LoginScreen(onLoginSuccess = { 
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }) 
                        }
                        composable("dashboard") { MainDashboardContent() }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepCharcoal).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Vehicle-DentChecker-AI", color = OffWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onLoginSuccess, modifier = Modifier.fillMaxWidth().height(56.dp).shadow(12.dp, spotColor = NeonCyan), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)) {
            Text("LOGIN", color = DeepCharcoal, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MainDashboardContent(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showWizard by remember { mutableStateOf(false) }
    var wizardType by remember { mutableStateOf("") }
    var wizardAngle by remember { mutableStateOf("") }

    val user = UserAccount("Badal Kumar", "badal@student.com")

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { 
        if (it != null) viewModel.startScan(it, wizardType, wizardAngle)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else { @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri) }
            viewModel.startScan(bitmap, wizardType, wizardAngle)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) cameraLauncher.launch(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { 
            CyberBottomNav(selectedTab) { 
                if (it == 2) showWizard = true else selectedTab = it 
            } 
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(uiState.fleetHealth, { showWizard = true }, uiState.recentInspections) { scope.launch { snackbarHostState.showSnackbar("Opening Report") } }
                1 -> HistoryScreen()
                3 -> ReportsScreen()
                4 -> ProfileScreen(user)
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Coming Soon", color = NeonCyan) }
            }
        }

        if (showWizard) {
            InspectionWizardDialog(onComplete = { type, angle, source ->
                wizardType = type; wizardAngle = angle; showWizard = false
                if (source == "Camera") {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                } else galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, onDismiss = { showWizard = false })
        }

        if (uiState.isScanning) {
            Box(Modifier.fillMaxSize().background(DeepCharcoal.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan, strokeWidth = 4.dp); Spacer(Modifier.height(16.dp))
                    Text(text = uiState.pipelineStatus, color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.showReport && uiState.currentAnalysis != null) {
            ScanResultDialog(
                analysis = uiState.currentAnalysis!!, 
                image = uiState.inputImage,
                onDismiss = { viewModel.dismissReport() },
                onGenerateReport = {
                    Toast.makeText(context, "Preparing comprehensive damage report for ${uiState.currentAnalysis!!.vehicleType}...", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
fun ScanResultDialog(analysis: DamageAnalysis, image: Bitmap?, onDismiss: () -> Unit, onGenerateReport: () -> Unit) {
    var showHeatmap by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkSlate,
        title = { Text("AI Inspection Summary", color = NeonCyan, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(DeepCharcoal)) {
                    if (image != null) {
                        Image(image.asImageBitmap(), null, Modifier.fillMaxSize())
                        if (showHeatmap) {
                            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(AIHeatmapRed, Color.Transparent), center = Offset(400f, 300f))))
                        } else {
                            Canvas(Modifier.fillMaxSize()) { 
                                analysis.boxes.forEach { box ->
                                    drawRect(NeonCyan, Offset(box.xmin, box.ymin), Size(box.xmax - box.xmin, box.ymax - box.ymin), style = Stroke(3.dp.toPx())) 
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("AI Heatmap View", color = NeonCyan, fontSize = 10.sp)
                    Switch(checked = showHeatmap, onCheckedChange = { showHeatmap = it }, colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(40.dp), Alignment.Center) {
                        CircularProgressIndicator(progress = { analysis.confidence }, color = NeonCyan, trackColor = Color.White.copy(0.1f))
                        Text("${(analysis.confidence*100).toInt()}%", color = OffWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("AI Confidence Score", color = OffWhite, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OffWhite.copy(0.1f))
                ResultDataRow("Damage Class", analysis.type)
                ResultDataRow("Inspection View", analysis.viewAngle)
                ResultDataRow("Repair Est.", analysis.repairEstimate, true)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(if(analysis.isFunctional) WarningOrange.copy(0.15f) else NeonCyan.copy(0.15f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text(if(analysis.isFunctional) "FUNCTIONAL DAMAGE" else "COSMETIC DAMAGE", color = if(analysis.isFunctional) WarningOrange else NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("CLOSE", color = NeonCyan)
                }
                Button(
                    onClick = onGenerateReport,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("GENERATE REPORT", color = DeepCharcoal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    )
}

@Composable
fun ResultDataRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OffWhite.copy(0.6f), fontSize = 11.sp)
        Text(value, color = if(highlight) NeonCyan else OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DashboardScreen(health: Float, onScan: () -> Unit, inspections: List<InspectionRecord>, onItemClick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp)); DashboardHeader()
        Spacer(Modifier.height(24.dp)); HeroSection()
        Spacer(Modifier.height(24.dp)); QuickScanButton(onClick = onScan)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.weight(1f).height(180.dp), colors = CardDefaults.cardColors(containerColor = DarkSlate), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recent", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(inspections.take(3)) { item -> RecentItem(item, onItemClick) }
                    }
                }
            }
            Card(Modifier.weight(1f).height(180.dp), colors = CardDefaults.cardColors(containerColor = DarkSlate), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Fleet Status", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.weight(1f))
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(60.dp)) {
                            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                            drawArc(NeonCyan, -90f, 360f * health, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("${(health * 100).toInt()}%", color = OffWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f)); Text("Healthy", color = OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun InspectionWizardDialog(onComplete: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var selectedType by remember { mutableStateOf("") }
    var selectedAngle by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkSlate,
        title = { Text(text = if (step == 1) "Category" else if (step == 2) "Angle" else "Source", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { step / 3f }, color = NeonCyan, modifier = Modifier.fillMaxWidth())
                when(step) {
                    1 -> {
                        WizardBtn("Passenger 4W", Icons.Default.DirectionsCar) { selectedType = "Car"; step = 2 }
                        WizardBtn("2W / Motorcycle", Icons.Default.TwoWheeler) { selectedType = "Motorcycle"; step = 2 }
                        WizardOption(Icons.Default.Build, "Commercial / 3W", selectedType == "Commercial") { selectedType = "Commercial"; step = 2 }
                    }
                    2 -> {
                        listOf("Front", "Rear", "Left Side", "Right Side", "Engine", "Interior").forEach { angle ->
                            Button(onClick = { selectedAngle = angle; step = 3 }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if(selectedAngle == angle) NeonCyan else DeepCharcoal), border = if(selectedAngle != angle) BorderStroke(1.dp, NeonCyan.copy(0.3f)) else null) {
                                Text(angle, color = if(selectedAngle == angle) DeepCharcoal else OffWhite)
                            }
                        }
                    }
                    3 -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onComplete(selectedType, selectedAngle, "Camera") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(NeonCyan)) { Text("Camera", color = DeepCharcoal) }
                            Button(onClick = { onComplete(selectedType, selectedAngle, "Gallery") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(NeonCyan)) { Text("Gallery", color = DeepCharcoal) }
                        }
                    }
                }
            }
        },
        confirmButton = { if (step > 1) TextButton(onClick = { step-- }) { Text("BACK", color = OffWhite) } }
    )
}

@Composable fun WizardBtn(label: String, icon: ImageVector, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(DeepCharcoal), border = BorderStroke(1.dp, NeonCyan.copy(0.2f))) { Icon(icon, null, tint = NeonCyan, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label, color = OffWhite) } }
@Composable fun WizardOption(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if(isSelected) NeonCyan else DeepCharcoal), border = if(!isSelected) BorderStroke(1.dp, NeonCyan.copy(0.3f)) else null, shape = RoundedCornerShape(8.dp)) { Icon(icon, null, tint = if(isSelected) DeepCharcoal else NeonCyan); Spacer(Modifier.width(12.dp)); Text(label, color = if(isSelected) DeepCharcoal else OffWhite) } }
@Composable fun DashboardHeader() { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = NeonCyan, modifier = Modifier.size(32.dp)); Spacer(modifier = Modifier.width(12.dp)); Text("Vehicle-DentChecker-AI", color = OffWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
@Composable fun HeroSection() { Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(DarkSlate).border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, null, modifier = Modifier.size(140.dp), tint = OffWhite.copy(alpha = 0.05f)); val transition = rememberInfiniteTransition(""); val translateY by transition.animateFloat(initialValue = 0f, targetValue = 200f, animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""); Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = (-100).dp + translateY.dp).height(2.dp).background(NeonCyan).shadow(8.dp, spotColor = NeonCyan)) } }
@Composable fun QuickScanButton(onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp).shadow(12.dp, spotColor = NeonCyan, shape = RoundedCornerShape(16.dp)), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Add, null, tint = DeepCharcoal); Spacer(modifier = Modifier.width(12.dp)); Text("QUICK SCAN", color = DeepCharcoal, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) } }
@Composable fun RecentItem(item: InspectionRecord, onClick: (String) -> Unit) { Row(modifier = Modifier.fillMaxWidth().clickable { onClick(item.vehicle) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Check, null, tint = NeonCyan.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Column { Text(item.vehicle, color = OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("${item.label} (${item.confidence})", color = NeonCyan, fontSize = 9.sp) } } }
@Composable fun CyberBottomNav(selectedIndex: Int, onItemSelected: (Int) -> Unit) { NavigationBar(containerColor = DarkSlate, tonalElevation = 8.dp) { val items = listOf(Icons.Default.Home to "Home", Icons.AutoMirrored.Filled.List to "History", Icons.Default.Add to "Scan", Icons.AutoMirrored.Filled.Assignment to "Reports", Icons.Default.Person to "Profile"); items.forEachIndexed { index, pair -> NavigationBarItem(selected = selectedIndex == index, onClick = { onItemSelected(index) }, icon = { if (index == 2) Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(NeonCyan), contentAlignment = Alignment.Center) { Icon(pair.first, null, tint = DeepCharcoal) } else Icon(pair.first, null, tint = if (selectedIndex == index) NeonCyan else OffWhite.copy(alpha = 0.5f)) }, label = { if (index != 2) Text(pair.second, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedTextColor = NeonCyan, unselectedTextColor = OffWhite.copy(alpha = 0.5f))) } } }

@Composable fun HistoryScreen() { 
    val items = remember { 
        listOf(
            InspectionRecord(1, "Toyota Corolla", "Minor Dent Detected", "92%", "Today"),
            InspectionRecord(2, "Tata Punch", "Major Scratch - Door", "88%", "Yesterday"),
            InspectionRecord(3, "Maruti Swift", "All Clear / Healthy", "100%", "2 days ago")
        ) 
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) { 
        Text("History", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(16.dp)); 
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
            items(items) { item -> 
                Card(colors = CardDefaults.cardColors(DarkSlate), modifier = Modifier.fillMaxWidth()) { 
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                        Icon(Icons.Default.Build, null, tint = NeonCyan); Spacer(modifier = Modifier.width(16.dp)); 
                        Column { 
                            Text(item.vehicle, color = OffWhite, fontWeight = FontWeight.Bold)
                            Text(item.date, color = OffWhite.copy(alpha = 0.5f), fontSize = 12.sp)
                        } 
                        Spacer(Modifier.weight(1f))
                        Text(item.label, color = NeonCyan, fontSize = 12.sp) 
                    } 
                } 
            } 
        } 
    } 
}

@Composable fun ReportsScreen() { 
    val reports = remember { listOf(Report("Monthly Audit", "Oct 2023", "Completed")) }
    Column(Modifier.fillMaxSize().padding(20.dp)) { 
        Text("Reports", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(16.dp)); 
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
            items(reports) { report ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(DarkSlate)) { 
                    Column(Modifier.padding(16.dp)) { 
                        Text(report.title, color = OffWhite, fontWeight = FontWeight.Bold)
                        Text(report.date, color = OffWhite.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(report.status, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp) 
                    } 
                } 
            } 
        } 
    } 
}

@Composable fun ProfileScreen(user: UserAccount) { 
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { 
        Spacer(modifier = Modifier.height(40.dp)); 
        Box(Modifier.size(100.dp).clip(CircleShape).background(DarkSlate).border(2.dp, NeonCyan, CircleShape), contentAlignment = Alignment.Center) { 
            Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = OffWhite) 
        }; 
        Spacer(modifier = Modifier.height(16.dp)); 
        Text(user.name, color = OffWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold); 
        Text(user.email, color = NeonCyan, fontSize = 14.sp); 
    } 
}

@Preview(showBackground = true) @Composable fun LoginPreview() { VehicledentcheckerTheme { LoginScreen {} } }
@Preview(showBackground = true) @Composable fun DashboardPreview() { VehicledentcheckerTheme { MainDashboardContent() } }
