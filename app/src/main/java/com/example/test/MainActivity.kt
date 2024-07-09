package com.example.test

import android.accounts.AccountManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.AccountPicker
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    internal val REQUEST_AUTHORIZATION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoogleSheetReaderApp(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTHORIZATION && resultCode == Activity.RESULT_OK) {
            setContent {
                GoogleSheetReaderApp(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSheetReaderApp(activity: MainActivity) {
    val context = LocalContext.current
    var sheetData by remember { mutableStateOf<List<List<Any>>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val spreadsheetId = "1Imz_p7t_jbVG7pvsPKgY-mCmK4Hveob-vgUNJkbokVI" // Spreadsheets ID
    val range = "test!A:C" // Range
    var selectedAccountName by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            selectedAccountName = accountName ?: ""
        }
    }

    LaunchedEffect(selectedAccountName) {
        if (selectedAccountName.isNotEmpty()) {
            loadData(context, spreadsheetId, range, selectedAccountName) { data ->
                sheetData = data
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Google Sheet Reader") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                coroutineScope.launch {
                    isLoading = true
                    if (selectedAccountName.isNotEmpty()) {
                        loadData(context, spreadsheetId, range, selectedAccountName) { data ->
                            sheetData = data
                        }
                    } else {
                        startAccountPicker(context, launcher)
                    }
                }
            }) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Icon(Icons.Filled.Refresh, "Обновить")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.wrapContentSize())
            } else if (sheetData != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    itemsIndexed(sheetData!!) { rowIndex, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            row.indices.forEach { columnIndex ->
                                val cellValue = row[columnIndex].toString()
                                Text(
                                    text = cellValue,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp),
                                    fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            } else {
                Text("Ошибка загрузки данных", color = Color.Red)
            }
        }
    }
}


private suspend fun loadData(
    context: android.content.Context,
    spreadsheetId: String,
    range: String,
    accountName: String,
    onDataLoaded: (List<List<Any>>?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(SheetsScopes.SPREADSHEETS_READONLY)
            ).setSelectedAccountName(accountName)
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val service = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("My Spreadsheet App")
                .build()

            val response: ValueRange = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()

            onDataLoaded(response.getValues())
        } catch (e: UserRecoverableAuthIOException) {
            (context as Activity).startActivityForResult(
                e.intent,
                (context as MainActivity).REQUEST_AUTHORIZATION
            )
        } catch (e: GoogleJsonResponseException) {
            e.printStackTrace()
            onDataLoaded(null)
        }
    }
}

fun startAccountPicker(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = AccountPicker.newChooseAccountIntent(
        null, null, arrayOf("com.google"),
        false, null, null, null, null
    )
    launcher.launch(intent)
}