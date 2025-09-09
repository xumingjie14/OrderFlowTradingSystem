package com.trading.orderflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trading.orderflow.data.model.BrandInfo
import com.trading.orderflow.ui.viewmodel.ActivationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivationSuccess: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    var serialNumber by remember { mutableStateOf("") }
    var showSecurityQuestions by remember { mutableStateOf(false) }
    var securityAnswers by remember { mutableStateOf(mapOf<String, String>()) }
    
    // 监听激活成功
    LaunchedEffect(uiState.isActivated) {
        if (uiState.isActivated) {
            onActivationSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 品牌信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "激活",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "软件激活",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = BrandInfo.APP_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "首次使用需要输入序列号进行激活",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            if (!showSecurityQuestions) {
                // 序列号输入界面
                SerialNumberInputCard(
                    serialNumber = serialNumber,
                    onSerialNumberChange = { serialNumber = it },
                    onVerify = { viewModel.verifySerialNumber(serialNumber) },
                    onForgotSerial = { showSecurityQuestions = true },
                    uiState = uiState
                )
            } else {
                // 安全问题界面
                SecurityQuestionsCard(
                    questions = uiState.securityQuestions,
                    answers = securityAnswers,
                    onAnswerChange = { questionId, answer ->
                        securityAnswers = securityAnswers.toMutableMap().apply {
                            put(questionId, answer)
                        }
                    },
                    onVerify = { viewModel.verifySecurityQuestions(securityAnswers) },
                    onBack = { showSecurityQuestions = false },
                    uiState = uiState
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 版权信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = BrandInfo.COPYRIGHT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "技术支持: ${BrandInfo.PHONE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SerialNumberInputCard(
    serialNumber: String,
    onSerialNumberChange: (String) -> Unit,
    onVerify: () -> Unit,
    onForgotSerial: () -> Unit,
    uiState: ActivationUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "请输入序列号",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            OutlinedTextField(
                value = serialNumber,
                onValueChange = onSerialNumberChange,
                label = { Text("序列号") },
                leadingIcon = {
                    Icon(Icons.Filled.Key, contentDescription = "序列号")
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (serialNumber.isNotBlank()) {
                            onVerify()
                        }
                    }
                ),
                singleLine = true,
                isError = uiState.error != null
            )
            
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (uiState.remainingAttempts != null && uiState.remainingAttempts > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "剩余尝试次数: ${uiState.remainingAttempts}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onVerify,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !uiState.isLoading && serialNumber.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "激活软件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onForgotSerial,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("忘记序列号？通过安全问题重置")
            }
        }
    }
}

@Composable
fun SecurityQuestionsCard(
    questions: List<SecurityQuestion>,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit,
    uiState: ActivationUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "安全验证",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "请回答以下安全问题以重置激活尝试次数：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            questions.forEachIndexed { index, question ->
                Column {
                    Text(
                        text = "${index + 1}. ${question.question}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = answers[question.id] ?: "",
                        onValueChange = { onAnswerChange(question.id, it) },
                        label = { Text("答案") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = if (index == questions.size - 1) ImeAction.Done else ImeAction.Next
                        ),
                        singleLine = true
                    )
                    
                    if (index < questions.size - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onVerify,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !uiState.isLoading && questions.all { answers[it.id]?.isNotBlank() == true },
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "验证答案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}