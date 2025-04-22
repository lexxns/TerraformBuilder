package terraformbuilder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun templateLibraryPanel(
    onTemplateSelected: (String, (String, Offset) -> CompositeBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Composite Templates",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Template items
        item {
            templateItem(
                name = "REST API",
                description = "API Gateway with Lambda and IAM roles",
                iconCode = "Api",
                color = Color(0xFF4285F4), // Blue
                onClick = {
                    onTemplateSelected("New REST API") { name, position ->
                        CompositeBlockFactory.createRestApi(name, position)
                    }
                }
            )
        }

        item {
            templateItem(
                name = "VPC Network",
                description = "VPC with subnets, route tables, and NAT gateways",
                iconCode = "Cloud",
                color = Color(0xFF34A853), // Green
                onClick = {
                    onTemplateSelected("New VPC Network") { name, position ->
                        CompositeBlockFactory.createVpcNetwork(name, position)
                    }
                }
            )
        }

        item {
            templateItem(
                name = "Serverless Backend",
                description = "Lambda, DynamoDB, and API Gateway",
                iconCode = "Code",
                color = Color(0xFFEA4335), // Red
                onClick = {
                    onTemplateSelected("New Serverless Backend") { name, position ->
                        CompositeBlockFactory.createServerlessBackend(name, position)
                    }
                }
            )
        }

        item {
            templateItem(
                name = "Database Cluster",
                description = "RDS instances and security groups",
                iconCode = "Storage",
                color = Color(0xFFFBBC05), // Yellow
                onClick = {
                    onTemplateSelected("New Database Cluster") { name, position ->
                        CompositeBlockFactory.createDatabaseCluster(name, position)
                    }
                }
            )
        }

        item {
            templateItem(
                name = "Empty Group",
                description = "Empty Group",
                iconCode = "Category",
                color = Color(0xFF9E9E9E), // Gray
                onClick = {
                    onTemplateSelected("New Group") { name, position ->
                        CompositeBlockFactory.createCustomGroup(name, position)
                    }
                }
            )
        }
    }
}

@Composable
private fun templateItem(
    name: String,
    description: String,
    iconCode: String,
    color: Color,
    onClick: () -> Unit
) {
    // Function to resolve the icon from string name (same as in compositeBlockView)
    val icon = remember(iconCode) {
        try {
            val iconClass = Icons.Filled::class.java
            val field = iconClass.getDeclaredField(iconCode)
            field.isAccessible = true
            field.get(Icons.Filled) as ImageVector
        } catch (e: Exception) {
            Icons.Default.Category
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color-coded icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}