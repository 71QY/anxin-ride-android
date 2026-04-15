package com.example.myapplication.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.CompleteProfileRequest
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
    
    // ⭐ 新增：验证码发送次数跟踪（防止恶意刷接口）
    private var sendCodeTimestamps = mutableListOf<Long>()
    private val MAX_SEND_COUNT = 10  // 最多发送次数
    private val TIME_WINDOW_MS = 60 * 1000L  // 时间窗口：1分钟

    // ⭐ 新增：用户协议勾选状态
    private val _agreeTerms = MutableStateFlow(false)
    val agreeTerms: StateFlow<Boolean> = _agreeTerms.asStateFlow()
    
    // ⭐ 新增：账号是否完善（用于新用户引导）
    private val _needCompleteProfile = MutableStateFlow(false)
    val needCompleteProfile: StateFlow<Boolean> = _needCompleteProfile.asStateFlow()

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
        Log.d("LoginViewModel", "是否忘记密码模式：$_isForgotPasswordMode")
        Log.d("LoginViewModel", "登录类型：${_loginType.value}")
        Log.d("LoginViewModel", "手机号：${_phone.value}")
        Log.d("LoginViewModel", "验证码：${_code.value}")
        
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
                } else if (_isRegisterMode.value) {
                    // ⭐ 修复：注册模式只需手机号，发送验证码后直接跳转到 RegisterInfo
                    sendCode()
                    Log.d("LoginViewModel", "注册模式，发送验证码，跳转到 RegisterInfo")
                    _currentStep.value = LoginStep.RegisterInfo
                } else if (_loginType.value == LoginRequest.TYPE_CODE) {
                    // 验证码登录模式：需要验证码才能登录
                    if (_code.value.isBlank()) {
                        _errorMessage.value = "请输入验证码"
                        Log.e("LoginViewModel", "验证码为空")
                        return
                    }
                    
                    // 发送验证码并启动倒计时
                    sendCode()
                    
                    // 登录模式，直接验证验证码
                    Log.d("LoginViewModel", "验证码登录模式，调用 loginWithCode()")
                    loginWithCode()
                } else {
                    // 密码模式：直接登录
                    if (_password.value.isBlank()) {
                        _errorMessage.value = "请输入密码"
                        Log.e("LoginViewModel", "密码为空")
                        return
                    }
                    
                    Log.d("LoginViewModel", "密码登录模式，调用 loginWithPassword()")
                    loginWithPassword()
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
                Log.d("LoginViewModel", "进入 RegisterInfo 分支")
                // ⭐ 修改：根据是否需要完善资料，调用不同的接口
                if (_needCompleteProfile.value) {
                    Log.d("LoginViewModel", "检测到 needCompleteProfile=true，调用 completeProfile()")
                    completeProfile()
                } else {
                    Log.d("LoginViewModel", "检测到 needCompleteProfile=false，调用 register()")
                    register()
                }
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
        Log.d("LoginViewModel", "=== toggleMode 被调用 ===")
        Log.d("LoginViewModel", "当前 isRegisterMode: $_isRegisterMode")
        Log.d("LoginViewModel", "当前 isForgotPasswordMode: $_isForgotPasswordMode")
        
        _currentStep.value = LoginStep.PhoneInput
        _phone.value = ""
        _code.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _errorMessage.value = null
        
        // ⭐ 修改：无论当前是什么模式，都切换到注册/登录模式
        if (_isForgotPasswordMode.value) {
            _isForgotPasswordMode.value = false
            _isRegisterMode.value = true  // ⭐ 从忘记密码切换到注册
            Log.d("LoginViewModel", "从忘记密码模式切换到注册模式")
        } else {
            _isRegisterMode.value = !_isRegisterMode.value
            Log.d("LoginViewModel", "切换注册模式：$_isRegisterMode")
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
        _loginSuccess.value = false
        // ⭐ 移除无效回调，导航逻辑由 UI 层的 LaunchedEffect 处理
    }

    fun dismissCodeSuccessDialog() {
        _showCodeSuccessDialog.value = false
    }

    // ⭐ 修改：发送验证码（带频率限制）
    fun sendCode() {
        // ⭐ 如果正在倒计时，不重复发送
        if (_isCountingDown.value) {
            Log.d("LoginViewModel", "正在倒计时中，不重复发送")
            return
        }
        
        // ⭐ 新增：检查发送频率（1分钟内最多10次）
        val now = System.currentTimeMillis()
        // 清理过期的时间戳（超过1分钟的）
        sendCodeTimestamps.removeAll { it < now - TIME_WINDOW_MS }
        
        if (sendCodeTimestamps.size >= MAX_SEND_COUNT) {
            _errorMessage.value = "操作频繁，请稍后再试（1分钟内最多发送${MAX_SEND_COUNT}次）"
            Log.w("LoginViewModel", "⚠️ 验证码发送过于频繁：${sendCodeTimestamps.size}次/${TIME_WINDOW_MS/1000}秒")
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
                    // ⭐ 记录发送时间戳
                    sendCodeTimestamps.add(System.currentTimeMillis())
                    Log.d("LoginViewModel", "✅ 验证码发送成功，当前窗口内已发送：${sendCodeTimestamps.size}次")
                    
                    _showCodeSuccessDialog.value = true
                    startCountdown()  // ⭐ 发送成功后启动倒计时
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
                        
                    // ⭐ 新增：检查账号是否需要完善
                    val needSetup = response.data.needSetup
                    Log.d("LoginViewModel", "账号完善状态: needSetup=$needSetup")
                        
                    if (needSetup) {
                        // 账号未完善，强制跳转到设置昵称和密码页面
                        Log.d("LoginViewModel", "⚠️ 账号未完善，跳转到 RegisterInfo 页面")
                        _needCompleteProfile.value = true
                        _currentStep.value = LoginStep.RegisterInfo
                        _isLoading.value = false
                        // ⭐ 新增：显示提示
                        _errorMessage.value = "请先完善账号信息（设置昵称和密码）"
                        return@launch
                    }
                        
                    // 账号已完善，正常进入主页
                    Log.d("LoginViewModel", "✅ 账号已完善，显示登录成功弹窗")
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                    _needCompleteProfile.value = false
                } else {
                    val errorMsg = response.message ?: "登录失败"
                    Log.e("LoginViewModel", "登录失败：$errorMsg")
                        
                    // ⭐ 修复：移除自动跳转到注册模式的逻辑，让用户手动选择
                    // 原逻辑：检测到“用户不存在”会自动切换，导致用户困惑
                    // 现逻辑：直接显示错误信息，用户可以通过底部按钮主动切换到注册
                    _errorMessage.value = errorMsg
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "登录异常", e)
                _errorMessage.value = when {
                    e.message?.contains("timeout") == true -> "❌ 请求超时，请检查网络连接"
                    e.message?.contains("Unable to resolve host") == true -> "❌ 无法连接到服务器"
                    e.message?.contains("401") == true -> "❌ 账号或密码错误"
                    else -> "❌ 网络错误：${e.message}"
                }
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
                        // 注册成功并返回 token
                        Log.d("LoginViewModel", "保存 Token: ${response.data.token.take(20)}...")
                        tokenManager.saveToken(response.data.token, response.data.userId)
                        
                        // ⭐ 新增：检查账号是否需要完善
                        val needSetup = response.data.needSetup
                        Log.d("LoginViewModel", "账号完善状态: needSetup=$needSetup")
                        
                        // ⭐ 修改：根据后端返回的 needSetup 决定是否跳转
                        if (needSetup) {
                            // 账号未完善，强制跳转到设置昵称和密码页面
                            Log.d("LoginViewModel", "⚠️ 账号未完善，跳转到 RegisterInfo 页面")
                            _needCompleteProfile.value = true
                            _currentStep.value = LoginStep.RegisterInfo
                            _isLoading.value = false
                            _errorMessage.value = "✅ 验证通过！请设置昵称和密码完成注册"
                            return@launch
                        }
                        
                        // 账号已完善，直接进入应用
                        Log.d("LoginViewModel", "✅ 注册并登录成功，显示成功弹窗")
                        _loginSuccess.value = true
                        _showLoginDialog.value = true
                        _needCompleteProfile.value = false
                    } else {
                        // 注册成功但未返回 token，提示用户登录
                        Log.d("LoginViewModel", "注册成功，但未返回 token")
                        _showLoginDialog.value = true
                        _errorMessage.value = "✅ 注册成功！请使用手机号和验证码登录"
                    }
                } else {
                    // ⭐ 注册失败（如验证码错误、手机号已存在等），保留当前步骤和数据，让用户可以重试
                    val errorMsg = response.message ?: "注册失败"
                    
                    // ⭐ 特殊处理：如果手机号已存在，提示用户直接登录
                    if (errorMsg.contains("已存在") || errorMsg.contains("已注册") || 
                        errorMsg.contains("already exists") || errorMsg.contains("registered")) {
                        _errorMessage.value = "⚠️ 该手机号已注册！请直接登录或使用“忘记密码”功能"
                        Log.w("LoginViewModel", "⚠️ 手机号已存在，提示用户直接登录")
                    } else {
                        _errorMessage.value = "❌ $errorMsg"
                        Log.e("LoginViewModel", "❌ 注册失败：$errorMsg")
                    }
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
    
    // ⭐ 新增：完善账号信息（调用 complete-profile 接口）
    private fun completeProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            // 验证密码格式
            if (!isValidPassword(_password.value)) {
                _errorMessage.value = "❌ 密码必须是 10 位，且包含字母和特殊符号"
                _isLoading.value = false
                return@launch
            }
            
            // 验证昵称
            if (_nickname.value.isBlank()) {
                _errorMessage.value = "❌ 请输入昵称"
                _isLoading.value = false
                return@launch
            }
            
            try {
                val request = CompleteProfileRequest(
                    password = _password.value,
                    nickname = _nickname.value
                )
                
                Log.d("LoginViewModel", "=== 开始完善账号 ===")
                Log.d("LoginViewModel", "请求参数：nickname=${_nickname.value}")
                
                val response = api.completeProfile(request)
                
                if (response.isSuccess()) {
                    Log.d("LoginViewModel", "✅ 账号完善成功")
                    
                    // 显示登录成功弹窗
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                    _needCompleteProfile.value = false
                } else {
                    _errorMessage.value = "❌ ${response.message ?: "完善失败"}"
                    Log.e("LoginViewModel", "❌ 完善账号失败：${response.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "❌ 完善账号异常", e)
                _errorMessage.value = when {
                    e.message?.contains("timeout") == true -> "❌ 请求超时，请检查网络连接"
                    e.message?.contains("Unable to resolve host") == true -> "❌ 无法连接到服务器"
                    else -> "❌ 网络错误：${e.message}"
                }
            } finally {
                _isLoading.value = false
                Log.d("LoginViewModel", "completeProfile() 执行完成")
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
                    
                    // ⭐ 新增：检查账号是否需要完善
                    val needSetup = response.data.needSetup
                    Log.d("LoginViewModel", "账号完善状态: needSetup=$needSetup")
                    
                    if (needSetup) {
                        // 账号未完善，强制跳转到设置昵称和密码页面
                        Log.d("LoginViewModel", "⚠️ 账号未完善，跳转到 RegisterInfo 页面")
                        _needCompleteProfile.value = true
                        _currentStep.value = LoginStep.RegisterInfo
                        _isLoading.value = false
                        return@launch
                    }
                    
                    // ⭐ 新增：检查是否为长辈模式
                    checkElderMode(response.data.userId)
                    
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                    _needCompleteProfile.value = false  // ⭐ 新增：重置状态
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
                    
                    // ⭐ 新增：检查账号是否需要完善
                    val needSetup = response.data.needSetup
                    Log.d("LoginViewModel", "账号完善状态: needSetup=$needSetup")
                    
                    if (needSetup) {
                        // 账号未完善，强制跳转到设置昵称和密码页面
                        Log.d("LoginViewModel", "⚠️ 账号未完善，跳转到 RegisterInfo 页面")
                        _needCompleteProfile.value = true
                        _currentStep.value = LoginStep.RegisterInfo
                        _isLoading.value = false
                        return@launch
                    }
                    
                    // ⭐ 新增：检查是否为长辈模式
                    checkElderMode(response.data.userId)
                    
                    _loginSuccess.value = true
                    _showLoginDialog.value = true
                    _needCompleteProfile.value = false  // ⭐ 新增：重置状态
                } else {
                    val errorMsg = response.message ?: "登录失败"
                    Log.e("LoginViewModel", "密码登录失败：$errorMsg")
                    
                    // ⭐ 修复：移除自动跳转到注册模式的逻辑，让用户手动选择
                    // 原逻辑：检测到"用户不存在"会自动切换，导致用户困惑
                    // 现逻辑：直接显示错误信息，用户可以通过底部按钮主动切换到注册
                    _errorMessage.value = errorMsg
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

    // 密码格式验证：必须是 10 位，包含字母和特殊符号
    fun isValidPassword(password: String): Boolean {
        if (password.length != 10) return false  // ⭐ 修改：必须是 exactly 10 位
        val hasLetter = password.any { it.isLetter() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasSymbol
    }

    fun toggleAgreeTerms() {
        _agreeTerms.value = !_agreeTerms.value
    }
    
    /**
     * ⭐ 检查是否为长辈模式
     */
    private fun checkElderMode(userId: Long) {
        viewModelScope.launch {
            try {
                val profile = api.getUserProfile()
                if (profile.isSuccess() && profile.data != null) {
                    // ⭐ 修改：使用 guardMode 字段（0普通模式 1长辈精简模式）
                    if (profile.data.guardMode == 1) {
                        Log.d("LoginViewModel", "检测到长辈模式，guardMode=${profile.data.guardMode}")
                        // ⭐ 保存到本地存储
                        tokenManager.saveGuardMode(1)
                    } else {
                        Log.d("LoginViewModel", "普通用户模式，guardMode=${profile.data.guardMode}")
                        // ⭐ 保存到本地存储
                        tokenManager.saveGuardMode(0)
                    }
                } else {
                    // ⭐ 修复：API失败时保留本地缓存的guardMode，不覆盖
                    val cachedGuardMode = tokenManager.getGuardMode()
                    Log.w("LoginViewModel", "⚠️ 获取用户信息失败，保留本地缓存的guardMode=$cachedGuardMode")
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "检查长辈模式失败", e)
                // ⭐ 修复：异常时保留本地缓存，不覆盖
                val cachedGuardMode = tokenManager.getGuardMode()
                Log.w("LoginViewModel", "⚠️ 网络异常，保留本地缓存的guardMode=$cachedGuardMode")
            }
        }
    }
    
    /**
     * ⭐ 新增：重置所有登录状态（用于退出登录后重新初始化）
     * 注意：不清除 Token，Token 由 ProfileViewModel.logout() 统一清除
     */
    fun resetAllState() {
        Log.d("LoginViewModel", "=== 重置所有登录状态 ===")
        _currentStep.value = LoginStep.PhoneInput
        _phone.value = ""
        _code.value = ""
        _password.value = ""
        _confirmPassword.value = ""
        _nickname.value = ""
        _loginType.value = LoginRequest.TYPE_CODE
        _isRegisterMode.value = false
        _isForgotPasswordMode.value = false
        _isLoading.value = false
        _errorMessage.value = null
        _loginSuccess.value = false
        _showLoginDialog.value = false
        _showCodeSuccessDialog.value = false
        _countdownSeconds.value = 60
        _isCountingDown.value = false
        _agreeTerms.value = false
        _needCompleteProfile.value = false  // ⭐ 新增：重置账号完善状态
        sendCodeTimestamps.clear()
        Log.d("LoginViewModel", "✅ 所有状态已重置（保留 Token）")
    }
}