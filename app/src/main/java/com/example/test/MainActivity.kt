package com.example.test

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.StringReader
import com.opencsv.CSVReaderBuilder
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class SpreadsheetHelper {

    private val client = HttpClient(Android) {
        install(ContentNegotiation)
    }

    suspend fun fetchData(): List<Map<String, String>> {
        val url = "INSERT_HERE_LINK_TO_YOUR_GOOGLE_SPREADSHEET" // Spreadsheet URL
        Log.d("SpreadsheetHelper", "Sending request to: $url")
        val response = client.get(url).bodyAsText()
        Log.d("SpreadsheetHelper", "Received response: $response")
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

    LaunchedEffect(Unit) {
        while (true) {
            sheetData = spreadsheetHelper.fetchData()
            delay(5000) // Updating the table every 5 seconds
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Google Sheet Reader") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sheetData) { row ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        row.forEach { (key, value) ->
                            Text(text = "$key: $value")
                        }
                    }
                }
            }
        }
    }
}