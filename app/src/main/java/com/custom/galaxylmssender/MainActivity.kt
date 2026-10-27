// ============================================================================
// [갤럭시 전용 무인 LMS 자동발송 앱] GalaxyLmsSender_App.kt (통합 단일 소스코드)
// ----------------------------------------------------------------------------
// ✅ 특징:
// 1. 복잡한 XML 레이아웃(activity_main.xml) 파일 없이, 이 코드 파일 1개만으로
//    전체 화면 UI + 백그라운드 발송 서비스 + 평일 10시 알람이 모두 작동합니다.
// 2. 사용자의 구글 시트 웹앱 URL과 [청호나이스] 프로모션 기본 문구, 무료수신거부 번호가
//    코드 내부에 기본값으로 내장되어 있어 앱 설치 후 바로 사용할 수 있습니다.
// 3. 앱 화면에서 직접 문자 제목·본문·무료수신거부 문구를 수정하고 저장할 수도 있습니다.
// ============================================================================

package com.custom.galaxylmssender

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SmsManager
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.random.Random

object DefaultConfig {
    const val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwzFtwvdoYvxHG1AturFH8Iy4bfzv4FcPv-Kv5pBzlGRsWxzSnAUB45c7QbfLYwzqM/exec"
    const val SECRET_KEY = "MY_GALAXY_SMS_2026"
    const val DEFAULT_LMS_TITLE = "(광고) [청호나이스] 타사 고객 감사 이벤트: 매트리스 무상 케어 + 상품권 증정"
    val DEFAULT_LMS_BODY = """
고객님, 지금 사용하시는 매트리스 관리는 어떻게 하고 계신가요?
눈에 보이지 않는 매트리스 속 집먼지진드기와 미세먼지, 일반 청소기로는 케어가 어렵습니다.
청호나이스가 타사 매트리스를 이용 중이신 고객님을 위해 '7단계 전문 홈케어'를 무상으로 경험해 보실 수 있는 특별 체험단을 모십니다.

■ 혜택 안내
청호나이스 전문 케어마스터의 7단계 정밀 클리닝 (무료)
■ 신청 대상
- 매트리스 사용 고객
■ 신청 기한
- 선착순 마감 시 조기 종료

무료수신거부: [080-880-7245]
    """.trimIndent()
}

// ----------------------------------------------------------------------------
// 1. 메인 화면 (MainActivity) - XML 파일 없이 코드만으로 UI 자동 생성
// ----------------------------------------------------------------------------
class MainActivity : Activity() {

    private lateinit var editWebAppUrl: EditText
    private lateinit var editSecretKey: EditText
    private lateinit var checkUseLocalMessage: CheckBox
    private lateinit var editLmsTitle: EditText
    private lateinit var editLmsBody: EditText
    private lateinit var switchAutoSchedule: Switch
    private lateinit var tvNextSchedule: TextView
    private lateinit var editTestPhone: EditText
    private lateinit var tvLogOutput: TextView

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra("log") ?: return
            appendLog(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildProgrammaticUi()
        loadPreferences()
        requestRequiredPermissions()
        requestIgnoreBatteryOptimizations()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
            background = bg
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(14)
            layoutParams = lp
        }
    }

    private fun makeButton(label: String, bgColor: String, textColor: String = "#FFFFFF"): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.parseColor(textColor))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(8).toFloat()
            }
            background = bg
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            layoutParams = lp
        }
    }

    private fun buildProgrammaticUi() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F1F5F9"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollView.addView(root)

        // 상단 헤더
        val headerTitle = TextView(this).apply {
            text = "갤럭시 LMS 무인 자동발송기"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }
        val headerSub = TextView(this).apply {
            text = "구글 시트 연동 · 수신거부 자동제외 · 35~50초 스팸방지 간격 발송"
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(headerTitle)
        root.addView(headerSub)

        // 카드 1: 구글 시트 웹앱 연동 설정
        val cardConfig = makeCard()
        cardConfig.addView(TextView(this).apply {
            text = "1. 구글 시트 웹앱 연동 설정"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        })
        cardConfig.addView(TextView(this).apply {
            text = "웹앱 URL (기본 입력 완료)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(8), 0, dp(2))
        })
        editWebAppUrl = EditText(this).apply {
            hint = "https://script.google.com/macros/s/.../exec"
            textSize = 12f
            setSingleLine(true)
        }
        cardConfig.addView(editWebAppUrl)

        cardConfig.addView(TextView(this).apply {
            text = "연동 보안키 (Code.gs와 동일)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(6), 0, dp(2))
        })
        editSecretKey = EditText(this).apply {
            hint = "MY_GALAXY_SMS_2026"
            textSize = 13f
            setSingleLine(true)
        }
        cardConfig.addView(editSecretKey)

        val btnSaveConfig = makeButton("설정 저장 및 구글 시트 연결 테스트", "#2563EB")
        btnSaveConfig.setOnClickListener {
            savePreferences()
            testSheetConnection()
        }
        cardConfig.addView(btnSaveConfig)
        root.addView(cardConfig)

        // 카드 2: 발송 문자(LMS) 제목·본문 직접 수정
        val cardMsg = makeCard()
        cardMsg.addView(TextView(this).apply {
            text = "2. 발송 문자(LMS) 문구 설정 및 수정"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        })
        checkUseLocalMessage = CheckBox(this).apply {
            text = "아래에 입력한 앱 문구로 우선 발송하기 (권장)"
            textSize = 13f
            isChecked = true
        }
        cardMsg.addView(checkUseLocalMessage)

        cardMsg.addView(TextView(this).apply {
            text = "문자 제목 (LMS 제목)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(6), 0, dp(2))
        })
        editLmsTitle = EditText(this).apply {
            textSize = 13f
            setSingleLine(true)
        }
        cardMsg.addView(editLmsTitle)

        cardMsg.addView(TextView(this).apply {
            text = "문자 본문 및 무료수신거부 문구 (자유롭게 수정 가능)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(6), 0, dp(2))
        })
        editLmsBody = EditText(this).apply {
            textSize = 13f
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        cardMsg.addView(editLmsBody)

        val btnResetMsg = makeButton("청호나이스 기본 이벤트 문구로 복원", "#64748B")
        btnResetMsg.setOnClickListener {
            editLmsTitle.setText(DefaultConfig.DEFAULT_LMS_TITLE)
            editLmsBody.setText(DefaultConfig.DEFAULT_LMS_BODY)
            savePreferences()
            Toast.makeText(this, "청호나이스 기본 문구로 복원되었습니다.", Toast.LENGTH_SHORT).show()
        }
        cardMsg.addView(btnResetMsg)
        root.addView(cardMsg)

        // 카드 3: 평일 오전 10시 무인 스케줄러 및 즉시 발송
        val cardRun = makeCard()
        cardRun.addView(TextView(this).apply {
            text = "3. 자동 발송 실행 및 스케줄러"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        })

        switchAutoSchedule = Switch(this).apply {
            text = "평일(월~금) 오전 10:00 자동 발송 켜기"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(4))
        }
        cardRun.addView(switchAutoSchedule)

        tvNextSchedule = TextView(this).apply {
            text = "다음 자동 발송 예정: 꺼짐"
            textSize = 12f
            setTextColor(Color.parseColor("#059669"))
            setPadding(0, 0, 0, dp(8))
        }
        cardRun.addView(tvNextSchedule)

        switchAutoSchedule.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("autoSchedule", isChecked).apply()
            if (isChecked) {
                val nextTime = ScheduleHelper.scheduleNextWeekday10AM(this)
                tvNextSchedule.text = "다음 자동 발송 예정: $nextTime"
                appendLog("[스케줄러 ON] 다음 자동 발송: $nextTime")
            } else {
                ScheduleHelper.cancelScheduledAlarm(this)
                tvNextSchedule.text = "다음 자동 발송 예정: 꺼짐"
                appendLog("[스케줄러 OFF] 자동 발송 알람이 해제되었습니다.")
            }
        }

        cardRun.addView(TextView(this).apply {
            text = "내 번호로 1건 테스트 수신 번호 ('-' 없이 입력)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        })
        editTestPhone = EditText(this).apply {
            hint = "01012345678"
            textSize = 14f
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        cardRun.addView(editTestPhone)

        val btnTestSingle = makeButton("내 번호로 1건 테스트 문자 즉시 발송", "#0D9488")
        btnTestSingle.setOnClickListener {
            val testPhone = editTestPhone.text.toString().replace(Regex("[^0-9]"), "")
            if (!testPhone.matches(Regex("^010[0-9]{8}$"))) {
                Toast.makeText(this, "유효한 010 번호 11자리를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePreferences()
            val svcIntent = Intent(this, LmsSenderService::class.java).apply {
                action = "ACTION_TEST_SINGLE"
                putExtra("testPhone", testPhone)
            }
            startSenderServiceCompat(svcIntent)
        }
        cardRun.addView(btnTestSingle)

        val btnStartNow = makeButton("▶ 지금 즉시 오늘치(최대 150통) 자동발송 시작", "#16A34A")
        btnStartNow.setOnClickListener {
            savePreferences()
            val svcIntent = Intent(this, LmsSenderService::class.java).apply {
                action = "ACTION_START_BATCH"
            }
            startSenderServiceCompat(svcIntent)
        }
        cardRun.addView(btnStartNow)

        val btnStopNow = makeButton("■ 진행 중인 발송 즉시 중지", "#DC2626")
        btnStopNow.setOnClickListener {
            val svcIntent = Intent(this, LmsSenderService::class.java).apply {
                action = "ACTION_STOP_BATCH"
            }
            startService(svcIntent)
            appendLog("[중지 요청] 발송 서비스 중지 명령을 전달했습니다.")
        }
        cardRun.addView(btnStopNow)
        root.addView(cardRun)

        // 카드 4: 실시간 진행 로그
        val cardLog = makeCard()
        cardLog.addView(TextView(this).apply {
            text = "4. 실시간 발송 진행 로그"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        })
        tvLogOutput = TextView(this).apply {
            text = "[준비 완료] 웹앱 URL과 청호나이스 문구가 기본 세팅되어 있습니다.
"
            textSize = 12f
            setTextColor(Color.parseColor("#E2E8F0"))
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 8
        }
        cardLog.addView(tvLogOutput)
        root.addView(cardLog)

        setContentView(scrollView)
    }

    private fun startSenderServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.custom.galaxylmssender.LOG_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
        editWebAppUrl.setText(prefs.getString("webAppUrl", DefaultConfig.WEB_APP_URL))
        editSecretKey.setText(prefs.getString("secretKey", DefaultConfig.SECRET_KEY))
        checkUseLocalMessage.isChecked = prefs.getBoolean("useLocalMessage", true)
        editLmsTitle.setText(prefs.getString("lmsTitle", DefaultConfig.DEFAULT_LMS_TITLE))
        editLmsBody.setText(prefs.getString("lmsBody", DefaultConfig.DEFAULT_LMS_BODY))

        val isAuto = prefs.getBoolean("autoSchedule", false)
        switchAutoSchedule.isChecked = isAuto
        if (isAuto) {
            val nextTime = ScheduleHelper.scheduleNextWeekday10AM(this)
            tvNextSchedule.text = "다음 자동 발송 예정: $nextTime"
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("webAppUrl", editWebAppUrl.text.toString().trim())
            .putString("secretKey", editSecretKey.text.toString().trim())
            .putBoolean("useLocalMessage", checkUseLocalMessage.isChecked)
            .putString("lmsTitle", editLmsTitle.text.toString().trim())
            .putString("lmsBody", editLmsBody.text.toString().trim())
            .apply()
        appendLog("[설정 저장 완료] 웹앱 URL 및 LMS 문구가 저장되었습니다.")
    }

    private fun testSheetConnection() {
        val url = editWebAppUrl.text.toString().trim()
        val key = editSecretKey.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "구글 시트 웹 앱 URL을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        appendLog("[시트 연결 확인 중...]")
        thread {
            val res = SheetApiClient.fetchStatus(url, key)
            runOnUiThread { appendLog(res) }
        }
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val current = tvLogOutput.text.toString()
        val updated = "[$time] $msg
$current"
        tvLogOutput.text = if (updated.length > 4000) updated.substring(0, 4000) else updated
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = mutableListOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val needed = perms.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 1001)
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 2. 평일(월~금) 오전 10:00 정각 알람 스케줄러 (ScheduleHelper & AlarmReceiver)
// ----------------------------------------------------------------------------
object ScheduleHelper {
    fun scheduleNextWeekday10AM(context: Context): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.custom.galaxylmssender.ACTION_START_DAILY_BATCH"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 2026, intent, flags)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        if (cal.before(now) || cal == now) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
               cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd (E) HH:mm", Locale.KOREA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return sdf.format(cal.time)
    }

    fun cancelScheduledAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.custom.galaxylmssender.ACTION_START_DAILY_BATCH"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, 2026, intent, flags)
        alarmManager.cancel(pendingIntent)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
        val isAuto = prefs.getBoolean("autoSchedule", false)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (isAuto) ScheduleHelper.scheduleNextWeekday10AM(context)
            return
        }

        if (intent.action == "com.custom.galaxylmssender.ACTION_START_DAILY_BATCH" && isAuto) {
            ScheduleHelper.scheduleNextWeekday10AM(context)
            val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
            val day = now.get(Calendar.DAY_OF_WEEK)
            if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
                val svcIntent = Intent(context, LmsSenderService::class.java).apply {
                    action = "ACTION_START_BATCH"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 3. 백그라운드 무인 발송 서비스 (LmsSenderService)
// - 문자 앱을 일일이 열지 않고 SmsManager를 통해 갤럭시 백그라운드에서 직접 LMS 발송
// ----------------------------------------------------------------------------
class LmsSenderService : Service() {

    @Volatile
    private var isStopped = false
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ""
        if (action == "ACTION_STOP_BATCH") {
            isStopped = true
            broadcastLog("사용자에 의해 발송 중지 요청됨.")
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, createNotification("자동 문자 발송 준비 중..."))

        if (isRunning) {
            broadcastLog("이미 발송 작업이 진행 중입니다.")
            return START_NOT_STICKY
        }

        val testPhone = intent?.getStringExtra("testPhone")
        isStopped = false
        isRunning = true

        thread {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GalaxyLmsSender::BatchWakeLock")
            wakeLock.acquire(3 * 60 * 60 * 1000L)

            try {
                if (action == "ACTION_TEST_SINGLE" && !testPhone.isNullOrEmpty()) {
                    runSingleTest(testPhone)
                } else {
                    runDailyBatch()
                }
            } catch (e: Exception) {
                broadcastLog("오류 발생: ${e.message}")
            } finally {
                isRunning = false
                if (wakeLock.isHeld) wakeLock.release()
                stopForegroundCompat()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun resolveFullMessage(sheetTitle: String, sheetBody: String): String {
        val prefs = getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
        val useLocal = prefs.getBoolean("useLocalMessage", true)
        val localTitle = prefs.getString("lmsTitle", DefaultConfig.DEFAULT_LMS_TITLE)?.trim() ?: DefaultConfig.DEFAULT_LMS_TITLE
        val localBody = prefs.getString("lmsBody", DefaultConfig.DEFAULT_LMS_BODY)?.trim() ?: DefaultConfig.DEFAULT_LMS_BODY

        val finalTitle = if (useLocal || sheetTitle.isBlank()) localTitle else sheetTitle
        val finalBody = if (useLocal || sheetBody.isBlank()) localBody else sheetBody
        return if (finalTitle.isNotEmpty()) "$finalTitle

$finalBody" else finalBody
    }

    private fun runSingleTest(testPhone: String) {
        val fullMessage = resolveFullMessage("", "")
        broadcastLog("테스트 번호($testPhone)로 장문(LMS) 발송 중...")
        val ok = sendLongMessage(testPhone, fullMessage)
        if (ok) {
            broadcastLog("✅ 테스트 발송 완료! 갤럭시 문자함을 확인해 보세요.")
        } else {
            broadcastLog("❌ 테스트 발송 실패. SMS 권한 허용 여부를 확인해 주세요.")
        }
    }

    private fun runDailyBatch() {
        val prefs = getSharedPreferences("LmsPrefs", Context.MODE_PRIVATE)
        val url = prefs.getString("webAppUrl", DefaultConfig.WEB_APP_URL) ?: DefaultConfig.WEB_APP_URL
        val key = prefs.getString("secretKey", DefaultConfig.SECRET_KEY) ?: DefaultConfig.SECRET_KEY

        if (url.isEmpty()) {
            broadcastLog("오류: 구글 시트 웹 앱 URL이 설정되지 않았습니다.")
            return
        }

        broadcastLog("구글 시트에 오늘의 발송 대상(최대 150건)을 요청합니다...")
        val batch = SheetApiClient.fetchTodayBatch(url, key)

        if (batch.status == "completed_for_today") {
            broadcastLog(batch.message)
            return
        }

        if (batch.targets.isEmpty()) {
            broadcastLog("발송할 대기 고객이 없습니다. (${batch.message})")
            return
        }

        val total = batch.targets.size
        val fullMessage = resolveFullMessage(batch.lmsTitle, batch.lmsBody)
        broadcastLog("총 ${total}명 추출 완료! 스팸 차단 방지를 위해 35~50초 간격으로 무인 발송을 시작합니다.")

        for ((idx, target) in batch.targets.withIndex()) {
            if (isStopped) {
                broadcastLog("발송이 중단되었습니다. (진행: ${idx}/${total}건)")
                break
            }

            val currentNum = idx + 1
            updateNotification("발송 진행 중: $currentNum / ${total}건 (${target.region} - ${target.customerName})")

            val ok = sendLongMessage(target.phone, fullMessage)
            val statusStr = if (ok) "발송완료" else "발송실패"

            val marked = SheetApiClient.postSendResult(url, key, target.rowNumber, target.phone, statusStr)
            broadcastLog("[$currentNum/$total] ${target.region} ${target.customerName}(${target.phone}) -> $statusStr (시트기록:${if (marked) "성공" else "재시도필요"})")

            if (currentNum < total && !isStopped) {
                val delaySec = Random.nextInt(35, 51)
                for (s in 1..delaySec) {
                    if (isStopped) break
                    Thread.sleep(1000L)
                }
            }
        }

        if (!isStopped) {
            broadcastLog("🎉 오늘 할당된 ${total}통 발송 및 시트 기록이 모두 완료되었습니다!")
        }
    }

    private fun sendLongMessage(phone: String, fullMessage: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(fullMessage)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            true
        } catch (e: Exception) {
            broadcastLog("발송 예외(${phone}): ${e.message}")
            false
        }
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("com.custom.galaxylmssender.LOG_EVENT").apply {
            setPackage(packageName)
            putExtra("log", msg)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(text: String): Notification {
        val channelId = "galaxy_lms_channel"
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "일일 150통 자동 문자 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("갤럭시 자동문자 (일 150통)")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, createNotification(text))
    }
}

// ----------------------------------------------------------------------------
// 4. 구글 시트 웹 앱 통신 클라이언트 (SheetApiClient)
// ----------------------------------------------------------------------------
data class TargetItem(
    val rowNumber: Int,
    val phone: String,
    val customerName: String,
    val region: String
)

data class BatchPayload(
    val status: String,
    val message: String,
    val lmsTitle: String,
    val lmsBody: String,
    val targets: List<TargetItem>
)

object SheetApiClient {

    fun fetchStatus(webAppUrl: String, secretKey: String): String {
        return try {
            val fullUrl = "$webAppUrl?key=${URLEncoder.encode(secretKey, "UTF-8")}&action=status"
            val jsonStr = httpGet(fullUrl)
            val obj = JSONObject(jsonStr)
            if (obj.optString("status") == "success") {
                "✅ 시트 연결 성공! 오늘 발송: ${obj.optInt("sentToday")} / ${obj.optInt("dailyLimit")}건 (전체 잔여 대기: ${obj.optInt("totalRemaining")}건)"
            } else {
                "응답 오류: ${obj.optString("message")}"
            }
        } catch (e: Exception) {
            "연결 실패: ${e.message}"
        }
    }

    fun fetchTodayBatch(webAppUrl: String, secretKey: String): BatchPayload {
        val fullUrl = "$webAppUrl?key=${URLEncoder.encode(secretKey, "UTF-8")}&action=getBatch"
        val jsonStr = httpGet(fullUrl)
        val obj = JSONObject(jsonStr)
        val status = obj.optString("status", "error")
        val message = obj.optString("message", "")
        val lmsTitle = obj.optString("lmsTitle", DefaultConfig.DEFAULT_LMS_TITLE)
        val lmsBody = obj.optString("lmsBody", DefaultConfig.DEFAULT_LMS_BODY)

        val list = mutableListOf<TargetItem>()
        val arr = obj.optJSONArray("targets") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(
                TargetItem(
                    rowNumber = item.getInt("rowNumber"),
                    phone = item.getString("phone"),
                    customerName = item.optString("customerName", ""),
                    region = item.optString("region", "")
                )
            )
        }
        return BatchPayload(status, message, lmsTitle, lmsBody, list)
    }

    fun postSendResult(webAppUrl: String, secretKey: String, rowNumber: Int, phone: String, status: String): Boolean {
        return try {
            val root = JSONObject().apply {
                put("key", secretKey)
                val arr = JSONArray()
                val item = JSONObject().apply {
                    put("rowNumber", rowNumber)
                    put("phone", phone)
                    put("status", status)
                }
                arr.put(item)
                put("results", arr)
            }
            val resStr = httpPostJson(webAppUrl, root.toString())
            val resObj = JSONObject(resStr)
            resObj.optString("status") == "success"
        } catch (e: Exception) {
            false
        }
    }

    private fun httpGet(urlStr: String): String {
        var currentUrl = urlStr
        for (redirect in 0..5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 20000
            conn.readTimeout = 20000

            val code = conn.responseCode
            if (code in 301..308) {
                currentUrl = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                continue
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            return sb.toString()
        }
        throw Exception("리다이렉트 횟수 초과")
    }

    private fun httpPostJson(urlStr: String, jsonBody: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 20000

        val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
        writer.write(jsonBody)
        writer.flush()
        writer.close()

        val code = conn.responseCode
        if (code in 301..308) {
            val redirectUrl = conn.getHeaderField("Location")
            conn.disconnect()
            if (redirectUrl != null) {
                return httpGet(redirectUrl)
            }
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }
}
