package com.example.pendulum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pendulum.GameConfig
import com.example.pendulum.GameConfigFactory
import com.example.pendulum.GameEngine
import com.example.pendulum.ControlMode
import com.example.pendulum.GameMode
import com.example.pendulum.GameStatus
import com.example.pendulum.difficultyLabel
import com.example.pendulum.label
import com.example.pendulum.storage.RankRepository
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private data class Layout(val scale: Double, val originX: Float, val originY: Float) {
    fun wx(wx: Double, wy: Double): Offset =
        Offset((originX.toDouble() + wx * scale).toFloat(), (originY.toDouble() - wy * scale).toFloat())
    fun toWorldX(px: Float): Double = (px - originX) / scale
}

@Composable
fun GameScreen(
    mode: GameMode,
    level: Int,
    onExit: () -> Unit,
    onShowRank: (GameMode, Int) -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RankRepository(context) }

    // 随机生成星星横向位置
    val starX = remember { (kotlin.random.Random.nextDouble() * 2 - 1) * 0.5 }
    val totalLength = 0.5 * level
    val config: GameConfig = remember(mode, level, starX) {
        GameConfigFactory.create(mode, level, starX, totalLength)
    }

    var restartKey by remember { mutableStateOf(0) }
    val engine = remember(restartKey, config) { GameEngine(config).apply { start() } }

    var tick by remember { mutableStateOf(0L) }
    var lqrOn by remember(restartKey) { mutableStateOf(false) }
    var savedRank by remember(restartKey) { mutableStateOf<Int?>(null) }
    var finished by remember(restartKey) { mutableStateOf(false) }
    var tiltOn by remember(restartKey) { mutableStateOf(false) }
    var playerName by remember(restartKey) { mutableStateOf("") }
    var saved by remember(restartKey) { mutableStateOf(false) }

    // 布局状态（Canvas 尺寸 → 世界坐标映射），必须在使用前声明
    val layoutState = remember { mutableStateOf<Layout?>(null) }

    // 触摸坐标 → 世界坐标（读取最新 layout）
    val onTouchX: (Float) -> Unit = { px -> layoutState.value?.let { engine.setTouch(it.toWorldX(px)) } }
    val onTouchEnd: () -> Unit = { engine.setTouch(null) }

    LaunchedEffect(engine) {
        var last = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            val dt = (now - last) / 1e9
            last = now
            engine.update(dt)
            tick++
            if (engine.status == GameStatus.FINISHED && !finished) {
                finished = true
            }
            kotlinx.coroutines.delay(16)
        }
    }

    // 重力感应（加速度计）控制：开启时注册传感器监听，将设备左右倾斜映射为小车目标位置
    DisposableEffect(tiltOn, engine) {
        if (!tiltOn) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@DisposableEffect onDispose {}
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 横屏时左右倾斜对应 device-y 轴，竖屏对应 device-x 轴；符号按旋转方向修正。
                val rot = wm.defaultDisplay.rotation
                val (axis, sign) = when (rot) {
                    Surface.ROTATION_90 -> 1 to 1.0
                    Surface.ROTATION_270 -> 1 to -1.0
                    else -> 0 to 1.0
                }
                val t = (sign * event.values[axis] / 9.81).coerceIn(-1.0, 1.0)
                engine.setTiltTarget(t * config.trackHalfWidth)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sm.unregisterListener(listener)
            engine.setTiltTarget(null)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    detectDragGestures(
                        onDragStart = { onTouchX(it.x) },
                        onDrag = { change, _ -> onTouchX(change.position.x) },
                        onDragEnd = { onTouchEnd() },
                        onDragCancel = { onTouchEnd() }
                    )
                }
        ) {
            // 画面
            CanvasScene(
                engine = engine,
                config = config,
                layoutState = layoutState,
                lqrOn = lqrOn,
                onLayout = { w, h ->
                    layoutState.value = computeLayout(w, h, config.trackHalfWidth, config.star.y)
                }
            )
            // HUD
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                // 1) 按钮排在最顶部
                ControlBar(
                    manualActive = !lqrOn && !tiltOn,
                    onManual = {
                        lqrOn = false
                        tiltOn = false
                        engine.setManualMode()
                    },
                    lqrOn = lqrOn,
                    onToggleLqr = {
                        lqrOn = !lqrOn
                        engine.setLQR(lqrOn)
                        // 仅同步 UI 标志；引擎内 setLQR 已把控制模式设为 LQR（并清空倾斜目标）。
                        // 注意：不能在此调用 engine.setTiltMode(false)，否则会把刚设好的
                        // LQR 模式又强制覆盖回 TOUCH，导致 LQR 永远不生效。
                        if (lqrOn) tiltOn = false
                    },
                    tiltOn = tiltOn,
                    onToggleTilt = {
                        tiltOn = !tiltOn
                        engine.setTiltMode(tiltOn)
                        // 同上：仅同步 UI 标志，不要调用 engine.setLQR(false)。
                        if (tiltOn) lqrOn = false
                    },
                    onRestart = { restartKey++ },
                    onExit = onExit
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 2) 提示文字在按钮下边
                val tip = when {
                    lqrOn -> if (engine.lqrStable()) "自动演示中（最优控制稳定摆并自动驶向星星）"
                    else "自动演示未收敛"
                    tiltOn -> "重力感应控制中：左右倾斜手机即可移动小车。"
                    else -> "手动模式：触摸并拖动屏幕左右移动小车。"
                }
                Text(tip, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                // 3) 得分和时间在最下边
                TopHud(engine = engine, tick = tick, mode = mode, level = level)
                Spacer(modifier = Modifier.weight(1f))
            }
            if (finished) {
                FinishOverlay(
                    score = engine.score,
                    rank = savedRank,
                    controlMode = engine.controlMode,
                    playerName = playerName,
                    onNameChange = { playerName = it },
                    saved = saved,
                    onSaveAndExit = {
                        // 关键修复：输入名字后直接「保存并退出」，确保记录被写入
                        savedRank = repo.addScore(config.mode, config.level, engine.score, playerName)
                        saved = true
                        onExit()
                    },
                    onSaveAndRestart = {
                        savedRank = repo.addScore(config.mode, config.level, engine.score, playerName)
                        saved = true
                        restartKey++
                    },
                    onRestart = { restartKey++ },
                    onShowRank = { onShowRank(mode, level) },
                    onExit = onExit
                )
            }
        }
    }
}

@Composable
private fun CanvasScene(
    engine: GameEngine,
    config: GameConfig,
    layoutState: MutableState<Layout?>,
    lqrOn: Boolean,
    onLayout: (Float, Float) -> Unit
) {
    val groundColor = MaterialTheme.colorScheme.outline
    val cartColor = MaterialTheme.colorScheme.primary
    val poleColor = Color(0xFFB5832E)
    val jointColor = MaterialTheme.colorScheme.tertiary
    val starColor = Color(0xFFFFC107)
    val aimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> onLayout(size.width.toFloat(), size.height.toFloat()) }
    ) {
        val w = size.width
        val h = size.height
        val layout = layoutState.value ?: return@Canvas

        // 地面 / 轨道
        val groundY = layout.originY
        drawLine(
            color = groundColor,
            start = Offset(0f, groundY),
            end = Offset(w, groundY),
            strokeWidth = 2.dp.toPx()
        )
        val leftX = layout.wx(-config.trackHalfWidth, 0.0).x
        val rightX = layout.wx(config.trackHalfWidth, 0.0).x
        drawLine(
            color = groundColor.copy(alpha = 0.5f),
            start = Offset(leftX, groundY - 10.dp.toPx()),
            end = Offset(leftX, groundY + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = groundColor.copy(alpha = 0.5f),
            start = Offset(rightX, groundY - 10.dp.toPx()),
            end = Offset(rightX, groundY + 10.dp.toPx()),
            strokeWidth = 3.dp.toPx()
        )

        val joints = engine.joints()
        val pivot = layout.wx(joints[0].first, joints[0].second)

        // 目标瞄准线（手动控制时）
        if (!lqrOn) {
            val aimX = engine.touchTargetX ?: engine.tiltTargetX
            aimX?.let { tx ->
                val txs = layout.wx(tx, joints[0].second)
                drawLine(
                    color = aimColor,
                    start = Offset(pivot.x, pivot.y),
                    end = Offset(txs.x, pivot.y),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }
        }

        // 摆杆
        val poleWidth = maxOf(6.dp.toPx(), (0.05 * layout.scale).toFloat())
        for (i in 0 until joints.size - 1) {
            val a = if (i == 0) pivot else layout.wx(joints[i].first, joints[i].second)
            val b = layout.wx(joints[i + 1].first, joints[i + 1].second)
            drawLine(color = poleColor, start = a, end = b, strokeWidth = poleWidth, cap = StrokeCap.Round)
        }

        // 关节
        for (j in joints) {
            val p = layout.wx(j.first, j.second)
            drawCircle(color = jointColor, radius = maxOf(4.dp.toPx(), poleWidth * 0.6f), center = p)
        }

        // 小车（摆杆铰接于小车几何中心）
        val poleBaseY = engine.dyn.params.poleBaseY
        val cartW = maxOf(46.dp.toPx(), (0.5 * layout.scale).toFloat())
        val cartH = (2.0 * poleBaseY * layout.scale).toFloat()
        drawRoundRect(
            color = cartColor,
            topLeft = Offset(pivot.x - cartW / 2, pivot.y - cartH / 2),
            size = androidx.compose.ui.geometry.Size(cartW, cartH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        )

        // 星星（固定目标）
        val star = layout.wx(config.star.x, config.star.y)
        val starR = (config.star.radius * layout.scale).toFloat()
        if (engine.inStar) {
            drawCircle(
                color = starColor.copy(alpha = 0.35f),
                radius = starR * 1.8f,
                center = star
            )
        }
        drawPath(
            path = starPath(star.x, star.y, starR, starR * 0.45f),
            color = starColor
        )
    }
}

private fun computeLayout(w: Float, h: Float, trackHalfWidth: Double, starY: Double): Layout {
    val groundY = h * 0.82f
    val marginX = w * 0.06f
    val scaleX = (w.toDouble() - 2.0 * marginX) / (2.0 * trackHalfWidth)
    // 预留顶部 10% 高度给 HUD/按钮，使五角星绘制在其下方
    val topReserve = h * 0.10f
    val topY = topReserve
    val scaleY = (groundY.toDouble() - topY) / (starY + 0.3)
    val scale = min(scaleX, scaleY)
    return Layout(scale, w / 2f, groundY)
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): ComposePath {
    val p = ComposePath()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

@Composable
private fun TopHud(engine: GameEngine, tick: Long, mode: GameMode, level: Int) {
    // 读取 tick 触发重绘
    val t = tick
    val score = engine.score
    val timeLeft = engine.timeLeft
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("得分", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f".format(score), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${mode.label()} · ${difficultyLabel(level)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f s".format(timeLeft), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ControlBar(
    manualActive: Boolean,
    onManual: () -> Unit,
    lqrOn: Boolean,
    onToggleLqr: () -> Unit,
    tiltOn: Boolean,
    onToggleTilt: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val btn = Modifier.width(78.dp).height(38.dp)
            OutlinedButton(
                onClick = onManual,
                modifier = btn,
                colors = if (manualActive) ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("手动", fontSize = 13.sp)
            }
            Button(
                onClick = onToggleLqr,
                modifier = btn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (lqrOn) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(if (lqrOn) "停止" else "自动", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onToggleTilt,
                modifier = btn,
                colors = if (tiltOn) ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("重力", fontSize = 13.sp)
            }
            OutlinedButton(onClick = onRestart, modifier = btn) {
                Text("重开", fontSize = 13.sp)
            }
            OutlinedButton(onClick = onExit, modifier = btn) {
                Text("退出", fontSize = 13.sp)
            }
        }
}

@Composable
private fun FinishOverlay(
    score: Double,
    rank: Int?,
    controlMode: ControlMode,
    playerName: String,
    onNameChange: (String) -> Unit,
    saved: Boolean,
    onSaveAndExit: () -> Unit,
    onSaveAndRestart: () -> Unit,
    onRestart: () -> Unit,
    onShowRank: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.92f).padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            // 横屏高度有限，内容可能超出：限制卡片高度并允许内部滚动，避免底部按钮被裁掉
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("时间到！", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("本局得分", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.1f".format(score), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                if (controlMode == ControlMode.LQR) {
                    // LQR 为自动演示，不计入排行榜
                    Text(
                        "LQR 自动演示不计分",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("再来一局")
                    }
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("退出")
                    }
                } else {
                    if (saved) {
                        if (rank != null && rank > 0) {
                            Text("已记录 · 本次排名第 $rank 名", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Text("成绩已记录", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("再来一局")
                        }
                        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("退出")
                        }
                    } else {
                        Text("输入名字记录成绩", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { onNameChange(it.take(16)) },
                            singleLine = true,
                            maxLines = 1,
                            placeholder = { Text("你的名字") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // 关键：输入名字后点此按钮＝保存并退出，确保记录被写入
                        Button(onClick = onSaveAndExit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("保存并退出")
                        }
                        OutlinedButton(onClick = onSaveAndRestart, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("保存并再来一局")
                        }
                        TextButton(onClick = onShowRank) {
                            Text("查看排行榜")
                        }
                        TextButton(onClick = onExit) {
                            Text("不保存，直接退出")
                        }
                    }
                }
            }
        }
    }
}
