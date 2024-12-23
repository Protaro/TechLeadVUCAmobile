import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DashboardDataDao {
    @Insert
    fun insertData(data: DashboardData)

    @Query("SELECT * FROM DashboardData ORDER BY timestamp ASC")
    fun getAllData(): List<DashboardData>

    @Query("DELETE FROM DashboardData")
    fun clearAllData()
}
