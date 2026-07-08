package com.example.pendulum

import com.example.pendulum.physics.Dynamics
import com.example.pendulum.physics.LQRController
import com.example.pendulum.physics.PendulumParams
import kotlin.math.abs
import kotlin.random.Random

enum class GameStatus { IDLE, RUNNING, FINISHED }

/**
 * 控制模式：三种方式互斥。
 *  - TOUCH：触摸拖动屏幕左右移动小车（玩家手动控制）
 *  - TILT ：重力感应（加速度计）控制小车
 *  - LQR  ：理想自动演示（最优控制，无需玩家输入）
 */
enum class ControlMode { TOUCH, TILT, LQR }

/**
 * 游戏引擎：驱动物理仿真、触摸/自动控制和积分逻辑。
 * 物理积分采用固定步长（子步）以保证数值稳定，与渲染帧率解耦。
 */
class GameEngine(private val config: GameConfig) {
    companion object {
        /** LQR 演示专用力限：作为"理想自动控制"应给足权限，确保各难度都能稳定。
         *  手动/重力模式复用同一控制器，力限同样为 60（见 LQRController 默认）。 */
        private const val LQR_FORCE = 60.0
    }

    private val params = PendulumParams.forLevel(config.level)
    val dyn = Dynamics(params)

    var state = PendulumParams.randomInitialState(config.level)
        private set
    var score = 0.0
        private set
    var timeLeft = config.timeLimitSec
        private set
    var status = GameStatus.IDLE
        private set

    /** 当前控制模式（三种互斥） */
    var controlMode = ControlMode.TOUCH
        private set

    /** 是否已处于触摸中（用于绘制瞄准线） */
    var inStar = false
        private set

    /** 触摸目标的小车横坐标（世界坐标，米）；null 表示当前未触摸 */
    var touchTargetX: Double? = null
        private set

    /** 重力感应得到的目标小车横坐标（世界坐标，米）；null 表示无数据 */
    var tiltTargetX: Double? = null
        private set

    private var lqr: LQRController? = null
    private val rng = Random.Default

    fun start() {
        state = PendulumParams.randomInitialState(config.level, rng)
        score = 0.0
        timeLeft = config.timeLimitSec
        status = GameStatus.RUNNING
        inStar = false
        tiltTargetX = null
        // 提前创建 LQR 控制器：仅 LQR 演示模式使用其全状态反馈自动稳定整套系统；
        // 手动/重力模式由玩家控制小车（PD 跟踪目标），摆角由物理自由演化，挑战性来自玩家自身。
        lqr = LQRController(dyn, refCartX = config.star.x, forceLimit = LQR_FORCE)
        controlMode = ControlMode.TOUCH
        touchTargetX = state[dyn.idxX()]
    }

    /** 锁定/解锁 LQR 自动演示 */
    fun setLQR(on: Boolean) {
        if (on) {
            controlMode = ControlMode.LQR
            touchTargetX = null
            tiltTargetX = null
        } else {
            controlMode = ControlMode.TOUCH
            touchTargetX = state[dyn.idxX()]
        }
    }

    /** 回到手动（触摸）模式 */
    fun setManualMode() {
        controlMode = ControlMode.TOUCH
        tiltTargetX = null
        touchTargetX = state[dyn.idxX()]
    }

    /** 开启/关闭重力感应控制（与 LQR、触摸互斥） */
    fun setTiltMode(on: Boolean) {
        if (on) {
            controlMode = ControlMode.TILT
            touchTargetX = null
        } else {
            controlMode = ControlMode.TOUCH
            tiltTargetX = null
            touchTargetX = state[dyn.idxX()]
        }
    }

    /** 触摸目标位置（仅在手动模式下生效） */
    fun setTouch(worldX: Double?) {
        if (controlMode == ControlMode.TOUCH) touchTargetX = worldX
    }

    /** 重力感应目标位置（仅在 TILT 模式下生效） */
    fun setTiltTarget(worldX: Double?) {
        if (controlMode == ControlMode.TILT) tiltTargetX = worldX
    }

    /** 主更新：realDt 为真实经过时间（秒） */
    fun update(realDt: Double) {
        if (status != GameStatus.RUNNING) return
        val fixed = 0.004
        var acc = minOf(realDt, 0.05)
        while (acc > 1e-6) {
            val h = minOf(fixed, acc)
            val force = computeForce()
            state = dyn.stepRK4(state, force, h)
            clampCart()
            checkStar(h)
            timeLeft -= h
            acc -= h
        }
        if (timeLeft <= 0.0) {
            timeLeft = 0.0
            status = GameStatus.FINISHED
        }
    }

    private fun computeForce(): Double {
        return when (controlMode) {
            // LQR 演示：完整状态反馈 + 理想力限，自动把整套系统（含摆角）稳住并驶向星星。
            ControlMode.LQR -> lqr?.control(state) ?: 0.0
            // 重力感应：加速度计 → 小车目标位置；小车 PD 跟踪，摆角由物理演化（玩家间接平衡）。
            ControlMode.TILT -> manualForce(tiltTargetX ?: 0.0)
            // 手动（触摸）：手指 → 小车目标位置；小车 PD 跟踪，摆角由物理演化（玩家间接平衡）。
            ControlMode.TOUCH -> manualForce(touchTargetX ?: 0.0)
        }
    }

    /**
     * 玩家手动控制力：只把小车作为被控对象，用 PD 跟踪目标位置。
     * 摆角不受任何自动增益干预，完全由物理（重力 + 小车运动）决定，
     * 因此玩家必须自己通过移动小车来接住 / 平衡摆杆——这才是倒立摆手动模式的挑战。
     * 力限取当前模式的 forceLimit（自由 60N / 限制 22N）。
     */
    private fun manualForce(targetX: Double): Double {
        val x = state[dyn.idxX()]
        val xdot = state[dyn.idxXDot()]
        val f = 60.0 * (targetX - x) - 15.0 * xdot
        return f.coerceIn(-config.forceLimit, config.forceLimit)
    }

    private fun clampCart() {
        val x = state[dyn.idxX()]
        val limit = config.trackHalfWidth
        if (x > limit) {
            state[dyn.idxX()] = limit
            if (state[dyn.idxXDot()] > 0) state[dyn.idxXDot()] = 0.0
        } else if (x < -limit) {
            state[dyn.idxX()] = -limit
            if (state[dyn.idxXDot()] < 0) state[dyn.idxXDot()] = 0.0
        }
    }

    private fun checkStar(h: Double) {
        val tip = dyn.topTip(state)
        val dx = tip.first - config.star.x
        val dy = tip.second - config.star.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist <= config.star.radius) {
            if (!inStar) {
                score += 50.0            // 触碰奖励
                inStar = true
            }
            score += 30.0 * h            // 持续接触积分
        } else {
            inStar = false
        }
    }

    /** 当前顶端摆杆尖端的世界坐标 */
    fun topTip(): Pair<Double, Double> = dyn.topTip(state)

    /** 当前各级关节世界坐标 */
    fun joints(): List<Pair<Double, Double>> = dyn.jointPoints(state)

    /** LQR 是否成功（用于演示提示） */
    fun lqrStable(): Boolean = lqr?.stable ?: false
}
