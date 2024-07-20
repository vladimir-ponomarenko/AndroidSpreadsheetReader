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
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward

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
    var currentLevel by remember { mutableStateOf(1) }

    when (currentScreen) {
        Screen.Task -> TaskScreen(
            activity = activity,
            currentLevel = currentLevel,
            onScreenChange = { newScreen ->
                currentScreen = newScreen
            },
            onLevelChange = { newLevel ->
                currentLevel = newLevel
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
fun TaskScreen(
    activity: MainActivity,
    currentLevel: Int,
    onScreenChange: (Screen) -> Unit,
    onLevelChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var sheetData by remember { mutableStateOf<List<List<Any>>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    val spreadsheetId = "ID" // Spreadsheet ID
    val range = "Sheet$currentLevel!A:L" // Range, adjusted for level
    var currentRow by remember { mutableStateOf(14) }
    var attempts by remember { mutableStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var showTranslations by remember { mutableStateOf(false) }
    var selectedAccountName by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            selectedAccountName = accountName ?: ""
            coroutineScope.launch {
                loadData(context, spreadsheetId, range, selectedAccountName) { data ->
                    sheetData = data
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(selectedAccountName) {
        if (selectedAccountName.isEmpty()) {
            startAccountPicker(context, launcher)
        } else {
            isLoading = true
            loadData(context, spreadsheetId, range, selectedAccountName) { data ->
                sheetData = data
                isLoading = false
                showTranslations = true
            }
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Уровень $currentLevel") },
                navigationIcon = {
                    if (currentLevel > 1) {
                        IconButton(onClick = { onLevelChange(currentLevel - 1) }) {
                            Icon(Icons.Filled.ArrowBack, "Предыдущий уровень")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onLevelChange(currentLevel + 1) }) {
                        Icon(Icons.Filled.ArrowForward, "Следующий уровень")
                    }
                }
            )
        },
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
                    Text("Выберите аккаунт Google")
                } else {
                    if (currentRow <= sheetData!!.size) {
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
                        val isAnswerCorrect = currentExercise[currentExercise.size - 2].toString().toBoolean()
                        val userSpentTime =
                            currentExercise[currentExercise.size - 1].toString().toLongOrNull() ?: 0L

                        if (!showTranslations) {
                            Text(
                                englishWord,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    showTranslations = true
                                    timerRunning = true
                                }
                            )
                        } else {
                            if (isAnswerCorrect) {
                                Text("Верно!", color = Color.Green, fontSize = 24.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    if (currentRow < sheetData!!.size) {
                                        currentRow++
                                        attempts = 0
                                        showHint = false
                                        elapsedTime = 0L
                                        timerRunning = false
                                        showTranslations = false
                                    } else {
//                                        // Level completed, go to next level or show a message
//                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                            Text(
//                                                "Уровень $currentLevel пройден!",
//                                                color = Color.Green,
//                                                fontSize = 24.sp
//                                            )
//                                        }
                                    }
                                }) {
                                    Text("Следующее задание")
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                    Text("${elapsedTime / 1000}", fontSize = 24.sp)
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
                                                                range,
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
                                                                range,
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
                    } else {
                        // Level completed, show a message or allow going to the next level
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Уровень $currentLevel пройден!", color = Color.Green, fontSize = 24.sp)
                        }
                    }
                }
            } else {

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
                    elapsedTime / 1000 // convert to seconds
                )
            )
            val body = ValueRange().setValues(values)
            val range = "Sheet1!J$row:L$row"
            val result: UpdateValuesResponse = service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute()
        } catch (e: Exception) {
            Log.e("Spreadsheet update", "Error updating spreadsheet: ${e.message}")
        }
    }
}