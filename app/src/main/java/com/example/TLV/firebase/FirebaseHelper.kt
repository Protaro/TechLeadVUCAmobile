package com.example.TLV.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
        return getStudentByLRN(qrCode)
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

        //Increment feedingattendance field of a student
        val studentRef = firestore.collection("Students").whereEqualTo("lrn", lrn).get().await()
        studentRef.documents.firstOrNull()?.reference?.update(
            "feedingattendance",
            FieldValue.increment(1)
        )?.await()
    }

    suspend fun checkStudentInCurrentDateCollection(lrn: String): Boolean {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return firestore.collection(currentDate).whereEqualTo("LRN", lrn).get().await().isEmpty
    }

    suspend fun addStudentToMeasurementsCollection(
        name: String,
        lrn: String,
        timestamp: String,
        height: Float,
        weight: Float
    ) {
        val collectionName = "Measurements"
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Query for an existing entry with the same LRN and current date
        val snapshot = firestore.collection(collectionName)
            .whereEqualTo("LRN", lrn)
            .whereGreaterThanOrEqualTo("Timestamp", "$currentDate 00:00:00")
            .whereLessThanOrEqualTo("Timestamp", "$currentDate 23:59:59")
            .get()
            .await()

        val existingDocument = snapshot.documents.firstOrNull()

        if (existingDocument != null) {
            // Update the existing entry
            existingDocument.reference.update(
                mapOf(
                    "Height" to height,
                    "Weight" to weight,
                    "Timestamp" to timestamp
                )
            ).await()
        } else {
            // Add a new entry
            val newStudentMeasurement = hashMapOf(
                "Name" to name,
                "LRN" to lrn,
                "Height" to height,
                "Weight" to weight,
                "Timestamp" to timestamp
            )
            firestore.collection(collectionName).add(newStudentMeasurement).await()
        }
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
