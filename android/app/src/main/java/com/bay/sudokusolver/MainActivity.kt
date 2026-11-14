package com.bay.sudokusolver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.lifecycleScope
import com.bay.sudokusolver.ui.newpuzzle.NewPuzzleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bay.sudokusolver.ui.camera.DockedBar
import com.bay.sudokusolver.ui.camera.GridOverlay
import com.bay.sudokusolver.ui.camera.TopChrome
import com.bay.sudokusolver.ui.camera.barContent
import com.bay.sudokusolver.ui.check.CheckScreen
import com.bay.sudokusolver.ui.check.GridPhoto
import com.bay.sudokusolver.ui.choose.ChooseScreen
import com.bay.sudokusolver.ui.play.PlayScreen
import com.bay.sudokusolver.ui.settings.SettingsScreen
import com.bay.sudokusolver.ui.settings.SettingsStore
import com.bay.sudokusolver.ui.theme.CameraChrome
import com.bay.sudokusolver.ui.theme.SudofolioTheme
import com.bay.sudokusolver.ui.welcome.NoCameraScreen
import com.bay.sudokusolver.ui.welcome.WelcomeScreen
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
    private lateinit var handClassifier: DigitClassifier // printed or handwritten? (Hand.kt)
    private lateinit var handReader: DigitClassifier     // which digit the user wrote
    private var analyzer: FrameAnalyzer? = null
    private lateinit var settings: SettingsStore
    private var palette by mutableStateOf(AnswerPalette.DEFAULT)
    private var debug by mutableStateOf(false)
    /** Which screen shows; the camera stays underneath (see Route.kt). */
    private var nav by mutableStateOf(NavStack())
    /** Freeze state before Check opened, restored when the flow returns to the camera. */
    private var frozenBeforeCheck: Bitmap? = null
    // core flow (Route.kt): the reading being checked, its photo, and the game in progress
    private var checkSession by mutableStateOf<CheckSession?>(null)
    private var checkPhoto by mutableStateOf<Bitmap?>(null)
    private var gameState by mutableStateOf<GameState?>(null)
    /** The game in progress. Every change is written to storage (GameStore) right away. */
    private var game: GameState?
        get() = gameState
        set(value) {
            val before = gameState
            gameState = value
            gameStore.update(before, value, gameMode)
        }
    /** How the kept game is played (Hints or Myself): Continue reopens it that way. */
    private var gameMode = PlayMode.HINTS
        set(value) {
            if (field == value) return
            field = value
            gameState?.let { gameStore.update(null, it, value) } // rewrite with the new mode
        }
    private lateinit var gameStore: GameStore
    private var board by mutableStateOf(BoardSettings())
    /** Play a puzzle: the difficulty being generated, null when idle. */
    private var making by mutableStateOf<Difficulty?>(null)
    /** The app is in front: the game clock only runs then. */
    private var inFront by mutableStateOf(false)
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null

    /** The welcome pages while [Route.Welcome] is open (first run, or again from Settings). */
    private var welcome by mutableStateOf<Welcome?>(null)
    // what could be seen around the last camera request (CameraAsk works out the rest)
    private var askedAt = 0L
    private var rationaleBefore = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            // refused twice before: Android answered without showing its dialog, so the
            // tap did nothing the user could see. Only the system settings can allow it now.
            val shown = CameraAsk.dialogWasShown(rationaleBefore,
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA),
                SystemClock.elapsedRealtime() - askedAt)
            if (!granted && !shown) openAppSettings()
            // asked from the welcome: either answer ends it (scanner, or the no-camera screen)
            if (welcome != null) closeWelcome()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initLocal()) error = "OpenCV failed to load"
        analysisExecutor = Executors.newSingleThreadExecutor()
        settings = SettingsStore(this)
        palette = settings.palette
        debug = settings.debug
        board = settings.board
        classifier = DigitClassifier(this)
        handClassifier = DigitClassifier(this, "hand.tflite", outputs = 1)
        handReader = DigitClassifier(this, "hand_digits.tflite")
        // a game kept from before (swiped away, phone restarted, process killed): the
        // camera bar offers Continue. If Android killed the process with the board open,
        // reopen the board itself.
        gameStore = GameStore(this)
        gameStore.load()?.let { saved ->
            gameState = saved.game
            gameMode = saved.mode
            if (savedInstanceState?.getBoolean(KEY_BOARD_OPEN) == true) nav = NavStack().open(saved.board)
        }

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        // First run: the welcome pages open over everything and ask for the camera on
        // their last page, after saying why. Nothing asks on its own at start any more;
        // without the permission the no-camera screen has an Allow camera button.
        if (!settings.welcomeSeen) {
            welcome = Welcome(cameraAllowed = hasCameraPermission)
            nav = nav.open(Route.Welcome)
        }

        setContent {
            SudofolioTheme(palette) {
                // status bar icons: light over the camera, dark on the Settings page
                val view = LocalView.current
                SideEffect {
                    // (and on the no-camera screen, which is a light page in the camera's place)
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        !nav.onCamera || !hasCameraPermission
                }
                // the full-screen flow pages cover the camera: stop reading frames under them
                LaunchedEffect(nav.onCamera) { setAnalyzing(nav.onCamera) }
                Box(Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
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
                            onFreeze = ::toggleFreeze, onReset = ::resetScan, onCheck = ::openCheck,
                            onPlayPuzzle = { nav = nav.open(Route.NewPuzzle) },
                            onContinue = continueAction())
                        CameraChrome {
                            TopChrome(content, result, showDebug = debug, thumbnail = result?.warped,
                                onSettings = { nav = nav.open(Route.Settings) }, modifier = Modifier.align(Alignment.TopCenter))
                            DockedBar(content, flashOn = if (hasFlash) torchOn else null, onFlash = ::toggleTorch,
                                modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    } else {
                        // no camera: a kept game and generated puzzles still work
                        NoCameraScreen(onAllow = ::askForCamera, onContinue = continueAction(),
                            onPlayPuzzle = { nav = nav.open(Route.NewPuzzle) })
                    }
                    // first run, first welcome page: nothing to go back to, so Back leaves the app
                    val leaveOnBack = nav.current == Route.Welcome && welcome?.let { it.isFirst && !it.replay } == true
                    if (!nav.onCamera) BackHandler(enabled = !leaveOnBack, onBack = ::goBack)
                    when (val route = nav.current) {
                        Route.Camera -> Unit
                        Route.NewPuzzle -> NewPuzzleScreen(making,
                            inProgress = game?.takeIf { it.puzzle.source == PuzzleSource.GENERATED && !it.finished },
                            onPick = ::newPuzzle,
                            onResume = { game?.let { nav = nav.open(Route.Play(it.puzzle, gameMode)) } },
                            onBack = ::goBack)
                        Route.Settings -> SettingsScreen(palette, debug, board,
                            onPalette = ::choosePalette, onDebug = ::chooseDebug, onBoard = ::chooseBoard,
                            onWelcome = ::replayWelcome, onBack = ::goBack)
                        Route.Welcome -> welcome?.let { w ->
                            WelcomeScreen(w,
                                onNext = { welcome = w.next() }, onBack = ::goBack, onSkip = { welcome = w.skip() },
                                onEnd = ::endWelcome, onNotNow = ::closeWelcome)
                        }
                        is Route.Check -> checkSession?.let { session ->
                            CheckScreen(session, checkPhoto,
                                onEdit = { i, d -> checkSession = session.edit(i, d) },
                                onMine = { i, on -> checkSession = session.setMine(i, on) },
                                onBack = ::goBack, onRescan = ::startOver, onConfirm = ::confirmReading)
                        }
                        is Route.Choose -> ChooseScreen(route.puzzle,
                            resumable = game?.let { it.puzzle == route.puzzle && it.canUndo } == true,
                            onPlay = { mode -> play(route.puzzle, mode, route.start) }, onBack = ::goBack, onCamera = ::backToCamera,
                            yours = route.start.count { it != 0 })
                        is Route.Play -> PlayScreen(
                            game = game?.takeIf { it.puzzle == route.puzzle } ?: GameState(route.puzzle),
                            mode = route.mode, settings = board, running = inFront,
                            update = { f -> game = f(game?.takeIf { it.puzzle == route.puzzle } ?: GameState(route.puzzle)) },
                            onBack = ::goBack,
                            onSettings = { nav = nav.open(Route.Settings) },
                            onPlayInstead = { nav = nav.back()!!; play(route.puzzle, PlayMode.HINTS) },
                            onScanAnother = ::startOver,
                            onPlayAnother = { startOver(); nav = nav.open(Route.NewPuzzle) })
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

    private fun chooseBoard(b: BoardSettings) {
        board = b
        settings.board = b
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
        val r = result ?: return
        val capture = r.capture ?: return
        frozenBeforeCheck = frozen
        if (frozen == null) frozen = previewView?.bitmap
        // same reading as last time (Back, then Play again): keep the user's fixes
        if (checkSession?.capture !== capture) {
            checkSession = CheckSession(capture)
            checkPhoto = frozen?.let { still -> r.corners?.let { GridPhoto.flatten(still, it, r.frameWidth, r.frameHeight) } }
        }
        nav = nav.open(Route.Check(capture))
    }

    /** Looks right: on to Choose when the fixed grid is a puzzle; otherwise Check says why. */
    private fun confirmReading(): CheckResult {
        val r = checkSession?.confirm() ?: return CheckResult.TooFew
        if (r is CheckResult.Ready) nav = nav.open(Route.Choose(r.puzzle, r.start))
        return r
    }

    /**
     * Choose -> Play. The game in progress for this puzzle carries on; Show solution
     * doesn't touch it. A new game starts from the digits the user wrote on paper ([start]).
     */
    private fun play(puzzle: Puzzle, mode: PlayMode, start: IntArray = IntArray(81)) {
        if (mode != PlayMode.SOLUTION) {
            if (game?.puzzle != puzzle) game = GameState(puzzle, start)
            gameMode = mode
        }
        nav = nav.open(Route.Play(puzzle, mode))
    }

    /** Settings -> How scanning works: the welcome pages again, closing back to Settings. */
    private fun replayWelcome() {
        welcome = Welcome(cameraAllowed = hasCameraPermission, replay = true)
        nav = nav.open(Route.Welcome)
    }

    /** The last welcome page's main button. */
    private fun endWelcome() {
        when (welcome?.end) {
            // the answer comes back in requestPermission, which closes the welcome. Seen is
            // written now: if the app dies behind Android's dialog, the welcome doesn't start over.
            WelcomeEnd.ALLOW_CAMERA -> { settings.welcomeSeen = true; askForCamera() }
            WelcomeEnd.START, WelcomeEnd.DONE -> closeWelcome()
            null -> Unit
        }
    }

    /** Done, Start scanning, Not now, or the camera was answered: don't show it at start again. */
    private fun closeWelcome() {
        settings.welcomeSeen = true
        welcome = null
        if (nav.current == Route.Welcome) nav = nav.back() ?: nav
    }

    /** Ask Android for the camera (welcome, no-camera screen). */
    private fun askForCamera() {
        rationaleBefore = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        askedAt = SystemClock.elapsedRealtime()
        requestPermission.launch(Manifest.permission.CAMERA)
    }

    /** The app's page in the system settings, where a twice-refused permission can be allowed. */
    private fun openAppSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)))
    }

    private fun goBack() {
        if (nav.current == Route.Welcome) {
            // the page before; from a replay's first page, back to Settings. (A first run's
            // first page never gets here: its Back is left to Android, see leaveOnBack.)
            val w = welcome
            val before = w?.back()
            if (before != null) welcome = before else if (w == null || w.replay) closeWelcome()
            return
        }
        val leaving = nav.current
        nav = nav.back() ?: return
        if (leaving is Route.Check) frozen = frozenBeforeCheck
    }

    /** "Back to the camera for live answers": the frozen answers, as they were. */
    private fun backToCamera() {
        nav = NavStack()
        frozen = frozenBeforeCheck
    }

    /** Rescan / Scan another: back to a live camera with nothing remembered from the last scan. */
    private fun startOver() {
        nav = NavStack()
        checkSession = null
        checkPhoto = null
        frozenBeforeCheck = null
        resetScan()
    }

    /**
     * Play a puzzle: generate one HintEngine rates [d] (tens of ms on a desktop JVM, so
     * off the main thread), then open it. Backing out meanwhile drops the result.
     */
    private fun newPuzzle(d: Difficulty) {
        if (making != null) return
        making = d
        lifecycleScope.launch {
            val p = withContext(Dispatchers.Default) { Generator.generate(d) }
            making = null
            if (p != null && nav.current == Route.NewPuzzle) {
                game = GameState(p)
                gameMode = PlayMode.HINTS
                nav = nav.open(Route.Play(p, PlayMode.HINTS))
            }
        }
    }

    /** Continue on the camera bar: a game kept and not finished. Null: nothing to continue. */
    private fun continueAction(): (() -> Unit)? {
        val g = game?.takeIf { SavedSession.keep(it) } ?: return null
        return { nav = NavStack().open(Route.Play(g.puzzle, gameMode)) }
    }

    /**
     * The game itself is already in GameStore; the Bundle only remembers whether the board
     * was open, so a process Android kills in the background comes back on the board.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val g = game ?: return
        outState.putBoolean(KEY_BOARD_OPEN, nav.stack.any { it is Route.Play && it.puzzle == g.puzzle && it.mode != PlayMode.SOLUTION })
    }

    /** Pause frame analysis while a full-screen page covers the camera; resume on return. */
    private fun setAnalyzing(on: Boolean) {
        val a = analysis ?: return
        val fa = analyzer ?: return
        if (on) a.setAnalyzer(analysisExecutor, fa) else a.clearAnalyzer()
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // the camera may have been allowed or taken away in the system settings meanwhile
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onPause() {
        inFront = false
        super.onPause()
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
            val frameAnalyzer = FrameAnalyzer(classifier, handClassifier, handReader) { r ->
                runOnUiThread { if (frozen == null) result = r } // frozen: keep the answers as they were
            }
            frameAnalyzer.palette = palette
            analyzer = frameAnalyzer
            this.analysis = analysis
            if (nav.onCamera) analysis.setAnalyzer(analysisExecutor, frameAnalyzer)

            provider.unbindAll()
            val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera = cam
            hasFlash = cam.cameraInfo.hasFlashUnit()
            // follow the real torch state (CameraX turns it off when the camera closes)
            cam.cameraInfo.torchState.observe(this) { torchOn = it == TorchState.ON }
        }, ContextCompat.getMainExecutor(this))
    }

    private companion object {
        const val KEY_BOARD_OPEN = "board_open"
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.execute { classifier.close(); handClassifier.close(); handReader.close() } // runs after the last frame finishes
        analysisExecutor.shutdown()
    }
}
