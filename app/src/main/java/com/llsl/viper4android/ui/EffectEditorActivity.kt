package com.llsl.viper4android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.llsl.viper4android.ui.screens.editor.EffectEditorScreen
import com.llsl.viper4android.ui.screens.editor.EditorKind
import com.llsl.viper4android.ui.screens.editor.editorKindFromRoute
import com.llsl.viper4android.ui.screens.editor.EffectEditorViewModel
import com.llsl.viper4android.ui.theme.ViperTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@AndroidEntryPoint
class EffectEditorActivity : ComponentActivity() {
    private var editorKind: EditorKind? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorKind = editorKindFromRoute(intent.getStringExtra(EXTRA_EDITOR_KIND))
        val kind = editorKind ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            ViperTheme {
                EffectEditorScreen(
                    kind = kind,
                    viewModel = hiltViewModel(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The ViewModel flushes settled values; this callback keeps back/background durable.
    }

    companion object {
        private const val EXTRA_EDITOR_KIND = "editor_kind"

        fun createIntent(
            context: Context,
            kind: EditorKind,
        ): Intent = Intent(context, EffectEditorActivity::class.java).putExtra(EXTRA_EDITOR_KIND, kind.route)
    }
}
