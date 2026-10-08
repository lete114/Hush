package cn.imlete.apps.hush.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.Track

/**
 * Row-leading icon chip —— 32dp rounded square (radius 10) with a Track background + 18dp icon (Muted).
 * The about-page rows and settings-page rows share this component, keeping the two pages visually identical; color/size changes touch only this file.
 * (Color-selection history: spec 2026-10-05-about-page revision 6–9.)
 */
@Composable
fun IconChip(icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Track),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
