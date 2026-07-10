package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.DrivingStat
import com.example.data.models.*
import com.example.ui.QorvehSimulationViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private val viewModel: QorvehSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QorvehSimulationApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QorvehSimulationApp(viewModel: QorvehSimulationViewModel) {
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel State bindings
    val cameraState by viewModel.camera.collectAsStateWithLifecycle()
    val posX by viewModel.posX.collectAsStateWithLifecycle()
    val posY by viewModel.posY.collectAsStateWithLifecycle()
    val carSpeed by viewModel.carSpeed.collectAsStateWithLifecycle()
    val carHeading by viewModel.carHeading.collectAsStateWithLifecycle()
    val steering by viewModel.steering.collectAsStateWithLifecycle()
    val activeVehicle by viewModel.activeVehicle.collectAsStateWithLifecycle()
    val timeOfDay by viewModel.timeOfDay.collectAsStateWithLifecycle()
    val isTimeFlowing by viewModel.isTimeFlowing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val filteredPlaces by viewModel.filteredPlaces.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val drivingStats by viewModel.drivingStats.collectAsStateWithLifecycle()
    val isAutopilotActive by viewModel.isAutopilotActive.collectAsStateWithLifecycle()
    val autopilotTarget by viewModel.autopilotTarget.collectAsStateWithLifecycle()
    val activePlaceReviews by viewModel.activePlaceReviews.collectAsStateWithLifecycle()

    // Screen Shake Animation triggered on collisions
    var isShaking by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        viewModel.screenShake.collect {
            isShaking = true
            // Play rapid vibration / shaking animation
            for (i in 0..5) {
                shakeOffset.animateTo(12f * (if (i % 2 == 0) 1f else -1f), spring(stiffness = Spring.StiffnessHigh))
            }
            shakeOffset.animateTo(0f)
            isShaking = false
        }
    }

    // UI State Holders
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isListDrawerOpen by remember { mutableStateOf(false) }
    var isReviewDialogOpen by remember { mutableStateOf(false) }
    var isTelemetryOpen by remember { mutableStateOf(false) }

    // Waving flag offset
    val transition = rememberInfiniteTransition(label = "flag_wave")
    val flagAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flag_wave"
    )

    // Twinkling stars seed coordinates (generated once)
    val starSeeds = remember {
        List(40) {
            Offset(Random.nextFloat(), Random.nextFloat() * 0.45f) // top half of sky
        }
    }

    // Trees collection (pre-generated relative positions)
    val trees = remember {
        val list = mutableListOf<Tree3D>()
        // Trees along the Seyyed Jamaleddin Blvd central green median:
        for (y in -450..450 step 25) {
            if (abs(y) > 40) { // Skip Imam Square roundabout
                list.add(Tree3D(0f, y.toFloat(), size = 3.5f))
            }
        }
        // Trees in Mellat Park (x in -290..-230, y in 230..290)
        for (i in 0 until 18) {
            val tx = -230f - (i * 7f % 55f)
            val ty = 230f + (i * 9f % 55f)
            list.add(Tree3D(tx, ty, size = 3f + (i % 3) * 0.4f))
        }
        // Trees around Sarab Lake (around radius 110-125m)
        for (i in 0 until 24) {
            val angle = (i * 2 * PI / 24).toFloat()
            val tr = 115f + (i % 2) * 6f
            list.add(Tree3D(sin(angle) * tr, -370f + cos(angle) * tr, size = 3.2f + (i % 2) * 0.6f))
        }
        list
    }

    // Main Scaffold handling Full Edge-to-Edge Draw
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = shakeOffset.value.dp)
        ) {
            // ==========================================
            // LAYER 1: The 3D Rendering Canvas Engine
            // ==========================================
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cameraState.mode) {
                        if (cameraState.mode == CameraMode.DRONE) {
                            // Drag on screen to orbit camera in 3D
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.rotateCamera(
                                    yawDelta = -dragAmount.x * 0.005f,
                                    pitchDelta = -dragAmount.y * 0.003f
                                )
                            }
                        }
                    }
            ) {
                val drawContext = ProjectionContext(size.width, size.height, cameraState)

                // 1. Draw Sky background gradient matching Time of Day
                drawSkyAndStars(this, timeOfDay, starSeeds, flagAngle)

                // 2. Draw Mountains silhouette (Parallax Effect)
                drawScenicMountains(this, drawContext, timeOfDay)

                // 3. Draw Ground plane grid / Landscape base
                drawGrassGroundAndFringe(this, drawContext, timeOfDay)

                // 4. Draw Qorveh Street layouts and intersections
                drawQorvehStreetGrid(this, drawContext, timeOfDay)

                // 5. Draw Sarab Qorveh Lake, Island and Bridge
                drawSarabLake3D(this, drawContext, timeOfDay)

                // 6. Gather and depth-sort all 3D obstacles (Buildings, Trees) using Painter's Algorithm
                val drawableObstacles = mutableListOf<Drawable3D>()
                
                // Add Buildings
                QorvehMapData.places.forEach { place ->
                    place.depth = drawContext.getDepth(place.center)
                    if (place.depth > 0.2f) {
                        drawableObstacles.add(place)
                    }
                }
                
                // Add Trees
                trees.forEach { tree ->
                    tree.depth = drawContext.getDepth(tree.center)
                    if (tree.depth > 0.2f) {
                        drawableObstacles.add(tree)
                    }
                }

                // Sort back to front (highest depth first)
                drawableObstacles.sortByDescending { it.depth }

                // Draw each sorted 3D object
                val pinPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(8f, 0f, 2f, android.graphics.Color.BLACK)
                }

                drawableObstacles.forEach { obstacle ->
                    obstacle.draw(
                        drawContext = drawContext,
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        timeOfDay = timeOfDay,
                        onDraw = { path, fillCol, outlineCol, strokeW ->
                            drawPath(path, fillCol)
                            if (outlineCol != null) {
                                drawPath(path, outlineCol, style = Stroke(width = strokeW))
                            }
                        },
                        onDrawLine = { start, end, col, stroke ->
                            drawLine(col, start, end, strokeWidth = stroke)
                        },
                        onDrawPoint = { pos, rad, col ->
                            drawCircle(col, radius = rad, center = pos)
                        },
                        onDrawText = { text, pos, col, isPOI ->
                            // Custom paint text drawing
                            drawIntoCanvas { canvas ->
                                pinPaint.color = col.toArgb()
                                canvas.nativeCanvas.drawText(text, pos.x, pos.y, pinPaint)
                            }
                        }
                    )
                }

                // 7. Draw Imam Khomeini Square Central Flag and Fountain
                drawSquareDetails3D(this, drawContext, timeOfDay, flagAngle)
            }

            // ==========================================
            // LAYER 2: Floating Header search bar & categories
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Search Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open List Drawer Button
                    FloatingActionButton(
                        onClick = { isListDrawerOpen = true },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("directory_button"),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.List, contentDescription = "فهرست مراکز")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Main Search text field
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)),
                        placeholder = { Text("جستجوی مغازه، مسجد، پارک در قروه...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Open Telemetry / Stats Button
                    FloatingActionButton(
                        onClick = { isTelemetryOpen = true },
                        modifier = Modifier.size(52.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Insights, contentDescription = "آمار رانندگی")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable categories list
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.setCategoryFilter(null) },
                            label = { Text("همه مراکز") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        )
                    }
                    items(PlaceCategory.values()) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setCategoryFilter(cat) },
                            label = { Text(cat.title) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(cat.color)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }

            // ==========================================
            // LAYER 3: Interactive Head Up Display (HUD) overlays
            // ==========================================
            
            // 3A. Autopilot Floating Banner
            if (isAutopilotActive && autopilotTarget != null) {
                Card(
                    modifier = Modifier
                        .padding(top = 165.dp)
                        .align(Alignment.TopCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "خلبان خودکار به سمت: ${autopilotTarget?.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        TextButton(
                            onClick = { viewModel.stopAutopilot() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("لغو", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3B. Camera & Time controls floating list (Top Left)
            Column(
                modifier = Modifier
                    .padding(top = 170.dp, start = 16.dp)
                    .align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera Mode Toggle
                FloatingActionButton(
                    onClick = {
                        val nextMode = if (cameraState.mode == CameraMode.DRIVE) CameraMode.DRONE else CameraMode.DRIVE
                        viewModel.setCameraMode(nextMode)
                    },
                    modifier = Modifier.size(46.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (cameraState.mode == CameraMode.DRIVE) Icons.Filled.Flight else Icons.Filled.DirectionsCar,
                        contentDescription = "تغییر زاویه دوربین",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Time Pause/Play Toggle
                FloatingActionButton(
                    onClick = { viewModel.toggleTimeFlow() },
                    modifier = Modifier.size(46.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isTimeFlowing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "تغییر زمان",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Settings Drawer Button
                FloatingActionButton(
                    onClick = { isSettingsOpen = true },
                    modifier = Modifier.size(46.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "تنظیمات خودرو",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3C. MiniMap & Position Compass (Top Right)
            Card(
                modifier = Modifier
                    .padding(top = 170.dp, end = 16.dp)
                    .size(105.dp)
                    .align(Alignment.TopEnd)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // MiniMap Render inside Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawMiniMap(this, posX, posY, carHeading, selectedPlace)
                    }
                    
                    // Simple Compass indicator overlay
                    Text(
                        text = when {
                            carHeading in -0.5f..0.5f -> "شمال"
                            carHeading in 1.0f..2.0f -> "شرق"
                            carHeading in -2.0f..-1.0f -> "غرب"
                            else -> "جنوب"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                    )
                }
            }

            // ==========================================
            // LAYER 4: Driving Dashboard controls (Bottom Overlay)
            // ==========================================
            if (cameraState.mode == CameraMode.DRIVE) {
                // Interactive Dashboard Overlay representing Saipa Pride / Samand style dashboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color(0xFF15181C)),
                                startY = 0f,
                                endY = 240f
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 1. Steering Wheel HUD component (Left side)
                        Box(
                            modifier = Modifier
                                .size(135.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            viewModel.isInputLeft = false
                                            viewModel.isInputRight = false
                                        },
                                        onDragCancel = {
                                            viewModel.isInputLeft = false
                                            viewModel.isInputRight = false
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount.x < -3f) {
                                            viewModel.isInputLeft = true
                                            viewModel.isInputRight = false
                                        } else if (dragAmount.x > 3f) {
                                            viewModel.isInputLeft = false
                                            viewModel.isInputRight = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Dial background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            )
                            
                            // Visual Rotating Steering Wheel
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground), // fallback visual
                                contentDescription = "فرمان خودرو",
                                modifier = Modifier
                                    .size(115.dp)
                                    .rotate(steering * 90f) // visually rotate steering wheel up to 90 degrees!
                                    .drawBehind {
                                        // Draw a premium custom steering wheel vector directly on top
                                        drawCircle(Color(0xFF2E3238), radius = size.width / 2.1f)
                                        drawCircle(Color(0xFF1E2125), radius = size.width / 2.7f)
                                        
                                        // Three Spokes
                                        val spokeW = size.width * 0.1f
                                        drawLine(Color(0xFF454B54), center, Offset(center.x, size.height), strokeWidth = spokeW)
                                        drawLine(Color(0xFF454B54), center, Offset(0f, center.y + size.height * 0.1f), strokeWidth = spokeW)
                                        drawLine(Color(0xFF454B54), center, Offset(size.width, center.y + size.height * 0.1f), strokeWidth = spokeW)
                                        
                                        // Center hub representation
                                        drawCircle(Color(0xFF1E2125), radius = size.width * 0.2f)
                                        drawCircle(Color(0xFF454B54), radius = size.width * 0.13f, style = Stroke(width = 4f))
                                    }
                            )

                            // Quick Left/Right Arrow touch zones inside the wheel to make steering dead-simple!
                            Row(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.isInputLeft = true
                                                    try { awaitRelease() } finally { viewModel.isInputLeft = false }
                                                }
                                            )
                                        }
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = "چپ", tint = Color.White)
                                }

                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.isInputRight = true
                                                    try { awaitRelease() } finally { viewModel.isInputRight = false }
                                                }
                                            )
                                        }
                                ) {
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "راست", tint = Color.White)
                                }
                            }
                        }

                        // 2. Circular Speedometer and Rev gauge (Center)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(130.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .drawBehind {
                                        // Draw beautiful circular dashboard speed dial
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.15f),
                                            startAngle = 135f,
                                            sweepAngle = 270f,
                                            useCenter = false,
                                            style = Stroke(width = 15f, cap = StrokeCap.Round)
                                        )
                                        // Speed Arc fill
                                        val maxSpeed = activeVehicle.maxSpeed
                                        val speedKmh = abs(carSpeed * 3.6f)
                                        val sweep = (speedKmh / maxSpeed) * 270f
                                        drawArc(
                                            color = activeVehicle.color,
                                            startAngle = 135f,
                                            sweepAngle = sweep.coerceAtMost(270f),
                                            useCenter = false,
                                            style = Stroke(width = 15f, cap = StrokeCap.Round)
                                        )
                                        
                                        // Speed needle
                                        val needleAngleRad = ((135f + sweep) * PI / 180f).toFloat()
                                        val length = size.width * 0.4f
                                        drawLine(
                                            color = Color.Red,
                                            start = center,
                                            end = Offset(center.x + cos(needleAngleRad) * length, center.y + sin(needleAngleRad) * length),
                                            strokeWidth = 5f
                                        )
                                        drawCircle(Color.White, radius = 12f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(abs(carSpeed * 3.6f)).roundToInt()}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "کیلومتر/ساعت",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "خودرو: ${activeVehicle.persianName}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // 3. Drive Pedals: Gas and Brake/Reverse (Right side)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // BRAKE Pedal (Left of gas)
                            Card(
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(90.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.isInputBrake = true
                                                try { awaitRelease() } finally { viewModel.isInputBrake = false }
                                            }
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (viewModel.isInputBrake) Color.Red.copy(alpha = 0.85f) else Color(0xFF37474F)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ترمز\nدنده عقب",
                                        textAlign = TextAlign.Center,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // GAS Pedal (Far right)
                            Card(
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(110.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.isInputGas = true
                                                try { awaitRelease() } finally { viewModel.isInputGas = false }
                                            }
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (viewModel.isInputGas) Color(0xFF4CAF50).copy(alpha = 0.85f) else Color(0xFF263238)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "گاز\nشتاب",
                                        textAlign = TextAlign.Center,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // DRONE mode simple panning instruction prompt
                Card(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Transparent),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "زاویه دوربین آزاد: با کشیدن انگشت دوربین را بچرخانید.\nبرای جابه‌جایی سریع، روی آیکون‌های نقشه ضربه بزنید.",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ==========================================
            // LAYER 5: Detailed Selected Place Bottom Sheet
            // ==========================================
            if (selectedPlace != null) {
                val place = selectedPlace!!
                val isSaved = savedPlaces.any { it.placeId == place.id }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (cameraState.mode == CameraMode.DRIVE) 220.dp else 16.dp, start = 16.dp, end = 16.dp)
                        .testTag("detail_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header with close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(place.category.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = place.category.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.selectPlace(null) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "بستن")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Title & Rating
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = place.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(Icons.Filled.Star, contentDescription = "امتیاز", tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${place.rating} (${place.reviewCount})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Hero Banner Image (using our generated JPG)
                        Image(
                            painter = painterResource(id = R.drawable.img_qorveh_hero),
                            contentDescription = place.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Description
                        Text(
                            text = place.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Address, working hours, and contact
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, contentDescription = "آدرس", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(place.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccessTime, contentDescription = "ساعت کاری", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ساعت کاری: ${place.workingHours}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, contentDescription = "تلفن تماس", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تلفن تماس: ${place.phone}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive action row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Navigation Autopilot Button
                            Button(
                                onClick = { viewModel.triggerAutopilot(place) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Navigation, contentDescription = "مسیریابی", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("خلبان خودکار", fontSize = 12.sp)
                            }

                            // Warp Teleport Button
                            Button(
                                onClick = { viewModel.teleportTo(place) },
                                modifier = Modifier.weight(0.9f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = "تله‌پورت", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تله‌پورت", fontSize = 12.sp)
                            }

                            // Favorite Button
                            OutlinedButton(
                                onClick = { viewModel.toggleSavedPlace(place.id) },
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "نشان کردن",
                                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }

                            // Custom reviews button
                            OutlinedButton(
                                onClick = { isReviewDialogOpen = true },
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.RateReview, contentDescription = "ثبت دیدگاه", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Local Reviews List (Room data)
                        if (activePlaceReviews.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("نظرات محلی ثبت شده (${activePlaceReviews.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Box(modifier = Modifier.heightIn(max = 80.dp)) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(activePlaceReviews) { review ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(review.authorName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Row {
                                                        repeat(review.rating) {
                                                            Icon(Icons.Filled.Star, contentDescription = "*", tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                                                        }
                                                    }
                                                }
                                                Text(review.reviewText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY: Directory & Landmarks List Drawer
    // ==========================================
    if (isListDrawerOpen) {
        Dialog(onDismissRequest = { isListDrawerOpen = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نقشه و اصناف ثبت شده قروه",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { isListDrawerOpen = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Bastan")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "برای نمایش جزئیات و هدایت خودکار به اصناف کلیک کنید.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Divider()

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPlaces) { place ->
                            val isBookmarked = savedPlaces.any { it.placeId == place.id }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectPlace(place)
                                        isListDrawerOpen = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left category colored square
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(place.category.color)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(place.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            if (isBookmarked) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Filled.Bookmark, contentDescription = "نشان شده", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                        Text(place.description, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = "*", tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                                        Text("${place.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (filteredPlaces.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("موردی با این مشخصات یافت نشد.", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY: Write Custom Reviews Dialog (Room)
    // ==========================================
    if (isReviewDialogOpen && selectedPlace != null) {
        val place = selectedPlace!!
        var rating by remember { mutableStateOf(5) }
        var reviewText by remember { mutableStateOf("") }
        var reviewerName by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { isReviewDialogOpen = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "ثبت دیدگاه در گوگل مپ قروه",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("برای مرکز: ${place.name}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Stars rating selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (i in 1..5) {
                            IconButton(
                                onClick = { rating = i },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "*",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reviewer Name input
                    OutlinedTextField(
                        value = reviewerName,
                        onValueChange = { reviewerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام شما") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Review Text input
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        label = { Text("متن دیدگاه شما") },
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isReviewDialogOpen = false }) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (reviewerName.isNotBlank() && reviewText.isNotBlank()) {
                                    viewModel.submitUserReview(place.id, rating, reviewText, reviewerName)
                                    isReviewDialogOpen = false
                                }
                            }
                        ) {
                            Text("ثبت دیدگاه")
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY: Vehicle Settings & Time Slider
    // ==========================================
    if (isSettingsOpen) {
        Dialog(onDismissRequest = { isSettingsOpen = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "تنظیمات شبیه‌ساز قروه",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Vehicles Selection Row
                    Text("انتخاب خودرو سواری:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    VehicleList.vehicles.forEach { veh ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.selectVehicle(veh)
                                },
                            border = BorderStroke(
                                width = if (activeVehicle.name == veh.name) 2.dp else 0.dp,
                                color = if (activeVehicle.name == veh.name) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (activeVehicle.name == veh.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(veh.persianName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("حداکثر سرعت: ${veh.maxSpeed} کیلومتر/ساعت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(veh.color)
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Time of Day slide controller
                    Text("تغییر ساعت شبانه‌روز: (${timeOfDay.roundToInt()}:00)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = timeOfDay,
                        onValueChange = { viewModel.setTimeOfDay(it) },
                        valueRange = 0f..24f
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { isSettingsOpen = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("تایید و بازگشت")
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY: Driving Telemetry Statistics Card (Room)
    // ==========================================
    if (isTelemetryOpen) {
        val stats = drivingStats ?: DrivingStat()
        Dialog(onDismissRequest = { isTelemetryOpen = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "آمار رانندگی در قروه (ثبت‌شده محلی)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("مسافت طی شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${(stats.totalDistanceMeters).roundToInt()} متر", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        VerticalDivider(modifier = Modifier.height(40.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("برخورد با جدول/موانع", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${stats.collisionCount} بار", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (stats.collisionCount > 0) Color.Red else Color.Green)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "دریاچه سراب، بوستان ملت و بازار سنتی قروه منتظر رانندگی شما هستند!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { isTelemetryOpen = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("بستن آمار")
                    }
                }
            }
        }
    }
}

// 3D sky rendering with Sun, Moon, and Twinkling Stars
private fun drawSkyAndStars(
    scope: DrawScope,
    timeOfDay: Float,
    stars: List<Offset>,
    wavePhase: Float
) {
    val h = scope.size.height
    val w = scope.size.width

    // Get sky skybox colors matching Time of Day
    val skyColors = when {
        timeOfDay in 6.0f..17.0f -> {
            // daytime transitions
            if (timeOfDay < 9.0f) {
                // sunrise
                listOf(Color(0xFF673AB7), Color(0xFFFF5722), Color(0xFFFFC107))
            } else if (timeOfDay > 15.0f) {
                // sunset
                listOf(Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFFFF9800))
            } else {
                // midday sky
                listOf(Color(0xFF1E88E5), Color(0xFF4FC3F7), Color(0xFFE0F7FA))
            }
        }
        else -> {
            // night sky
            listOf(Color(0xFF030A16), Color(0xFF091428), Color(0xFF112240))
        }
    }

    // Draw background linear gradient sky box
    scope.drawRect(
        brush = Brush.verticalGradient(
            colors = skyColors,
            startY = 0f,
            endY = h * 0.55f
        ),
        size = Size(w, h * 0.55f)
    )

    // Draw twinkling stars if night time
    val isNight = timeOfDay < 5.5f || timeOfDay > 18.5f
    if (isNight) {
        val starAlpha = if (timeOfDay < 4f || timeOfDay > 20f) 1.0f else 0.4f
        stars.forEachIndexed { idx, seed ->
            // twinkle effect
            val twinkleFactor = sin(wavePhase + idx) * 0.4f + 0.6f
            scope.drawCircle(
                color = Color.White.copy(alpha = starAlpha * twinkleFactor),
                radius = (4f + idx % 6) * twinkleFactor,
                center = Offset(seed.x * w, seed.y * h * 0.55f)
            )
        }
    }

    // Draw Sun or Moon in 3D-projected sky space
    val theta = ((timeOfDay - 6f) / 12f * PI).toFloat()
    val skyHeightRatio = sin(theta)

    if (timeOfDay in 5.8f..18.2f) {
        // Draw Sun
        val sunX = w * 0.2f + (timeOfDay - 6f) / 12f * (w * 0.6f)
        val sunY = h * 0.35f - skyHeightRatio * (h * 0.22f)
        scope.drawCircle(
            color = Color(0xFFFFD54F),
            radius = 70f,
            center = Offset(sunX, sunY)
        )
        scope.drawCircle(
            color = Color(0xFFFFF9C4).copy(alpha = 0.4f),
            radius = 95f,
            center = Offset(sunX, sunY)
        )
    } else {
        // Draw Moon
        val adjustedTime = if (timeOfDay > 18.2f) timeOfDay - 18.2f else timeOfDay + 5.8f
        val moonX = w * 0.15f + (adjustedTime / 12f) * (w * 0.7f)
        val moonY = h * 0.4f - sin((adjustedTime / 12f) * PI).toFloat() * (h * 0.22f)
        
        scope.drawCircle(
            color = Color(0xFFECEFF1),
            radius = 40f,
            center = Offset(moonX, moonY)
        )
        // draw shadow to make crescent
        scope.drawCircle(
            color = skyColors[0],
            radius = 38f,
            center = Offset(moonX - 15f, moonY - 8f)
        )
    }
}

// Draw Mountains (Badre & Parishan in the South: Y = -700)
private fun drawScenicMountains(
    scope: DrawScope,
    proj: ProjectionContext,
    timeOfDay: Float
) {
    // We position the peaks far south
    val peaks = listOf(
        Triple(-280f, -800f, 240f), // Parishan Mountain
        Triple(0f, -850f, 290f),    // Badre Mountain
        Triple(250f, -800f, 210f)   // South-East Ridge
    )

    peaks.forEach { (px, py, pz) ->
        // project peak and base corners
        val peakPos = Vector3(px, py, pz)
        val baseL = Vector3(px - 380f, py, 0f)
        val baseR = Vector3(px + 380f, py, 0f)

        val peakOffset = proj.project(peakPos) ?: return@forEach
        val lOffset = proj.project(baseL) ?: return@forEach
        val rOffset = proj.project(baseR) ?: return@forEach

        val path = Path().apply {
            moveTo(lOffset.x, lOffset.y)
            lineTo(peakOffset.x, peakOffset.y)
            lineTo(rOffset.x, rOffset.y)
            close()
        }

        // Mountain color
        val baseColor = Color(0xFF303E4F)
        val litColor = adjustColorForTime(baseColor, timeOfDay)
        
        scope.drawPath(path, litColor)

        // Draw snowy peaks if high enough
        if (pz > 220f) {
            val snowP = proj.project(Vector3(px, py, pz))
            val snowL = proj.project(Vector3(px - 60f, py, pz - 45f))
            val snowR = proj.project(Vector3(px + 60f, py, pz - 45f))

            if (snowP != null && snowL != null && snowR != null) {
                val snowPath = Path().apply {
                    moveTo(snowL.x, snowL.y)
                    lineTo(snowP.x, snowP.y)
                    lineTo(snowR.x, snowR.y)
                    close()
                }
                scope.drawPath(snowPath, Color.White.copy(alpha = if (timeOfDay > 18.5f) 0.35f else 0.85f))
            }
        }
    }
}

// Ground and Fringe
private fun drawGrassGroundAndFringe(
    scope: DrawScope,
    proj: ProjectionContext,
    timeOfDay: Float
) {
    val h = scope.size.height
    val w = scope.size.width

    // Grass color
    val grassBase = Color(0xFF4CAF50)
    val grassLit = adjustColorForTime(grassBase, timeOfDay)

    // Sidewalk base / Horizon plane is drawn from bottom to the horizon center
    // Project ground corners of the map bounds [-500, -500] to [500, 500]
    val c0 = proj.project(Vector3(-500f, -500f, 0f))
    val c1 = proj.project(Vector3(500f, -500f, 0f))
    val c2 = proj.project(Vector3(500f, 500f, 0f))
    val c3 = proj.project(Vector3(-500f, 500f, 0f))

    val path = Path().apply {
        if (c0 != null && c1 != null && c2 != null && c3 != null) {
            moveTo(c0.x, c0.y)
            lineTo(c1.x, c1.y)
            lineTo(c2.x, c2.y)
            lineTo(c3.x, c3.y)
            close()
        } else {
            // Draw a flat quad from horizon to bottom of screen if camera goes out
            moveTo(0f, h * 0.45f)
            lineTo(w, h * 0.45f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    }

    scope.drawPath(path, grassLit)
}

// Draw Streets in 3D
private fun drawQorvehStreetGrid(
    scope: DrawScope,
    proj: ProjectionContext,
    timeOfDay: Float
) {
    val roadColor = adjustColorForTime(Color(0xFF37474F), timeOfDay)
    val laneColor = adjustColorForTime(Color(0xFFFFD54F), timeOfDay)

    // 1. Seyyed Jamaleddin Blvd (North-South): x runs from -14 to 14
    val blvdL = proj.project(Vector3(-14f, -480f, 0f))
    val blvdR = proj.project(Vector3(14f, -480f, 0f))
    val blvdRT = proj.project(Vector3(14f, 480f, 0f))
    val blvdLT = proj.project(Vector3(-14f, 480f, 0f))

    if (blvdL != null && blvdR != null && blvdRT != null && blvdLT != null) {
        val path = Path().apply {
            moveTo(blvdL.x, blvdL.y)
            lineTo(blvdR.x, blvdR.y)
            lineTo(blvdRT.x, blvdRT.y)
            lineTo(blvdLT.x, blvdLT.y)
            close()
        }
        scope.drawPath(path, roadColor)
    }

    // 2. Shariati Street (East-West): y runs from -12 to 12
    val shariatiL = proj.project(Vector3(-480f, -12f, 0f))
    val shariatiR = proj.project(Vector3(480f, -12f, 0f))
    val shariatiRT = proj.project(Vector3(480f, 12f, 0f))
    val shariatiLT = proj.project(Vector3(-480f, 12f, 0f))

    if (shariatiL != null && shariatiR != null && shariatiRT != null && shariatiLT != null) {
        val path = Path().apply {
            moveTo(shariatiL.x, shariatiL.y)
            lineTo(shariatiR.x, shariatiR.y)
            lineTo(shariatiRT.x, shariatiRT.y)
            lineTo(shariatiLT.x, shariatiLT.y)
            close()
        }
        scope.drawPath(path, roadColor)
    }

    // 3. Taleghani Street (East-West at y=220): width 16m
    val talL = proj.project(Vector3(-480f, 212f, 0f))
    val talR = proj.project(Vector3(480f, 212f, 0f))
    val talRT = proj.project(Vector3(480f, 228f, 0f))
    val talLT = proj.project(Vector3(-480f, 228f, 0f))

    if (talL != null && talR != null && talRT != null && talLT != null) {
        val path = Path().apply {
            moveTo(talL.x, talL.y)
            lineTo(talR.x, talR.y)
            lineTo(talRT.x, talRT.y)
            lineTo(talLT.x, talLT.y)
            close()
        }
        scope.drawPath(path, roadColor)
    }

    // 4. Aboozar Street (North-South at x=-220): width 16m
    val abL = proj.project(Vector3(-228f, -480f, 0f))
    val abR = proj.project(Vector3(-212f, -480f, 0f))
    val abRT = proj.project(Vector3(-212f, 480f, 0f))
    val abLT = proj.project(Vector3(-228f, 480f, 0f))

    if (abL != null && abR != null && abRT != null && abLT != null) {
        val path = Path().apply {
            moveTo(abL.x, abL.y)
            lineTo(abR.x, abR.y)
            lineTo(abRT.x, abRT.y)
            lineTo(abLT.x, abLT.y)
            close()
        }
        scope.drawPath(path, roadColor)
    }

    // 5. Center Lane Markers
    // Seyyed Jamaleddin Blvd median boulevard (runs from y=-450 to y=450, x is [-2, 2])
    val medianL = proj.project(Vector3(-2f, -450f, 0.1f))
    val medianR = proj.project(Vector3(2f, -450f, 0.1f))
    val medianRT = proj.project(Vector3(2f, 450f, 0.1f))
    val medianLT = proj.project(Vector3(-2f, 450f, 0.1f))

    if (medianL != null && medianR != null && medianRT != null && medianLT != null) {
        val path = Path().apply {
            moveTo(medianL.x, medianL.y)
            lineTo(medianR.x, medianR.y)
            lineTo(medianRT.x, medianRT.y)
            lineTo(medianLT.x, medianLT.y)
            close()
        }
        // green median garden strip in the center of Seyyed Jamaleddin Blvd!
        scope.drawPath(path, adjustColorForTime(Color(0xFF2E7D32), timeOfDay))
    }
}

// 3D lake rendering of Sarab Lake
private fun drawSarabLake3D(
    scope: DrawScope,
    proj: ProjectionContext,
    timeOfDay: Float
) {
    val lakeCenterY = -370f
    val lakeRadius = 110f

    // Construct 3D lake points
    val lakePoints = mutableListOf<Vector3>()
    for (i in 0 until 18) {
        val angle = (i * 2 * PI / 18).toFloat()
        lakePoints.add(Vector3(sin(angle) * lakeRadius, lakeCenterY + cos(angle) * lakeRadius, 0.05f))
    }

    val offsets = lakePoints.mapNotNull { proj.project(it) }
    if (offsets.size >= 3) {
        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            for (i in 1 until offsets.size) {
                lineTo(offsets[i].x, offsets[i].y)
            }
            close()
        }
        scope.drawPath(path, adjustColorForTime(Color(0xFF0288D1), timeOfDay))
    }

    // Islet in center
    val islandRadius = 25f
    val islandPoints = mutableListOf<Vector3>()
    for (i in 0 until 12) {
        val angle = (i * 2 * PI / 12).toFloat()
        islandPoints.add(Vector3(sin(angle) * islandRadius, lakeCenterY + cos(angle) * islandRadius, 0.1f))
    }

    val islandOffsets = islandPoints.mapNotNull { proj.project(it) }
    if (islandOffsets.size >= 3) {
        val path = Path().apply {
            moveTo(islandOffsets[0].x, islandOffsets[0].y)
            for (i in 1 until islandOffsets.size) {
                lineTo(islandOffsets[i].x, islandOffsets[i].y)
            }
            close()
        }
        scope.drawPath(path, adjustColorForTime(Color(0xFF689F38), timeOfDay))
    }

    // Bridge from shore (0, -260) to the island (0, -345)
    val bridgeStart = Vector3(-3f, -260f, 0.3f)
    val bridgeEnd = Vector3(3f, -260f, 0.3f)
    val bridgeEndIsland = Vector3(3f, -345f, 0.3f)
    val bridgeStartIsland = Vector3(-3f, -345f, 0.3f)

    val bs = proj.project(bridgeStart)
    val be = proj.project(bridgeEnd)
    val bei = proj.project(bridgeEndIsland)
    val bsi = proj.project(bridgeStartIsland)

    if (bs != null && be != null && bei != null && bsi != null) {
        val bridgePath = Path().apply {
            moveTo(bs.x, bs.y)
            lineTo(be.x, be.y)
            lineTo(bei.x, bei.y)
            lineTo(bsi.x, bsi.y)
            close()
        }
        scope.drawPath(bridgePath, adjustColorForTime(Color(0xFF5D4037), timeOfDay))
    }
}

// Imam Khomeini Square Roundabout details
private fun drawSquareDetails3D(
    scope: DrawScope,
    proj: ProjectionContext,
    timeOfDay: Float,
    flagAngle: Float
) {
    // 1. Central Circle (Grass area)
    val points = mutableListOf<Vector3>()
    for (i in 0 until 16) {
        val angle = (i * 2 * PI / 16).toFloat()
        points.add(Vector3(sin(angle) * 35f, cos(angle) * 35f, 0.05f))
    }

    val offsets = points.mapNotNull { proj.project(it) }
    if (offsets.size >= 3) {
        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            for (i in 1 until offsets.size) {
                lineTo(offsets[i].x, offsets[i].y)
            }
            close()
        }
        scope.drawPath(path, adjustColorForTime(Color(0xFF2E7D32), timeOfDay))
    }

    // 2. Central Flagpole and Flag of Iran
    val poleBottom = Vector3(0f, 0f, 0.1f)
    val poleTop = Vector3(0f, 0f, 16f) // 16m high flagpole

    val pbOffset = proj.project(poleBottom)
    val ptOffset = proj.project(poleTop)

    if (pbOffset != null && ptOffset != null) {
        scope.drawLine(
            color = adjustColorForTime(Color(0xFFCFD8DC), timeOfDay),
            start = pbOffset,
            end = ptOffset,
            strokeWidth = (4f / pbOffset.y * 300f).coerceIn(1f, 8f)
        )

        // Draw waving flag of Iran
        val flagH = 3.5f
        val flagW = 5f
        // wave calculations
        val waveOffsetX = sin(flagAngle) * 0.6f

        val fTopL = Vector3(0f, 0f, 15.5f)
        val fTopR = Vector3(flagW, waveOffsetX, 15.5f)
        val fBotR = Vector3(flagW, waveOffsetX, 15.5f - flagH)
        val fBotL = Vector3(0f, 0f, 15.5f - flagH)

        val ftl = proj.project(fTopL)
        val ftr = proj.project(fTopR)
        val fbr = proj.project(fBotR)
        val fbl = proj.project(fBotL)

        if (ftl != null && ftr != null && fbr != null && fbl != null) {
            // Draw three horizontal stripes: Green, White, Red
            // Stripe 1: Green
            val stripeHeightY = (fbl.y - ftl.y) / 3f
            
            // Draw complete flag backing
            val flagPath = Path().apply {
                moveTo(ftl.x, ftl.y)
                lineTo(ftr.x, ftr.y)
                lineTo(fbr.x, fbr.y)
                lineTo(fbl.x, fbl.y)
                close()
            }
            scope.drawPath(flagPath, Color.White)

            // green stripe top
            val greenPath = Path().apply {
                moveTo(ftl.x, ftl.y)
                lineTo(ftr.x, ftr.y)
                lineTo(ftr.x + (fbr.x - ftr.x)/3f, ftr.y + (fbr.y - ftr.y)/3f)
                lineTo(ftl.x + (fbl.x - ftl.x)/3f, ftl.y + (fbl.y - ftl.y)/3f)
                close()
            }
            scope.drawPath(greenPath, Color(0xFF4CAF50))

            // red stripe bottom
            val redPath = Path().apply {
                moveTo(fbl.x - (fbl.x - ftl.x)/3f, fbl.y - (fbl.y - ftl.y)/3f)
                lineTo(fbr.x - (fbr.x - ftr.x)/3f, fbr.y - (fbr.y - ftr.y)/3f)
                lineTo(fbr.x, fbr.y)
                lineTo(fbl.x, fbl.y)
                close()
            }
            scope.drawPath(redPath, Color(0xFFF44336))
        }
    }

    // 3. Central Fountain Arches
    for (i in 0 until 6) {
        val angle = (i * 2 * PI / 6).toFloat()
        // fountain bezier curve coordinates
        val fStart = Vector3(0f, 0f, 0.5f)
        val fMid = Vector3(sin(angle) * 7f, cos(angle) * 7f, 6f)
        val fEnd = Vector3(sin(angle) * 12f, cos(angle) * 12f, 0.2f)

        val fs = proj.project(fStart)
        val fm = proj.project(fMid)
        val fe = proj.project(fEnd)

        if (fs != null && fm != null && fe != null) {
            val fPath = Path().apply {
                moveTo(fs.x, fs.y)
                quadraticTo(fm.x, fm.y, fe.x, fe.y)
            }
            scope.drawPath(fPath, Color(0xFF80DEEA).copy(alpha = 0.8f), style = Stroke(width = 3f))
        }
    }
}

// Draw MiniMap Overlay
private fun drawMiniMap(
    scope: DrawScope,
    playerX: Float,
    playerY: Float,
    heading: Float,
    selectedPlace: Building3D?
) {
    val size = scope.size.width
    val center = scope.center
    val radius = size / 2f
    
    // Draw background
    scope.drawCircle(Color.Black.copy(alpha = 0.65f), radius = radius)
    scope.drawCircle(Color.White.copy(alpha = 0.2f), radius = radius, style = Stroke(width = 3f))

    // Calculate map scale: map bounds are 500m, map size is radius (e.g., 50dp)
    // Scale: 1m = (radius / 200m) - zoom in so we see details around player
    val zoomRadius = 160f // radius represents 160 meters
    val scale = radius / zoomRadius

    // Draw grid of streets relative to player
    // A point (x, y) relative to player is (dx = x - playerX, dy = y - playerY)
    // Convert to top-down pixel offset: screenX = centerX + dx * scale, screenY = centerY - dy * scale (flip Y)
    fun worldToMapOffset(wx: Float, wy: Float): Offset {
        val dx = wx - playerX
        val dy = wy - playerY
        return Offset(center.x + dx * scale, center.y - dy * scale)
    }

    // 1. Draw lake
    val lakeCenterY = -370f
    val lakeRadius = 110f
    val lakeOffset = worldToMapOffset(0f, lakeCenterY)
    val mappedLakeRadius = lakeRadius * scale
    if (lakeOffset.x in -mappedLakeRadius..size + mappedLakeRadius && lakeOffset.y in -mappedLakeRadius..size + mappedLakeRadius) {
        scope.drawCircle(Color(0xFF29B6F6).copy(alpha = 0.5f), radius = mappedLakeRadius, center = lakeOffset)
    }

    // 2. Draw streets
    // Seyyed Jamaleddin Blvd (runs along x=0)
    val blvdX = worldToMapOffset(0f, 0f).x
    if (blvdX in 0f..size) {
        scope.drawLine(
            Color.White.copy(alpha = 0.45f),
            start = Offset(blvdX, 0f),
            end = Offset(blvdX, size),
            strokeWidth = 24f
        )
    }

    // Shariati Street (runs along y=0)
    val shariatiY = worldToMapOffset(0f, 0f).y
    if (shariatiY in 0f..size) {
        scope.drawLine(
            Color.White.copy(alpha = 0.45f),
            start = Offset(0f, shariatiY),
            end = Offset(size, shariatiY),
            strokeWidth = 20f
        )
    }

    // Taleghani Street (runs along y=220)
    val talY = worldToMapOffset(0f, 220f).y
    if (talY in 0f..size) {
        scope.drawLine(
            Color.White.copy(alpha = 0.3f),
            start = Offset(0f, talY),
            end = Offset(size, talY),
            strokeWidth = 12f
        )
    }

    // Aboozar Street (runs along x=-220)
    val abX = worldToMapOffset(-220f, 0f).x
    if (abX in 0f..size) {
        scope.drawLine(
            Color.White.copy(alpha = 0.3f),
            start = Offset(abX, 0f),
            end = Offset(abX, size),
            strokeWidth = 12f
        )
    }

    // 3. Draw POI Pins on Map
    QorvehMapData.places.forEach { place ->
        val mapPos = worldToMapOffset(place.bx, place.by)
        val distToPl = sqrt((mapPos.x - center.x) * (mapPos.x - center.x) + (mapPos.y - center.y) * (mapPos.y - center.y))
        
        // draw if inside circular map
        if (distToPl < radius - 15f) {
            scope.drawCircle(place.category.color, radius = 10f, center = mapPos)
            scope.drawCircle(Color.White, radius = 3f, center = mapPos)
        }
    }

    // 4. Draw Player as rotating Arrow at map center
    scope.drawIntoCanvas { canvas ->
        val arrowPath = Path().apply {
            moveTo(center.x, center.y - 18f)
            lineTo(center.x - 12f, center.y + 14f)
            lineTo(center.x, center.y + 8f)
            lineTo(center.x + 12f, center.y + 14f)
            close()
        }
        
        canvas.save()
        // Rotate canvas around center by heading angle (heading is in radians, convert to degrees)
        canvas.rotate(heading * 180f / PI.toFloat(), center.x, center.y)
        canvas.drawPath(arrowPath, Paint().apply {
            color = Color(0xFFE53935)
            style = PaintingStyle.Fill
            isAntiAlias = true
        })
        canvas.restore()
    }
}
