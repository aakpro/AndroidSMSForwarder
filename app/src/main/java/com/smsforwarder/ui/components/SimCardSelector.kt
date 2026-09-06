package com.smsforwarder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.smsforwarder.data.model.SimCardInfo
import com.smsforwarder.data.model.SimFilterOption

@Composable
fun SimCardSelector(
    activeSims: List<SimCardInfo>,
    selectedFilter: SimFilterOption,
    onFilterSelected: (SimFilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (activeSims.isNotEmpty()) Icons.Default.SimCard else Icons.Default.SimCardAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Dual-SIM Configuration",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display detected SIMs
            if (activeSims.isEmpty()) {
                Text(
                    text = "No active SIM cards detected (or READ_PHONE_STATE permission needed)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeSims.forEach { sim ->
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "SIM ${sim.slotIndex + 1}: ${sim.displayName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = sim.carrierName,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                sim.phoneNumber?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose which SIM card messages to forward:",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            SimFilterOption.entries.forEach { option ->
                val label = when (option) {
                    SimFilterOption.ALL -> "Both SIMs (Forward All)"
                    SimFilterOption.SIM_1 -> {
                        val carrier1 = activeSims.find { it.slotIndex == 0 }?.carrierName ?: "SIM 1"
                        "SIM 1 Only ($carrier1)"
                    }
                    SimFilterOption.SIM_2 -> {
                        val carrier2 = activeSims.find { it.slotIndex == 1 }?.carrierName ?: "SIM 2"
                        "SIM 2 Only ($carrier2)"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (option == selectedFilter),
                            onClick = { onFilterSelected(option) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == selectedFilter),
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
