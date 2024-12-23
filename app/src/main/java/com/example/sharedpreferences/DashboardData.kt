import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DashboardData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val studentNumber: String,
    val timestamp: Long
)
