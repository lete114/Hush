package cn.imlete.apps.hush.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.imlete.apps.hush.BuildConfig
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.ui.components.IconChip
import cn.imlete.apps.hush.ui.theme.BodySize
import cn.imlete.apps.hush.ui.theme.HeadlineSize
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.Ink
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.ui.theme.SmallSize
import cn.imlete.apps.hush.ui.theme.Surface
import cn.imlete.apps.hush.ui.theme.TitleSize
import cn.imlete.apps.hush.ui.theme.nightWash

/** Source repository URL: tapping the "Source" row opens the browser (the URL text is not shown, spec 2026-10-05-about-page revision). */
private const val REPO_URL = "https://github.com/lete114/Hush"

/**
 * About page —— centered identity block (app icon + Hush + Version) + author / source card.
 * Same structure as the settings page (night aurora background, theme.nightWash). The sponsor row is to be added once assets are ready.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        // The header is fixed outside the scrolling container: the back key and title stay put while the content scrolls (spec 2026-10-05-pinned-subpage-header)
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                AboutHeader(onBack)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                // Identity block: icon + app name + version (the version is shown only here, not in the settings entry)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The launcher icon is an adaptive-icon XML, which painterResource does not support (it throws "Only VectorDrawables...",
                // the crash is in the 2026-10-05 logcat) → use the foreground vector over the same background color #1A1725 (= Surface).
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = Color.Unspecified, // Multicolor vector (amber moon/clock) must not be tinted to a single color by the default tint
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Hush",
                        color = OnInk,
                        fontSize = HeadlineSize,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        color = Muted,
                        fontSize = SmallSize,
                    )
                }

                Spacer(Modifier.height(28.dp))

                AboutCard {
                    InfoRow(label = stringResource(R.string.about_author), leadingIcon = R.drawable.ic_person, value = "Lete114")
                    InfoRow(
                        label = stringResource(R.string.about_source),
                        leadingIcon = R.drawable.ic_code,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                            } catch (_: Exception) {
                                // No browser or a ROM blocking external links —— silently ignore; the about page does not depend on the jump succeeding
                            }
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.label_about), color = OnInk, fontSize = TitleSize, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.action_back),
                tint = Muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}

/**
 * Info row: leading icon + label (OnInk), [value] on the right (Muted, nullable);
 * when [onClick] is non-null the whole row is clickable and an ↗ external-link icon is shown at the far right.
 */
@Composable
private fun InfoRow(
    label: String,
    leadingIcon: Int,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(leadingIcon)
        Spacer(Modifier.width(10.dp))
        Text(label, color = OnInk, fontSize = BodySize, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = Muted, fontSize = SmallSize)
        }
        if (onClick != null) {
            if (value != null) Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_open_in_new),
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 929)
@Composable
private fun AboutScreenPreview() {
    HushTheme {
        Box(Modifier.fillMaxSize().background(Ink).nightWash()) {
            AboutScreen(onBack = {})
        }
    }
}
