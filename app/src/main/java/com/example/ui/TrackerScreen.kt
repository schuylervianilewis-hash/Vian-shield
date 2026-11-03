package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SkyBlueBackground
import com.example.ui.theme.SkyBlueOnPrimaryContainer
import com.example.ui.theme.SkyBluePrimaryContainer
import com.example.ui.theme.SkyBlueSurface
import com.example.ui.theme.SkyBlueSurfaceBorder
import com.example.ui.theme.SkyBlueTextPrimary
import com.example.ui.theme.SkyBlueTextSecondary
import com.example.ui.theme.SkyBlueTextTertiary

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SkyBlueBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tracker",
                        fontWeight = FontWeight.Bold,
                        color = SkyBlueTextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SkyBlueSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tracker_placeholder_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlueSurface),
                border = BorderStroke(1.dp, SkyBlueSurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = SkyBlueOnPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tracker monitoring is not active yet",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueTextPrimary
                        )
                        Text(
                            text = "This space is reserved for app tracker insights. Tracker detection, data usage details and country information will appear here after the TrackerControl engine is integrated.",
                            fontSize = 13.sp,
                            color = SkyBlueTextSecondary
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tracker_integration_status"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pending,
                    contentDescription = null,
                    tint = SkyBlueTextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "TrackerControl integration pending",
                    fontSize = 12.sp,
                    color = SkyBlueTextTertiary
                )
            }
        }
    }
}
