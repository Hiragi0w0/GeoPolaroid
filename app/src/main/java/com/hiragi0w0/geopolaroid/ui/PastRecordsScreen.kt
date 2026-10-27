package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import java.time.LocalDate

/** SC-02 過去の記録。完成した記録日を新しい順に、代表写真と枚数で並べる。 */
@Composable
fun PastRecordsScreen(
    dates: List<RecordDateSummary>,
    onBack: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    Scaffold(
        topBar = { PastRecordsTopBar(onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (dates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.past_empty_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.past_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(dates, key = { it.recordDate.toString() }) { summary ->
                    PastRecordRow(summary = summary, onClick = { onSelect(summary.recordDate) })
                }
            }
        }
    }
}

@Composable
private fun PastRecordsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = stringResource(R.string.home_past_records),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PastRecordRow(summary: RecordDateSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThumbnailImage(
            imageUri = summary.coverImageUri,
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = DisplayFormat.date(summary.recordDate),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
            )
            Text(
                text = stringResource(R.string.past_item_count, summary.photoCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
