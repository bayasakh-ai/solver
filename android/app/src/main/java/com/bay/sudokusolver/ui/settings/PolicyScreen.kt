package com.bay.sudokusolver.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bay.sudokusolver.PolicyText
import com.bay.sudokusolver.ui.board.BackButton
import com.bay.sudokusolver.ui.theme.Classical
import com.bay.sudokusolver.ui.theme.SudofolioTheme

/*
 * The privacy policy inside the app (Settings > About). Not in the design files: built
 * from the theme's type. The text comes from assets/privacy-policy.md (PolicyText).
 */
@Composable
fun PolicyScreen(markdown: String, onBack: () -> Unit) {
    val blocks = remember(markdown) { PolicyText.parse(markdown) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            BackButton("Back", onBack, Modifier.padding(start = 10.dp, top = 8.dp))
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (block in blocks) {
                    val text = buildAnnotatedString {
                        for (s in block.spans) withStyle(SpanStyle(
                            // the design system has no bold: semibold at most
                            fontWeight = if (s.strong) FontWeight.SemiBold else null,
                            fontStyle = if (s.emphasis) FontStyle.Italic else null,
                        )) { append(s.text) }
                    }
                    when (block.kind) {
                        PolicyText.Kind.TITLE -> Text(text, style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() })
                        PolicyText.Kind.HEADING -> Text(text, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                        PolicyText.Kind.PARAGRAPH -> Text(text, style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2)
                        PolicyText.Kind.BULLET -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("–", style = MaterialTheme.typography.bodyMedium, color = Classical.TextSecondary)
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = Classical.TextBody2, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Privacy policy", widthDp = 390, heightDp = 844)
@Composable
private fun PPolicy() = SudofolioTheme {
    PolicyScreen("# Sudofolio privacy policy\n\n- **App:** Sudofolio\n- **Contact:** someone\n\n## The short version\n\n" +
        "Sudofolio reads a sudoku through your phone's camera and works it out on your phone.\nIt has no internet access.\n\n" +
        "## What the app accesses\n\n**Camera.** The app asks for the camera. Choose *Not now* to go without.\n\n" +
        "- No photo or video is saved.\n- Camera pictures never leave your phone.", onBack = {})
}
