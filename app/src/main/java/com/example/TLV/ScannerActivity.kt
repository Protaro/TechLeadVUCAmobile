package com.example.TLV

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.TLV.databinding.ActivityScannerBinding
import com.example.TLV.firebase.FirebaseHelper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// ── Data model for the attendance log ──────────────────────────────────────
data class ScannedEntry(
    val lrn: String,
    val name: String,
    val time: String,
    val isDuplicate: Boolean
)

@ExperimentalGetImage
class ScannerActivity : AppCompatActivity() {

    // ── Modes ──────────────────────────────────────────────────────────────
    private enum class ScanMode { FILL, ATTENDANCE }
    private var currentMode = ScanMode.FILL

    // ── Binding ────────────────────────────────────────────────────────────
    private lateinit var binding: ActivityScannerBinding

    // ── Camera ────────────────────────────────────────────────────────────
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private lateinit var barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner
    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    // ── Scan gate ─────────────────────────────────────────────────────────
    private val scanLock = AtomicBoolean(false)

    // ── Attendance state ──────────────────────────────────────────────────
    private val firebaseHelper by lazy { FirebaseHelper(this) }
    private val scannedEntries = mutableListOf<ScannedEntry>()
    private val scannedLRNs   = mutableSetOf<String>()
    private lateinit var scannedAdapter: ScannedStudentAdapter

    private val SCAN_COOLDOWN_MS = 2_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        setupModeToggle()
        setupRecyclerView()
        binding.exitButton.setOnClickListener { finish() }

        if (allPermissionsGranted()) startCamera() else requestPermissions()
    }

    // ── Mode toggle ────────────────────────────────────────────────────────

    private fun setupModeToggle() {
        binding.toggleMode.setOnCheckedChangeListener { _, isChecked ->
            currentMode = if (isChecked) ScanMode.ATTENDANCE else ScanMode.FILL
            applyModeUI()
            scanLock.set(false)
        }
        applyModeUI()
    }

    private fun applyModeUI() {
        when (currentMode) {
            ScanMode.FILL -> {
                binding.instructionText.text = "Align QR code within the frame"
                binding.rvScanned.visibility = View.GONE
            }
            ScanMode.ATTENDANCE -> {
                binding.instructionText.text = "Scanning continuously — tap Exit when done"
                binding.rvScanned.visibility = View.VISIBLE
            }
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        scannedAdapter = ScannedStudentAdapter()
        binding.rvScanned.apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
                .also { it.stackFromEnd = true }
            adapter = scannedAdapter
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview  = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.cameraPreview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::processImageProxy) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // @ExperimentalGetImage is satisfied by the class-level annotation
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val lrn = barcode.rawValue?.trim() ?: continue
                    if (!scanLock.compareAndSet(false, true)) break
                    when (currentMode) {
                        ScanMode.FILL       -> handleFillScan(lrn)
                        ScanMode.ATTENDANCE -> handleAttendanceScan(lrn)
                    }
                    break
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    // ── Fill mode ─────────────────────────────────────────────────────────

    private fun handleFillScan(lrn: String) {
        toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        flashScreen()
        setResult(RESULT_OK, Intent().apply { putExtra("SCAN_RESULT", lrn) })
        finish()
    }

    // ── Attendance mode ───────────────────────────────────────────────────

    private fun handleAttendanceScan(lrn: String) {
        val isDuplicate = scannedLRNs.contains(lrn)
        val timestamp   = firebaseHelper.getCurrentTimestamp()
        val timeOnly    = timestamp.substringAfter(" ")

        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) {
                firebaseHelper.getStudentByLRN(lrn)?.name ?: lrn
            }

            if (isDuplicate) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_REORDER, 200)
            } else {
                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                withContext(Dispatchers.IO) {
                    firebaseHelper.addStudentToAttendanceCollection(lrn, timestamp)
                }
                scannedLRNs.add(lrn)
            }

            flashScreen()

            scannedEntries.add(ScannedEntry(lrn, name, timeOnly, isDuplicate))
            scannedAdapter.submitList(scannedEntries.toList())
            binding.rvScanned.scrollToPosition(scannedEntries.lastIndex)

            binding.root.postDelayed({ scanLock.set(false) }, SCAN_COOLDOWN_MS)
        }
    }

    // ── Flash ─────────────────────────────────────────────────────────────

    private fun flashScreen() {
        ObjectAnimator.ofFloat(binding.flashOverlay, "alpha", 0.5f, 0f).apply {
            duration = 300
            start()
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else
            finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
        toneGen.release()
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────

class ScannedStudentAdapter :
    ListAdapter<ScannedEntry, ScannedStudentAdapter.VH>(DiffCB()) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus:    TextView = view.findViewById(R.id.tvStatus)
        val tvName:      TextView = view.findViewById(R.id.tvName)
        val tvTime:      TextView = view.findViewById(R.id.tvTime)
        val tvDuplicate: TextView = view.findViewById(R.id.tvDuplicate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_student, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvName.text = item.name
        holder.tvTime.text = item.time
        if (item.isDuplicate) {
            holder.tvStatus.text = "!"
            holder.tvStatus.setTextColor(0xFFFF9800.toInt())
            holder.tvDuplicate.visibility = View.VISIBLE
        } else {
            holder.tvStatus.text = "✓"
            holder.tvStatus.setTextColor(0xFF4CAF50.toInt())
            holder.tvDuplicate.visibility = View.GONE
        }
    }

    class DiffCB : DiffUtil.ItemCallback<ScannedEntry>() {
        override fun areItemsTheSame(a: ScannedEntry, b: ScannedEntry) =
            a.lrn == b.lrn && a.time == b.time
        override fun areContentsTheSame(a: ScannedEntry, b: ScannedEntry) = a == b
    }
}