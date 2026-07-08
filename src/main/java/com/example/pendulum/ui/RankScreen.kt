package com.example.pendulum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pendulum.GameMode
import com.example.pendulum.difficultyLabel
import com.example.pendulum.label
import com.example.pendulum.storage.RankRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(
    initialMode: GameMode,
    initialLevel: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RankRepository(context) }

    var mode by remember { mutableStateOf(initialMode) }
    var level by remember { mutableStateOf(initialLevel) }
    var scores by remember { mutableStateOf(repo.getScores(mode, level)) }

    fun refresh() { scores = repo.getScores(mode, level) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排行榜") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("‹ 返回", fontSize = 15.sp, maxLines = 1, softWrap = false)
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 左半：模式 / 难度筛选
            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("筛选", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                // 模式选择
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (m in GameMode.values()) {
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m; refresh() },
                            label = { Text(m.label()) }
                        )
                    }
                }
                // 难度选择
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (lv in 1..3) {
                        FilterChip(
                            selected = level == lv,
                            onClick = { level = lv; refresh() },
                            label = { Text(difficultyLabel(lv)) }
                        )
                    }
                }
                Text(
                    "当前：${mode.label()} · ${difficultyLabel(level)}",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // 右半：说明 + 记录列表
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("榜单说明", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "· 仅手动 / 重力模式记录成绩 · 自动演示不排名 · 每榜保留前 10 名",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scores.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无记录\n快去挑战吧！", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(scores) { idx, e ->
                            RankRow(rank = idx + 1, name = e.name, score = e.score, date = e.date)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, name: String, score: Double, date: Long) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateStr = fmt.format(Date(date))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rank == 1) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "#$rank",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
                color = if (rank == 1) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                dateStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%.1f".format(score),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
    }
}
