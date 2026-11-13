package com.iancompany.voiceledger

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Phase 1 diagnostic harness, not the shipping ledger UI. No expenses/audio are stored. */
class ProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private val speechWorker = Executors.newSingleThreadExecutor()
    private val speechGeneration = AtomicInteger()
    private var transcriber: OfflineTranscriber? = null // speechWorker owns this native resource.
    @Volatile private var capturing = false
    private var speechBusy = false
    private var parked: Boolean? = null
    private var gearAt = 0L
    private var started = false
    private var vehicleGeneration = 0
    private lateinit var vehicleStatus: TextView
    private lateinit var distanceStatus: TextView
    private lateinit var speechStatus: TextView
    private lateinit var result: TextView
    private lateinit var startButton: Button
    private val isEmulator get() = Build.FINGERPRINT.contains("generic") ||
        Build.MODEL.contains("sdk_gphone") || Build.HARDWARE in listOf("ranchu", "goldfish")
    private val refresh = object : Runnable {
        override fun run() {
            if (!started) return
            readVehicle()
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        fun label(text: String, size: Float = 20f): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xff17202a.toInt())
            setPadding(0, dp(8), 0, dp(8))
            column.addView(this)
        }
        fun button(text: String, action: () -> Unit): Button = Button(this).apply {
            this.text = text
            textSize = 20f
            minHeight = dp(56)
            isAllCaps = false
            setOnClickListener { action() }
            column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        label("차계부 · 연결 확인", 30f)
        label("개발 검증용 화면입니다. 지출 저장 기능은 다음 단계에서 추가합니다.", 18f)
        label(if (isEmulator) "실행 환경: 에뮬레이터 · 차량 값은 시뮬레이션 데이터" else "실행 환경: 실제 기기 · P 상태 확인 후 테스트", 18f)
        vehicleStatus = label("차량 상태 확인 중")
        distanceStatus = label("주행거리 확인 중")
        speechStatus = label("음성 서비스 확인 중")
        startButton = button("오프라인 음성 테스트") {
            if (capturing) {
                capturing = false
                startButton.isEnabled = false
                speechStatus.text = "음성 해석 중"
            } else requestRecognition()
        }
        button("음성 테스트 중지") {
            stopRecognition()
            speechStatus.text = "음성 테스트를 중지했습니다"
        }
        result = label("인식 결과가 여기에 표시됩니다.\n예: 오늘 주유 칠만 원", 24f)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onStart() {
        super.onStart()
        started = true
        vehicleGeneration++
        try {
            vehicle = Vehicle(this).also { it.initialize() }
        } catch (e: Exception) {
            vehicleStatus.text = "차량 연결 실패: ${e.javaClass.simpleName}"
        }
        handler.post(refresh)
        val available = SpeechRecognizer.isRecognitionAvailable(this)
        speechStatus.text = "오프라인 Whisper 경로 · 인식 오류 가능, 저장 전 확인 필요\nAndroid 기본 서비스 감지: $available · 이번 앱은 오프라인 엔진 사용"
        startButton.isEnabled = true
    }

    private fun readVehicle() {
        val current = vehicle ?: return
        val generation = vehicleGeneration
        fun update(block: () -> Unit) = runOnUiThread {
            if (started && generation == vehicleGeneration) block()
        }
        try {
            current.getGear().getCurrentGearSelectionState(
                onComplete = { gear -> update {
                    parked = gear == VehicleGear.GEAR_PARK
                    gearAt = SystemClock.elapsedRealtime()
                    vehicleStatus.text = "Vehicle State 연결됨 · 기어: $gear"
                    if (!isEmulator && parked != true) stopRecognition()
                } },
                onFailed = { error -> update {
                    parked = null
                    vehicleStatus.text = "차량 상태 미상: ${error.message}"
                    if (!isEmulator) stopRecognition()
                } }
            )
            current.getOdometer().getCurrentDistanceDriven(
                onComplete = { value -> update { distanceStatus.text = ProbePolicy.distanceLabel(value) } },
                onFailed = { error -> update { distanceStatus.text = "주행거리 조회 실패: ${error.message}" } }
            )
        } catch (e: Exception) {
            parked = null
            vehicleStatus.text = "차량 조회 실패: ${e.javaClass.simpleName}"
            if (!isEmulator) stopRecognition()
        }
        if (!ProbePolicy.mayRecord(isEmulator, parked, SystemClock.elapsedRealtime() - gearAt)) {
            stopRecognition()
        }
    }

    private fun requestRecognition() {
        if (speechBusy) return
        if (!ProbePolicy.mayRecord(isEmulator, parked, SystemClock.elapsedRealtime() - gearAt)) {
            speechStatus.text = "P 상태를 확인할 수 없어 음성을 시작할 수 없습니다"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        stopRecognition()
        val generation = speechGeneration.incrementAndGet()
        speechBusy = true
        startButton.isEnabled = false
        speechStatus.text = "오프라인 음성 모델 준비 중"
        fun update(block: () -> Unit) = runOnUiThread {
            if (started && speechGeneration.get() == generation) block()
        }
        speechWorker.execute {
            var recorder: AudioRecord? = null
            try {
                val engine = transcriber ?: OfflineTranscriber(assets).also { transcriber = it }
                if (speechGeneration.get() != generation) return@execute
                val minimum = AudioRecord.getMinBufferSize(PcmAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0) { "16kHz 마이크 입력 미지원" }
                recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, PcmAudio.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096))
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "마이크 초기화 실패" }
                if (speechGeneration.get() != generation) return@execute
                capturing = true
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "마이크 시작 실패" }
                update {
                    speechStatus.text = "듣고 있어요 · 최대 8초, 말한 뒤 끝내기를 누르세요"
                    startButton.text = "말하기 끝내기"
                    startButton.isEnabled = true
                }
                val pcm = FloatArray(PcmAudio.MAX_SAMPLES)
                val buffer = ShortArray(1024)
                var count = 0
                while (capturing && speechGeneration.get() == generation && count < pcm.size) {
                    val read = recorder.read(buffer, 0, minOf(buffer.size, pcm.size - count))
                    check(read > 0) { "마이크 입력 실패: $read" }
                    for (index in 0 until read) pcm[count++] = buffer[index] / 32768f
                }
                recorder.stop()
                recorder.release()
                recorder = null
                if (speechGeneration.get() != generation) return@execute
                capturing = false
                update { startButton.isEnabled = false; speechStatus.text = "음성 해석 중" }
                val text = engine.transcribe(pcm.copyOf(count))
                update {
                    result.text = text.ifBlank { "음성을 인식하지 못했습니다. 마이크 입력을 확인하고 다시 말해 주세요." }
                    speechStatus.text = "인식 결과 · 금액과 항목을 반드시 확인하세요. 아직 저장되지 않습니다."
                }
            } catch (e: Exception) {
                update { speechStatus.text = "음성 테스트 실패: ${e.message ?: e.javaClass.simpleName}" }
            } catch (e: LinkageError) {
                update { speechStatus.text = "음성 엔진을 불러올 수 없습니다: ${e.javaClass.simpleName}" }
            } finally {
                recorder?.release()
                update {
                    capturing = false
                    speechBusy = false
                    startButton.text = "오프라인 음성 테스트"
                    startButton.isEnabled = true
                }
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 1 && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED && started) requestRecognition()
        else if (code == 1) speechStatus.text = "마이크 권한이 거부되었습니다 · 다시 눌러 권한을 허용하세요"
    }

    private fun stopRecognition() {
        speechGeneration.incrementAndGet()
        capturing = false
        speechBusy = false
        if (::startButton.isInitialized) {
            startButton.text = "오프라인 음성 테스트"
            startButton.isEnabled = true
        }
    }

    override fun onStop() {
        started = false
        vehicleGeneration++
        handler.removeCallbacks(refresh)
        stopRecognition()
        vehicle?.release()
        vehicle = null
        parked = null
        super.onStop()
    }

    override fun onPause() {
        // Automotive multi-window can lose focus without reaching onStop.
        stopRecognition()
        super.onPause()
    }

    override fun onDestroy() {
        speechWorker.execute { transcriber?.close(); transcriber = null }
        speechWorker.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
