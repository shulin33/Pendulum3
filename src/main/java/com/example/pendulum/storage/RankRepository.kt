package com.example.pendulum.storage

import android.content.Context
import com.example.pendulum.GameMode
import java.io.File
import java.util.Locale

data class RankEntry(val name: String, val score: Double, val date: Long)

/**
 * 排行榜存储：按 (模式, 难度) 分别保存前若干名成绩。
 *
 * 物理文件极小：单文件紧凑文本 `ranks.txt`（位于应用私有 filesDir），
 * 每行一个榜单：`KEY=s1|d1|n1,s2|d2|n2,...`
 *   - KEY  = `${mode}_${level}`
 *   - 字段 = 分数|时间戳|名字（竖线分隔，名字已清洗去除分隔符）
 *   - 条目间逗号分隔
 * 调用方应只在手动(TOUCH)/重力(TILT)模式下调用 addScore；LQR 演示不计分。
 */
class RankRepository(context: Context) {
    private val file = File(context.filesDir, "ranks.txt")
    private val lock = Any()

    private fun key(mode: GameMode, level: Int) = "${mode.name}_$level"

    /** 添加一局成绩，返回名次（1 起），超出榜单返回 -1 */
    fun addScore(mode: GameMode, level: Int, score: Double, rawName: String): Int {
        val name = rawName.replace(Regex("[\r\n,|]"), " ").replace(Regex("\\s+"), " ").trim().take(16).ifBlank { "匿名" }
        val entry = RankEntry(name, score, System.currentTimeMillis())
        synchronized(lock) {
            val all = readAll()
            val list = all.getOrDefault(key(mode, level), mutableListOf())
            list.add(entry)
            list.sortByDescending { it.score }
            all[key(mode, level)] = list.take(MAX_ENTRIES).toMutableList()
            writeAll(all)
            val trimmed = all[key(mode, level)]!!
            val pos = trimmed.indexOfFirst { it.date == entry.date && it.score == entry.score && it.name == entry.name }
            return if (pos >= 0) pos + 1 else -1
        }
    }

    fun getScores(mode: GameMode, level: Int): List<RankEntry> {
        synchronized(lock) {
            return readAll()[key(mode, level)]?.sortedByDescending { it.score } ?: emptyList()
        }
    }

    fun clear(mode: GameMode, level: Int) {
        synchronized(lock) {
            val all = readAll()
            all.remove(key(mode, level))
            writeAll(all)
        }
    }

    private fun readAll(): MutableMap<String, MutableList<RankEntry>> {
        val map = mutableMapOf<String, MutableList<RankEntry>>()
        if (!file.exists()) return map
        file.readLines().forEach { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val k = line.substring(0, eq)
            val v = line.substring(eq + 1)
            if (v.isBlank()) return@forEach
            val list = v.split(",").mapNotNull { seg ->
                val p = seg.split("|")
                if (p.size == 3) {
                    val s = p[0].toDoubleOrNull()
                    val d = p[1].toLongOrNull()
                    if (s != null && d != null) RankEntry(p[2], s, d) else null
                } else null
            }.toMutableList()
            if (list.isNotEmpty()) map[k] = list
        }
        return map
    }

    private fun writeAll(all: Map<String, List<RankEntry>>) {
        val sb = StringBuilder()
        all.forEach { (k, list) ->
            if (list.isEmpty()) return@forEach
            val line = list.joinToString(",") {
                "${String.format(Locale.US, "%.4f", it.score)}|${it.date}|${it.name}"
            }
            sb.append(k).append('=').append(line).append('\n')
        }
        file.writeText(sb.toString())
    }

    companion object {
        /** 每个榜单最多保留条数，控制文件大小 */
        const val MAX_ENTRIES = 10
    }
}
