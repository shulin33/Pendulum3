package com.example.pendulum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pendulum.GameMode
import com.example.pendulum.difficultyLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onStart: (GameMode, Int) -> Unit,
    onShowRank: (GameMode, Int) -> Unit
) {
    var mode by remember { mutableStateOf(GameMode.FREE) }
    var level by remember { mutableStateOf(1) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("倒立摆挑战") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "用你的手指移动小车，\n保持倒立摆平衡并触碰顶端的五角星。",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 模式选择
            SectionTitle("游戏模式")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeChip("自由模式", "轨道宽·控制力强", mode == GameMode.FREE, Modifier.weight(1f)) {
                    mode = GameMode.FREE
                }
                ModeChip("限制模式", "轨道窄·控制力弱", mode == GameMode.LIMITED, Modifier.weight(1f)) {
                    mode = GameMode.LIMITED
                }
            }

            // 难度选择
            SectionTitle("难度（摆的级数）")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (lv in 1..3) {
                    DifficultyChip(
                        label = difficultyLabel(lv),
                        selected = level == lv,
                        modifier = Modifier.weight(1f)
                    ) { level = lv }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onStart(mode, level) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("开始游戏", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onShowRank(mode, level) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("查看排行榜", fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "提示：也可点击「自动」按钮，观察理想自动控制（最优控制）如何让摆自动稳定并触碰星星。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ModeChip(title: String, sub: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sel = selected
    Card(
        modifier = modifier.selectable(selected = sel, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sub,
                fontSize = 12.sp,
                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DifficultyChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sel = selected
    Card(
        modifier = modifier
            .selectable(selected = sel, onClick = onClick)
            .height(72.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sel) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = if (sel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
