package com.example.TLV.firebase

import android.util.Log
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ====================== DATA CLASSES ======================

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

// ====================== FIREBASE HELPER ======================

class FirebaseHelper {

    private val firestore = FirebaseFirestore.getInstance()

    private var studentCache: List<DocumentSnapshot>? = null
    private var filipinoCache: DocumentSnapshot? = null
    private var mathCache: DocumentSnapshot? = null
    private var measurementsCache: DocumentSnapshot? = null

    // ====================== CACHE ======================

    private suspend fun ensureStudentCacheLoaded() {
        if (studentCache != null) return
        studentCache = firestore.collection("Students")
            .get(Source.CACHE)
            .await()
            .documents
    }

    private suspend fun ensureFilipinoCacheLoaded() {
        if (filipinoCache != null) return

        val date = getCurrentDate()
        val docRef = firestore.collection("Filipino Scores").document(date)

        filipinoCache = try {
            docRef.get(Source.CACHE).await()
        } catch (e: Exception) {
            docRef.get().await()
        }
    }

    private suspend fun ensureMathCacheLoaded() {
        if (mathCache != null) return

        val date = getCurrentDate()
        val docRef = firestore.collection("Math Scores").document(date)

        mathCache = try {
            docRef.get(Source.CACHE).await()
        } catch (e: Exception) {
            docRef.get().await()
        }
    }

    private suspend fun ensureMeasurementsCacheLoaded() {
        if (measurementsCache != null) return

        val date = getCurrentDate()
        val docRef = firestore.collection("Measurements").document(date)

        measurementsCache = try {
            docRef.get(Source.CACHE).await()
        } catch (e: Exception) {
            docRef.get().await()
        }
    }

    suspend fun getCachedFilipinoScores(): List<StudentRatings> {
        ensureFilipinoCacheLoaded()

        return filipinoCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null

            StudentRatings(
                name = map["name"] as? String ?: "",
                lrn = lrn,
                literacy = map["literacy"] as? String ?: "",
                numeracy = "",
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    suspend fun getCachedMathScores(): List<StudentRatings> {
        ensureMathCacheLoaded()

        return mathCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null

            StudentRatings(
                name = map["name"] as? String ?: "",
                lrn = lrn,
                literacy = "",
                numeracy = map["numeracy"] as? String ?: "",
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    suspend fun getCachedMeasurements(): List<StudentMeasurement> {
        ensureMeasurementsCacheLoaded()

        return measurementsCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null

            StudentMeasurement(
                name = map["name"] as? String ?: "",
                lrn = lrn,
                height = (map["height"] as? Number)?.toFloat(),
                weight = (map["weight"] as? Number)?.toFloat(),
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    // ====================== DATE/TIME ======================

    fun getCurrentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    // ====================== NAME ======================

    private fun formatMiddleName(middlename: String?): String {
        return middlename?.split(" ")
            ?.joinToString("") { it.firstOrNull()?.uppercase() ?: "" } + "."
    }

    private fun buildFullName(doc: DocumentSnapshot): String {
        val first = doc.getString("firstname") ?: ""
        val middle = formatMiddleName(doc.getString("middlename"))
        val last = doc.getString("lastname") ?: ""
        return "$first $middle $last".trim()
    }

    // ====================== LOOKUP ======================

    suspend fun getAllValidNames(): List<String> {
        ensureStudentCacheLoaded()
        return studentCache!!.mapNotNull { doc ->
            val first = doc.getString("firstname")
            val last = doc.getString("lastname")
            if (!first.isNullOrEmpty() && !last.isNullOrEmpty()) {
                "${first} ${formatMiddleName(doc.getString("middlename"))} $last".trim()
            } else null
        }
    }

    suspend fun getAllValidLRNs(): List<String> {
        ensureStudentCacheLoaded()
        return studentCache!!.mapNotNull { it.getString("lrn") }
    }

    suspend fun getStudentByName(name: String): Student? {
        ensureStudentCacheLoaded()
        return studentCache!!.firstOrNull { buildFullName(it) == name }?.let {
            Student(buildFullName(it), it.getString("lrn") ?: "")
        }
    }

    suspend fun getStudentByLRN(lrn: String): Student? {
        ensureStudentCacheLoaded()
        return studentCache!!.firstOrNull { it.getString("lrn") == lrn }?.let {
            Student(buildFullName(it), lrn)
        }
    }

    // ====================== ATTENDANCE ======================

    suspend fun addStudentToAttendanceCollection(lrn: String, timestamp: String) {
        val date = getCurrentDate()

        firestore.collection("Feeding")
            .document(date)
            .set(mapOf(lrn to timestamp), SetOptions.merge())
            .await()
    }

    fun listenToAttendance(
        date: String,
        onUpdate: (List<Student>) -> Unit
    ): ListenerRegistration {

        return firestore.collection("Feeding")
            .document(date)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val list = mutableListOf<Student>()

                    snapshot.data?.forEach { (lrn, timestamp) ->
                        if (timestamp is String) {
                            getStudentByLRN(lrn)?.let {
                                list.add(it.copy(timestamp = timestamp))
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onUpdate(list)
                    }
                }
            }
    }

    // ====================== MEASUREMENTS ======================

    suspend fun addStudentToMeasurementsCollection(
        name: String,
        lrn: String,
        timestamp: String,
        height: Float,
        weight: Float
    ) {
        val date = getCurrentDate()

        val data = mapOf(
            lrn to mapOf(
                "name" to name,
                "height" to height,
                "weight" to weight,
                "timestamp" to timestamp
            )
        )

        firestore.collection("Measurements")
            .document(date)
            .set(data, SetOptions.merge())
            .await()
    }

    fun listenToMeasurements(
        date: String,
        onUpdate: (List<StudentMeasurement>) -> Unit
    ): ListenerRegistration {

        val docRef = firestore.collection("Measurements").document(date)

        docRef.get(Source.CACHE)

        return docRef.addSnapshotListener { snapshot, _ ->
            CoroutineScope(Dispatchers.IO).launch {

                val list = mutableListOf<StudentMeasurement>()
                val sourceData = snapshot?.data ?: measurementsCache?.data

                sourceData?.forEach { (lrn, value) ->
                    val map = value as? Map<*, *> ?: return@forEach

                    list.add(
                        StudentMeasurement(
                            name = map["name"] as? String ?: "",
                            lrn = lrn,
                            height = (map["height"] as? Number)?.toFloat(),
                            weight = (map["weight"] as? Number)?.toFloat(),
                            timestamp = map["timestamp"] as? String ?: ""
                        )
                    )
                }

                if (snapshot != null && snapshot.exists()) {
                    measurementsCache = snapshot
                }

                withContext(Dispatchers.Main) {
                    onUpdate(list)
                }
            }
        }
    }

    // ====================== FILIPINO ======================

    suspend fun addStudentToFilipinoCollection(
        name: String,
        lrn: String,
        rating: String,
        timestamp: String
    ) {
        val date = getCurrentDate()

        val data = mapOf(
            lrn to mapOf(
                "name" to name,
                "literacy" to rating,
                "timestamp" to timestamp
            )
        )

        firestore.collection("Filipino Scores")
            .document(date)
            .set(data, SetOptions.merge())
            .await()
    }

    // ====================== MATH ======================

    suspend fun addStudentToMathCollection(
        name: String,
        lrn: String,
        rating: String,
        timestamp: String
    ) {
        val date = getCurrentDate()

        val data = mapOf(
            lrn to mapOf(
                "name" to name,
                "numeracy" to rating,
                "timestamp" to timestamp
            )
        )

        firestore.collection("Math Scores")
            .document(date)
            .set(data, SetOptions.merge())
            .await()
    }

    // ====================== RATINGS ======================

    fun listenToRatings(
        date: String,
        onUpdate: (List<StudentRatings>) -> Unit
    ): List<ListenerRegistration> {

        val filipinoMap = mutableMapOf<String, StudentRatings>()
        val mathMap = mutableMapOf<String, StudentRatings>()

        fun emit() {
            val combined = mutableMapOf<String, StudentRatings>()

            filipinoMap.forEach { (lrn, f) ->
                val m = mathMap[lrn]
                combined[lrn] = StudentRatings(
                    name = f.name,
                    lrn = lrn,
                    literacy = f.literacy,
                    numeracy = m?.numeracy ?: "",
                    timestamp = f.timestamp
                )
            }

            mathMap.forEach { (lrn, m) ->
                if (!combined.containsKey(lrn)) {
                    combined[lrn] = StudentRatings(
                        name = m.name,
                        lrn = lrn,
                        literacy = "",
                        numeracy = m.numeracy,
                        timestamp = m.timestamp
                    )
                }
            }

            onUpdate(combined.values.toList())
        }

        val fRef = firestore.collection("Filipino Scores").document(date)
        val mRef = firestore.collection("Math Scores").document(date)

        fRef.get(Source.CACHE)
        mRef.get(Source.CACHE)

        val fListener = fRef.addSnapshotListener { snapshot, _ ->
            CoroutineScope(Dispatchers.IO).launch {

                filipinoMap.clear()
                val sourceData = snapshot?.data ?: filipinoCache?.data

                sourceData?.forEach { (lrn, value) ->
                    val map = value as? Map<*, *> ?: return@forEach

                    filipinoMap[lrn] = StudentRatings(
                        name = map["name"] as? String ?: "",
                        lrn = lrn,
                        literacy = map["literacy"] as? String ?: "",
                        numeracy = "",
                        timestamp = map["timestamp"] as? String ?: ""
                    )
                }

                if (snapshot != null && snapshot.exists()) {
                    filipinoCache = snapshot
                }

                withContext(Dispatchers.Main) {
                    emit()
                }
            }
        }

        val mListener = mRef.addSnapshotListener { snapshot, _ ->
            CoroutineScope(Dispatchers.IO).launch {

                mathMap.clear()
                val sourceData = snapshot?.data ?: mathCache?.data

                sourceData?.forEach { (lrn, value) ->
                    val map = value as? Map<*, *> ?: return@forEach

                    mathMap[lrn] = StudentRatings(
                        name = map["name"] as? String ?: "",
                        lrn = lrn,
                        literacy = "",
                        numeracy = map["numeracy"] as? String ?: "",
                        timestamp = map["timestamp"] as? String ?: ""
                    )
                }

                if (snapshot != null && snapshot.exists()) {
                    mathCache = snapshot
                }

                withContext(Dispatchers.Main) {
                    emit()
                }
            }
        }

        return listOf(fListener, mListener)
    }

    // ====================== EXISTENCE ======================

    suspend fun ensureDailyDocumentsExist() {
        val date = getCurrentDate()

        val batch = firestore.batch()

        val feedingRef = firestore.collection("Feeding").document(date)
        val measurementRef = firestore.collection("Measurements").document(date)
        val filipinoRef = firestore.collection("Filipino Scores").document(date)
        val mathRef = firestore.collection("Math Scores").document(date)

        batch.set(feedingRef, emptyMap<String, Any>(), SetOptions.merge())
        batch.set(measurementRef, emptyMap<String, Any>(), SetOptions.merge())
        batch.set(filipinoRef, emptyMap<String, Any>(), SetOptions.merge())
        batch.set(mathRef, emptyMap<String, Any>(), SetOptions.merge())

        batch.commit().await()
    }
}