package com.example.data.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.*

// 1. Vector3 representation for 3D coordinates
data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vector3) = Vector3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vector3) = Vector3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vector3(x * s, y * s, z * s)

    fun distanceTo(o: Vector3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

// 2. Camera Projection Context
data class Camera(
    val x: Float = 0f,
    val y: Float = -150f, // Start a bit south of the central square
    val z: Float = 2.5f,  // Eye height (higher in drone mode)
    val heading: Float = 0f, // Yaw angle in radians (0 is North/positive Y, PI/2 is East)
    val pitch: Float = 0f,   // Pitch angle in radians (0 is level, negative is looking down)
    val mode: CameraMode = CameraMode.DRIVE,
    val zoom: Float = 1f     // Zoom scale for drone/orbit mode
)

enum class CameraMode {
    DRIVE,  // First person view from behind steering wheel
    DRONE   // Orbiting/Birds-eye view of the city
}

// Projection utility helper
class ProjectionContext(
    val screenWidth: Float,
    val screenHeight: Float,
    val camera: Camera
) {
    val centerX = screenWidth / 2f
    val centerY = screenHeight / 2f
    
    // Horizontal/vertical focal length scaling
    val focalLength = (screenWidth * 0.9f) * camera.zoom

    // Projects a 3D world coordinate to a 2D screen coordinate
    // Returns null if behind the camera
    fun project(point: Vector3): Offset? {
        val dx = point.x - camera.x
        val dy = point.y - camera.y
        val dz = point.z - camera.z

        // Heading (yaw) rotation
        val cosH = cos(camera.heading)
        val sinH = sin(camera.heading)
        
        // Transform to camera-relative coordinate space
        // rx: left/right relative to camera look (West-East)
        // ry: depth/distance along look vector (South-North)
        val rx = dx * cosH + dy * sinH
        val ry = -dx * sinH + dy * cosH
        val rz = dz

        // Pitch rotation (tilt) around the camera's local horizontal axis
        val cosP = cos(camera.pitch)
        val sinP = sin(camera.pitch)
        
        val transY = ry * cosP + rz * sinP  // Depth after pitch
        val transZ = -ry * sinP + rz * cosP // Height relative to camera view line

        // Clip things behind or too close to camera
        if (transY < 0.2f) return null

        val px = centerX + (rx / transY) * focalLength
        val py = centerY - (transZ / transY) * focalLength

        return Offset(px, py)
    }

    // High performance calculation of depth (relative camera distance)
    fun getDepth(point: Vector3): Float {
        val dx = point.x - camera.x
        val dy = point.y - camera.y
        val dz = point.z - camera.z
        
        // Depth is along the camera look direction
        val cosH = cos(camera.heading)
        val sinH = sin(camera.heading)
        val ry = -dx * sinH + dy * cosH
        
        val cosP = cos(camera.pitch)
        val sinP = sin(camera.pitch)
        return ry * cosP + dz * sinP
    }
}

// 3. 3D Drawable items (for depth sorting painter's algorithm)
sealed class Drawable3D {
    abstract val center: Vector3
    abstract val depth: Float
    
    // Draw call
    abstract fun draw(
        drawContext: ProjectionContext,
        canvasWidth: Float,
        canvasHeight: Float,
        timeOfDay: Float, // 0.0f to 24.0f (0/24 is midnight, 12 is noon)
        onDraw: (Path, Color, Color?, Float) -> Unit,
        onDrawLine: (Offset, Offset, Color, Float) -> Unit,
        onDrawPoint: (Offset, Float, Color) -> Unit,
        onDrawText: (String, Offset, Color, Boolean) -> Unit // isPOI text
    )
}

// A 3D polygon face
class Polygon3D(
    val vertices: List<Vector3>,
    val color: Color,
    val outlineColor: Color? = null,
    val strokeWidth: Float = 1f,
    val isRoad: Boolean = false,
    val isWater: Boolean = false
) : Drawable3D() {
    override val center: Vector3
        get() {
            var sx = 0f
            var sy = 0f
            var sz = 0f
            vertices.forEach {
                sx += it.x
                sy += it.y
                sz += it.z
            }
            return Vector3(sx / vertices.size, sy / vertices.size, sz / vertices.size)
        }
        
    override var depth: Float = 0f

    override fun draw(
        drawContext: ProjectionContext,
        canvasWidth: Float,
        canvasHeight: Float,
        timeOfDay: Float,
        onDraw: (Path, Color, Color?, Float) -> Unit,
        onDrawLine: (Offset, Offset, Color, Float) -> Unit,
        onDrawPoint: (Offset, Float, Color) -> Unit,
        onDrawText: (String, Offset, Color, Boolean) -> Unit
    ) {
        val offsets = vertices.map { drawContext.project(it) ?: return }
        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            for (i in 1 until offsets.size) {
                lineTo(offsets[i].x, offsets[i].y)
            }
            close()
        }
        
        // Adjust color based on time of day (ambient lighting)
        val litColor = adjustColorForTime(color, timeOfDay)
        val litOutline = outlineColor?.let { adjustColorForTime(it, timeOfDay) }
        
        onDraw(path, litColor, litOutline, strokeWidth)
    }
}

// A 3D box representing a building
class Building3D(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val bx: Float,
    val by: Float,
    val width: Float,
    val length: Float,
    val height: Float,
    val color: Color,
    val rating: Float = 4.5f,
    val reviewCount: Int = 12,
    val description: String = "",
    val phone: String = "۰۸۷-۳۵۲۲۰۰۰۰",
    val address: String = "",
    val workingHours: String = "۸:۰۰ تا ۲۲:۰۰"
) : Drawable3D() {
    override val center: Vector3 = Vector3(bx, by, height / 2f)
    override var depth: Float = 0f

    // Vertices of the building box
    private val v0 = Vector3(bx - width/2, by - length/2, 0f)
    private val v1 = Vector3(bx + width/2, by - length/2, 0f)
    private val v2 = Vector3(bx + width/2, by + length/2, 0f)
    private val v3 = Vector3(bx - width/2, by + length/2, 0f)
    private val v4 = Vector3(bx - width/2, by - length/2, height)
    private val v5 = Vector3(bx + width/2, by - length/2, height)
    private val v6 = Vector3(bx + width/2, by + length/2, height)
    private val v7 = Vector3(bx - width/2, by + length/2, height)

    // Face definitions (list of vertex indices and color shading factor)
    private val faces = listOf(
        // South face (front)
        Face(listOf(v0, v1, v5, v4), 1.0f, "front"),
        // East face (right)
        Face(listOf(v1, v2, v6, v5), 0.85f, "side"),
        // North face (back)
        Face(listOf(v2, v3, v7, v6), 0.7f, "back"),
        // West face (left)
        Face(listOf(v3, v0, v4, v7), 0.85f, "side"),
        // Top face (roof)
        Face(listOf(v4, v5, v6, v7), 0.95f, "roof")
    )

    data class Face(val verts: List<Vector3>, val shadeFactor: Float, val type: String)

    override fun draw(
        drawContext: ProjectionContext,
        canvasWidth: Float,
        canvasHeight: Float,
        timeOfDay: Float,
        onDraw: (Path, Color, Color?, Float) -> Unit,
        onDrawLine: (Offset, Offset, Color, Float) -> Unit,
        onDrawPoint: (Offset, Float, Color) -> Unit,
        onDrawText: (String, Offset, Color, Boolean) -> Unit
    ) {
        // Draw each face of the building
        // Sort faces of this building internally by depth
        val sortedFaces = faces.map { face ->
            val fc = Vector3(
                face.verts.map { it.x }.average().toFloat(),
                face.verts.map { it.y }.average().toFloat(),
                face.verts.map { it.z }.average().toFloat()
            )
            val faceDepth = drawContext.getDepth(fc)
            face to faceDepth
        }.sortedByDescending { it.second }

        for ((face, _) in sortedFaces) {
            val offsets = face.verts.map { drawContext.project(it) ?: return }
            val path = Path().apply {
                moveTo(offsets[0].x, offsets[0].y)
                for (i in 1 until offsets.size) {
                    lineTo(offsets[i].x, offsets[i].y)
                }
                close()
            }

            // Apply shadow / ambient light depending on time of day
            val baseColor = color
            val baseR = baseColor.red
            val baseG = baseColor.green
            val baseB = baseColor.blue
            
            // Adjust color for time of day
            val ambientLit = adjustColorForTime(Color(baseR * face.shadeFactor, baseG * face.shadeFactor, baseB * face.shadeFactor), timeOfDay)
            
            // Add custom details to faces (like windows or shop signs)
            onDraw(path, ambientLit, adjustColorForTime(Color.Black.copy(alpha = 0.15f), timeOfDay), 1f)

            // Draw windows on side and front walls at day, or light them up at night!
            val isNight = timeOfDay < 6f || timeOfDay > 18.5f
            if (face.type != "roof" && height > 4f) {
                // simple window grid drawing if close enough
                if (depth < 120f) {
                    val windowColor = if (isNight) Color(0xFFFFE082) else Color(0xFF80DEEA).copy(alpha = 0.6f)
                    
                    // Draw small windows in the face
                    // We project 3D relative window positions
                    val stepsX = 4
                    val stepsZ = if (height > 10f) 3 else 1
                    for (sx in 1..stepsX) {
                        for (sz in 1..stepsZ) {
                            val fx = sx.toFloat() / (stepsX + 1)
                            val fz = sz.toFloat() / (stepsZ + 1)
                            
                            // interpolate 3D coordinates for window
                            val bottomL = face.verts[0]
                            val bottomR = face.verts[1]
                            val topR = face.verts[2]
                            val topL = face.verts[3]
                            
                            // Interp
                            val pX1 = bottomL + (bottomR - bottomL) * (fx - 0.05f)
                            val pX2 = bottomL + (bottomR - bottomL) * (fx + 0.05f)
                            val wBottom = pX1 + (topL - bottomL) * (fz - 0.05f)
                            val wTop = pX1 + (topL - bottomL) * (fz + 0.05f)
                            val wRightB = pX2 + (topR - bottomR) * (fz - 0.05f)
                            val wRightT = pX2 + (topR - bottomR) * (fz + 0.05f)
                            
                            val wOffsets = listOf(wBottom, wRightB, wRightT, wTop).mapNotNull { drawContext.project(it) }
                            if (wOffsets.size == 4) {
                                val wPath = Path().apply {
                                    moveTo(wOffsets[0].x, wOffsets[0].y)
                                    for (i in 1..3) lineTo(wOffsets[i].x, wOffsets[i].y)
                                    close()
                                }
                                onDraw(wPath, windowColor, null, 0f)
                            }
                        }
                    }
                }
            }
        }

        // 4. Draw floating Google Maps style Pin / Logo above center of the building
        val pinZ = height + 3.5f
        val pinPos = Vector3(bx, by, pinZ)
        val pinOffset = drawContext.project(pinPos)
        if (pinOffset != null && depth < 200f) {
            // Draw a beautiful map pin at this coordinate
            onDrawPoint(pinOffset, 14f, category.color)
            onDrawPoint(pinOffset, 6f, Color.White)
            
            // Draw shop label
            if (depth < 120f) {
                onDrawText(name, pinOffset + Offset(0f, -24f), Color.White, true)
            }
        }
    }
}

// A 3D Tree
class Tree3D(
    val tx: Float,
    val ty: Float,
    val size: Float = 3f
) : Drawable3D() {
    override val center: Vector3 = Vector3(tx, ty, size / 2f)
    override var depth: Float = 0f

    override fun draw(
        drawContext: ProjectionContext,
        canvasWidth: Float,
        canvasHeight: Float,
        timeOfDay: Float,
        onDraw: (Path, Color, Color?, Float) -> Unit,
        onDrawLine: (Offset, Offset, Color, Float) -> Unit,
        onDrawPoint: (Offset, Float, Color) -> Unit,
        onDrawText: (String, Offset, Color, Boolean) -> Unit
    ) {
        val bottomPos = Vector3(tx, ty, 0f)
        val topPos = Vector3(tx, ty, size * 1.5f)

        val bOffset = drawContext.project(bottomPos) ?: return
        val tOffset = drawContext.project(topPos) ?: return

        // Trunk
        val trunkColor = adjustColorForTime(Color(0xFF8D6E63), timeOfDay)
        onDrawLine(bOffset, tOffset, trunkColor, (6f / depth * 20f).coerceIn(2f, 12f))

        // Foliage
        val leafColor = adjustColorForTime(Color(0xFF4CAF50), timeOfDay)
        val radius = (size * 8f / depth * 20f).coerceIn(4f, 40f)
        onDrawPoint(tOffset, radius, leafColor)
    }
}

// 4. Time of day color adjustments (ambient lighting)
fun adjustColorForTime(color: Color, timeOfDay: Float): Color {
    // Determine lighting factor (0.1 at midnight, 1.0 at noon)
    val factor = if (timeOfDay in 6.0f..18.0f) {
        // Day time
        if (timeOfDay < 9.0f) {
            // Sunrise transitions
            0.3f + (timeOfDay - 6.0f) / 3.0f * 0.7f
        } else if (timeOfDay > 15.0f) {
            // Sunset transitions
            0.3f + (18.0f - timeOfDay) / 3.0f * 0.7f
        } else {
            // Midday
            1.0f
        }
    } else {
        // Night time
        0.22f
    }

    // Color shift based on sunset (reddish) or night (bluish)
    val isSunset = timeOfDay in 16.5f..18.5f
    val isNight = timeOfDay < 5.5f || timeOfDay > 18.5f

    return when {
        isSunset -> {
            val r = (color.red * factor + 0.12f).coerceIn(0f, 1f)
            val g = (color.green * factor * 0.85f).coerceIn(0f, 1f)
            val b = (color.blue * factor * 0.75f).coerceIn(0f, 1f)
            Color(r, g, b, color.alpha)
        }
        isNight -> {
            val r = (color.red * factor * 0.7f).coerceIn(0f, 1f)
            val g = (color.green * factor * 0.8f).coerceIn(0f, 1f)
            val b = (color.blue * factor + 0.04f).coerceIn(0f, 1f)
            Color(r, g, b, color.alpha)
        }
        else -> {
            Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
        }
    }
}

// 5. Place Categories
enum class PlaceCategory(val title: String, val color: Color) {
    FOOD("رستوران و کافه", Color(0xFFE53935)),
    SHOPPING("فروشگاه و بازار", Color(0xFF1E88E5)),
    MEDICAL("پزشکی و سلامت", Color(0xFF43A047)),
    HISTORICAL("تاریخی و مذهبی", Color(0xFFD81B60)),
    PARK("پارک و طبیعت", Color(0xFF8E24AA)),
    HOTEL("هتل و اقامت", Color(0xFFF4511E)),
    CIVIC("خدمات شهری", Color(0xFF757575))
}

// 6. Pre-configured Google Maps POIs in Qorveh
object QorvehMapData {
    val places = listOf(
        Building3D(
            id = "sarab_lake",
            name = "دریاچه تفریحی سراب قروه",
            category = PlaceCategory.PARK,
            bx = 0f, by = -370f,
            width = 40f, length = 40f, height = 3f, // lake is drawn manually, this represents center marker
            color = Color(0xFF29B6F6),
            rating = 4.8f, reviewCount = 845,
            description = "مهم‌ترین جاذبه گردشگری و نگین شهر قروه، دریاچه طبیعی سراب با چشمه‌های طبیعی جوشان و محیط تفریحی خانوادگی عالی.",
            phone = "۰۸۷-۳۵۲۴۸۲۰۰",
            address = "کردستان، قروه، انتهای خیابان سید جمال‌الدین، منطقه نمونه گردشگری سراب قروه",
            workingHours = "۲۴ ساعته (سراسری)"
        ),
        Building3D(
            id = "sarab_hotel",
            name = "هتل جهانگردی سراب قروه",
            category = PlaceCategory.HOTEL,
            bx = 75f, by = -310f,
            width = 30f, length = 20f, height = 15f,
            color = Color(0xFF8D6E63),
            rating = 4.2f, reviewCount = 138,
            description = "مجموعه اقامتی زیبا و آرام شرکت جهانگردی در جوار دریاچه سراب با کادر مجرب و چشم‌انداز استثنایی بدر و پریشان.",
            phone = "۰۸۷-۳۵۲۲۴۰۰۱",
            address = "قروه، خیابان سید جمال‌الدین، حاشیه شرقی دریاچه تفریحی سراب",
            workingHours = "پذیرش ۲۴ ساعته"
        ),
        Building3D(
            id = "sarab_cafe",
            name = "کافه رستوران سنتی سراب",
            category = PlaceCategory.FOOD,
            bx = -75f, by = -320f,
            width = 24f, length = 16f, height = 6f,
            color = Color(0xFFFFB74D),
            rating = 4.5f, reviewCount = 92,
            description = "ارائه انواع کباب‌های کُردی، چای ذغالی و غذای سنتی بی‌نظیر کلانه با روغن محلی در آلاچیق‌های مشرف به دریاچه.",
            phone = "۰۹۱۸-۳۷۲۰۰۰۰",
            address = "جنوب قروه، پارک ساحلی غربی دریاچه سراب",
            workingHours = "۱۲:۰۰ تا ۲۴:۰۰"
        ),
        Building3D(
            id = "beheshti_hospital",
            name = "بیمارستان شهید بهشتی قروه",
            category = PlaceCategory.MEDICAL,
            bx = -210f, by = -180f,
            width = 40f, length = 30f, height = 24f,
            color = Color(0xFFE53935),
            rating = 4.1f, reviewCount = 312,
            description = "بیمارستان دولتی و مرکز اصلی خدمات درمانی شهرستان قروه با بخش‌های اورژانس، بستری، آزمایشگاه و داروخانه.",
            phone = "۰۸۷-۳۵۲۲۲۱۸۱",
            address = "قروه، خیابان بیمارستان (ضلع غربی شهر)",
            workingHours = "اورژانس شبانه‌روزی (۲۴ ساعته)"
        ),
        Building3D(
            id = "grand_mosque",
            name = "مسجد جامع تاریخی قروه",
            category = PlaceCategory.HISTORICAL,
            bx = -110f, by = 90f,
            width = 32f, length = 32f, height = 12f,
            color = Color(0xFF26A69A),
            rating = 4.6f, reviewCount = 203,
            description = "میراث معماری اسلامی قروه و مرکز گردهمایی‌های بزرگ مذهبی، با شبستان زیبای سنتی و گلدسته‌های برجسته.",
            phone = "۰۸۷-۳۵۲۲۳۴۰۰",
            address = "ضلع شمال غربی میدان امام خمینی (ره)",
            workingHours = "۴:۳۰ تا ۲۱:۰۰ (اوقات شرعی)"
        ),
        Building3D(
            id = "sadaf_shopping",
            name = "پاساژ تجاری صدف قروه",
            category = PlaceCategory.SHOPPING,
            bx = -150f, by = -30f,
            width = 32f, length = 24f, height = 14f,
            color = Color(0xFF1E88E5),
            rating = 4.3f, reviewCount = 175,
            description = "یکی از قدیمی‌ترین و معروف‌ترین پاساژهای تجاری قروه، بورس پوشاک، کیف و کفش و لوازم آرایشی و بهداشتی.",
            phone = "۰۸۷-۳۵۲۲۹۹۰۰",
            address = "خیابان شریعتی، مابین میدان امام و میدان طالقانی",
            workingHours = "۹:۰۰ تا ۱۳:۳۰ و ۱۶:۰۰ تا ۲۱:۳۰"
        ),
        Building3D(
            id = "traditional_bazaar",
            name = "بازار سنتی قروه (راسته طلا)",
            category = PlaceCategory.SHOPPING,
            bx = -120f, by = 0f,
            width = 45f, length = 18f, height = 8f,
            color = Color(0xFFFFD54F),
            rating = 4.3f, reviewCount = 312,
            description = "راسته بازار قدیمی و پرجنب‌وجوش، معروف به بورس طلا و جواهرآلات، پارچه‌های سنتی سنندجی و صنایع دستی نفیس کُردی.",
            phone = "۰۸۷-۳۵۲۲۱۰۱۰",
            address = "خیابان شریعتی غربی، منتهی به بافت بازار قدیم",
            workingHours = "۹:۰۰ تا ۱۹:۰۰"
        ),
        Building3D(
            id = "shaho_super",
            name = "هایپرمارکت شاهو",
            category = PlaceCategory.SHOPPING,
            bx = 35f, by = 220f,
            width = 25f, length = 20f, height = 8f,
            color = Color(0xFF42A5F5),
            rating = 4.4f, reviewCount = 76,
            description = "هایپرمارکت زنجیره‌ای مدرن با تنوع کامل کالایی و ارزاق عمومی با تخفیف‌های ویژه روزانه.",
            phone = "۰۸۷-۳۵۲۲۸۸۹۹",
            address = "بلوار سید جمال‌الدین شمالی، تقاطع خیابان طالقانی",
            workingHours = "۸:۰۰ تا ۲۳:۳۰"
        ),
        Building3D(
            id = "kordestan_pastry",
            name = "شیرینی‌سرای کردستان",
            category = PlaceCategory.FOOD,
            bx = 45f, by = -120f,
            width = 20f, length = 15f, height = 7f,
            color = Color(0xFFF8BBD0),
            rating = 4.7f, reviewCount = 184,
            description = "ارائه مرغوب‌ترین کلوخه‌های محلی، باقلوای قزوینی و سنتی، و سوغات ناب کُردی از جمله شیرینی کنجدی معروف.",
            phone = "۰۸۷-۳۵۲۲۶۵۴۳",
            address = "بلوار سید جمال‌الدین جنوبی، نبش کوچه یاس",
            workingHours = "۸:۳۰ تا ۲۲:۳۰"
        ),
        Building3D(
            id = "municipality",
            name = "شهرداری مرکزی قروه",
            category = PlaceCategory.CIVIC,
            bx = 130f, by = 110f,
            width = 30f, length = 22f, height = 16f,
            color = Color(0xFF757575),
            rating = 3.9f, reviewCount = 88,
            description = "ساختمان اداری مرکزی شهرداری قروه، متولی خدمات عمران شهری، مدیریت فضاها و پارک‌های تفریحی منطقه.",
            phone = "۰۸۷-۳۵۲۲۴۰۰۰",
            address = "خیابان شهرداری، نرسیده به میدان شورا",
            workingHours = "۷:۳۰ تا ۱۴:۳۰ (شنبه تا چهارشنبه)"
        ),
        Building3D(
            id = "governorate",
            name = "فرمانداری ویژه شهرستان قروه",
            category = PlaceCategory.CIVIC,
            bx = -80f, by = 240f,
            width = 32f, length = 22f, height = 15f,
            color = Color(0xFF607D8B),
            rating = 4.0f, reviewCount = 45,
            description = "مرکز عالی تصمیم‌گیری حاکمیتی و اداری شهرستان قروه جهت هماهنگی امور اجرایی و امنیتی کل منطقه.",
            phone = "۰۸۷-۳۵۲۲۳۲۲۱",
            address = "بلوار امام علی (ع)، روبروی مصلای بزرگ قروه",
            workingHours = "۷:۳۰ تا ۱۴:۳۰"
        ),
        Building3D(
            id = "petrol_station",
            name = "پمپ بنزین الغدیر قروه",
            category = PlaceCategory.CIVIC,
            bx = 110f, by = 210f,
            width = 28f, length = 16f, height = 6f,
            color = Color(0xFFFFB300),
            rating = 4.2f, reviewCount = 198,
            description = "جایگاه سوخت‌رسانی دو منظوره (بنزین و گازوئیل) مجهز به کارواش و سوپرمارکت در ورودی اصلی بلوار الغدیر.",
            phone = "۰۸۷-۳۵۲۴۷۶۵۰",
            address = "بلوار الغدیر، تقاطع ورودی جاده همدان به قروه",
            workingHours = "شبانه روزی (۲۴ ساعته)"
        ),
        Building3D(
            id = "passenger_terminal",
            name = "ترمینال مسافربری قروه",
            category = PlaceCategory.CIVIC,
            bx = 220f, by = -140f,
            width = 36f, length = 24f, height = 10f,
            color = Color(0xFF90A4AE),
            rating = 4.1f, reviewCount = 82,
            description = "پایانه حمل‌ونقل جاده‌ای مسافربری قروه، دارای سرویس‌های روزانه اتوبوسی و سواری به تهران، سنندج و همدان.",
            phone = "۰۸۷-۳۵۲۲۶۶۰۰",
            address = "ورودی شرقی قروه، بلوار خلیج فارس",
            workingHours = "۶:۰۰ تا ۲۲:۰۰"
        ),
        Building3D(
            id = "payam_noor_uni",
            name = "دانشگاه پیام نور مرکز قروه",
            category = PlaceCategory.CIVIC,
            bx = 180f, by = -280f,
            width = 32f, length = 26f, height = 18f,
            color = Color(0xFF5C6BC0),
            rating = 4.3f, reviewCount = 110,
            description = "بزرگترین مجتمع آموزش عالی دولتی قروه، ارائه‌دهنده مقاطع کارشناسی و کارشناسی ارشد در رشته‌های فنی و علوم انسانی.",
            phone = "۰۸۷-۳۵۲۲۵۰۶۰",
            address = "بلوار سید جمال‌الدین جنوبی، شهرک دانشگاه",
            workingHours = "۸:۰۰ تا ۱۵:۳۰"
        ),
        Building3D(
            id = "andisheh_books",
            name = "کتاب‌فروشی اندیشه",
            category = PlaceCategory.SHOPPING,
            bx = -35f, by = 150f,
            width = 18f, length = 14f, height = 6f,
            color = Color(0xFFAB47BC),
            rating = 4.5f, reviewCount = 42,
            description = "کتابفروشی تخصصی با بورس کتب ادبی کُردی، تاریخی، آموزشی و عرضه مستقیم انواع لوازم تحریر هنری و مهندسی.",
            phone = "۰۸۷-۳۵۲۲۸۲۸۲",
            address = "بلوار سید جمال‌الدین شمالی, نرسیده به خیابان طالقانی",
            workingHours = "۹:۰۰ تا ۱۳:۰۰ و ۱۵:۳۰ تا ۲۱:۰۰"
        ),
        Building3D(
            id = "aria_clinic",
            name = "ساختمان پزشکان آریا",
            category = PlaceCategory.MEDICAL,
            bx = -40f, by = -80f,
            width = 28f, length = 18f, height = 18f,
            color = Color(0xFF66BB6A),
            rating = 4.1f, reviewCount = 115,
            description = "مجتمع تخصصی و مجهز شامل مطب‌های دندانپزشکی، ارتوپدی، مامایی و آزمایشگاه تشخیص طبی به همراه داروخانه معتبر آریا.",
            phone = "۰۸۷-۳۵۲۲۱۵۰۰",
            address = "خیابان شریعتی، ضلع جنوبی میدان امام خمینی (ره)",
            workingHours = "۹:۰۰ تا ۲۱:۰۰"
        ),
        Building3D(
            id = "melli_bank",
            name = "بانک ملی شعبه مرکزی قروه",
            category = PlaceCategory.CIVIC,
            bx = 45f, by = 60f,
            width = 25f, length = 18f, height = 10f,
            color = Color(0xFF78909C),
            rating = 4.0f, reviewCount = 59,
            description = "شعبه اصلی و محوری بانک ملی ایران در شهرستان قروه جهت ارائه تسهیلات بازرگانی، ارزی و امور عمومی مشتریان.",
            phone = "۰۸۷-۳۵۲۲۲۱۱۱",
            address = "ضلع شمال شرقی میدان امام خمینی (ره)",
            workingHours = "۷:۳۰ تا ۱۳:۳۰"
        ),
        Building3D(
            id = "ghasr_cinema",
            name = "پردیس سینمایی قصر قروه",
            category = PlaceCategory.HISTORICAL,
            bx = 120f, by = -40f,
            width = 35f, length = 22f, height = 11f,
            color = Color(0xFFEC407A),
            rating = 4.4f, reviewCount = 142,
            description = "مجموعه فرهنگی سینمایی قروه با سالن مجهز دالبی جهت نمایش آثار تراز اول سینمای ایران و برگزاری جشن‌های ملی.",
            phone = "۰۸۷-۳۵۲۲۹۰۹۰",
            address = "خیابان شریعتی شرقی، مجتمع فرهنگی قصر",
            workingHours = "۱۶:۰۰ تا ۲۳:۰۰"
        ),
        Building3D(
            id = "mellat_park",
            name = "بوستان ملت قروه",
            category = PlaceCategory.PARK,
            bx = -260f, by = 260f,
            width = 50f, length = 50f, height = 2f,
            color = Color(0xFF9CCC65),
            rating = 4.5f, reviewCount = 490,
            description = "یکی از بزرگترین بوستان‌های شهری قروه با پوشش گیاهی درختان تبریزی، بورس وسایل شهربازی کودکان و بوفه زمستانه.",
            phone = "۰۸۷-۳۵۲۲۷۳۰۰",
            address = "شمال غرب قروه، تقاطع خیابان ابوذر و طالقانی",
            workingHours = "۲۴ ساعته (سراسری)"
        ),
        Building3D(
            id = "rezvan_park",
            name = "پارک ساحلی رضوان قروه",
            category = PlaceCategory.PARK,
            bx = 160f, by = 320f,
            width = 45f, length = 45f, height = 2f,
            color = Color(0xFF26A69A),
            rating = 4.4f, reviewCount = 154,
            description = "از زیباترین فضاهای سبز شمال شرقی شهر قروه مجهز به مسیرهای تندرستی، پیست دوچرخه‌سواری و آلاچیق‌های تفریحی خانوادگی.",
            phone = "۰۸۷-۳۵۲۲۷۴۰۰",
            address = "بلوار سید جمال‌الدین شمالی، روبروی مجتمع ورزشی رضوان",
            workingHours = "۲۴ ساعته (سراسری)"
        ),
        Building3D(
            id = "kebabsara",
            name = "کباب‌سرای نمونه قروه",
            category = PlaceCategory.FOOD,
            bx = 35f, by = 30f,
            width = 18f, length = 16f, height = 6f,
            color = Color(0xFFEF5350),
            rating = 4.7f, reviewCount = 210,
            description = "برترین کباب‌سرای منتخب قروه با پخت فوق‌العاده کباب کوبیده کُردی، برگ اعلا و جوجه کباب ذغالی با برنج ایرانی.",
            phone = "۰۹۱۸-۳۷۲۱۱۱۱",
            address = "ضلع شمالی میدان امام خمینی، ابتدای بلوار سید جمال‌الدین شمالی",
            workingHours = "۱۱:۳۰ تا ۲۳:۳۰"
        )
    )
}

// 7. Vehicles available for selection
data class Vehicle(
    val name: String,
    val persianName: String,
    val maxSpeed: Float, // km/h
    val acceleration: Float,
    val handling: Float, // steer responsiveness
    val enginePitch: Float, // audio simulation factor
    val color: Color
)

object VehicleList {
    val vehicles = listOf(
        Vehicle("Pride", "پراید نوستالژیک (صبای سفید)", 110f, 1.8f, 1.5f, 1.0f, Color.White),
        Vehicle("Samand", "سمند EF7 (خودروی ملی)", 140f, 2.3f, 1.2f, 0.85f, Color(0xFFCFD8DC)),
        Vehicle("Peugeot", "پارس ELX (شوتی اسپرت)", 170f, 3.2f, 1.8f, 1.3f, Color(0xFF212121))
    )
}
