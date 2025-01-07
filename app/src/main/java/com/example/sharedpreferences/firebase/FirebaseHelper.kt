package com.example.sharedpreferences.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

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

class FirebaseHelper {
    private val firestore = FirebaseFirestore.getInstance()

    private fun formatMiddleName(middlename: String?): String {
        if (middlename.isNullOrBlank()) return ""
        return middlename.split(" ")
            .joinToString("") { it.firstOrNull()?.toString()?.uppercase() ?: "" } + "."
    }

    suspend fun getAllValidNames(): List<String> {
        val snapshot = firestore.collection("Students").get().await()
        return snapshot.documents.mapNotNull {
            val firstname = it.getString("firstname")
            val middlename = it.getString("middlename")
            val lastname = it.getString("lastname")
            if (!firstname.isNullOrEmpty() && !lastname.isNullOrEmpty()) {
                val middleInitial = formatMiddleName(middlename)
                "$firstname $middleInitial $lastname".trim()
            } else null
        }
    }

    suspend fun getAllValidLRNs(): List<String> {
        val snapshot = firestore.collection("Students").get().await()
        return snapshot.documents.mapNotNull { it.getString("lrn") }
    }

    suspend fun getStudentByName(fullName: String): Student? {
        val snapshot = firestore.collection("Students").get().await()
        val matchingDocument = snapshot.documents.firstOrNull {
            val firstname = it.getString("firstname")
            val middlename = it.getString("middlename")
            val lastname = it.getString("lastname")
            val middleInitial = formatMiddleName(middlename)
            val combinedName = "$firstname $middleInitial $lastname".trim()
            combinedName == fullName
        }
        return matchingDocument?.let {
            val firstname = it.getString("firstname")
            val middlename = it.getString("middlename")
            val lastname = it.getString("lastname")
            val middleInitial = formatMiddleName(middlename)
            Student(
                name = "$firstname $middleInitial $lastname".trim(),
                lrn = it.getString("lrn") ?: ""
            )
        }
    }

    suspend fun getStudentByLRN(lrn: String): Student? {
        val snapshot = firestore.collection("Students")
            .whereEqualTo("lrn", lrn)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let {
            val firstname = it.getString("firstname")
            val middlename = it.getString("middlename")
            val lastname = it.getString("lastname")
            val middleInitial = formatMiddleName(middlename)
            Student(
                name = "$firstname $middleInitial $lastname".trim(),
                lrn = lrn
            )
        }
    }


    suspend fun getStudentByQR(qrCode: String): Student? {
        val snapshot = firestore.collection("Scanner")
            .whereEqualTo("LRN", qrCode)
            .get()
            .await()
            getStudentByLRN(qrCode)

        return snapshot.documents.firstOrNull()?.let {
            val firstname = it.getString("firstname")
            val middlename = it.getString("middlename")
            val lastname = it.getString("lastname")
            val middleInitial = formatMiddleName(middlename)
            Student(
                name = "$firstname $middleInitial $lastname".trim(),
                lrn = qrCode
            )
        }
    }


    suspend fun uploadScannedLRNToFirebase(lrn: String) {
        val scannedData = hashMapOf(
            "LRN" to lrn,
        )
        firestore.collection("Scanner").add(scannedData).await()
    }

    suspend fun addStudentToDateCollection(name: String, lrn: String, timestamp: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newStudent = hashMapOf(
            "Name" to name,
            "LRN" to lrn,
            "Timestamp" to timestamp
        )
        firestore.collection(currentDate).add(newStudent).await()
    }

    suspend fun addStudentToMeasurementsCollection(
        name: String,
        lrn: String,
        timestamp: String,
        height: Float,
        weight: Float
    ) {
        val collectionName = "Measurements"
        val newStudentMeasurement = hashMapOf(
            "Name" to name,
            "LRN" to lrn,
            "Height" to height,
            "Weight" to weight,
            "Timestamp" to timestamp
        )
        firestore.collection(collectionName).add(newStudentMeasurement).await()
    }

    suspend fun getStudentsFromDateCollection(date: String): List<Student> {
        val snapshot = firestore.collection(date)
            .orderBy("Timestamp")
            .get()
            .await()
        return snapshot.documents.mapNotNull {
            val name = it.getString("Name")
            val lrn = it.getString("LRN")
            val timestamp = it.getString("Timestamp")
            if (name != null && lrn != null && timestamp != null) {
                Student(name, lrn, timestamp)
            } else null
        }
    }

    suspend fun getStudentsFromMeasurementsCollection(): List<StudentMeasurement> {
        val snapshot = firestore.collection("Measurements")
            .orderBy("Timestamp")
            .get()
            .await()
        return snapshot.documents.mapNotNull {
            val name = it.getString("Name")
            val lrn = it.getString("LRN")
            val height = it.getDouble("Height")?.toFloat()
            val weight = it.getDouble("Weight")?.toFloat()
            val timestamp = it.getString("Timestamp")
            if (name != null && lrn != null && timestamp != null) {
                StudentMeasurement(name, lrn, height, weight, timestamp)
            } else null
        }
    }
}