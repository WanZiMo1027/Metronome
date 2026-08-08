package com.yuntian.metronome.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

@Composable
internal fun CountInControl(
    checked: Boolean,
    enabled: Boolean,
    isCountIn: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    statusTag: String,
    activeStatus: String = "正在播放一小节预备拍",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "预备拍", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (isCountIn) activeStatus else "开始前先播放一小节",
                style = MaterialTheme.typography.labelSmall,
                color = if (isCountIn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .testTag(statusTag),
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .semantics {
                    role = Role.Switch
                    contentDescription = "预备拍"
                    stateDescription = when {
                        isCountIn -> "正在预备，播放中不可更改"
                        checked && !enabled -> "已开启，播放中不可更改"
                        !checked && !enabled -> "已关闭，播放中不可更改"
                        checked -> "已开启"
                        else -> "已关闭"
                    }
                }
                .testTag(testTag),
        )
    }
}
