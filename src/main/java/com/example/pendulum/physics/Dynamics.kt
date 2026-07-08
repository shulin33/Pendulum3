package com.example.pendulum.physics

import kotlin.math.cos
import kotlin.math.sin

/**
 * 通用 N 级小车倒立摆动力学（拉格朗日方程推导）。
 *
 * 广义坐标 q = [x, θ0, θ1, ..., θ_{n-1}]，其中 x 为小车水平位置，
 * θ_i 为第 i 根摆杆相对竖直向上方向的夹角（θ=0 为倒立平衡位置）。
 *
 * 状态向量布局（长度 2*(n+1)）：
 *   index 0      -> x
 *   index 1      -> ẋ
 *   index 2+2i   -> θ_i
 *   index 3+2i   -> θ̇_i
 */
class Dynamics(val params: PendulumParams) {
    val n = params.poleMasses.size
    val stateDim = 2 * (n + 1)

    fun idxX() = 0
    fun idxXDot() = 1
    fun idxTheta(i: Int) = 2 + 2 * i
    fun idxThetaDot(i: Int) = 3 + 2 * i

    // ---- 质量矩阵 M(q)，维数 (n+1) x (n+1) ----
    fun massMatrix(angles: DoubleArray): Array<DoubleArray> {
        val N = n + 1
        val m = params.poleMasses
        val L = params.poleLengths
        val I = params.poleInertias
        val A = Array(n) { DoubleArray(N) }   // A[i][col] COM x 速度对 q̇_col 的系数
        val B = Array(n) { DoubleArray(N) }   // B[i][col] COM y 速度对 q̇_col 的系数
        for (i in 0 until n) {
            A[i][0] = 1.0
            B[i][0] = 0.0
            for (k in 0 until n) {
                if (k < i) {
                    A[i][k + 1] = L[k] * cos(angles[k])
                    B[i][k + 1] = -L[k] * sin(angles[k])
                } else if (k == i) {
                    A[i][k + 1] = (L[i] / 2.0) * cos(angles[i])
                    B[i][k + 1] = -(L[i] / 2.0) * sin(angles[i])
                } else {
                    A[i][k + 1] = 0.0
                    B[i][k + 1] = 0.0
                }
            }
        }
        val M = Array(N) { DoubleArray(N) }
        M[0][0] = params.cartMass + m.sum()
        for (p in 0 until n) {
            for (q in 0 until n) {
                var s = 0.0
                for (i in 0 until n) {
                    s += m[i] * (A[i][p + 1] * A[i][q + 1] + B[i][p + 1] * B[i][q + 1])
                }
                if (p == q) s += I[p]
                M[p + 1][q + 1] = s
            }
            var sx = 0.0
            for (i in 0 until n) sx += m[i] * A[i][p + 1]
            M[0][p + 1] = sx
            M[p + 1][0] = sx
        }
        return M
    }

    // ---- 重力势能对广义坐标的梯度 ∂V/∂q ----
    fun gravityTorque(angles: DoubleArray): DoubleArray {
        val N = n + 1
        val g = params.gravity
        val L = params.poleLengths
        val m = params.poleMasses
        val grad = DoubleArray(N)
        for (j in 0 until n) {
            var sumAbove = 0.0
            for (i in (j + 1) until n) sumAbove += m[i]
            val Sj = sumAbove + m[j] / 2.0
            grad[j + 1] = -g * L[j] * sin(angles[j]) * Sj
        }
        return grad
    }

    /** 质量矩阵对第 a 个角度 θ_a 的偏导数（有限差分） */
    private fun dMass_dTheta(a: Int, angles: DoubleArray, h: Double = 1e-6): Array<DoubleArray> {
        val base = massMatrix(angles)
        val pert = angles.copyOf()
        pert[a] += h
        val plus = massMatrix(pert)
        val N = n + 1
        val d = Array(N) { DoubleArray(N) }
        for (i in 0 until N) for (j in 0 until N) d[i][j] = (plus[i][j] - base[i][j]) / h
        return d
    }

    /**
     * 计算状态导数 ẋ = f(state, force)
     * force: 作用在小车上的水平控制力 (N)
     */
    fun derivatives(state: DoubleArray, force: Double): DoubleArray {
        val N = n + 1
        val angles = DoubleArray(n) { state[idxTheta(it)] }
        val qdot = DoubleArray(N) { if (it == 0) state[idxXDot()] else state[idxThetaDot(it - 1)] }

        val M = massMatrix(angles)
        val dVdq = gravityTorque(angles)

        // 科氏/离心项 C_i = Σ_{j,k}(∂M_ij/∂q_k - ½∂M_jk/∂q_i) q̇_j q̇_k
        val C = DoubleArray(N)
        val dM = Array(n) { dMass_dTheta(it, angles) } // dM[a][i][j] = ∂M_ij/∂θ_a
        for (i in 0 until N) {
            for (j in 0 until N) {
                for (k in 0 until N) {
                    val dMij_dqk = if (k == 0) 0.0 else dM[k - 1][i][j]
                    val dMjk_dqi = if (i == 0) 0.0 else dM[i - 1][j][k]
                    C[i] += (dMij_dqk - 0.5 * dMjk_dqi) * qdot[j] * qdot[k]
                }
            }
        }

        // 右侧 rhs_i = Q_i - C_i - ∂V/∂q_i - 阻尼项
        val rhs = DoubleArray(N)
        rhs[0] = force - C[0] - dVdq[0]
        for (i in 1 until N) rhs[i] = 0.0 - C[i] - dVdq[i]
        // 阻尼：与广义速度成正比、方向相反（小车摩擦 + 摆杆关节/空气阻尼）
        rhs[0] -= params.cartDamping * qdot[0]
        for (i in 1 until N) rhs[i] -= params.poleDamping * qdot[i]

        // 解 M q̈ = rhs
        val qddot = solveLinear(M, rhs)

        val deriv = DoubleArray(stateDim)
        deriv[idxX()] = state[idxXDot()]
        deriv[idxXDot()] = qddot[0]
        for (i in 0 until n) {
            deriv[idxTheta(i)] = state[idxThetaDot(i)]
            deriv[idxThetaDot(i)] = qddot[i + 1]
        }
        return deriv
    }

    /** 四阶龙格-库塔积分一步 */
    fun stepRK4(state: DoubleArray, force: Double, dt: Double): DoubleArray {
        val k1 = derivatives(state, force)
        val s2 = add(state, k1, dt / 2)
        val k2 = derivatives(s2, force)
        val s3 = add(state, k2, dt / 2)
        val k3 = derivatives(s3, force)
        val s4 = add(state, k3, dt)
        val k4 = derivatives(s4, force)
        val out = DoubleArray(stateDim)
        for (i in 0 until stateDim) out[i] = state[i] + dt / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i])
        return out
    }

    private fun add(s: DoubleArray, d: DoubleArray, scale: Double): DoubleArray {
        val r = DoubleArray(s.size)
        for (i in s.indices) r[i] = s[i] + d[i] * scale
        return r
    }

    /** 高斯消元解 M x = b（M 为 (n+1)x(n+1) 方阵） */
    private fun solveLinear(M: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val N = b.size
        val A = Array(N) { DoubleArray(N + 1) }
        for (i in 0 until N) {
            System.arraycopy(M[i], 0, A[i], 0, N)
            A[i][N] = b[i]
        }
        for (col in 0 until N) {
            var pivot = col
            for (r in col + 1 until N) if (kotlin.math.abs(A[r][col]) > kotlin.math.abs(A[pivot][col])) pivot = r
            val tmp = A[col]; A[col] = A[pivot]; A[pivot] = tmp
            val pv = A[col][col]
            for (j in col..N) A[col][j] /= pv
            for (r in 0 until N) {
                if (r == col) continue
                val f = A[r][col]
                if (f == 0.0) continue
                for (j in col..N) A[r][j] -= f * A[col][j]
            }
        }
        return DoubleArray(N) { A[it][N] }
    }

    /**
     * 计算摆杆关节点世界坐标（y 轴向上，单位：米）。
     * 返回列表：索引 0 = 小车枢轴 (x,0)，之后依次为各级摆杆顶端。
     */
    fun jointPoints(state: DoubleArray): List<Pair<Double, Double>> {
        val x = state[idxX()]
        val pts = mutableListOf(Pair(x, params.poleBaseY))
        var cx = x
        var cy = params.poleBaseY
        for (i in 0 until n) {
            val th = state[idxTheta(i)]
            cx += params.poleLengths[i] * sin(th)
            cy += params.poleLengths[i] * cos(th)
            pts.add(Pair(cx, cy))
        }
        return pts
    }

    /** 最顶端摆杆尖端的世界坐标 */
    fun topTip(state: DoubleArray): Pair<Double, Double> = jointPoints(state).last()

    /** 小车枢轴（摆杆铰接点，即小车几何中心）世界坐标 */
    fun pivot(state: DoubleArray): Pair<Double, Double> = Pair(state[idxX()], params.poleBaseY)
}
