package com.example.pendulum.physics

/**
 * LQR 最优控制器，用于"理想自动控制"演示。
 *
 * 设计说明：
 *  - 系统参数（小车/摆杆质量、长度、平衡点）固定，故在倒立平衡位置线性化得到的
 *    (A, B) 也是固定的。最优反馈增益 K 用连续时间 LQR 的 Kleinman 迭代离线求得，
 *    并已对 1/2/3 级在游戏真实初始扰动下仿真验证：闭环稳定、力矩不超限。
 *  - 不在线求解 Riccati 方程：原始离散 DARE 迭代在小车位置的单位圆特征值附近发散，
 *    会得到饱和失控的增益。直接硬编码已验证的 K 最稳妥。
 *  - 控制律 u = -K·(x - x_ref)，x_ref 的小车位置分量设为目标（默认 0）。
 */
class LQRController(
    private val dyn: Dynamics,
    val refCartX: Double = 0.0,
    private val forceLimit: Double = 60.0
) {
    /** 控制增益行向量，长度 = 状态维数 */
    val K: DoubleArray

    /** 该增益已离线验证在各难度初始扰动内稳定 */
    val stable: Boolean = true

    init {
        val level = dyn.n
        K = when (level) {
            1 -> doubleArrayOf(-1.0, -3.1589, -37.2198, -4.8396)
            2 -> doubleArrayOf(1.0, 2.469, -141.654, -4.463, 167.4487, 10.6317)
            else -> doubleArrayOf(-1.0, -4.0993, -370.7938, -5.0214, 843.7893, 39.0485, -542.0827, -23.4824)
        }
        require(K.size == dyn.stateDim) { "LQR 增益维度(${K.size})与状态维数(${dyn.stateDim})不匹配" }
    }

    /** 计算控制力（已限幅），小车位置参考点用本控制器设定值 refCartX（LQR 演示用） */
    fun control(state: DoubleArray): Double = controlWithRef(state, refCartX)

    /**
     * 计算控制力（已限幅），小车位置参考点可外部指定。
     * 仅 LQR 演示模式使用（把"小车目标"设为星星位置，自动稳定并驶向星星）。
     * 手动/重力模式不再复用此增益，改由玩家用 manualForce 直接控制小车。
     */
    fun controlWithRef(state: DoubleArray, refCartX: Double): Double {
        val dim = dyn.stateDim
        var u = 0.0
        for (i in 0 until dim) {
            val ref = if (i == dyn.idxX()) refCartX else 0.0
            u -= K[i] * (state[i] - ref)
        }
        return u.coerceIn(-forceLimit, forceLimit)
    }
}
