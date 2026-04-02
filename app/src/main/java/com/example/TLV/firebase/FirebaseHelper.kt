package com.example.TLV.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Student(
    val name: String = "",
    val lrn: String = "",
    val timestamp: String = ""
)

data class StudentMeasurement(
    val name: String = "",
    val lrn: String = "",
    val height: Float? = null,
    val weight: Float? = null,
    val timestamp: String = ""
)

data class StudentRatings(
    val name: String = "",
    val lrn: String = "",
    val numeracy: String = "",
    val literacy: String = "",
    val timestamp: String = ""
)

class FirebaseHelper {

    private val firestore = FirebaseFirestore.getInstance()

    // ====================== HELPER FUNCTIONS ======================

    internal fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    internal fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun formatMiddleName(middlename: String?): String {
        return middlename?.split(" ")
            ?.joinToString("") { it.firstOrNull()?.toString()?.uppercase() ?: "" } + "."
    }

    private fun buildFullName(document: DocumentSnapshot): String {
        val firstName = document.getString("firstname") ?: ""
        val middleName = document.getString("middlename")
        val lastName = document.getString("lastname") ?: ""
        val middleInitial = formatMiddleName(middleName)
        return "$firstName $middleInitial $lastName".trim()
    }

    // ====================== READ OPERATIONS ======================

    suspend fun getAllValidNames(): List<String> {
        val snapshot = firestore.collection("Students").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val firstName = doc.getString("firstname")
            val lastName = doc.getString("lastname")
            if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
                val middleInitial = formatMiddleName(doc.getString("middlename"))
                "$firstName $middleInitial $lastName".trim()
            } else null
        }
    }

    suspend fun getAllValidLRNs(): List<String> {
        val snapshot = firestore.collection("Students").get().await()
        return snapshot.documents.mapNotNull { it.getString("lrn") }
    }

    suspend fun getStudentByName(fullName: String): Student? {
        val snapshot = firestore.collection("Students").get().await()
        return snapshot.documents.firstOrNull { buildFullName(it) == fullName }?.let { doc ->
            Student(
                name = buildFullName(doc),
                lrn = doc.getString("lrn") ?: ""
            )
        }
    }

    suspend fun getStudentByLRN(lrn: String): Student? {
        val snapshot = firestore.collection("Students")
            .whereEqualTo("lrn", lrn)
            .get().await()
        return snapshot.documents.firstOrNull()?.let { doc ->
            Student(
                name = buildFullName(doc),
                lrn = doc.getString("lrn") ?: ""
            )
        }
    }

    // ====================== WRITE OPERATIONS ======================

    // Attendance (Feeding collection)
    suspend fun addStudentToAttendanceCollection(lrn: String, timestamp: String) {
        val currentDate = getCurrentDate()
        val attendanceData = mapOf(lrn to timestamp)

        firestore.collection("Feeding").document(currentDate)
            .set(attendanceData, SetOptions.merge()).await()

        incrementFeedingAttendance(lrn)
    }

    private suspend fun incrementFeedingAttendance(lrn: String) {
        val studentRef = firestore.collection("Students")
            .whereEqualTo("lrn", lrn)
            .get().await()

        studentRef.documents.firstOrNull()?.reference
            ?.update("feedingattendance", FieldValue.increment(1))
            ?.await()
    }

    // Measurements - Document ID = "yyyy-MM-dd - LRN"
    suspend fun addStudentToMeasurementsCollection(
        name: String,
        lrn: String,
        timestamp: String,
        height: Float,
        weight: Float
    ) {
        val currentDate = getCurrentDate()
        val documentId = "$currentDate - $lrn"

        val data = hashMapOf(
            "name" to name,
            "lrn" to lrn,
            "height" to height,
            "weight" to weight,
            "timestamp" to timestamp
        )

        firestore.collection("Measurements")
            .document(documentId)
            .set(data, SetOptions.merge())
            .await()
    }

    // Filipino Scores (formerly Literacy)
    suspend fun addStudentToFilipinoCollection(name: String, lrn: String, rating: String, timestamp: String) {
        val currentDate = getCurrentDate()
        val documentId = "$currentDate - $lrn"

        val data = hashMapOf(
            "name" to name,           // will be filled if needed later
            "lrn" to lrn,
            "literacy" to rating,
            "timestamp" to timestamp
        )

        firestore.collection("Filipino Scores")
            .document(documentId)
            .set(data, SetOptions.merge())
            .await()
    }

    // Math Scores
    suspend fun addStudentToMathCollection(name: String, lrn: String, rating: String, timestamp: String) {
        val currentDate = getCurrentDate()
        val documentId = "$currentDate - $lrn"

        val data = hashMapOf(
            "name" to name,
            "lrn" to lrn,
            "numeracy" to rating,
            "timestamp" to timestamp
        )

        firestore.collection("Math Scores")
            .document(documentId)
            .set(data, SetOptions.merge())
            .await()
    }

    // ====================== READ FOR TABLES ======================

    suspend fun getStudentsFromAttendanceCollection(date: String): List<Student> {
        val doc = firestore.collection("Feeding").document(date).get().await()
        val students = mutableListOf<Student>()

        if (doc.exists()) {
            for ((lrn, timestamp) in doc.data ?: emptyMap<String, Any>()) {
                if (timestamp is String) {
                    getStudentByLRN(lrn)?.let {
                        students.add(it.copy(timestamp = timestamp))
                    }
                }
            }
        }
        return students
    }

    suspend fun getStudentsFromMeasurementsCollection(): List<StudentMeasurement> {
        val snapshot = firestore.collection("Measurements").get().await()
        return snapshot.documents.mapNotNull { doc ->
            StudentMeasurement(
                name = doc.getString("name") ?: "",
                lrn = doc.getString("lrn") ?: "",
                height = doc.getDouble("height")?.toFloat(),
                weight = doc.getDouble("weight")?.toFloat(),
                timestamp = doc.getString("timestamp") ?: ""
            )
        }
    }

    suspend fun getStudentsFromRatingsCollection(): List<StudentRatings> {
        val filipinoMap = getRatingsMap("Filipino Scores")
        val mathMap = getRatingsMap("Math Scores")

        // Combine both
        filipinoMap.forEach { (lrn, filipinoRating) ->
            mathMap[lrn]?.let { mathRating ->
                mathMap[lrn] = mathRating.copy(literacy = filipinoRating.literacy)
            } ?: run {
                mathMap[lrn] = filipinoRating
            }
        }

        return mathMap.values.toList()
    }

    private suspend fun getRatingsMap(collectionName: String): MutableMap<String, StudentRatings> {
        return try {
            val snapshot = firestore.collection(collectionName).get().await()

            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val lrn = doc.getString("lrn") ?: return@mapNotNull null
                val rating = doc.getString("literacy") ?: doc.getString("numeracy") ?: ""
                val timestamp = doc.getString("timestamp") ?: ""

                lrn to StudentRatings(name, lrn, rating, rating, timestamp)
            }.toMap().toMutableMap()
        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Error reading $collectionName", e)
            mutableMapOf()
        }
    }
}