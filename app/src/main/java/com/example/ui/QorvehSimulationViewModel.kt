package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import com.example.data.database.QorvehDatabase
import com.example.data.database.QorvehRepository
import com.example.data.database.UserReview
import com.example.data.models.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

class QorvehSimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: QorvehRepository
    
    init {
        val database = QorvehDatabase.getDatabase(application)
        repository = QorvehRepository(database)
    }

    // 1. Reactive Room State Flow
    val savedPlaces = repository.savedPlaces.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val drivingStats = repository.drivingStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // 2. Local View UI States
    private val _camera = MutableStateFlow(Camera())
    val camera: StateFlow<Camera> = _camera.asStateFlow()

    // Car/Player Position (World Coordinates in meters)
    private val _posX = MutableStateFlow(0f) // Start near central square
    val posX: StateFlow<Float> = _posX.asStateFlow()

    private val _posY = MutableStateFlow(-100f) // Start 100m south
    val posY: StateFlow<Float> = _posY.asStateFlow()

    private val _posZ = MutableStateFlow(1.5f) // eye level (height)
    val posZ: StateFlow<Float> = _posZ.asStateFlow()

    private val _carHeading = MutableStateFlow(0f) // Yaw in radians (0 is North)
    val carHeading: StateFlow<Float> = _carHeading.asStateFlow()

    private val _carSpeed = MutableStateFlow(0f) // m/s
    val carSpeed: StateFlow<Float> = _carSpeed.asStateFlow()

    private val _steering = MutableStateFlow(0f) // Steering input: -1f (left) to 1f (right)
    val steering: StateFlow<Float> = _steering.asStateFlow()

    private val _activeVehicle = MutableStateFlow(VehicleList.vehicles[0]) // Pride default
    val activeVehicle: StateFlow<Vehicle> = _activeVehicle.asStateFlow()

    private val _timeOfDay = MutableStateFlow(17.5f) // Start at beautiful sunset (5:30 PM)
    val timeOfDay: StateFlow<Float> = _timeOfDay.asStateFlow()

    private val _isTimeFlowing = MutableStateFlow(true)
    val isTimeFlowing: StateFlow<Boolean> = _isTimeFlowing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<PlaceCategory?>(null)
    val selectedCategory: StateFlow<PlaceCategory?> = _selectedCategory.asStateFlow()

    private val _selectedPlace = MutableStateFlow<Building3D?>(null)
    val selectedPlace: StateFlow<Building3D?> = _selectedPlace.asStateFlow()

    // Custom review triggers
    private val _activePlaceReviews = MutableStateFlow<List<UserReview>>(emptyList())
    val activePlaceReviews: StateFlow<List<UserReview>> = _activePlaceReviews.asStateFlow()

    // Collision Event (causes HUD screen shake)
    private val _screenShake = MutableSharedFlow<Boolean>()
    val screenShake: SharedFlow<Boolean> = _screenShake.asSharedFlow()

    // Autopilot state
    private val _isAutopilotActive = MutableStateFlow(false)
    val isAutopilotActive: StateFlow<Boolean> = _isAutopilotActive.asStateFlow()

    private val _autopilotTarget = MutableStateFlow<Building3D?>(null)
    val autopilotTarget: StateFlow<Building3D?> = _autopilotTarget.asStateFlow()

    // User controls mapping
    var isInputGas = false
    var isInputBrake = false
    var isInputLeft = false
    var isInputRight = false

    private var physicsJob: Job? = null
    private var reviewsJob: Job? = null

    init {
        // Start simulation loops
        startPhysicsLoop()
        
        // Listen to active vehicle in saved stats
        viewModelScope.launch {
            repository.drivingStats.collect { stat ->
                if (stat != null) {
                    val matchingVeh = VehicleList.vehicles.find { it.name == stat.activeVehicle }
                    if (matchingVeh != null) {
                        _activeVehicle.value = matchingVeh
                    }
                }
            }
        }
    }

    private fun startPhysicsLoop() {
        physicsJob?.cancel()
        physicsJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val dt = (now - lastTime) / 1000f // delta time in seconds
                lastTime = now

                // 1. Advance Time of Day
                if (_isTimeFlowing.value) {
                    var newTime = _timeOfDay.value + dt * 0.05f // Day lasts about 8 minutes
                    if (newTime >= 24f) newTime = 0f
                    _timeOfDay.value = newTime
                }

                // 2. Autopilot Steering Calculation
                if (_isAutopilotActive.value) {
                    val target = _autopilotTarget.value
                    if (target != null) {
                        calculateAutopilotPhysics(target)
                    } else {
                        _isAutopilotActive.value = false
                    }
                }

                // 3. Process Driving Physics
                updateDrivingPhysics(dt)

                // 4. Update Camera coordinates based on camera mode
                val currentCam = _camera.value
                val pX = _posX.value
                val pY = _posY.value
                val pZ = _posZ.value
                val heading = _carHeading.value

                if (currentCam.mode == CameraMode.DRIVE) {
                    // First person, behind the steering wheel
                    // Place camera slightly behind and above car center looking forward
                    val offsetDistance = -8f
                    val camX = pX + sin(heading) * offsetDistance
                    val camY = pY - cos(heading) * offsetDistance
                    
                    // Dynamic pitch based on speed/acceleration (look down slightly)
                    _camera.value = currentCam.copy(
                        x = camX,
                        y = camY,
                        z = pZ + 2.5f,
                        heading = heading,
                        pitch = -0.15f // Tilt slightly forward
                    )
                } else {
                    // DRONE / Map Mode:
                    // Orbiting look-at target (the car or a selected place)
                    // We allow the user to drag to rotate heading or pinch to zoom.
                    // Keep camera centered over car but elevated.
                    val orbitRadius = 130f / currentCam.zoom
                    val camX = pX + sin(currentCam.heading) * orbitRadius * cos(currentCam.pitch)
                    val camY = pY - cos(currentCam.heading) * orbitRadius * cos(currentCam.pitch)
                    val camZ = max(15f, pZ + orbitRadius * sin(abs(currentCam.pitch)))

                    _camera.value = currentCam.copy(
                        x = camX,
                        y = camY,
                        z = camZ
                    )
                }

                // Tick interval
                delay(16) // roughly 60 fps
            }
        }
    }

    private fun calculateAutopilotPhysics(target: Building3D) {
        val px = _posX.value
        val py = _posY.value
        val tx = target.bx
        val ty = target.by

        val dist = sqrt((tx - px) * (tx - px) + (ty - py) * (ty - py))
        
        // If close to target, stop autopilot
        if (dist < 12f) {
            _isAutopilotActive.value = false
            _carSpeed.value = 0f
            isInputGas = false
            isInputBrake = true
            isInputLeft = false
            isInputRight = false
            _selectedPlace.value = target
            return
        }

        // Target angle (where 0 is North/positive Y, PI/2 is East)
        // atan2(dx, dy) matches our angle orientation beautifully!
        val targetAngle = atan2(tx - px, ty - py)

        // Heading diff normalized to -PI to PI
        var diff = targetAngle - _carHeading.value
        while (diff < -PI) diff += (2 * PI).toFloat()
        while (diff > PI) diff -= (2 * PI).toFloat()

        // Steer towards target
        _steering.value = (diff * 2.0f).coerceIn(-1.0f, 1.0f)
        
        isInputGas = true
        isInputBrake = false
        
        // Auto inputs translation based on steering
        isInputLeft = _steering.value < -0.15f
        isInputRight = _steering.value > 0.15f
    }

    private fun updateDrivingPhysics(dt: Float) {
        val vehicle = _activeVehicle.value
        var speed = _carSpeed.value
        var heading = _carHeading.value
        var pX = _posX.value
        var pY = _posY.value

        // 1. Process Steering
        if (!_isAutopilotActive.value) {
            var steerInput = 0f
            if (isInputLeft) steerInput = -1f
            if (isInputRight) steerInput = 1f
            
            // smooth steering interpolation
            _steering.value = _steering.value * 0.82f + steerInput * 0.18f
        }

        // Adjust heading based on steering and speed (can only steer while moving)
        val steeringSpeedFactor = (speed / 10f).coerceIn(0f, 1f)
        heading += _steering.value * vehicle.handling * steeringSpeedFactor * dt

        // Keep heading normalized
        while (heading < -PI) heading += (2 * PI).toFloat()
        while (heading > PI) heading -= (2 * PI).toFloat()
        _carHeading.value = heading

        // 2. Process Gas and Brake
        val maxSpeedMps = vehicle.maxSpeed / 3.6f // Convert km/h to m/s
        
        // Slow down if off road (grass deceleration)
        val isOnRoad = checkIfOnRoad(pX, pY)
        val speedLimit = if (isOnRoad) maxSpeedMps else 5.0f // Off road max speed: 18 km/h

        if (isInputGas) {
            speed += vehicle.acceleration * dt
            if (speed > speedLimit) speed = speed * 0.95f + speedLimit * 0.05f
        } else if (isInputBrake) {
            speed -= vehicle.acceleration * 1.8f * dt // brakes are stronger
            if (speed < -5f) speed = -5f // reverse speed max 18 km/h
        } else {
            // Friction/Deceleration
            speed *= 0.96f
            if (abs(speed) < 0.1f) speed = 0f
        }
        _carSpeed.value = speed

        // 3. Move car
        // x is East (sin of heading), y is North (cos of heading)
        val dx = sin(heading) * speed * dt
        val dy = cos(heading) * speed * dt

        val newPX = pX + dx
        val newPY = pY + dy

        // 4. Collision Detection with Buildings
        var collided = false
        for (place in QorvehMapData.places) {
            // skip the lake center marker collision
            if (place.id == "sarab_lake") continue
            
            val halfW = place.width / 2f + 2.5f // padded collision bounds
            val halfL = place.length / 2f + 2.5f
            
            if (abs(newPX - place.bx) < halfW && abs(newPY - place.by) < halfL) {
                collided = true
                break
            }
        }

        // Cap map boundaries
        val inBounds = newPX in -480f..480f && newPY in -480f..480f

        if (!collided && inBounds) {
            _posX.value = newPX
            _posY.value = newPY
            
            // Increment driving distance statistics
            val distanceTraveled = sqrt(dx * dx + dy * dy)
            if (distanceTraveled > 0.01f) {
                viewModelScope.launch {
                    repository.updateDrivingStats(distanceTraveled, 0, false)
                }
            }
        } else {
            // Collision event! Stop car, shake screen, add to stat
            _carSpeed.value = -speed * 0.3f // bounce back slightly
            viewModelScope.launch {
                _screenShake.emit(true)
                repository.updateDrivingStats(0f, 0, true)
            }
        }
    }

    // Helper to check if coordinates are within road bounds of Qorveh grid
    private fun checkIfOnRoad(x: Float, y: Float): Boolean {
        // Roundabout check at Imam Khomeini Square
        val distToCenter = sqrt(x * x + y * y)
        if (distToCenter < 35f) return true

        // Seyyed Jamaleddin Blvd (North-South): x is [-15, 15]
        if (abs(x) < 15f && abs(y) < 480f) return true

        // Shariati Street (East-West): y is [-12, 12]
        if (abs(y) < 12f && abs(x) < 480f) return true

        // Taleghani Street (East-West at y=220): y is [212.5, 227.5]
        if (abs(y - 220f) < 8f && abs(x) < 480f) return true

        // Aboozar Street (North-South at x=-220): x is [-227.5, -212.5]
        if (abs(x + 220f) < 8f && abs(y) < 480f) return true

        // Lake shore road (around South park)
        if (y < -260f && y > -380f && abs(x) < 120f && distToCenter > 330f) return true

        return false
    }

    // 3. User Actions / Controls
    fun setCameraMode(mode: CameraMode) {
        _camera.value = _camera.value.copy(mode = mode)
    }

    fun rotateCamera(yawDelta: Float, pitchDelta: Float) {
        val current = _camera.value
        val newHeading = current.heading + yawDelta
        val newPitch = (current.pitch + pitchDelta).coerceIn(-1.2f, 0.4f)
        _camera.value = current.copy(heading = newHeading, pitch = newPitch)
    }

    fun setZoom(zoom: Float) {
        _camera.value = _camera.value.copy(zoom = zoom.coerceIn(0.5f, 4.0f))
    }

    fun selectVehicle(vehicle: Vehicle) {
        _activeVehicle.value = vehicle
        viewModelScope.launch {
            repository.updateActiveVehicle(vehicle.name)
        }
    }

    fun selectPlace(place: Building3D?) {
        _selectedPlace.value = place
        if (place != null) {
            // Load custom reviews for this place from Room
            reviewsJob?.cancel()
            reviewsJob = viewModelScope.launch {
                repository.getReviewsForPlace(place.id).collect { list ->
                    _activePlaceReviews.value = list
                }
            }
        } else {
            _activePlaceReviews.value = emptyList()
        }
    }

    fun toggleSavedPlace(placeId: String) {
        viewModelScope.launch {
            val isSaved = repository.isPlaceSaved(placeId)
            if (isSaved) {
                repository.unsavePlace(placeId)
            } else {
                repository.savePlace(placeId)
            }
        }
    }

    fun submitUserReview(placeId: String, rating: Int, text: String, author: String) {
        viewModelScope.launch {
            repository.addReview(placeId, rating, text, author)
        }
    }

    fun setTimeOfDay(time: Float) {
        _timeOfDay.value = time.coerceIn(0f, 24f)
    }

    fun toggleTimeFlow() {
        _isTimeFlowing.value = !_isTimeFlowing.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: PlaceCategory?) {
        _selectedCategory.value = category
    }

    fun triggerAutopilot(place: Building3D) {
        _autopilotTarget.value = place
        _isAutopilotActive.value = true
        // Switch to DRIVE mode for immersion!
        setCameraMode(CameraMode.DRIVE)
    }

    fun stopAutopilot() {
        _isAutopilotActive.value = false
        _carSpeed.value = 0f
        isInputGas = false
        isInputBrake = false
    }

    // Teleport directly to landmark/shop
    fun teleportTo(place: Building3D) {
        stopAutopilot()
        // Teleport slightly south of building facing North (heading = 0)
        _posX.value = place.bx
        _posY.value = place.by - 15f
        _carHeading.value = 0f
        _carSpeed.value = 0f
        selectPlace(place)
    }

    // Filtered Places list matching search query & category filter
    val filteredPlaces = combine(searchQuery, selectedCategory) { query, cat ->
        QorvehMapData.places.filter { place ->
            val matchesCat = cat == null || place.category == cat
            val matchesQuery = query.isEmpty() ||
                    place.name.contains(query, ignoreCase = true) ||
                    place.description.contains(query, ignoreCase = true) ||
                    place.address.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QorvehMapData.places
    )
}
