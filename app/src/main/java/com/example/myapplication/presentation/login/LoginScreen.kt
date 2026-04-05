package com.example.myapplication.presentation.login

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.model.LoginRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
    onRequestFloatPermission: () -> Unit = {}
) {
    val phone by viewModel.phone.collectAsState()
    val code by viewModel.code.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    val isRegisterMode by viewModel.isRegisterMode.collectAsState()
    val isForgotPasswordMode by viewModel.isForgotPasswordMode.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val showLoginDialog by viewModel.showLoginDialog.collectAsState()
    val showCodeSuccessDialog by viewModel.showCodeSuccessDialog.collectAsState()
    val loginType by viewModel.loginType.collectAsState()
    val agreeTerms by viewModel.agreeTerms.collectAsState()

    Log.d("LoginScreen", "Current state: loginSuccess=$loginSuccess, currentStep=$currentStep")

    // 登录成功弹窗
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLoginDialog,
            title = { Text("登录成功") },
            text = { Text("欢迎回来！") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissLoginDialog()
                    onRequestFloatPermission()
                    onLoginSuccess()
                }) {
                    Text("确定")
                }
            }
        )
    }

    // 验证码成功弹窗
    if (showCodeSuccessDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCodeSuccessDialog,
            title = { Text("验证码已发送") },
            text = { Text("验证码已发送至您的手机，请注意查收。") },
            confirmButton = {
                Button(onClick = viewModel::dismissCodeSuccessDialog) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = when {
                            isForgotPasswordMode -> "重置密码"
                            isRegisterMode -> "注册"
                            else -> "登录"
                        }
                    )
                },
                navigationIcon = {
            if (isRegisterMode || currentStep !is LoginViewModel.LoginStep.PhoneInput) {
                        IconButton(onClick = viewModel::goToPreviousStep) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "安心出行",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(0.3f, fill = false))

            // 根据当前步骤显示不同内容
            when (currentStep) {
                is LoginViewModel.LoginStep.PhoneInput -> {
                    // ========== 注册模式 UI ==========
                    if (isRegisterMode) {
                        // 1. 手机号输入框 - ⭐ 使用自适应高度
                        OutlinedTextField(
                            value = phone,
                            onValueChange = viewModel::updatePhone,
                            label = { Text("手机号") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),  // ⭐ 使用 heightIn 确保最小高度
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            placeholder = { Text("请输入手机号") },
                            leadingIcon = {
                                Text(
                                    text = "+86",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 验证码输入框（带获取按钮）- ⭐ 自适应高度
                        OutlinedTextField(
                            value = code,
                            onValueChange = viewModel::updateCode,
                            label = { Text("验证码") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),  // ⭐ 使用 heightIn
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            placeholder = { Text("请输入验证码") },
                            trailingIcon = {
                                TextButton(
                                    onClick = viewModel::sendCode,
                                    enabled = !isCountingDown && !isLoading && phone.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)  // ⭐ 减小内边距
                                ) {
                                    Text(
                                        text = if (isCountingDown) "${countdownSeconds}s" else "获取验证码",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1  // ⭐ 防止换行
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. 设置密码输入框 - ⭐ 自适应高度
                        OutlinedTextField(
                            value = password,
                            onValueChange = viewModel::updatePassword,
                            label = { Text("设置密码") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),  // ⭐ 使用 heightIn
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            placeholder = { Text("请设置密码") }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. 确认密码输入框 - ⭐ 自适应高度
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = viewModel::updateConfirmPassword,
                            label = { Text("确认密码") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),  // ⭐ 使用 heightIn
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            placeholder = { Text("请再次输入密码") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "密码要求：至少 10 位，必须包含字母和特殊符号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 错误提示
                        errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 注册按钮
                        Button(
                            onClick = viewModel::goToNextStep,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !isLoading &&
                                    phone.isNotBlank() &&
                                    code.isNotBlank() &&
                                    password.isNotBlank() &&
                                    confirmPassword.isNotBlank() &&
                                    password == confirmPassword &&
                                    agreeTerms,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "注册",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 已有账号？去登录 - ⭐ 减小间距
                        TextButton(
                            onClick = { viewModel.toggleMode() },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)  // ⭐ 减小按钮内边距
                        ) {
                            Text(
                                text = "已有账号？去登录",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                    } else {
                        // ========== 登录模式 UI ==========
                        OutlinedTextField(
                            value = phone,
                            onValueChange = viewModel::updatePhone,
                            label = { Text("手机号") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            placeholder = { Text("请输入手机号") },
                            leadingIcon = {
                                Text(
                                    text = "+86",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ⭐ 修改：忘记密码模式下，始终显示验证码输入框
                        if (loginType == LoginRequest.TYPE_CODE || isForgotPasswordMode) {
                            // 验证码登录或忘记密码模式
                            OutlinedTextField(
                                value = code,
                                onValueChange = viewModel::updateCode,
                                label = { Text("验证码") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = { Text("请输入验证码") },
                                trailingIcon = {
                                    TextButton(
                                        onClick = viewModel::sendCode,
                                        enabled = !isCountingDown && !isLoading && phone.isNotBlank()
                                    ) {
                                        Text(
                                            text = if (isCountingDown) "${countdownSeconds}s" else "获取验证码",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            )
                        } else {
                            // 密码登录模式
                            OutlinedTextField(
                                value = password,
                                onValueChange = viewModel::updatePassword,
                                label = { Text("密码") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                placeholder = { Text("请输入密码") }
                            )
                            
                            // ⭐ 新增：忘记密码按钮（右对齐）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = viewModel::toggleForgotPassword) {
                                    Text(
                                        text = "忘记密码？",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // ⭐ 新增：错误提示
                        errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // ⭐ 修改：忘记密码模式：点击重置密码按钮，跳转到第二步
                        Button(
                            onClick = viewModel::goToNextStep,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = when {
                                // ⭐ 修改：忘记密码模式第一步只需要手机号
                                isForgotPasswordMode && currentStep is LoginViewModel.LoginStep.PhoneInput -> !isLoading && phone.isNotBlank() && agreeTerms
                                loginType == LoginRequest.TYPE_CODE -> !isLoading && phone.isNotBlank() && code.isNotBlank() && agreeTerms
                                else -> !isLoading && phone.isNotBlank() && password.isNotBlank() && agreeTerms
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            // ⭐ 修改:根据模式和步骤显示不同文字
                            Text(
                                text = when {
                                    isForgotPasswordMode -> "重置密码"
                                    currentStep is LoginViewModel.LoginStep.VerifyCode -> "下一步"
                                    else -> "登录"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 切换登录方式 - ⭐ 减小间距
                        TextButton(
                            onClick = { viewModel.toggleLoginType() },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (loginType == LoginRequest.TYPE_CODE) "密码登录" else "验证码登录",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 切换注册模式 - ⭐ 减小间距
                        TextButton(
                            onClick = { viewModel.toggleMode() },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(
                                text = "没有账号？去注册",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                is LoginViewModel.LoginStep.VerifyCode -> {
                    // 第二步：展示手机号（可编辑）+ 输入验证码
                    PhoneNumberDisplay(
                        phone = phone,
                        onEditClick = viewModel::goToPreviousStep
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ⭐ 修改：忘记密码模式下，手机号和验证码在同一页面，按钮文字为"重置密码"
                    // 验证码输入框
                    OutlinedTextField(
                        value = code,
                        onValueChange = viewModel::updateCode,
                        label = { Text("验证码") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("请输入验证码") },
                        trailingIcon = {
                            TextButton(
                                onClick = viewModel::sendCode,
                                enabled = !isCountingDown && !isLoading && phone.isNotBlank()
                            ) {
                                Text(
                                    if (isCountingDown) "${countdownSeconds}s" else "获取验证码"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 重置密码按钮
                    Button(
                        onClick = viewModel::goToNextStep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = when {
                            // ⭐ 修改：忘记密码模式和验证码登录都需要验证码
                            isForgotPasswordMode || loginType == LoginRequest.TYPE_CODE -> 
                                !isLoading && code.isNotBlank() && phone.isNotBlank() && agreeTerms
                            else -> !isLoading && password.isNotBlank() && phone.isNotBlank() && agreeTerms
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = if (isForgotPasswordMode) "重置密码" else "登录",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                is LoginViewModel.LoginStep.SetNewPassword -> {
                    // 第三步：设置新密码（仅忘记密码模式）
                    PhoneNumberDisplay(
                        phone = phone,
                        onEditClick = { viewModel.goToPreviousStep() }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 验证码显示（只读）
                    OutlinedTextField(
                        value = code,
                        onValueChange = {},
                        label = { Text("验证码") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 新密码输入框
                    OutlinedTextField(
                        value = password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("新密码*") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        placeholder = { Text("请输入新密码") },
                        isError = password.isNotBlank() && !viewModel.isValidPassword(password)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ⭐ 新增：实时密码验证反馈
                    if (password.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // 长度检查
                            val hasMinLength = password.length >= 10
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasMinLength) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (hasMinLength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "至少 10 位",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasMinLength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // 字母检查
                            val hasLetter = password.any { it.isLetter() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasLetter) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (hasLetter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "包含字母",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasLetter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // 特殊符号检查
                            val hasSymbol = password.any { !it.isLetterOrDigit() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasSymbol) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (hasSymbol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "包含特殊符号",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasSymbol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "密码要求：至少 10 位，必须包含字母和特殊符号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 确认新密码输入框
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = viewModel::updateConfirmPassword,
                        label = { Text("确认新密码*") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        placeholder = { Text("请再次输入新密码") },
                        isError = password.isNotBlank() && confirmPassword.isNotBlank() && password != confirmPassword
                    )

                    if (password.isNotBlank() && confirmPassword.isNotBlank() && password != confirmPassword) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "两次输入的密码不一致",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))  // ⭐ 修改：增加间距到 40dp

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 确认重置按钮
                    Button(
                        onClick = viewModel::goToNextStep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isLoading && 
                                  password.isNotBlank() && 
                                  confirmPassword.isNotBlank() && 
                                  password == confirmPassword && 
                                  viewModel.isValidPassword(password) &&
                                  agreeTerms,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "确认重置",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 返回按钮
                    OutlinedButton(
                        onClick = viewModel::goToPreviousStep,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("返回")
                    }
                }

                is LoginViewModel.LoginStep.RegisterInfo -> {
                    // 第三步：注册信息（仅注册模式）
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = viewModel::updateNickname,
                        label = { Text("昵称（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入昵称") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text("设置密码") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        placeholder = { Text("请设置密码") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 确认密码输入框
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = viewModel::updateConfirmPassword,
                        label = { Text("确认密码") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        placeholder = { Text("请再次输入密码") }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "密码要求：至少 10 位，必须包含字母和特殊符号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = viewModel::goToNextStep,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && password.isNotBlank() && confirmPassword.isNotBlank() && password == confirmPassword && agreeTerms
                    ) {
                        Text("注册")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 返回按钮
                    OutlinedButton(
                        onClick = viewModel::goToPreviousStep,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("返回")
                    }
                }

            }

            // 底部条款 + 帮助图标（优化后）- ⭐ 移到底部，固定位置
            Spacer(modifier = Modifier.weight(1f))
            
            AgreementRow(
                agreeTerms = agreeTerms,
                onToggleAgree = viewModel::toggleAgreeTerms,
                onHelpClick = { /* 帮助页面跳转 */ }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 手机号展示组件（仅展示 + 编辑按钮）
 */
@Composable
private fun PhoneNumberDisplay(
    phone: String,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "手机号",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (phone.isNotBlank()) "+86 $phone" else "未设置手机号",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "修改手机号",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    Divider(modifier = Modifier.fillMaxWidth())
}

/**
 * 优化后的条款组件：自定义复选框 + 协议文本 + 帮助图标
 */
@Composable
private fun AgreementRow(
    agreeTerms: Boolean,
    onToggleAgree: () -> Unit,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 自定义复选框（使用 Icon 模拟，视觉更现代）
            Icon(
                imageVector = if (agreeTerms) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (agreeTerms) "已同意" else "未同意",
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onToggleAgree() },
                tint = if (agreeTerms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "登录即表示您同意",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            TextButton(
                onClick = { /* 打开用户协议 */ },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "《用户协议》",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            Text(
                text = "和",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            TextButton(
                onClick = { /* 打开隐私政策 */ },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "《隐私政策》",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = "帮助",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
