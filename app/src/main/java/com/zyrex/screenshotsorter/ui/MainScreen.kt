package com.zyrex.screenshotsorter.ui

import android.os.Build
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyrex.screenshotsorter.R
import com.zyrex.screenshotsorter.core.LogEntry
import com.zyrex.screenshotsorter.core.Outcome

class MainActions(
    val onToggle: (Boolean) -> Unit = {},
    val onGrantStorage: () -> Unit = {},
    val onGrantUsage: () -> Unit = {},
    val onAllowBackground: () -> Unit = {},
    val onOpenAutostart: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onSortOld: () -> Unit = {},
    val onConfirmSortOld: () -> Unit = {},
    val onDismissSortOld: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(ui: MainUi, actions: MainActions) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SwitchCard(ui, actions.onToggle) }
            item { SectionTitle(R.string.setup_title) }
            item { SetupCard(ui.setup, actions) }
            item { SectionTitle(R.string.old_title) }
            item { OldCard(ui.oldSort, canSort = ui.setup.storage, onSortOld = actions.onSortOld) }
            item { SectionTitle(R.string.recent_title) }
            if (ui.recent.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            } else {
                items(ui.recent) { RecentRow(it) }
            }
        }
    }
    OldSortDialog(ui.oldSort, actions)
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
private fun SwitchCard(ui: MainUi, onToggle: (Boolean) -> Unit) {
    val ready = ui.setup.storage
    val on = ui.enabled && ready
    val container = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(if (on) R.string.sorting_on else R.string.sorting_off),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        when {
                            !ready -> R.string.sorting_needs_files
                            on -> R.string.sorting_on_detail
                            else -> R.string.sorting_off_detail
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = on, onCheckedChange = onToggle, enabled = ready)
        }
    }
}

private enum class StepState {
    Done, Todo, NotNeeded, Manual;

    companion object {
        fun of(done: Boolean) = if (done) Done else Todo
    }
}

@Composable
private fun SetupCard(setup: SetupUi, actions: MainActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        SetupRow(
            R.string.setup_files,
            stringResource(R.string.setup_files_detail),
            StepState.of(setup.storage),
            R.string.action_allow,
            actions.onGrantStorage,
        )
        HorizontalDivider()
        if (setup.usageNeeded) {
            // Android 15 hides this switch for apps installed from an APK until the user unlocks it.
            val restricted = !setup.usage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            val detail = stringResource(R.string.setup_usage_detail) +
                if (restricted) "\n" + stringResource(R.string.setup_usage_restricted) else ""
            SetupRow(R.string.setup_usage, detail, StepState.of(setup.usage), R.string.action_allow, actions.onGrantUsage)
        } else {
            SetupRow(
                R.string.setup_usage,
                stringResource(R.string.setup_usage_not_needed),
                StepState.NotNeeded,
                R.string.action_allow,
                actions.onGrantUsage,
            )
        }
        HorizontalDivider()
        SetupRow(
            R.string.setup_battery,
            stringResource(R.string.setup_battery_detail),
            StepState.of(setup.battery),
            R.string.action_allow,
            actions.onAllowBackground,
        )
        if (setup.brandHint != null) {
            HorizontalDivider()
            SetupRow(R.string.setup_autostart, setup.brandHint, StepState.Manual, R.string.action_open, actions.onOpenAutostart)
        }
        if (setup.asksNotifications) {
            HorizontalDivider()
            SetupRow(
                R.string.setup_notifications,
                stringResource(R.string.setup_notifications_detail),
                StepState.of(setup.notifications),
                R.string.action_allow,
                actions.onAllowNotifications,
            )
        }
    }
}

@Composable
private fun SetupRow(@StringRes title: Int, detail: String, state: StepState, @StringRes action: Int, onClick: () -> Unit) {
    val ticked = state == StepState.Done || state == StepState.NotNeeded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (ticked) R.drawable.ic_check_circle else R.drawable.ic_circle),
            contentDescription = null,
            tint = if (state == StepState.Done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state == StepState.Todo || state == StepState.Manual) {
            FilledTonalButton(onClick = onClick) { Text(stringResource(action)) }
        }
    }
}

@Composable
private fun OldCard(state: OldSort, canSort: Boolean, onSortOld: () -> Unit) {
    val busy = state is OldSort.Counting || state is OldSort.Working
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.old_detail), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSortOld, enabled = canSort && !busy) {
                    Text(stringResource(R.string.old_button))
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun OldSortDialog(state: OldSort, actions: MainActions) {
    when (state) {
        is OldSort.Confirm -> if (state.count > 0) {
            AlertDialog(
                onDismissRequest = actions.onDismissSortOld,
                title = { Text(pluralStringResource(R.plurals.old_confirm_title, state.count, state.count)) },
                text = { Text(stringResource(R.string.old_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = actions.onConfirmSortOld) { Text(stringResource(R.string.action_sort)) }
                },
                dismissButton = {
                    TextButton(onClick = actions.onDismissSortOld) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } else {
            NoticeDialog(stringResource(R.string.old_none_title), stringResource(R.string.old_none_text), actions.onDismissSortOld)
        }
        is OldSort.Done -> NoticeDialog(
            pluralStringResource(R.plurals.old_done_title, state.moved, state.moved),
            null,
            actions.onDismissSortOld,
        )
        else -> Unit
    }
}

@Composable
private fun NoticeDialog(title: String, text: String?, onDismiss: () -> Unit) {
    val body: (@Composable () -> Unit)? = if (text != null) {
        { Text(text) }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun RecentRow(entry: LogEntry) {
    @DrawableRes val icon: Int
    val tint: Color
    val text: String
    when (entry.outcome) {
        Outcome.MOVED -> {
            icon = R.drawable.ic_folder
            tint = MaterialTheme.colorScheme.primary
            text = stringResource(R.string.recent_moved, entry.detail)
        }
        Outcome.UNKNOWN_APP -> {
            icon = R.drawable.ic_help
            tint = MaterialTheme.colorScheme.outline
            text = stringResource(R.string.recent_unknown)
        }
        Outcome.FAILED -> {
            icon = R.drawable.ic_alert
            tint = MaterialTheme.colorScheme.error
            text = stringResource(R.string.recent_failed, entry.detail)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            DateUtils.getRelativeTimeSpanString(entry.timeMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
