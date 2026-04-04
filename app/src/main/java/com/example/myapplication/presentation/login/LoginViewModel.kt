package com.example.myapplication.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    // ⭐ 新增：登录步骤状态（B 站风格）
    sealed class LoginStep {
        object PhoneInput : LoginStep()  // 第一步：输入手机号
        object VerifyCode : LoginStep()  // 第二步：验证验证码
        object SetNewPassword : LoginStep() // 第三步：设置新密码（仅忘记密码模式）
        object RegisterInfo : LoginStep() // 第四步：注册信息（仅注册模式）
    }
    
    private val _currentStep = MutableStateFlow<LoginStep>(LoginStep.PhoneInput)
    val currentStep: StateFlow<LoginStep> = _currentStep.asStateFlow()

    // 输入字段
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    // ⭐ 新增：确认密码字段
    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    // 登录类型：验证码 / 密码
    private val _loginType = MutableStateFlow(LoginRequest.TYPE_CODE)
    val loginType: StateFlow<String> = _loginType.asStateFlow()

    // 是否是注册模式
    private val _isRegisterMode = MutableStateFlow(false)
    val isRegisterMode: StateFlow<Boolean> = _isRegisterMode.asStateFlow()
    
    // 是否是忘记密码模式
    private val _isForgotPasswordMode = MutableStateFlow(false)
    val isForgotPasswordMode: StateFlow<Boolean> = _isForgotPasswordMode.asStateFlow()

    // 状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    // 弹窗控制
    private val _showLoginDialog = MutableStateFlow(false)
    val showLoginDialog: StateFlow<Boolean> = _showLoginDialog.asStateFlow()

    private val _showCodeSuccessDialog = MutableStateFlow(false)
    val showCodeSuccessDialog: StateFlow<Boolean> = _showCodeSuccessDialog.asStateFlow()
    
    // ⭐ 新增：验证码倒计时
    private val _countdownSeconds = MutableStateFlow(60)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()
    
    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown: StateFlow<Boolean> = _isCountingDown.asStateFlow()

    // ⭐ 新增：用户协议勾选状态
    private val _agreeTerms = MutableStateFlow(false)
    val agreeTerms: StateFlow<Boolean> = _agreeTerms.asStateFlow()

    fun updatePhone(phone: String) { _phone.value = phone }
    fun updateCode(code: String) {
        _code.value = code
        
        // ⭐ 忘记密码模式：验证码输入完成（6位）后自动跳转到设置新密码页面
        if (_isForgotPasswordMode.value && code.length == 6) {
            Log.d("LoginViewModel", "验证码输入完成（6位），自动跳转到 SetNewPassword")
            _currentStep.value = LoginStep.SetNewPassword
        }
    }
    fun updatePassword(password: String) { _password.value = password }
    fun updateConfirmPassword(confirmPassword: String) { _confirmPassword.value = confirmPassword }
    fun updateNickname(nickname: String) { _nickname.value = nickname }

    // ⭐ 修改：步骤跳转（B 站风格）
    fun goToNextStep() {
        _errorMessage.value = null
        Log.d("LoginViewModel", "=== goToNextStep 被调用 ===")
        Log.d("LoginViewModel", "当前步骤：${_currentStep.value}")
        Log.d("LoginViewModel", "是否注册模式：$_isRegisterMode")
        Log.d("LoginViewModel", "登录类型：${_loginType.value}")
        Log.d("LoginViewModel", "手机号：${_phone.value}")
        
        when (_currentStep.value) {
            is LoginStep.PhoneInput -> {
                Log.d("LoginViewModel", "进入 PhoneInput 分支")
                if (!isValidPhone(_phone.value)) {
                    _errorMessage.value = "请输入正确的手机号"
                    Log.e("LoginViewModel", "手机号格式错误")
                    return
                }
                
                // ⭐ 修改：忘记密码模式的处理逻辑
                if (_isForgotPasswordMode.value) {
                    // 第一步：只需要验证手机号，发送验证码后跳转到验证码页面
                    sendCode()
                    Log.d("LoginViewModel", "忘记密码模式，发送验证码，跳转到 VerifyCode")
                    _currentStep.value = LoginStep.VerifyCode
                } else if (_loginType.value == LoginRequest.TYPE_CODE) {
                    // 验证码模式：需要验证码才能登录/注册
                    if (_code.value.isBlank()) {
                        _errorMessage.value = "请输入验证码"
                        Log.e("LoginViewModel", "验证码为空")
                        return
                    }
                    
                    // 发送验证码并启动倒计时
                    sendCode()
                    
                    // 如果是注册模式，跳转到下一步填写密码
                    if (_isRegisterMode.value) {
                        Log.d("LoginViewModel", "注册模式，切换到 RegisterInfo")
                        _currentStep.value = LoginStep.RegisterInfo
                    } else {
                        // 登录模式，直接验证验证码
                        Log.d("LoginViewModel", "验证码登录模式，调用 loginWithCode()")
                        loginWithCode()
                    }
                } else {
                    // 密码模式：直接登录/注册
                    if (_password.value.isBlank()) {
                        _errorMessage.value = "请输入密码"
                        Log.e("LoginViewModel", "密码为空")
                        return
                    }
                    
                    if (_isRegisterMode.value) {
                        Log.d("LoginViewModel", "注册模式，切换到 RegisterInfo")
                        _currentStep.value = LoginStep.RegisterInfo
                    } else {
                        Log.d("LoginViewModel", "密码登录模式，调用 loginWithPassword()")
                        loginWithPassword()
                    }
                }
            }
            is LoginStep.VerifyCode -> {
                Log.d("LoginViewModel", "进入 VerifyCode 分支")
                Log.d("LoginViewModel", "isForgotPasswordMode = $_isForgotPasswordMode.value")
                Log.d("LoginViewModel", "code = ${_code.value}")
                
                // ⭐ 忘记密码模式：验证验证码后跳转到设置新密码页面
                if (_isForgotPasswordMode.value) {
                    // ⭐ 自动跳转逻辑已经在 updateCode 中实现，这里不再重复处理
                    // 如果用户手动点击按钮，也跳转到下一步
                    Log.d("LoginViewModel", "忘记密码模式，跳转到 SetNewPassword")
                    _currentStep.value = LoginStep.SetNewPassword
                } else {
                    // 普通登录/注册流程
                    if (_loginType.value == LoginRequest.TYPE_CODE) {
                        // 验证码登录
                        if (_code.value.isBlank()) {
                            _errorMessage.value = "请输入验证码"
                            Log.e("LoginViewModel", "验证码为空")
                            return
                        }
                        if (_isRegisterMode.value) {
                            Log.d("LoginViewModel", "注册模式，切换到 RegisterInfo")
                            _currentStep.value = LoginStep.RegisterInfo
                        } else {
                            Log.d("LoginViewModel", "验证码登录模式，调用 loginWithCode()")
                            loginWithCode()
                        }
                    } else {
                        // 密码登录/注册
                        if (_isRegisterMode.value) {
                            Log.d("LoginViewModel", "注册模式，切换到 RegisterInfo")
                            _currentStep.value = LoginStep.RegisterInfo
                        } else {
                            Log.d("LoginViewModel", "密码登录模式，调用 loginWithPassword()")
                            loginWithPassword()
                        }
                    }
                }
            }
            is LoginStep.SetNewPassword -> {
                Log.d("LoginViewModel", "进入 SetNewPassword 分支")
                // 验证密码
                if (_password.value.isBlank()) {
                    _errorMessage.value = "请输入新密码"
                    Log.e("LoginViewModel", "新密码为空")
                    return
                }
                if (_password.value != _confirmPassword.value) {
                    _errorMessage.value = "两次输入的密码不一致"
                    Log.e("LoginViewModel", "密码不一致")
                    return
                }
                if (!isValidPassword(_password.value)) {
                    _errorMessage.value = "密码必须是 10 位，且包含字母和特殊符号"
                    Log.e("LoginViewModel", "密码格式错误")
                    return
                }
                Log.d("LoginViewModel", "密码验证通过，调用 forgotPassword()")
                forgotPassword()
            }
            is LoginStep.RegisterInfo -> {
                Log.d("LoginViewModel", "进入 RegisterInfo 分支，调用 register()")
                register()
            }
        }
    }
    
    fun goToPreviousStep() {
        _errorMessage.value = null
        when (_currentStep.value) {
            is LoginStep.VerifyCode -> {
                // ⭐ 如果是注册模式且在验证码步骤，返回到 PhoneInput 但保持注册模式
                if (_isRegisterMode.value) {
                    _currentStep.value = LoginStep.PhoneInput
                    _code.value = ""
                    Log.d("LoginViewModel", "注册模式下从 VerifyCode 返回到 PhoneInput")
                } else {
                    _currentStep.value = LoginStep.PhoneInput
                    _code.value = ""
                }
            }
            is LoginStep.SetNewPassword -> {
                _currentStep.value = LoginStep.VerifyCode
                _password.value = ""
                _confirmPassword.value = ""
            }
            is LoginStep.RegisterInfo -> {
                _currentStep.value = LoginStep.VerifyCode
                _password.value = ""
                _confirmPassword.value = ""
                _nickname.value = ""
            }
            is LoginStep.PhoneInput -> {
                // ⭐ 如果在 PhoneInput 且是注册模式，点击返回则切换回登录模式
                if (_isRegisterMode.value) {
                    _isRegisterMode.value = false
                    _phone.value = ""
                    _code.value = ""
                    _password.value = ""
                    _confirmPassword.value = ""
                    _nickname.value = ""
                    Log.d("LoginViewModel", "从注册模式切换回登录模式")
                }
            }
        }
    }

    // ⭐ 新增：倒计时逻辑
    private fun startCountdown() {
        viewModelScope.launch {
            _isCountingDown.value = true
            _countdownSeconds.value = 60
            while (_countdownSeconds.value > 0) {
                delay(1000)
                _countdownSeconds.value--
            }
            _isCountingDown.value = false
        }
    }
    
    fun resendCode() {
        if (_isCountingDown.value) return
        sendCode()
    }

    // 切换登录类型
    fun toggleLoginType() {
        // ⭐ 修改：如果在忘记密码模式下，先退出忘记密码模式
        if (_isForgotPasswordMode.value) {
            _isForgotPasswordMode.value = false
            _currentStep.value = LoginStep.PhoneInput
            _phone.value = ""
            _code.value = ""
            _password.value = ""
            _confirmPassword.value = ""
            _errorMessage.value = null
            Log.d("LoginViewModel", "从忘记密码模式退出，切换到普通登录模式")
            return
        }
        
        _loginType.value = if (_loginType.value == LoginRequest.TYPE_CODE) {
            LoginRequest.TYPE_PASSWORD
        } else {
            LoginRequest.TYPE_CODE
        }
        // ⭐ 修改：切换登录类型时，清除密码和验证码输入
        _password.value = ""
        _code.value = ""
        _errorMessage.value = null
        Log.d("LoginViewModel", "切换登录类型：${if (_loginType.value == LoginRequest.TYPE_CODE) "验证码登录" else "密码登录"}")
    }

    // 切换登录/注册模式
    fun toggleMode() {
        _currentStep.value = LoginStep.PhoneInput
        _phone.value = ""
        _code.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _errorMessage.value = null
        
        if (_isForgotPasswordMode.value) {
            _isForgotPasswordMode.value = false
        } else {
            _isRegisterMode.value = !_isRegisterMode.value
        }
    }
    
    // 切换到忘记密码模式
    fun toggleForgotPassword() {
        _currentStep.value = LoginStep.PhoneInput
        _phone.value = ""
        _code.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _isForgotPasswordMode.value = !_isForgotPasswordMode.value
        _isRegisterMode.value = false
        _loginType.value = LoginRequest.TYPE_CODE  // 忘记密码必须使用验证码
        _errorMessage.value = null
        Log.d("LoginViewModel", "切换到忘记密码模式：${_isForgotPasswordMode.value}")
    }
    
    // ⭐ 新增：取消注册流程（但不断开 WebSocket）
    fun cancelRegistration() {
        // ⭐ 只清除注册相关的数据，但不清除手机号和验证码
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _currentStep.value = LoginStep.VerifyCode  // ⭐ 修复：改为 VerifyCode
        _errorMessage.value = null
        Log.d("LoginViewModel", "已取消注册，可以继续填写验证码")
    }

    // 弹窗管理
    fun dismissLoginDialog() {
        _showLoginDialog.value = false
    }

    fun dismissCodeSuccessDialog() {
        _showCodeSuccessDialog.value = false
    }

    // ⭐ 修改：发送验证码
    fun sendCode() {
        // ⭐ 如果正在倒计时，不重复发送
        if (_isCountingDown.value) {
            Log.d("LoginViewModel", "正在倒计时中，不重复发送")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                Log.d("LoginViewModel", "=== 开始发送验证码 ===")
                Log.d("LoginViewModel", "手机号：${_phone.value}")
                
                val response = api.sendCode(_phone.value)
                
                Log.d("LoginViewModel", "=== 收到响应 ===")
                Log.d("LoginViewModel", "响应 code: ${response.code}")
                Log.d("LoginViewModel", "响应 message: ${response.message}")
                Log.d("LoginViewModel", "响应 success: ${response.isSuccess()}")
                
                if (response.isSuccess()) {
                    _showCodeSuccessDialog.value = true
                    startCountdown()  // ⭐ 发送成功后启动倒计时
                    Log.d("LoginViewModel", "✅ 验证码发送成功")
                } else {
                    _errorMessage.value = response.message ?: "验证码发送失败"
                    Log.e("LoginViewModel", "❌ 验证码发送失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "❌ 验证码发送异常", e)
                e.printStackTrace()
                
                // ⭐ 优化：根据异常类型提供更友好的错误提示
                _errorMessage.value = when {
                    e is java.net.SocketTimeoutException -> 
                        "请求超时，请检查网络连接或稍后重试"
                    e is java.net.UnknownHostException -> 
                        "无法连接到服务器，请检查网络设置"
                    e is java.net.ConnectException -> 
                        "连接失败，请检查后端服务是否启动"
                    e.message?.contains("failed to connect") == true -> 
                        "无法连接到服务器，请检查 IP 地址和端口配置"
                    else -> "网络错误：${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：验证码登录
    private fun loginWithCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val request = LoginRequest(_phone.value, code = _code.value, loginType = LoginRequest.TYPE_CODE)
                
                Log.d("LoginViewModel", "=== 开始验证码登录 ===")
                val response = api.login(request)
                
                if (response.isSuccess() && response.data != null) {
                    Log.d("LoginViewModel", "=== 登录成功 ===")
                    tokenManager.saveToken(response.data.token, response.data.userId)
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                } else {
                    val errorMsg = response.message ?: "登录失败"
                    Log.e("LoginViewModel", "登录失败：$errorMsg")
                    
                    // ⭐ 检测是否是账户不存在的错误，自动跳转到注册
                    if (isUserNotExistError(errorMsg)) {
                        Log.d("LoginViewModel", "检测到账户不存在，自动跳转到注册模式")
                        _errorMessage.value = "该手机号未注册，已为您切换到注册模式"
                        switchToRegisterMode()
                    } else {
                        _errorMessage.value = errorMsg
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "登录异常", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：注册
    private fun register() {
        viewModelScope.launch {
            Log.d("LoginViewModel", "=== register() 协程启动 ===")
            _isLoading.value = true
            _errorMessage.value = null
            
            // 验证两次密码是否一致
            if (_password.value != _confirmPassword.value) {
                _errorMessage.value = "两次输入的密码不一致"
                Log.e("LoginViewModel", "密码不一致")
                _isLoading.value = false
                return@launch
            }
            
            if (!isValidPassword(_password.value)) {
                _errorMessage.value = "密码必须是 10 位，且包含字母和特殊符号"
                Log.e("LoginViewModel", "密码格式错误")
                _isLoading.value = false
                return@launch
            }
            
            try {
                val request = RegisterRequest(
                    phone = _phone.value,
                    code = _code.value,
                    password = _password.value,
                    nickname = _nickname.value.ifBlank { null }
                )
                
                Log.d("LoginViewModel", "=== 开始注册 ===")
                Log.d("LoginViewModel", "请求参数：phone=${_phone.value}, code=${_code.value}, nickname=${_nickname.value}")
                
                val response = api.register(request)
                
                Log.d("LoginViewModel", "=== 收到注册响应 ===")
                Log.d("LoginViewModel", "响应 code: ${response.code}")
                Log.d("LoginViewModel", "响应 message: ${response.message}")
                Log.d("LoginViewModel", "响应 data: ${response.data}")
                
                if (response.isSuccess()) {
                    Log.d("LoginViewModel", "=== 注册成功 ===")
                    if (response.data != null) {
                        // 注册成功并返回 token，直接登录
                        Log.d("LoginViewModel", "保存 Token: ${response.data.token.take(20)}...")
                        tokenManager.saveToken(response.data.token, response.data.userId)
                        _loginSuccess.value = true
                        _showLoginDialog.value = true
                        Log.d("LoginViewModel", "✅ 注册并登录成功")
                    } else {
                        // 注册成功但未返回 token，提示用户登录
                        Log.d("LoginViewModel", "注册成功，但未返回 token")
                        _showLoginDialog.value = true
                        _errorMessage.value = "注册成功，请使用手机号和验证码登录"
                    }
                } else {
                    // ⭐ 注册失败（如验证码错误），保留当前步骤和数据，让用户可以重试
                    _errorMessage.value = response.message ?: "注册失败"
                    Log.e("LoginViewModel", "❌ 注册失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "❌ 注册异常", e)
                _errorMessage.value = e.message ?: "网络错误"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                Log.d("LoginViewModel", "register() 执行完成")
            }
        }
    }
    
    // ⭐ 修改：忘记密码
    fun forgotPassword() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // ⭐ 验证两次密码是否一致
            if (_password.value != _confirmPassword.value) {
                _errorMessage.value = "两次输入的密码不一致"
                Log.e("LoginViewModel", "密码不一致")
                _isLoading.value = false
                return@launch
            }
            
            if (!isValidPassword(_password.value)) {
                _errorMessage.value = "密码必须是 10 位，且包含字母和特殊符号"
                _isLoading.value = false
                return@launch
            }
            
            try {
                val request = ChangePasswordRequest(
                    phone = _phone.value,
                    code = _code.value,  // 使用第二步已验证的验证码
                    newPassword = _password.value
                )
                
                Log.d("LoginViewModel", "=== 开始重置密码 ===")
                val response = api.forgotPassword(request)
                
                if (response.isSuccess()) {
                    Log.d("LoginViewModel", "=== 密码重置成功 ===")
                    _showLoginDialog.value = true
                    _isForgotPasswordMode.value = false
                    _currentStep.value = LoginStep.PhoneInput
                    _phone.value = ""
                    _code.value = ""
                    _password.value = ""
                    _confirmPassword.value = ""
                } else {
                    _errorMessage.value = response.message ?: "重置失败"
                    Log.e("LoginViewModel", "密码重置失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "=== 密码重置异常 ===", e)
                _errorMessage.value = when {
                    e.message?.contains("timeout") == true -> "请求超时，请检查网络连接"
                    e.message?.contains("Unable to resolve host") == true -> "无法连接到服务器"
                    e.message?.contains("code") == true && e.message?.contains("错误") == true -> "验证码错误"
                    else -> "网络错误，请稍后重试"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val request = when (_loginType.value) {
                    LoginRequest.TYPE_CODE -> LoginRequest(_phone.value, code = _code.value, loginType = LoginRequest.TYPE_CODE)
                    LoginRequest.TYPE_PASSWORD -> LoginRequest(_phone.value, password = _password.value, loginType = LoginRequest.TYPE_PASSWORD)
                    else -> throw IllegalStateException("未知的登录类型")
                }
                
                Log.d("LoginViewModel", "=== 开始登录流程 ===")
                Log.d("LoginViewModel", "请求参数：phone=${_phone.value}, loginType=${_loginType.value}")
                
                val response = api.login(request)
                Log.d("LoginViewModel", "=== 收到响应 ===")
                Log.d("LoginViewModel", "响应 code: ${response.code}")
                Log.d("LoginViewModel", "响应 message: ${response.message}")
                
                if (response.isSuccess() && response.data != null) {
                    Log.d("LoginViewModel", "=== 登录成功 ===")
                    Log.d("LoginViewModel", "UserId: ${response.data.userId}")
                    
                    tokenManager.saveToken(response.data.token, response.data.userId)
                    
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                } else {
                    _errorMessage.value = response.message ?: "登录失败"
                    Log.e("LoginViewModel", "登录失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "登录异常", e)
                _errorMessage.value = when {
                    e.message?.contains("timeout") == true -> "请求超时，请检查网络连接"
                    e.message?.contains("Unable to resolve host") == true -> "无法连接到服务器"
                    e.message?.contains("401") == true -> "账号或密码错误"
                    else -> "网络错误，请稍后重试"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：密码登录
    private fun loginWithPassword() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            if (_password.value.isBlank()) {
                _errorMessage.value = "请输入密码"
                _isLoading.value = false
                return@launch
            }
            
            try {
                val request = LoginRequest(_phone.value, password = _password.value, loginType = LoginRequest.TYPE_PASSWORD)
                
                Log.d("LoginViewModel", "=== 开始密码登录 ===")
                Log.d("LoginViewModel", "手机号：${_phone.value}, 密码长度：${_password.value.length}")
                
                val response = api.login(request)
                
                Log.d("LoginViewModel", "=== 收到响应 ===")
                Log.d("LoginViewModel", "响应 code: ${response.code}")
                Log.d("LoginViewModel", "响应 message: ${response.message}")
                
                if (response.isSuccess() && response.data != null) {
                    Log.d("LoginViewModel", "=== 密码登录成功 ===")
                    tokenManager.saveToken(response.data.token, response.data.userId)
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                } else {
                    val errorMsg = response.message ?: "登录失败"
                    Log.e("LoginViewModel", "密码登录失败：$errorMsg")
                    
                    // ⭐ 检测是否是账户不存在的错误，自动跳转到注册
                    if (isUserNotExistError(errorMsg)) {
                        Log.d("LoginViewModel", "检测到账户不存在，自动跳转到注册模式")
                        _errorMessage.value = "该手机号未注册，已为您切换到注册模式"
                        switchToRegisterMode()
                    } else {
                        _errorMessage.value = errorMsg
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "密码登录异常", e)
                _errorMessage.value = when {
                    e.message?.contains("timeout") == true -> "请求超时，请检查网络连接"
                    e.message?.contains("Unable to resolve host") == true -> "无法连接到服务器"
                    e.message?.contains("401") == true -> "账号或密码错误"
                    else -> "网络错误，请稍后重试"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：手机号验证
    private fun isValidPhone(phone: String): Boolean {
        val regex = Regex("^1[3-9]\\d{9}$")
        return regex.matches(phone)
    }

    // 密码格式验证：至少 10 位，包含字母和特殊符号
    fun isValidPassword(password: String): Boolean {
        if (password.length < 10) return false
        val hasLetter = password.any { it.isLetter() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasSymbol
    }

    fun toggleAgreeTerms() {
        _agreeTerms.value = !_agreeTerms.value
    }
    
    // ⭐ 新增：检测是否是账户不存在的错误
    private fun isUserNotExistError(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("用户不存在") || 
               lowerMessage.contains("账户不存在") || 
               lowerMessage.contains("账号不存在") ||
               lowerMessage.contains("not found") ||
               lowerMessage.contains("not exist") ||
               lowerMessage.contains("未注册")
    }
    
    // ⭐ 新增：切换到注册模式（保留手机号和验证码）
    private fun switchToRegisterMode() {
        _isRegisterMode.value = true
        _isForgotPasswordMode.value = false
        // 保留手机号和验证码，只清空密码相关字段
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _currentStep.value = LoginStep.RegisterInfo
        Log.d("LoginViewModel", "已切换到注册模式，当前步骤：RegisterInfo")
    }
}