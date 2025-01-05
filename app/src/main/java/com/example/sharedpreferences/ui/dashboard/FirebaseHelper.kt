package com.example.sharedpreferences.firebase

import android.widget.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class Student(
    val name: String = "",
    val studentNumber: String = "",
    val timestamp: String = ""
)

data class StudentMeasurement(
    val name: String = "",
    val studentNumber: String = "",
    val height: Float? = 0f,
    val weight: Float? = 0f,
    val timestamp: String = ""
)

class FirebaseHelper {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getAllValidNames(): List<String> {
        val snapshot = firestore.collection("validStudents").get().await()
        return snapshot.documents.mapNotNull { it.getString("Name") }
    }

    suspend fun getAllValidStudentNumbers(): List<String> {
        val snapshot = firestore.collection("validStudents").get().await()
        return snapshot.documents.mapNotNull { it.getString("Student Number") }
    }

    suspend fun getStudentByName(name: String): Student? {
        val snapshot = firestore.collection("validStudents")
            .whereEqualTo("Name", name)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let {
            Student(
                name = it.getString("Name") ?: "",
                studentNumber = it.getString("Student Number") ?: ""
            )
        }
    }

    suspend fun getStudentByNumber(studentNumber: String): Student? {
        val snapshot = firestore.collection("validStudents")
            .whereEqualTo("Student Number", studentNumber)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.let {
            Student(
                name = it.getString("Name") ?: "",
                studentNumber = it.getString("Student Number") ?: ""
            )
        }
    }

    suspend fun addStudentToDateCollection(name: String, studentNumber: String, timestamp: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val collectionName = currentDate

        val newStudent = hashMapOf(
            "Name" to name,
            "Student Number" to studentNumber,
            "Timestamp" to timestamp
        )

        firestore.collection(collectionName).add(newStudent).await()
    }

    suspend fun addStudentToMeasurementsCollection(name: String, studentNumber: String, timestamp: String, height: Float, weight: Float) {
        val collectionName = "Measurements"

        val newStudentMeasurement = hashMapOf(
            "name" to name,
            "lrn" to studentNumber,
            "height" to height,
            "weight" to weight,
            "timestamp" to timestamp
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
            val studentNumber = it.getString("Student Number")
            val timestamp = it.getString("Timestamp")
            if (name != null && studentNumber != null && timestamp != null) {
                Student(name, studentNumber, timestamp)
            } else null
        }
    }

    suspend fun getStudentsFromMeasurementsCollection(): List<StudentMeasurement> {
        val snapshot = firestore.collection("Measurements")
            .get()
            .await()

        return snapshot.documents.mapNotNull {
            val name = it.getString("name")
            val studentNumber = it.getString("lrn")
            val height = it.getDouble("height")?.toFloat()
            val weight = it.getDouble("weight")?.toFloat()
            val timestamp = it.getString("timestamp")
            if (name != null && studentNumber != null && timestamp != null) {
                StudentMeasurement(name, studentNumber, height, weight, timestamp)
            } else null
        }
    }
}
