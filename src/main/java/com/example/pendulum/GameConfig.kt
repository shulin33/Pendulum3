package com.example.pendulum

import com.example.pendulum.physics.PendulumParams

/** 游戏模式：自由模式 / 限制模式 */
enum class GameMode {
    FREE, LIMITED
}

fun GameMode.label(): String = when (this) {
    GameMode.FREE -> "自由模式"
    GameMode.LIMITED -> "限制模式"
}

/** 难度等级（1/2/3 级倒立摆）对应的显示文案 */
fun difficultyLabel(level: Int): String = when (level) {
    1 -> "1 级摆"
    2 -> "2 级摆"
    else -> "3 级摆"
}

/**
 * 单局游戏的配置。
 * @param mode           游戏模式
 * @param level          难度（摆的级数 1~3）
 * @param timeLimitSec   单局时长（秒）
 * @param trackHalfWidth 小车可移动轨道半宽（米）
 * @param forceLimit     最大控制力（牛）
 * @param star           五角星目标位置与半径（世界坐标，米，y 向上）
 */
data class GameConfig(
    val mode: GameMode,
    val level: Int,
    val timeLimitSec: Double = 30.0,
    val trackHalfWidth: Double,
    val forceLimit: Double,
    val star: StarConfig
)

data class StarConfig(val x: Double, val y: Double, val radius: Double)

/** 依据模式与难度工厂创建配置。star 位置在每次开局时随机生成。 */
object GameConfigFactory {
    fun create(mode: GameMode, level: Int, starX: Double, totalLength: Double): GameConfig {
        val (trackHalfWidth, forceLimit) = when (mode) {
            GameMode.FREE -> 2.2 to 60.0
            GameMode.LIMITED -> 1.0 to 22.0
        }
        // 星星位于摆杆竖直时顶端略上方，x 为目标横向位置（含小车几何中心高度）
        val star = StarConfig(x = starX, y = PendulumParams.DEFAULT_POLE_BASE_Y + totalLength + 0.12, radius = 0.18)
        return GameConfig(
            mode = mode,
            level = level,
            timeLimitSec = 30.0,
            trackHalfWidth = trackHalfWidth,
            forceLimit = forceLimit,
            star = star
        )
    }
}
