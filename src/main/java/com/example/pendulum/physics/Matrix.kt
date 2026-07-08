package com.example.pendulum.physics

/**
 * 极简矩阵库，仅用于 LQR 的 Riccati 求解（维度 ≤ 16）。
 * 所有运算使用 Double，规模为 O(n^3)，对小车倒立摆足够。
 */
class Matrix(val rows: Int, val cols: Int) {
    val data: Array<DoubleArray> = Array(rows) { DoubleArray(cols) }

    operator fun get(i: Int, j: Int): Double = data[i][j]
    operator fun set(i: Int, j: Int, v: Double) {
        data[i][j] = v
    }

    fun copy(): Matrix {
        val m = Matrix(rows, cols)
        for (i in 0 until rows) System.arraycopy(data[i], 0, m.data[i], 0, cols)
        return m
    }

    fun transpose(): Matrix {
        val t = Matrix(cols, rows)
        for (i in 0 until rows) for (j in 0 until cols) t.data[j][i] = data[i][j]
        return t
    }

    operator fun plus(other: Matrix): Matrix {
        require(rows == other.rows && cols == other.cols)
        val r = Matrix(rows, cols)
        for (i in 0 until rows) for (j in 0 until cols) r.data[i][j] = data[i][j] + other.data[i][j]
        return r
    }

    operator fun minus(other: Matrix): Matrix {
        require(rows == other.rows && cols == other.cols)
        val r = Matrix(rows, cols)
        for (i in 0 until rows) for (j in 0 until cols) r.data[i][j] = data[i][j] - other.data[i][j]
        return r
    }

    operator fun times(scalar: Double): Matrix {
        val r = Matrix(rows, cols)
        for (i in 0 until rows) for (j in 0 until cols) r.data[i][j] = data[i][j] * scalar
        return r
    }

    operator fun times(other: Matrix): Matrix {
        require(cols == other.rows)
        val r = Matrix(rows, other.cols)
        for (i in 0 until rows) {
            for (k in 0 until cols) {
                val a = data[i][k]
                if (a == 0.0) continue
                for (j in 0 until other.cols) r.data[i][j] += a * other.data[k][j]
            }
        }
        return r
    }

    /** 矩阵乘以向量，返回向量（列矩阵） */
    fun timesVec(v: DoubleArray): DoubleArray {
        require(cols == v.size)
        val r = DoubleArray(rows)
        for (i in 0 until rows) {
            var s = 0.0
            for (j in 0 until cols) s += data[i][j] * v[j]
            r[i] = s
        }
        return r
    }

    companion object {
        fun identity(n: Int): Matrix {
            val m = Matrix(n, n)
            for (i in 0 until n) m.data[i][i] = 1.0
            return m
        }

        fun zeros(rows: Int, cols: Int): Matrix = Matrix(rows, cols)

        fun column(v: DoubleArray): Matrix {
            val m = Matrix(v.size, 1)
            for (i in v.indices) m.data[i][0] = v[i]
            return m
        }

        fun row(v: DoubleArray): Matrix {
            val m = Matrix(1, v.size)
            for (j in v.indices) m.data[0][j] = v[j]
            return m
        }
    }

    /** 高斯-约当求逆，要求非奇异矩阵 */
    fun inverse(): Matrix {
        require(rows == cols)
        val n = rows
        val aug = Array(n) { DoubleArray(2 * n) }
        for (i in 0 until n) {
            System.arraycopy(data[i], 0, aug[i], 0, n)
            aug[i][n + i] = 1.0
        }
        for (col in 0 until n) {
            // 选主元
            var pivot = col
            for (r in col + 1 until n) if (kotlin.math.abs(aug[r][col]) > kotlin.math.abs(aug[pivot][col])) pivot = r
            val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            val pv = aug[col][col]
            if (kotlin.math.abs(pv) < 1e-12) throw ArithmeticException("矩阵接近奇异，无法求逆")
            for (j in 0 until 2 * n) aug[col][j] /= pv
            for (r in 0 until n) {
                if (r == col) continue
                val f = aug[r][col]
                if (f == 0.0) continue
                for (j in 0 until 2 * n) aug[r][j] -= f * aug[col][j]
            }
        }
        val inv = Matrix(n, n)
        for (i in 0 until n) System.arraycopy(aug[i], n, inv.data[i], 0, n)
        return inv
    }
}
