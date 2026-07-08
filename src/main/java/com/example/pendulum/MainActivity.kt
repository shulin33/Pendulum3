package com.example.pendulum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.pendulum.ui.GameScreen
import com.example.pendulum.ui.MenuScreen
import com.example.pendulum.ui.PendulumTheme
import com.example.pendulum.ui.RankScreen

sealed class Screen {
    object Menu : Screen()
    data class Game(val mode: GameMode, val level: Int) : Screen()
    data class Rank(val mode: GameMode, val level: Int) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PendulumTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
                when (val s = screen) {
                    is Screen.Menu -> MenuScreen(
                        onStart = { mode, level -> screen = Screen.Game(mode, level) },
                        onShowRank = { mode, level -> screen = Screen.Rank(mode, level) }
                    )
                    is Screen.Game -> GameScreen(
                        mode = s.mode,
                        level = s.level,
                        onExit = { screen = Screen.Menu },
                        onShowRank = { mode, level -> screen = Screen.Rank(mode, level) }
                    )
                    is Screen.Rank -> RankScreen(
                        initialMode = s.mode,
                        initialLevel = s.level,
                        onBack = { screen = Screen.Menu }
                    )
                }
            }
        }
    }
}
