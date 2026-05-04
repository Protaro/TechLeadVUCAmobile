package com.example.TLV.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
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

// Internal plain-data representation of a student record.
// Used as the common currency between Firestore DocumentSnapshots
// and the SharedPreferences offline cache.
private data class StudentRecord(
    val lrn: String,
    val firstname: String,
    val middlename: String,
    val lastname: String
)

// ====================== FIREBASE HELPER ======================

class FirebaseHelper(private val context: Context? = null) {

    private val firestore = FirebaseFirestore.getInstance()

    // In-memory caches (session-scoped)
    private var studentRecords: List<StudentRecord>? = null
    private var filipinoCache: DocumentSnapshot? = null
    private var mathCache: DocumentSnapshot? = null
    private var measurementsCache: DocumentSnapshot? = null

    companion object {
        private const val PREFS_NAME = "tlv_student_cache"
        private const val KEY_STUDENTS = "cached_students"

        // Process-level cache so all FirebaseHelper instances share one warm list
        @Volatile private var sharedStudentRecords: List<StudentRecord>? = null
    }

    // ====================== SHARED-PREFS PERSISTENCE ======================

    /**
     * Persists the student roster to SharedPreferences as JSON so it survives
     * app restarts without any network connection.
     */
    private fun persistStudents(records: List<StudentRecord>) {
        sharedStudentRecords = records      // update process-level cache immediately

        val ctx = context ?: return
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("lrn",        r.lrn)
                put("firstname",  r.firstname)
                put("middlename", r.middlename)
                put("lastname",   r.lastname)
            })
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STUDENTS, arr.toString())
            .apply()
    }

    /** Loads the roster from SharedPreferences. Returns null if nothing saved yet. */
    private fun loadStudentsFromPrefs(): List<StudentRecord>? {
        val ctx = context ?: return null
        val json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STUDENTS, null) ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StudentRecord(
                    lrn        = o.optString("lrn"),
                    firstname  = o.optString("firstname"),
                    middlename = o.optString("middlename"),
                    lastname   = o.optString("lastname")
                )
            }
        } catch (e: Exception) {
            Log.w("FirebaseHelper", "Failed to parse prefs student cache", e)
            null
        }
    }

    // ====================== STUDENT CACHE LOADING ======================

    private suspend fun ensureStudentCacheLoaded() {
        if (studentRecords != null) return

        // 1. Process-level cache (fastest — already parsed this session)
        sharedStudentRecords?.let {
            if (it.isNotEmpty()) { studentRecords = it; return }
        }

        // 2. Firestore on-disk cache (works offline after first-ever sync)
        try {
            val snap = firestore.collection("Students").get(Source.CACHE).await()
            if (snap.documents.isNotEmpty()) {
                val records = snap.documents.toStudentRecords()
                studentRecords = records
                persistStudents(records)
                return
            }
        } catch (e: Exception) {
            Log.d("FirebaseHelper", "Firestore disk cache miss for Students: ${e.message}")
        }

        // 3. SharedPreferences (survives process kills / restarts with no network)
        loadStudentsFromPrefs()?.let {
            if (it.isNotEmpty()) {
                sharedStudentRecords = it
                studentRecords = it
                return
            }
        }

        // 4. Network fetch — only when all offline sources are exhausted
        try {
            val snap = firestore.collection("Students").get(Source.DEFAULT).await()
            val records = snap.documents.toStudentRecords()
            studentRecords = records
            persistStudents(records)
        } catch (e: Exception) {
            Log.e("FirebaseHelper", "Could not load Students from any source", e)
            studentRecords = emptyList()    // prevent repeated retries this session
        }
    }

    private fun List<DocumentSnapshot>.toStudentRecords() = map { doc ->
        StudentRecord(
            lrn        = doc.getString("lrn")        ?: "",
            firstname  = doc.getString("firstname")  ?: "",
            middlename = doc.getString("middlename") ?: "",
            lastname   = doc.getString("lastname")   ?: ""
        )
    }

    // ====================== DAILY-DOC CACHES ======================

    private suspend fun ensureFilipinoCacheLoaded() {
        if (filipinoCache != null) return
        val docRef = firestore.collection("Filipino Scores").document(getCurrentDate())
        filipinoCache = try { docRef.get(Source.CACHE).await() }
        catch (e: Exception) {
            try { docRef.get(Source.DEFAULT).await() } catch (e2: Exception) { null }
        }
    }

    private suspend fun ensureMathCacheLoaded() {
        if (mathCache != null) return
        val docRef = firestore.collection("Math Scores").document(getCurrentDate())
        mathCache = try { docRef.get(Source.CACHE).await() }
        catch (e: Exception) {
            try { docRef.get(Source.DEFAULT).await() } catch (e2: Exception) { null }
        }
    }

    private suspend fun ensureMeasurementsCacheLoaded() {
        if (measurementsCache != null) return
        val docRef = firestore.collection("Measurements").document(getCurrentDate())
        measurementsCache = try { docRef.get(Source.CACHE).await() }
        catch (e: Exception) {
            try { docRef.get(Source.DEFAULT).await() } catch (e2: Exception) { null }
        }
    }

    suspend fun getCachedFilipinoScores(): List<StudentRatings> {
        ensureFilipinoCacheLoaded()
        return filipinoCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            StudentRatings(
                name      = map["name"]      as? String ?: "",
                lrn       = lrn,
                literacy  = map["literacy"]  as? String ?: "",
                numeracy  = "",
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    suspend fun getCachedMathScores(): List<StudentRatings> {
        ensureMathCacheLoaded()
        return mathCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            StudentRatings(
                name      = map["name"]      as? String ?: "",
                lrn       = lrn,
                literacy  = "",
                numeracy  = map["numeracy"]  as? String ?: "",
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    suspend fun getCachedMeasurements(): List<StudentMeasurement> {
        ensureMeasurementsCacheLoaded()
        return measurementsCache?.data?.mapNotNull { (lrn, value) ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            StudentMeasurement(
                name      = map["name"]      as? String ?: "",
                lrn       = lrn,
                height    = (map["height"]  as? Number)?.toFloat(),
                weight    = (map["weight"]  as? Number)?.toFloat(),
                timestamp = map["timestamp"] as? String ?: ""
            )
        } ?: emptyList()
    }

    // ====================== DATE / TIME ======================

    fun getCurrentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun getCurrentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    // ====================== NAME HELPERS ======================

    private fun formatMiddleName(middlename: String?): String {
        if (middlename.isNullOrBlank()) return ""
        return middlename.split(" ")
            .joinToString("") { it.firstOrNull()?.uppercase() ?: "" } + "."
    }

    private fun StudentRecord.fullName(): String =
        listOf(firstname, formatMiddleName(middlename), lastname)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    // ====================== LOOKUP ======================

    suspend fun getAllValidNames(): List<String> {
        ensureStudentCacheLoaded()
        return studentRecords!!
            .filter { it.firstname.isNotEmpty() && it.lastname.isNotEmpty() }
            .map { it.fullName() }
    }

    suspend fun getAllValidLRNs(): List<String> {
        ensureStudentCacheLoaded()
        return studentRecords!!.map { it.lrn }.filter { it.isNotEmpty() }
    }

    suspend fun getStudentByName(name: String): Student? {
        ensureStudentCacheLoaded()
        return studentRecords!!.firstOrNull { it.fullName() == name }
            ?.let { Student(it.fullName(), it.lrn) }
    }

    suspend fun getStudentByLRN(lrn: String): Student? {
        ensureStudentCacheLoaded()
        return studentRecords!!.firstOrNull { it.lrn == lrn }
            ?.let { Student(it.fullName(), lrn) }
    }

    // ====================== ATTENDANCE ======================

    /**
     * Fire-and-forget write. No .await() so it returns immediately when offline;
     * Firestore queues the mutation and syncs when connectivity is restored.
     */
    suspend fun addStudentToAttendanceCollection(lrn: String, timestamp: String) {
        firestore.collection("Feeding")
            .document(getCurrentDate())
            .set(mapOf(lrn to timestamp), SetOptions.merge())
        // intentionally no .await()
    }

    fun listenToAttendance(
        date: String,
        onUpdate: (List<Student>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("Feeding")
            .document(date)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    onUpdate(emptyList()); return@addSnapshotListener
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val list = mutableListOf<Student>()
                    snapshot.data?.forEach { (lrn, timestamp) ->
                        if (timestamp is String)
                            getStudentByLRN(lrn)?.let { list.add(it.copy(timestamp = timestamp)) }
                    }
                    withContext(Dispatchers.Main) { onUpdate(list) }
                }
            }
    }

    // ====================== MEASUREMENTS ======================

    /** Fire-and-forget write — safe offline. */
    suspend fun addStudentToMeasurementsCollection(
        name: String, lrn: String, timestamp: String, height: Float, weight: Float
    ) {
        firestore.collection("Measurements")
            .document(getCurrentDate())
            .set(
                mapOf(lrn to mapOf(
                    "name" to name, "height" to height,
                    "weight" to weight, "timestamp" to timestamp
                )),
                SetOptions.merge()
            )
        // intentionally no .await()
    }

    fun listenToMeasurements(
        date: String,
        onUpdate: (List<StudentMeasurement>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("Measurements").document(date)
            .addSnapshotListener { snapshot, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val list = mutableListOf<StudentMeasurement>()
                    (snapshot?.data ?: measurementsCache?.data)?.forEach { (lrn, value) ->
                        val map = value as? Map<*, *> ?: return@forEach
                        list.add(StudentMeasurement(
                            name      = map["name"]      as? String ?: "",
                            lrn       = lrn,
                            height    = (map["height"]  as? Number)?.toFloat(),
                            weight    = (map["weight"]  as? Number)?.toFloat(),
                            timestamp = map["timestamp"] as? String ?: ""
                        ))
                    }
                    if (snapshot != null && snapshot.exists()) measurementsCache = snapshot
                    withContext(Dispatchers.Main) { onUpdate(list) }
                }
            }
    }

    // ====================== FILIPINO SCORES ======================

    /** Fire-and-forget write — safe offline. */
    suspend fun addStudentToFilipinoCollection(
        name: String, lrn: String, rating: String, timestamp: String
    ) {
        firestore.collection("Filipino Scores")
            .document(getCurrentDate())
            .set(
                mapOf(lrn to mapOf("name" to name, "literacy" to rating, "timestamp" to timestamp)),
                SetOptions.merge()
            )
        // intentionally no .await()
    }

    // ====================== MATH SCORES ======================

    /** Fire-and-forget write — safe offline. */
    suspend fun addStudentToMathCollection(
        name: String, lrn: String, rating: String, timestamp: String
    ) {
        firestore.collection("Math Scores")
            .document(getCurrentDate())
            .set(
                mapOf(lrn to mapOf("name" to name, "numeracy" to rating, "timestamp" to timestamp)),
                SetOptions.merge()
            )
        // intentionally no .await()
    }

    // ====================== RATINGS (combined listener) ======================

    fun listenToRatings(
        date: String,
        onUpdate: (List<StudentRatings>) -> Unit
    ): List<ListenerRegistration> {

        val filipinoMap = mutableMapOf<String, StudentRatings>()
        val mathMap     = mutableMapOf<String, StudentRatings>()

        fun emit() {
            val combined = mutableMapOf<String, StudentRatings>()
            filipinoMap.forEach { (lrn, f) ->
                combined[lrn] = StudentRatings(
                    name = f.name, lrn = lrn,
                    literacy  = f.literacy,
                    numeracy  = mathMap[lrn]?.numeracy ?: "",
                    timestamp = f.timestamp
                )
            }
            mathMap.forEach { (lrn, m) ->
                if (!combined.containsKey(lrn)) {
                    combined[lrn] = StudentRatings(
                        name = m.name, lrn = lrn,
                        literacy  = "",
                        numeracy  = m.numeracy,
                        timestamp = m.timestamp
                    )
                }
            }
            onUpdate(combined.values.toList())
        }

        val fRef = firestore.collection("Filipino Scores").document(date)
        val mRef = firestore.collection("Math Scores").document(date)

        val fListener = fRef.addSnapshotListener { snapshot, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                filipinoMap.clear()
                (snapshot?.data ?: filipinoCache?.data)?.forEach { (lrn, value) ->
                    val map = value as? Map<*, *> ?: return@forEach
                    filipinoMap[lrn] = StudentRatings(
                        name = map["name"] as? String ?: "", lrn = lrn,
                        literacy  = map["literacy"]  as? String ?: "",
                        numeracy  = "",
                        timestamp = map["timestamp"] as? String ?: ""
                    )
                }
                if (snapshot != null && snapshot.exists()) filipinoCache = snapshot
                withContext(Dispatchers.Main) { emit() }
            }
        }

        val mListener = mRef.addSnapshotListener { snapshot, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                mathMap.clear()
                (snapshot?.data ?: mathCache?.data)?.forEach { (lrn, value) ->
                    val map = value as? Map<*, *> ?: return@forEach
                    mathMap[lrn] = StudentRatings(
                        name = map["name"] as? String ?: "", lrn = lrn,
                        literacy  = "",
                        numeracy  = map["numeracy"]  as? String ?: "",
                        timestamp = map["timestamp"] as? String ?: ""
                    )
                }
                if (snapshot != null && snapshot.exists()) mathCache = snapshot
                withContext(Dispatchers.Main) { emit() }
            }
        }

        return listOf(fListener, mListener)
    }

    // ====================== EXISTENCE ======================

    suspend fun ensureDailyDocumentsExist() {
        val date  = getCurrentDate()
        val batch = firestore.batch()
        val empty = emptyMap<String, Any>()
        batch.set(firestore.collection("Feeding").document(date),         empty, SetOptions.merge())
        batch.set(firestore.collection("Measurements").document(date),    empty, SetOptions.merge())
        batch.set(firestore.collection("Filipino Scores").document(date), empty, SetOptions.merge())
        batch.set(firestore.collection("Math Scores").document(date),     empty, SetOptions.merge())
        try {
            batch.commit().await()
        } catch (e: Exception) {
            Log.w("FirebaseHelper", "ensureDailyDocumentsExist offline — Firestore will retry: ${e.message}")
        }
    }
}