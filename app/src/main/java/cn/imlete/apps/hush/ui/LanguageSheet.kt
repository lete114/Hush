package cn.imlete.apps.hush.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.ui.components.DragHandle
import cn.imlete.apps.hush.ui.components.SheetContainer
import cn.imlete.apps.hush.ui.theme.HeadlineSize
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.util.AppLocale

/**
 * Drawer · language (spec 2026-10-06-i18n-design §5):
 * English / Chinese only; selecting takes effect immediately (save pref → sync notification → recreate). The selected state reuses the DurationSheet style.
 */
@Composable
fun LanguageSheet(
    visible: Boolean,
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    SheetContainer(visible, onDismiss) {
        DragHandle()
        Text(
            text = stringResource(R.string.settings_language),
            color = OnInk,
            fontSize = HeadlineSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        )
        SheetOptionRow(
            label = stringResource(R.string.language_en),
            selected = current == AppLocale.TAG_EN,
            onClick = { onSelect(AppLocale.TAG_EN) },
        )
        SheetOptionRow(
            label = stringResource(R.string.language_zh),
            selected = current == AppLocale.TAG_ZH,
            onClick = { onSelect(AppLocale.TAG_ZH) },
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 400)
@Composable
private fun LanguageSheetPreview() {
    HushTheme { LanguageSheet(true, AppLocale.TAG_EN, {}, {}) }
}
