import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DashboardData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dashboardDataDao(): DashboardDataDao
}
