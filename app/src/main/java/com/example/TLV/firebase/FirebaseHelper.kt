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
import kotlin.collections.iterator

data class Student(
    val name: String = "",
    val lrn: String = "",
    val timestamp: String = ""
)

data class StudentMeasurement(
    val name: String = "",
    val lrn: String = "",
    val height: Float? = 0f,
    val weight: Float? = 0f,
    val timestamp: String = ""
)

data class StudentRatings(
    val lrn: String = "",
    val numeracy: String = "",
    val literacy: String = "",
    val timestamp: String = ""
)

class FirebaseHelper {
    private val firestore = FirebaseFirestore.getInstance()

    // Fetch all documents from a collection
    private suspend fun getAllDocuments(collection: String) = firestore.collection(collection).get().await()

    // Get all valid names from the Students collection
    suspend fun getAllValidNames(): List<String> {
        val snapshot = getAllDocuments("Students")
        return snapshot.documents.mapNotNull { document ->
            val firstName = document.getString("firstname")
            val middleName = document.getString("middlename")
            val lastName = document.getString("lastname")
            if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
                val middleInitial = formatMiddleName(middleName)
                "$firstName $middleInitial $lastName".trim()
            } else null
        }
    }

    // Get all valid LRN numbers from the Students collection
    suspend fun getAllValidLRNs(): List<String> {
        val snapshot = getAllDocuments("Students")
        return snapshot.documents.mapNotNull { it.getString("lrn") }
    }

    // Get a student by a specific field
    private suspend fun getStudentByField(field: String, value: String): Student? {
        val snapshot = firestore.collection("Students").whereEqualTo(field, value).get().await()
        return snapshot.documents.firstOrNull()?.let { document ->
            val fullName = buildFullName(document)
            Student(
                name = fullName,
                lrn = document.getString("lrn") ?: ""
            )
        }
    }

    // Get a student by their full name
    suspend fun getStudentByName(fullName: String): Student? {
        val snapshot = getAllDocuments("Students")
        return snapshot.documents.firstOrNull { document ->
            buildFullName(document) == fullName
        }?.let { document ->
            Student(
                name = buildFullName(document),
                lrn = document.getString("lrn") ?: ""
            )
        }
    }

    // Build full name from document
    private fun buildFullName(document: DocumentSnapshot): String {
        val firstName = document.getString("firstname")
        val middleName = document.getString("middlename")
        val lastName = document.getString("lastname")
        val middleInitial = formatMiddleName(middleName)
        return "$firstName $middleInitial $lastName".trim()
    }

    // Get a student by their LRN
    suspend fun getStudentByLRN(lrn: String): Student? {
        return getStudentByField("lrn", lrn)
    }

    suspend fun getStudentByQR(qrCode: String): Student? {
        return getStudentByLRN(qrCode)
    }

    // Add a student to the attendance collection
    suspend fun addStudentToAttendanceCollection(lrn: String, timestamp: String) {
        val currentDate = getCurrentDate()
        val attendanceData = mapOf(lrn to timestamp)
        firestore.collection("Feeding").document(currentDate)
            .set(attendanceData, SetOptions.merge()).await()

        incrementFeedingAttendance(lrn)
    }

    // Increment feeding attendance for a student
    private suspend fun incrementFeedingAttendance(lrn: String) {
        val studentRef = firestore.collection("Students").whereEqualTo("lrn", lrn).get().await()
        studentRef.documents.firstOrNull()?.reference?.update("feedingattendance", FieldValue.increment(1))?.await()
    }

    // Add a student to the measurements collection
    suspend fun addStudentToMeasurementsCollection(
        name: String,
        lrn: String,
        timestamp: String,
        height: Float,
        weight: Float
    ) {
        val currentDate = getCurrentDate()
        val existingDocument = getExistingDocument("Measurements", lrn, currentDate)

        if (existingDocument != null) {
            updateExistingMeasurement(existingDocument, height, weight, timestamp)
        } else {
            addNewMeasurement(name, lrn, height, weight, timestamp)
        }
    }

    // Get existing document based on LRN and date
    private suspend fun getExistingDocument(collection: String, lrn: String, currentDate: String) =
        firestore.collection(collection)
            .whereEqualTo("lrn", lrn)
            .whereGreaterThanOrEqualTo("timestamp", "$currentDate 00:00:00")
            .whereLessThanOrEqualTo("timestamp", "$currentDate 23:59:59")
            .get()
            .await()
            .documents.firstOrNull()

    // Update existing measurement record
    private suspend fun updateExistingMeasurement(existingDocument: DocumentSnapshot, height: Float, weight: Float, timestamp: String) {
        existingDocument.reference.update(
            mapOf(
                "height" to height,
                "weight" to weight,
                "timestamp" to timestamp
            )
        ).await()
    }

    // Add a new measurement record
    private suspend fun addNewMeasurement(name: String, lrn: String, height: Float, weight: Float, timestamp: String) {
        val newStudentMeasurement = hashMapOf(
            "name" to name,
            "lrn" to lrn,
            "height" to height,
            "weight" to weight,
            "timestamp" to timestamp
        )
        firestore.collection("Measurements").add(newStudentMeasurement).await()
    }

    // Add a student to the literacy collection
    suspend fun addStudentToLiteracyCollection(lrn: String, rating: String?, timestamp: String) {
        ensureCollectionExists("Literacy_Scores")
        addStudentToScoreCollection("Literacy_Scores", lrn, rating, timestamp)
    }

    suspend fun addStudentToNumeracyCollection(lrn: String, rating: String?, timestamp: String) {
        ensureCollectionExists("Numeracy_Scores")
        addStudentToScoreCollection("Numeracy_Scores", lrn, rating, timestamp)
    }

    // Add a student to a score collection (literacy or numeracy)
    private suspend fun addStudentToScoreCollection(collectionName: String, lrn: String, rating: String?, timestamp: String) {
        val currentDate = getCurrentDate()
        val existingDocument = getExistingDocument(collectionName, lrn, currentDate)

        if (existingDocument != null) {
            // Update existing record
            existingDocument.reference.update(
                mapOf(
                    "rating" to rating,
                    "timestamp" to timestamp
                )
            ).await()
        } else {
            // Add new record
            val newRecord = hashMapOf(
                "lrn" to lrn,
                "rating" to rating,
                "timestamp" to timestamp
            )
            firestore.collection(collectionName).add(newRecord).await()
        }
    }

    // Check if a student is in the attendance collection
    suspend fun checkStudentInAttendanceCollection(lrn: String): Boolean {
        val currentDate = getCurrentDate()
        val snapshot = firestore.collection("Feeding").document(currentDate)
        return !snapshot.get().await().contains(lrn)
    }

    // Get students from the attendance collection for a specific date
    suspend fun getStudentsFromAttendanceCollection(date: String): List<Student> {
        val dateDocument = firestore.collection("Feeding").document(date).get().await()
        val students = mutableListOf<Student>()

        if (dateDocument.exists()) {
            for ((lrn, timestamp) in dateDocument.data ?: emptyMap<String, Any>()) {
                if (timestamp is String) {
                    getStudentByLRN(lrn)?.let { student ->
                        students.add(student.copy(timestamp = timestamp))
                    }
                }
            }
        }
        return students
    }

    // Get students from the measurements collection
    suspend fun getStudentsFromMeasurementsCollection(): List<StudentMeasurement> {
        val snapshot = firestore.collection("Measurements").orderBy("timestamp").get().await()
        return snapshot.documents.mapNotNull { document ->
            val name = document.getString("name")
            val lrn = document.getString("lrn")
            val height = document.getDouble("height")?.toFloat()
            val weight = document.getDouble("weight")?.toFloat()
            val timestamp = document.getString("timestamp")
            if (name != null && lrn != null && timestamp != null) {
                StudentMeasurement(name, lrn, height, weight, timestamp)
            } else null
        }
    }

    // Get students from the ratings collection
    suspend fun getStudentsFromRatingsCollection(): List<StudentRatings> {
        val numeracyMap = getRatingsMap("Numeracy_Scores")
        val literacyMap = getRatingsMap("Literacy_Scores")

        // Combine the results from both maps
        literacyMap.forEach { (lrn, literacyRating) ->
            // If the LRN exists in the numeracy map, combine the ratings
            numeracyMap[lrn]?.let { numeracyRating ->
                numeracyMap[lrn] = numeracyRating.copy(literacy = literacyRating.literacy, timestamp = literacyRating.timestamp)
            } ?: run {
                // If it doesn't exist, add it to the numeracy map
                numeracyMap[lrn] = literacyRating
            }
        }

        return numeracyMap.values.toList()
    }

    // Get ratings map for a specific collection
    private suspend fun getRatingsMap(collectionName: String): MutableMap<String, StudentRatings> {
        Log.d("FirebaseHelper", "Fetching ratings from collection: $collectionName")
        return try {
            val snapshot = firestore.collection(collectionName).orderBy("timestamp").get().await()
            Log.d("FirebaseHelper", "Fetched ${snapshot.size()} documents from $collectionName")

            snapshot.documents.mapNotNull {
                val lrn = it.getString("lrn") // Ensure the field name matches exactly
                val rating = it.getString("rating") // Get the rating as a String
                val timestamp = it.getString("timestamp") // Ensure the field name matches exactly

                if (lrn != null && rating != null && timestamp != null) {
                    lrn to StudentRatings(lrn, rating, rating, timestamp) // Store rating as a String
                } else {
                    Log.w("FirebaseHelper", "Document missing required fields: $it")
                    null
                }
            }.toMap().toMutableMap()
        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Error fetching ratings from $collectionName: ${e.message}", e)
            mutableMapOf() // Return an empty map on error
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun formatMiddleName(middlename: String?): String {
        return middlename?.split(" ")
            ?.joinToString("") { it.firstOrNull()?.toString()?.uppercase() ?: "" } + "."
    }

    suspend fun ensureCollectionExists(collectionName: String) {
        // Attempt to get the collection
        val snapshot = firestore.collection(collectionName).get().await()

        // If the collection is empty, create a placeholder document
        if (snapshot.isEmpty) {
            val placeholderData = hashMapOf("placeholder" to true)
            firestore.collection(collectionName).add(placeholderData).await()
        }
    }
}