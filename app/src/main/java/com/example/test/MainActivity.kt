package com.example.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.StringReader
import com.opencsv.CSVReaderBuilder
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

class SpreadsheetHelper {

    private val client = HttpClient(Android) {
        install(ContentNegotiation)
    }

    suspend fun fetchData(): List<Map<String, String>> {
        val url = "INSERT_HERE_LINK_TO_YOUR_GOOGLE_SPREADSHEET" // Spreadsheet URL
        val response = client.get(url).bodyAsText()
        return parseCsv(response)
    }

    private fun parseCsv(text: String): List<Map<String, String>> {
        val reader = CSVReaderBuilder(StringReader(text)).build()
        val lines = reader.readAll()
        val headers = lines.removeAt(0)

        return lines.map { line ->
            headers.mapIndexed { index, header ->
                header to (line.getOrNull(index) ?: "")
            }.toMap()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoogleSheetReaderApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSheetReaderApp() {
    val spreadsheetHelper = SpreadsheetHelper()
    var sheetData by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        sheetData = spreadsheetHelper.fetchData()
        isLoading = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Google Sheet Reader") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                coroutineScope.launch {
                    isLoading = true
                    sheetData = spreadsheetHelper.fetchData()
                    isLoading = false
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
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.wrapContentSize())
            } else {
                if (sheetData.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        sheetData[0].forEach { (header, _) ->
                            Text(
                                text = header,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Divider(color = Color.LightGray)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(sheetData.drop(1)) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            row.forEach { (_, value) ->
                                Text(
                                    text = value,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}