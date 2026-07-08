package com.example.pendulum.physics

import kotlin.math.PI
import kotlin.random.Random

/**
 * 小车倒立摆物理参数。
 * @param cartMass   小车质量 (kg)
 * @param poleMasses 各级摆杆质量 (kg)，长度 = 摆杆级数
 * @param poleLengths 各级摆杆总长度 (m)
 * @param poleInertias 各级摆杆绕质心的转动惯量 (kg·m²)
 * @param gravity    重力加速度 (m/s²)
 * @param poleBaseY  摆杆铰接点（小车几何中心）距轨道的高度 (m)
 * @param cartDamping 小车与轨道间的线性阻尼系数
 * @param poleDamping 摆杆关节/空气的线性阻尼系数
 */
data class PendulumParams(
    val cartMass: Double,
    val poleMasses: DoubleArray,
    val poleLengths: DoubleArray,
    val poleInertias: DoubleArray,
    val gravity: Double = 9.81,
    val poleBaseY: Double = DEFAULT_POLE_BASE_Y,
    val cartDamping: Double = 0.6,
    val poleDamping: Double = 0.08
) {
    val level: Int get() = poleMasses.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendulumParams) return false
        return cartMass == other.cartMass &&
                poleMasses.contentEquals(other.poleMasses) &&
                poleLengths.contentEquals(other.poleLengths) &&
                poleInertias.contentEquals(other.poleInertias) &&
                gravity == other.gravity &&
                poleBaseY == other.poleBaseY &&
                cartDamping == other.cartDamping &&
                poleDamping == other.poleDamping
    }

    override fun hashCode(): Int {
        var r = cartMass.hashCode()
        r = 31 * r + poleMasses.contentHashCode()
        r = 31 * r + poleLengths.contentHashCode()
        r = 31 * r + poleInertias.contentHashCode()
        r = 31 * r + gravity.hashCode()
        r = 31 * r + poleBaseY.hashCode()
        r = 31 * r + cartDamping.hashCode()
        r = 31 * r + poleDamping.hashCode()
        return r
    }

    companion object {
        /** 小车几何中心（摆杆铰接点）的高度，单位 m */
        const val DEFAULT_POLE_BASE_Y = 0.15

        /** 根据难度等级构造参数：1/2/3 级倒立摆 */
        fun forLevel(level: Int): PendulumParams {
            require(level in 1..3) { "难度等级必须为 1~3" }
            val cartMass = 1.0
            val m = 0.2
            val L = 0.5
            val I = (1.0 / 12.0) * m * L * L // 细杆绕质心转动惯量
            val masses = DoubleArray(level) { m }
            val lengths = DoubleArray(level) { L }
            val inertias = DoubleArray(level) { I }
            return PendulumParams(cartMass, masses, lengths, inertias)
        }

        /** 在倒立平衡位置附近生成随机初始状态（角度、角速度、位置均小幅随机） */
        fun randomInitialState(level: Int, rng: Random = Random.Default): DoubleArray {
            val n = level
            val stateDim = 2 * (n + 1)
            val s = DoubleArray(stateDim)
            // 难度越大（摆杆级数越多），系统越难控，初始扰动反而要更小，
            // 否则连"理想 LQR 自动控制"也无法在限幅内稳定住（吸引域更小）。
            // 1/2 级在 0.08/0.10 下 LQR 已 40/40 稳定；3 级需收到 0.05。
            val delta = when (level) {
                1 -> 0.08
                2 -> 0.10
                else -> 0.05
            }
            s[0] = (rng.nextDouble() * 2 - 1) * 0.3          // x
            s[1] = (rng.nextDouble() * 2 - 1) * 0.2          // ẋ
            for (i in 0 until n) {
                s[2 + 2 * i] = (rng.nextDouble() * 2 - 1) * delta          // θ_i
                s[3 + 2 * i] = (rng.nextDouble() * 2 - 1) * 0.25           // θ̇_i
            }
            return s
        }
    }
}
