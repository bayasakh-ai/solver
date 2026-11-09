package com.bay.sudokusolver

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bay.sudokusolver.ui.camera.DockedBar
import com.bay.sudokusolver.ui.camera.GridOverlay
import com.bay.sudokusolver.ui.camera.TopChrome
import com.bay.sudokusolver.ui.camera.barContent
import com.bay.sudokusolver.ui.check.CheckScreen
import com.bay.sudokusolver.ui.settings.SettingsScreen
import com.bay.sudokusolver.ui.settings.SettingsStore
import com.bay.sudokusolver.ui.theme.CameraChrome
import com.bay.sudokusolver.ui.theme.SudokuLensTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var result by mutableStateOf<FrameResult?>(null)
    private var error by mutableStateOf<String?>(null)
    /** Freeze: the still shown instead of the live preview; results are ignored meanwhile. */
    private var frozen by mutableStateOf<Bitmap?>(null)
    private var torchOn by mutableStateOf(false)
    private var hasFlash by mutableStateOf(false)
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var classifier: DigitClassifier
    private var analyzer: FrameAnalyzer? = null
    private lateinit var settings: SettingsStore
    private var palette by mutableStateOf(AnswerPalette.DEFAULT)
    private var debug by mutableStateOf(false)
    /** Which screen shows; the camera stays underneath (see Route.kt). */
    private var nav by mutableStateOf(NavStack())
    /** Freeze state before Check opened, restored when it closes. */
    private var frozenBeforeCheck: Bitmap? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) error = "OpenCV failed to load"
        analysisExecutor = Executors.newSingleThreadExecutor()
        settings = SettingsStore(this)
        palette = settings.palette
        debug = settings.debug
        classifier = DigitClassifier(this)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) requestPermission.launch(Manifest.permission.CAMERA)

        setContent {
            SudokuLensTheme(palette) {
                // status bar icons: light over the camera, dark on the Settings page
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !nav.onCamera
                }
                if (hasCameraPermission) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx -> PreviewView(ctx).also { previewView = it; startCamera(it) } },
                            modifier = Modifier.fillMaxSize()
                        )
                        // the still has the view's own size and FILL_CENTER crop, so the
                        // overlay's frame -> view maths lines up on it unchanged
                        frozen?.let {
                            Image(it.asImageBitmap(), contentDescription = "Frozen camera image",
                                contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        }
                        GridOverlay(result, Modifier.fillMaxSize())
                        val content = barContent(result, frozen != null, error,
                            onFreeze = ::toggleFreeze, onReset = ::resetScan, onCheck = ::openCheck)
                        CameraChrome {
                            TopChrome(content, result, showDebug = debug, thumbnail = result?.warped,
                                onSettings = { nav = nav.open(Route.Settings) }, modifier = Modifier.align(Alignment.TopCenter))
                            DockedBar(content, flashOn = if (hasFlash) torchOn else null, onFlash = ::toggleTorch,
                                modifier = Modifier.align(Alignment.BottomCenter))
                        }
                        if (!nav.onCamera) BackHandler(onBack = ::goBack)
                        when (val route = nav.current) {
                            Route.Camera -> Unit
                            Route.Settings -> SettingsScreen(palette, debug,
                                onPalette = ::choosePalette, onDebug = ::chooseDebug, onBack = ::goBack)
                            is Route.Check -> CheckScreen(route.capture, onBack = ::goBack)
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

    private fun choosePalette(p: AnswerPalette) {
        palette = p
        settings.palette = p
        analyzer?.palette = p // steps are re-chosen from scratch on the next frame
    }

    private fun chooseDebug(on: Boolean) {
        debug = on
        settings.debug = on
    }

    /** Freeze keeps the current answers on a still; Live resumes the camera. */
    private fun toggleFreeze() {
        if (frozen != null) {
            frozen = null
        } else {
            frozen = previewView?.bitmap // null if the preview isn't streaming yet: stays live
        }
    }

    /**
     * Play / Check the reading: hand the settled reading to the core flow. The camera
     * freezes underneath, so Back returns to the same answers.
     */
    private fun openCheck() {
        val capture = result?.capture ?: return
        frozenBeforeCheck = frozen
        if (frozen == null) frozen = previewView?.bitmap
        nav = nav.open(Route.Check(capture))
    }

    private fun goBack() {
        val leaving = nav.current
        nav = nav.back() ?: return
        if (leaving is Route.Check) frozen = frozenBeforeCheck
    }

    /** Reset / Rescan: forget the puzzle and start reading again. */
    private fun resetScan() {
        analyzer?.requestReset()
        frozen = null
        result = null
    }

    private fun toggleTorch() {
        camera?.cameraControl?.enableTorch(!torchOn) // torchOn follows cameraInfo.torchState
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
            val frameAnalyzer = FrameAnalyzer(classifier) { r ->
                runOnUiThread { if (frozen == null) result = r } // frozen: keep the answers as they were
            }
            frameAnalyzer.palette = palette
            analyzer = frameAnalyzer
            analysis.setAnalyzer(analysisExecutor, frameAnalyzer)

            provider.unbindAll()
            val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera = cam
            hasFlash = cam.cameraInfo.hasFlashUnit()
            // follow the real torch state (CameraX turns it off when the camera closes)
            cam.cameraInfo.torchState.observe(this) { torchOn = it == TorchState.ON }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.execute { classifier.close() } // runs after the last frame finishes
        analysisExecutor.shutdown()
    }
}
