package com.example.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Saved/Favorite Places Entity
@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey val placeId: String,
    val savedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

// 2. User Reviews Entity
@Entity(tableName = "user_reviews")
data class UserReview(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val placeId: String,
    val rating: Int,
    val reviewText: String,
    val authorName: String,
    val timestamp: Long = System.currentTimeMillis()
)

// 3. Driving Stats Entity
@Entity(tableName = "driving_stats")
data class DrivingStat(
    @PrimaryKey val id: Int = 1, // Single row for tracking total stats
    val totalDistanceMeters: Float = 0f,
    val totalTimeSeconds: Long = 0,
    val collisionCount: Int = 0,
    val activeVehicle: String = "Pride"
)

// DAOs
@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places")
    fun getSavedPlacesFlow(): Flow<List<SavedPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlace(place: SavedPlace)

    @Query("DELETE FROM saved_places WHERE placeId = :placeId")
    suspend fun unsavePlace(placeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_places WHERE placeId = :placeId)")
    suspend fun isPlaceSaved(placeId: String): Boolean
}

@Dao
interface UserReviewDao {
    @Query("SELECT * FROM user_reviews WHERE placeId = :placeId ORDER BY timestamp DESC")
    fun getReviewsForPlaceFlow(placeId: String): Flow<List<UserReview>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: UserReview)

    @Query("DELETE FROM user_reviews WHERE id = :id")
    suspend fun deleteReview(id: Int)
}

@Dao
interface DrivingStatDao {
    @Query("SELECT * FROM driving_stats WHERE id = 1")
    suspend fun getStats(): DrivingStat?

    @Query("SELECT * FROM driving_stats WHERE id = 1")
    fun getStatsFlow(): Flow<DrivingStat?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStats(stat: DrivingStat)
}

// Room Database Definition
@Database(
    entities = [SavedPlace::class, UserReview::class, DrivingStat::class],
    version = 1,
    exportSchema = false
)
abstract class QorvehDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun userReviewDao(): UserReviewDao
    abstract fun drivingStatDao(): DrivingStatDao

    companion object {
        @Volatile
        private var INSTANCE: QorvehDatabase? = null

        fun getDatabase(context: Context): QorvehDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QorvehDatabase::class.java,
                    "qorveh_3d_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository implementation for neat clean architecture
class QorvehRepository(private val db: QorvehDatabase) {
    val savedPlaces: Flow<List<SavedPlace>> = db.savedPlaceDao().getSavedPlacesFlow()
    val drivingStats: Flow<DrivingStat?> = db.drivingStatDao().getStatsFlow()

    fun getReviewsForPlace(placeId: String): Flow<List<UserReview>> =
        db.userReviewDao().getReviewsForPlaceFlow(placeId)

    suspend fun savePlace(placeId: String, notes: String = "") {
        db.savedPlaceDao().savePlace(SavedPlace(placeId, notes = notes))
    }

    suspend fun unsavePlace(placeId: String) {
        db.savedPlaceDao().unsavePlace(placeId)
    }

    suspend fun isPlaceSaved(placeId: String): Boolean {
        return db.savedPlaceDao().isPlaceSaved(placeId)
    }

    suspend fun addReview(placeId: String, rating: Int, text: String, author: String) {
        db.userReviewDao().insertReview(
            UserReview(placeId = placeId, rating = rating, reviewText = text, authorName = author)
        )
    }

    suspend fun updateDrivingStats(distance: Float, timeSeconds: Long, collision: Boolean) {
        val current = db.drivingStatDao().getStats() ?: DrivingStat()
        val updated = current.copy(
            totalDistanceMeters = current.totalDistanceMeters + distance,
            totalTimeSeconds = current.totalTimeSeconds + timeSeconds,
            collisionCount = current.collisionCount + (if (collision) 1 else 0)
        )
        db.drivingStatDao().saveStats(updated)
    }

    suspend fun updateActiveVehicle(vehicle: String) {
        val current = db.drivingStatDao().getStats() ?: DrivingStat()
        db.drivingStatDao().saveStats(current.copy(activeVehicle = vehicle))
    }
}
