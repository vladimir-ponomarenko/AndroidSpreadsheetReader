package com.example.test

import android.accounts.AccountManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.common.AccountPicker
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.UpdateValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.api.services.sheets.v4.model.Spreadsheet
import androidx.compose.foundation.background
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File


class MainActivity : ComponentActivity() {
    internal val REQUEST_AUTHORIZATION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppContent(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTHORIZATION && resultCode == Activity.RESULT_OK) {
            setContent {
                AppContent(this)
            }
        }
    }
}

@Composable
fun AppContent(activity: MainActivity) {
    var currentScreen by remember { mutableStateOf(Screen.Task) }

    when (currentScreen) {
        Screen.Task -> TaskScreen(
            activity = activity,
            currentScreen = currentScreen,
            onScreenChange = { newScreen ->
                currentScreen = newScreen
            }
        )
        Screen.News -> NewsScreen()
        Screen.Profile -> ProfileScreen()
    }
}

enum class Screen {
    Task, News, Profile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(activity: MainActivity, currentScreen: Screen, onScreenChange: (Screen) -> Unit) {
    val context = LocalContext.current
    var sheetData by remember { mutableStateOf<List<List<Any>>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val originalSpreadsheetId = "1sK3y5p8MPDds_obhS0LCtar2SMFFc9PC8G-3SE8zoHc"
    var spreadsheetId by remember { mutableStateOf("") }
    var currentRow by remember { mutableStateOf(1) }
    var selectedWordIndex by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var showTranslations by remember { mutableStateOf(false) }
    var selectedAccountName by remember { mutableStateOf("") }
    var userSpreadsheet by remember { mutableStateOf<Spreadsheet?>(null) }


    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            selectedAccountName = accountName ?: ""
            coroutineScope.launch {
                copySpreadsheet(context, originalSpreadsheetId, selectedAccountName) { spreadsheet ->
                    userSpreadsheet = spreadsheet
                    spreadsheetId = spreadsheet.spreadsheetId ?: ""
                }
                loadData(context, spreadsheetId, "Sheet1!A:L", selectedAccountName) { data ->
                    sheetData = data
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(selectedAccountName) {
        if (selectedAccountName.isNotEmpty() && spreadsheetId.isEmpty()) {
            isLoading = true
        }
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timerRunning) {
                delay(1L)
                elapsedTime++
            }
        }
    }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = { onScreenChange(Screen.News) }) {
                    Text("Новости")
                }
                Button(onClick = { onScreenChange(Screen.Task) }) {
                    Text("Задание")
                }
                Button(onClick = { onScreenChange(Screen.Profile) }) {
                    Text("Профиль")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.wrapContentSize())
            } else if (sheetData != null) {
                if (selectedAccountName.isEmpty()) {
                    Button(onClick = { startAccountPicker(context, launcher) }) {
                        Text("Выбрать аккаунт Google")
                    }
                } else {
                    val currentExercise = sheetData!![currentRow - 1]
                    val englishWord = currentExercise[0].toString()
                    val russianTranslations = currentExercise.subList(1, currentExercise.size - 6)
                        .filterIsInstance<String>()
                    val correctTranslationIndex =
                        currentExercise[currentExercise.size - 6].toString().toIntOrNull() ?: -1
                    val hint = currentExercise[currentExercise.size - 5].toString()
                    val correctAnswer = currentExercise[currentExercise.size - 4].toString()
                    val userAnswerIndex =
                        currentExercise[currentExercise.size - 3].toString().toIntOrNull() ?: -1
                    val isAnswerCorrect =
                        currentExercise[currentExercise.size - 2].toString().toBoolean()
                    val userSpentTime = currentExercise[currentExercise.size - 1].toString().toLongOrNull() ?: 0L


                    if (!showTranslations) {
                        Text(englishWord, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            showTranslations = true
                            timerRunning = true
                        })
                    } else {

                        if (isAnswerCorrect) {
                            Text("Верно!", color = Color.Green, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                currentRow++
                                selectedWordIndex = 0
                                attempts = 0
                                showHint = false
                                elapsedTime = 0L
                                timerRunning = false
                                showTranslations = false
                            }) {
                                Text("Следующее задание")
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${elapsedTime}", fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(32.dp))
                                if (showHint) {
                                    Text("Подсказка: $hint", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(modifier = Modifier
                                        .clickable {
                                            showHint = false
                                        }
                                        .padding(16.dp)
                                        .background(Color.LightGray)) {
                                        Text("Скрыть подсказку")
                                    }
                                } else {
                                    russianTranslations.forEachIndexed { index, translation ->
                                        Text(translation, modifier = Modifier
                                            .padding(8.dp)
                                            .clickable {
                                                coroutineScope.launch {
                                                    if (index == correctTranslationIndex) {
                                                        // Correct answer
                                                        updateSpreadsheet(
                                                            context,
                                                            spreadsheetId,
                                                            currentRow,
                                                            index + 1,
                                                            true,
                                                            elapsedTime,
                                                            selectedAccountName
                                                        )
                                                        loadData(
                                                            context,
                                                            spreadsheetId,
                                                            "Sheet1!A:L",
                                                            selectedAccountName
                                                        ) { data ->
                                                            sheetData = data
                                                        }
                                                    } else {
                                                        // Incorrect answer
                                                        attempts++
                                                        if (attempts >= 2) {
                                                            showHint = true
                                                        }
                                                        updateSpreadsheet(
                                                            context,
                                                            spreadsheetId,
                                                            currentRow,
                                                            index + 1,
                                                            false,
                                                            elapsedTime,
                                                            selectedAccountName
                                                        )
                                                        loadData(
                                                            context,
                                                            spreadsheetId,
                                                            "Sheet1!A:L",
                                                            selectedAccountName
                                                        ) { data ->
                                                            sheetData = data
                                                        }
                                                    }

                                                }
                                            })
                                    }
                                }
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


@Composable
fun NewsScreen() {
    Text("Новости")
}

@Composable
fun ProfileScreen() {
    Text("Профиль")
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
            Log.d("Data Loading", "Loading data from spreadsheet: $spreadsheetId, range: $range")
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(SheetsScopes.SPREADSHEETS_READONLY)
            ).setSelectedAccountName(accountName)
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val service = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("My Spreadsheet App")
                .build()

            Log.d("Data Loading", "Executing Sheets API request")
            val response: ValueRange = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()
            Log.d("Data Loading", "Data retrieved successfully")

            onDataLoaded(response.getValues())
        } catch (e: UserRecoverableAuthIOException) {
            Log.e("Data Loading", "UserRecoverableAuthIOException: ${e.message}", e)
            (context as Activity).startActivityForResult(
                e.intent,
                (context as MainActivity).REQUEST_AUTHORIZATION
            )
        } catch (e: GoogleJsonResponseException) {
            Log.e("Data Loading", "GoogleJsonResponseException: ${e.message}", e)
            e.printStackTrace()
            onDataLoaded(null)
        } catch (e: Exception) {
            Log.e("Data Loading", "General Exception: ${e.message}", e)
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

private suspend fun copySpreadsheet(
    context: android.content.Context,
    spreadsheetId: String,
    accountName: String,
    onSpreadsheetCopied: (Spreadsheet) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            Log.d("Spreadsheet copy", "Starting copy process")
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(SheetsScopes.DRIVE, SheetsScopes.SPREADSHEETS)
            ).setSelectedAccountName(accountName)
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()

            Log.d("Spreadsheet copy", "Creating Sheets service")
            val sheetsService = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("My Spreadsheet App")
                .build()

            Log.d("Spreadsheet copy", "Creating Drive service")
            val driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("My Spreadsheet App")
                .build()

            Log.d("Spreadsheet copy", "Getting original spreadsheet: $spreadsheetId")
            val copiedSpreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute()
            val newTitle = "Copy - ${copiedSpreadsheet.properties.title}"
            Log.d("Spreadsheet copy", "New spreadsheet title: $newTitle")

            Log.d("Spreadsheet copy", "Creating copy request")
            val copyRequest = File().setName(newTitle)

            Log.d("Spreadsheet copy", "Copying file with Drive API")
            val file = driveService.files().copy(spreadsheetId, copyRequest).execute()
            val spreadsheetCopyId = file.id
            Log.d("Spreadsheet copy", "Copied file ID: $spreadsheetCopyId")

            if (spreadsheetCopyId != null) {
                Log.d("Spreadsheet copy", "Getting new spreadsheet with Sheets API")
                val newSpreadsheet = sheetsService.spreadsheets().get(spreadsheetCopyId).execute()
                Log.d("Spreadsheet copy", "New spreadsheet retrieved")
                onSpreadsheetCopied(newSpreadsheet)
            } else {
                Log.e("Spreadsheet copy", "Spreadsheet copy ID is null")
            }
        } catch (e: Exception) {
            Log.e("Spreadsheet copy", "Error copying spreadsheet: ${e.message}", e)
        }
    }
}

private suspend fun updateSpreadsheet(
    context: android.content.Context,
    spreadsheetId: String,
    row: Int,
    selectedAnswerIndex: Int,
    isAnswerCorrect: Boolean,
    elapsedTime: Long,
    accountName: String
) {
    withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(SheetsScopes.SPREADSHEETS)
            ).setSelectedAccountName(accountName)
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val service = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("My Spreadsheet App")
                .build()

            val values = listOf(
                listOf(
                    selectedAnswerIndex,
                    if (isAnswerCorrect) "TRUE" else "FALSE",
                    elapsedTime
                )
            )
            val body = ValueRange().setValues(values)
            val range = "Sheet1!K$row:M$row"
            val result: UpdateValuesResponse = service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute()
        } catch (e: Exception) {
            Log.e("Spreadsheet update", "Error updating spreadsheet: ${e.message}")
        }
    }
}