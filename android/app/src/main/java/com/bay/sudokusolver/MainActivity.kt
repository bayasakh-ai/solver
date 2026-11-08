package com.bay.sudokusolver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import com.bay.sudokusolver.ui.camera.GridOverlay
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var result by mutableStateOf<FrameResult?>(null)
    private var error by mutableStateOf<String?>(null)
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var classifier: DigitClassifier

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) error = "OpenCV failed to load"
        analysisExecutor = Executors.newSingleThreadExecutor()
        classifier = DigitClassifier(this)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) requestPermission.launch(Manifest.permission.CAMERA)

        setContent {
            SudokuLensTheme {
                if (hasCameraPermission) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx -> PreviewView(ctx).also { startCamera(it) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        GridOverlay(result, Modifier.fillMaxSize())
                        StatusLine(error ?: statusText(result), Modifier.align(Alignment.TopCenter))
                        result?.warped?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Flattened grid",
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .safeDrawingPadding()
                                    .padding(12.dp)
                                    .size(150.dp)
                                    .border(2.dp, Color.White)
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Camera permission is needed to scan puzzles.")
                    }
                }
            }
        }
    }

    // Temporary wording until the docked bar (stage 2, step 4) replaces the status line.
    private fun stateText(s: ScanState): String = when (s) {
        ScanState.Searching -> "Point the camera at a sudoku"
        is ScanState.Reading -> "Reading digits… ${s.frames}/${s.window}"
        is ScanState.HoldSteady -> "Hold steady…"
        is ScanState.Solved ->
            if (s.fixes == 0) "Solved ✓" else "Solved ✓ (fixed ${s.fixes} misread digit${if (s.fixes > 1) "s" else ""})"
        is ScanState.NeedsReview -> when (s.reason) {
            ScanState.Reason.TOO_FEW_DIGITS -> "Too few digits found — move closer"
            ScanState.Reason.NO_UNIQUE_SOLUTION -> "Can't solve this reading — try better light or another angle"
        }
    }

    private fun statusText(r: FrameResult?): String = when {
        r == null -> "Starting camera…"
        r.corners == null -> stateText(r.state)
        else -> "%s\n%d digits · sharpness %.0f · %.0f fps · %d ms · steps %d µs%s".format(
            stateText(r.state), r.digits, r.sharpness, r.fps, r.processMs, r.stepsMicros,
            if (r.inkCells > 0) " · ignoring your ink in ${r.inkCells} cells" else "")
    }

    private fun startCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            // Same 4:3 shape for preview and analysis, so overlay coordinates line up.
            val previewSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val analysisSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder().setResolutionSelector(previewSelector).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(analysisSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, FrameAnalyzer(classifier) { r ->
                runOnUiThread { result = r }
            })

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.execute { classifier.close() } // runs after the last frame finishes
        analysisExecutor.shutdown()
    }
}

@Composable
private fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .safeDrawingPadding()
            .padding(12.dp)
            .background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
