package app.fixd.messaging.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.fixd.messaging.data.GroupRepository
import kotlinx.coroutines.launch

/**
 * Screen for creating a named group conversation.
 *
 * The user enters a group name and adds member phone numbers one at a time.
 * On "Create", [GroupRepository.createGroup] is called and the caller receives
 * the new groupId via [onGroupCreated] to navigate to the group thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupComposeScreen(
    onBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var groupName by remember { mutableStateOf("") }
    var memberInput by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun addMember() {
        val num = memberInput.trim().filter { it.isDigit() || it == '+' }
        if (num.length >= 7 && num !in members) {
            members = members + num
            memberInput = ""
            error = null
        } else if (num in members) {
            error = "Already added"
        } else {
            error = "Enter a valid phone number"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group name
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group name") },
                leadingIcon = { Icon(Icons.Filled.Group, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Add member row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = memberInput,
                    onValueChange = { memberInput = it; error = null },
                    label = { Text("Add phone number") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMember() })
                )
                FilledIconButton(onClick = { addMember() }) {
                    Icon(Icons.Filled.Add, "Add member")
                }
            }

            // Members list
            if (members.isNotEmpty()) {
                Text(
                    "${members.size} member${if (members.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(members) { num ->
                        ListItem(
                            headlineContent = { Text(num) },
                            trailingContent = {
                                IconButton(onClick = { members = members - num }) {
                                    Icon(Icons.Filled.Close, "Remove $num")
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Add at least 2 members to create a group",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Create button
            Button(
                onClick = {
                    if (groupName.isBlank()) { error = "Enter a group name"; return@Button }
                    if (members.size < 2) { error = "Add at least 2 members"; return@Button }
                    creating = true
                    scope.launch {
                        val group = GroupRepository(ctx).createGroup(groupName.trim(), members)
                        creating = false
                        onGroupCreated(group.groupId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating && groupName.isNotBlank() && members.size >= 2
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Group")
                }
            }
        }
    }
}
