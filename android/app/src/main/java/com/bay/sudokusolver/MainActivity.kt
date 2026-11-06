package com.bay.sudokusolver

import android.Manifest
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

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
            MaterialTheme {
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

    private fun statusText(r: FrameResult?): String = when {
        r == null -> "Starting camera…"
        r.corners == null -> r.message
        else -> "%s\n%d digits · sharpness %.0f · %.0f fps · %d ms".format(
            r.message, r.digits, r.sharpness, r.fps, r.processMs)
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

/**
 * Draws the detected grid over the camera preview. The preview fills the screen
 * (PreviewView's default FILL_CENTER), so frame pixels are scaled by the larger
 * ratio and centred — the same maths here keeps the outline on top of the puzzle.
 */
@Composable
private fun GridOverlay(result: FrameResult?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = result ?: return@Canvas
        val c = r.corners ?: return@Canvas
        val scale = max(size.width / r.frameWidth, size.height / r.frameHeight)
        val ox = (size.width - r.frameWidth * scale) / 2f
        val oy = (size.height - r.frameHeight * scale) / 2f
        fun corner(i: Int) = Offset(ox + c[2 * i] * scale, oy + c[2 * i + 1] * scale)
        val tl = corner(0); val tr = corner(1); val br = corner(2); val bl = corner(3)

        // true perspective: maps (u, v) in the unit square onto the grid's 4 corners
        val dx1 = tr.x - br.x; val dx2 = bl.x - br.x; val dx3 = tl.x - tr.x + br.x - bl.x
        val dy1 = tr.y - br.y; val dy2 = bl.y - br.y; val dy3 = tl.y - tr.y + br.y - bl.y
        val det = dx1 * dy2 - dx2 * dy1
        val g = (dx3 * dy2 - dx2 * dy3) / det
        val h = (dx1 * dy3 - dx3 * dy1) / det
        val a = tr.x - tl.x + g * tr.x; val b = bl.x - tl.x + h * bl.x
        val d = tr.y - tl.y + g * tr.y; val e = bl.y - tl.y + h * bl.y
        fun at(u: Float, v: Float): Offset {
            val w = g * u + h * v + 1f
            return Offset((a * u + b * v + tl.x) / w, (d * u + e * v + tl.y) / w)
        }

        val color = if (r.isSharp) Color(0xFF2ECC71) else Color(0xFFF1C40F)
        for (k in 1..2) { // the 3x3 box lines, thin
            val f = k / 3f
            drawLine(color, at(f, 0f), at(f, 1f), strokeWidth = 1.5.dp.toPx())
            drawLine(color, at(0f, f), at(1f, f), strokeWidth = 1.5.dp.toPx())
        }
        val outline = Path().apply {
            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
        }
        drawPath(outline, color, style = Stroke(width = 4.dp.toPx()))

        // the answers, drawn into each empty cell with the cell's own size and perspective
        val puzzle = r.puzzle ?: return@Canvas
        val solution = r.solution ?: return@Canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.rgb(46, 204, 113)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val canvas = drawContext.canvas.nativeCanvas
        for (i in 0 until 81) {
            if (puzzle[i] != 0) continue
            val row = i / 9
            val col = i % 9
            val u = (col + 0.5f) / 9f
            val center = at(u, (row + 0.5f) / 9f)
            val cellHeight = (at(u, (row + 1f) / 9f) - at(u, row / 9f)).getDistance()
            paint.textSize = cellHeight * 0.7f
            canvas.drawText(solution[i].toString(), center.x, center.y + paint.textSize * 0.36f, paint)
        }
    }
}
