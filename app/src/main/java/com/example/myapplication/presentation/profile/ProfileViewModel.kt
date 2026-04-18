package com.example.myapplication.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.core.datastore.TokenManager
import com.example.myapplication.core.network.ApiService
// ⭐ 修复：移除 AppIconSwitcher 导入，图标切换由 LoginViewModel 负责
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.RealNameRequest
import com.example.myapplication.data.model.UserProfile
import com.example.myapplication.data.model.AvatarResponse
import com.example.myapplication.data.model.AddElderRequest
import com.example.myapplication.data.model.RegisterElderRequest  // ⭐ 新增：帮长辈注册请求
import com.example.myapplication.data.model.BindExistingElderRequest  // ⭐ 新增：绑定已有长辈请求
import com.example.myapplication.data.model.GuardianInfo
import com.example.myapplication.data.model.ElderInfo  // ⭐ 新增：长辈信息模型
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val api: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _isContactsLoading = MutableStateFlow(false)
    val isContactsLoading: StateFlow<Boolean> = _isContactsLoading.asStateFlow()

    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // ⭐ 新增：认证失败标志（用于自动退出登录）
    private val _authFailure = MutableStateFlow(false)
    val authFailure: StateFlow<Boolean> = _authFailure.asStateFlow()
    
    /**
     * ⭐ 重置认证失败标志
     */
    fun resetAuthFailure() {
        _authFailure.value = false
        Log.d("ProfileViewModel", "✅ 已重置认证失败标志")
    }

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    private val _nicknameInput = MutableStateFlow("")
    val nicknameInput: StateFlow<String> = _nicknameInput.asStateFlow()

    private val _codeInput = MutableStateFlow("")
    val codeInput: StateFlow<String> = _codeInput.asStateFlow()

    private val _newPasswordInput = MutableStateFlow("")
    val newPasswordInput: StateFlow<String> = _newPasswordInput.asStateFlow()

    private val _realNameInput = MutableStateFlow("")
    val realNameInput: StateFlow<String> = _realNameInput.asStateFlow()

    private val _idCardInput = MutableStateFlow("")
    val idCardInput: StateFlow<String> = _idCardInput.asStateFlow()

    private val _contactNameInput = MutableStateFlow("")
    val contactNameInput: StateFlow<String> = _contactNameInput.asStateFlow()

    private val _contactPhoneInput = MutableStateFlow("")
    val contactPhoneInput: StateFlow<String> = _contactPhoneInput.asStateFlow()

    // ⭐ 亲情守护相关状态
    private val _isAddingElder = MutableStateFlow(false)
    val isAddingElder: StateFlow<Boolean> = _isAddingElder.asStateFlow()

    private val _elderPhoneInput = MutableStateFlow("")
    val elderPhoneInput: StateFlow<String> = _elderPhoneInput.asStateFlow()

    private val _elderNameInput = MutableStateFlow("")
    val elderNameInput: StateFlow<String> = _elderNameInput.asStateFlow()
    
    // ⭐ 新增：长辈身份证号（v2.0 必填）
    private val _elderIdCardInput = MutableStateFlow("")
    val elderIdCardInput: StateFlow<String> = _elderIdCardInput.asStateFlow()
    
    // ⭐ 新增：与长辈关系（v2.0 选填）
    private val _relationshipInput = MutableStateFlow("")
    val relationshipInput: StateFlow<String> = _relationshipInput.asStateFlow()
    
    // ⭐ 新增：长辈昵称（方案2：亲友代设）
    private val _elderNicknameInput = MutableStateFlow("")
    val elderNicknameInput: StateFlow<String> = _elderNicknameInput.asStateFlow()
    
    // ⭐ 新增：长辈密码（方案2：亲友代设）
    private val _elderPasswordInput = MutableStateFlow("")
    val elderPasswordInput: StateFlow<String> = _elderPasswordInput.asStateFlow()
    
    // ⭐ 新增：确认密码
    private val _elderConfirmPasswordInput = MutableStateFlow("")
    val elderConfirmPasswordInput: StateFlow<String> = _elderConfirmPasswordInput.asStateFlow()
    
    // ⭐ 新增：亲友信息（后端要求必填）
    private val _guardianNameInput = MutableStateFlow("")
    val guardianNameInput: StateFlow<String> = _guardianNameInput.asStateFlow()
    
    private val _guardianIdCardInput = MutableStateFlow("")
    val guardianIdCardInput: StateFlow<String> = _guardianIdCardInput.asStateFlow()

    // ⭐ 修改：守护者信息状态（后端返回列表）
    private val _guardianInfoList = MutableStateFlow<List<GuardianInfo>>(emptyList())
    val guardianInfoList: StateFlow<List<GuardianInfo>> = _guardianInfoList.asStateFlow()
    
    // 保留单个守护者信息用于兼容性（取第一个）
    val guardianInfo: StateFlow<GuardianInfo?> = _guardianInfoList
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)
    
    // ⭐ 新增：我的长辈列表状态（普通用户视角）
    private val _elderInfoList = MutableStateFlow<List<ElderInfo>>(emptyList())
    val elderInfoList: StateFlow<List<ElderInfo>> = _elderInfoList.asStateFlow()

    init {
        Log.d("ProfileViewModel", "init called")
        viewModelScope.launch {
            delay(1000)
            loadProfile()
            loadEmergencyContacts()
        }
    }

    fun isValidPassword(password: String): Boolean {
        if (password.length < 10) return false
        val hasLetter = password.any { it.isLetter() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasSymbol
    }

    fun isValidIdCard(idCard: String): Boolean {
        val regex = Regex("^[1-9]\\d{5}(18|19|20)?\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}(\\d|X|x)$")
        return regex.matches(idCard)
    }

    private suspend fun waitForToken(): String? {
        var token = tokenManager.getToken()
        var attempts = 0
        val maxAttempts = 5
        
        Log.d("ProfileViewModel", "First attempt to get Token: ${if (token != null) "exists" else "null"}")
        
        while (token.isNullOrBlank() && attempts < maxAttempts) {
            delay(300)
            token = tokenManager.getToken()
            attempts++
            Log.d("ProfileViewModel", "Waiting for Token, attempt $attempts/$maxAttempts, token=${if (token != null) "exists" else "null"}")
        }
        
        if (token.isNullOrBlank()) {
            Log.e("ProfileViewModel", "请先登录")
            return null
        }
        
        Log.d("ProfileViewModel", "Token acquired, length: ${token.length}")
        return token
    }

    fun loadProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "=== loadProfile started ===")
            _isProfileLoading.value = true
            _errorMessage.value = null
            try {
                val token = waitForToken()
                if (token.isNullOrBlank()) {
                    _errorMessage.value = "请先登录"
                    _isProfileLoading.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "Calling getUserProfile API")
                
                val response = api.getUserProfile()
                Log.d("ProfileViewModel", "getUserProfile response:")
                Log.d("ProfileViewModel", "  code=${response.code}")
                Log.d("ProfileViewModel", "  message=${response.message}")
                
                val profileData = try {
                    response.data
                } catch (e: ClassCastException) {
                    Log.e("ProfileViewModel", "Type conversion failed, data may not be UserProfile type", e)
                    null
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "Failed to parse data", e)
                    null
                }
                
                if (profileData == null) {
                    Log.e("ProfileViewModel", "Data parsing failed, JSON format may not match")
                    _errorMessage.value = "数据格式错误，请联系管理员"
                    _isProfileLoading.value = false
                    return@launch
                }
                
                if (response.isSuccess()) {
                    _profile.value = profileData
                    _nicknameInput.value = profileData?.nickname ?: ""
                    Log.d("ProfileViewModel", "User info loaded successfully")
                    Log.d("ProfileViewModel", "  ID: ${profileData?.id}")
                    Log.d("ProfileViewModel", "  Phone: ${profileData?.phone}")
                    Log.d("ProfileViewModel", "  Nickname: ${profileData?.nickname}")
                    Log.d("ProfileViewModel", "  Avatar: ${profileData?.avatar}")
                    Log.d("ProfileViewModel", "  Real name: ${profileData?.realName}")
                    Log.d("ProfileViewModel", "  Verified status: verified=${profileData?.verified}")
                    Log.d("ProfileViewModel", "  Guard mode: guardMode=${profileData?.guardMode}")
                    // ⭐ 修复：移除图标切换逻辑，避免 Activity 重建
                    // 图标切换仅在登录成功后由 LoginViewModel 执行
                    // ⭐ 重置认证失败标志
                    _authFailure.value = false
                } else {
                    Log.e("ProfileViewModel", "loadProfile failed: ${response.message}")
                    _errorMessage.value = response.message ?: "加载失败"
                    // ⭐ 新增：如果是401错误，标记为认证失败
                    if (response.code == 401) {
                        Log.w("ProfileViewModel", "⚠️ Token已失效，需要重新登录")
                        _authFailure.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ loadProfile 异常", e)
                // ⭐ Bug 修复：打印完整的异常堆栈
                _errorMessage.value = "${e.message ?: "网络错误"}\n请检查后端接口返回的数据格式"
                // ⭐ 新增：如果是401错误，标记为认证失败
                if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                    Log.w("ProfileViewModel", "⚠️ Token已失效，需要重新登录")
                    _authFailure.value = true
                }
            } finally {
                _isProfileLoading.value = false
                Log.d("ProfileViewModel", "=== loadProfile 执行完毕 ===")
            }
        }
    }

    /**
     * 加载紧急联系人列表
     */
    fun loadEmergencyContacts() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "=== loadEmergencyContacts 开始执行 ===")
            _isContactsLoading.value = true
            try {
                // ⭐ 修改：使用封装的方法等待 Token
                val token = waitForToken()
                if (token.isNullOrBlank()) {
                    _errorMessage.value = "请先登录"
                    _isContactsLoading.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "开始调用 getEmergencyContacts API")
                
                val response = api.getEmergencyContacts()
                Log.d("ProfileViewModel", "getEmergencyContacts 响应:")
                Log.d("ProfileViewModel", "  code=${response.code}")
                Log.d("ProfileViewModel", "  message=${response.message}")
                Log.d("ProfileViewModel", "  data size=${response.data?.size}")
                
                if (response.isSuccess()) {
                    _contacts.value = response.data ?: emptyList()
                    Log.d("ProfileViewModel", "✅ 联系人列表加载成功：${_contacts.value.size}个")
                    _contacts.value.forEachIndexed { index, contact ->
                        Log.d("ProfileViewModel", "  [$index] ${contact.name} - ${contact.phone} (关系：${contact.relationship})")
                    }
                } else {
                    Log.e("ProfileViewModel", "❌ loadEmergencyContacts 失败：${response.message}")
                    _errorMessage.value = response.message ?: "加载失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ loadEmergencyContacts 异常", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isContactsLoading.value = false
                Log.d("ProfileViewModel", "=== loadEmergencyContacts 执行完毕 ===")
            }
        }
    }

    /**
     * 发送验证码（用于修改密码）
     */
    fun sendCodeForPassword() {
        viewModelScope.launch {
            _isSendingCode.value = true
            _errorMessage.value = null
            try {
                val phone = tokenManager.getToken()?.let { 
                    // 从 Token 中提取手机号，或者使用其他方式获取
                    _profile.value?.phone
                }
                
                if (phone.isNullOrBlank()) {
                    _errorMessage.value = "未获取到手机号"
                    return@launch
                }
                
                val response = api.sendCode(phone)
                Log.d("ProfileViewModel", "sendCode response: $response")
                if (response.isSuccess()) {
                    _successMessage.value = "验证码已发送"
                } else {
                    _errorMessage.value = response.message ?: "发送失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "sendCode exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isSendingCode.value = false
            }
        }
    }

    /**
     * 上传头像（带裁剪）
     */
    fun uploadAvatar(uri: Uri, file: File) {
        Log.d("ProfileViewModel", "🚀 === uploadAvatar 被调用 ===")
        Log.d("ProfileViewModel", "URI: $uri")
        Log.d("ProfileViewModel", "文件路径: ${file.absolutePath}")
        Log.d("ProfileViewModel", "文件大小: ${file.length()} bytes (${file.length() / 1024}KB)")
        Log.d("ProfileViewModel", "文件是否存在: ${file.exists()}")
        
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                Log.d("ProfileViewModel", "📦 开始压缩图片...")
                // ⭐ 修改：先压缩图片，然后自动适配头像大小
                val compressedFile = compressImage(file)
                Log.d("ProfileViewModel", "✅ 压缩完成: ${compressedFile.absolutePath}")
                Log.d("ProfileViewModel", "压缩后大小: ${compressedFile.length() / 1024}KB")
                
                // ⭐ TODO：这里可以添加手动裁剪功能，使用图片编辑库
                // 目前先使用自动压缩到 200x200
                
                Log.d("ProfileViewModel", "📤 准备上传请求...")
                val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "avatar",
                    compressedFile.name,
                    requestBody
                )
                
                Log.d("ProfileViewModel", "开始上传头像，文件大小：${compressedFile.length() / 1024}KB")
                
                val response = api.uploadAvatar(filePart)
                Log.d("ProfileViewModel", "📥 上传响应: code=${response.code}, message=${response.message}")
                Log.d("ProfileViewModel", "响应 data (原始): ${response.data}")
                Log.d("ProfileViewModel", "响应 data 类型: ${response.data?.javaClass}")
                
                if (response.isSuccess()) {
                    // ⭐ 修复：直接使用字符串类型的 data（头像 URL）
                    val avatarUrl = response.data
                    Log.d("ProfileViewModel", "✅ 解析后的 avatarUrl: $avatarUrl")
                    
                    if (!avatarUrl.isNullOrBlank()) {
                        // ⭐ 修改：正确处理 URL 拼接（与 ProfileScreen 保持一致）
                        val fullUrl = if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
                            // 已经是完整 URL，直接使用
                            avatarUrl
                        } else if (avatarUrl.startsWith("/api/")) {
                            // 后端返回 /api/xxx，直接拼接 BASE_URL（去掉末尾的 /api/）
                            // 旧后端地址 (A): "http://10.241.75.80:8080$avatarUrl"
                            // 中间后端地址 (B): "http://192.168.189.57:8080$avatarUrl"
                            // 新后端地址 (C):
                            "http://192.168.189.80:8080$avatarUrl"
                        } else if (avatarUrl.startsWith("/")) {
                            // 以 / 开头的其他路径
                            // 旧后端地址 (A): "http://10.241.75.80:8080/api$avatarUrl"
                            // 中间后端地址 (B): "http://192.168.189.57:8080/api$avatarUrl"
                            // 新后端地址 (C):
                            "http://192.168.189.80:8080/api$avatarUrl"
                        } else {
                            // 不带 / 的相对路径
                            // 旧后端地址 (A): "http://10.241.75.80:8080/api/$avatarUrl"
                            // 中间后端地址 (B): "http://192.168.189.57:8080/api/$avatarUrl"
                            // 新后端地址 (C):
                            "http://192.168.189.80:8080/api/$avatarUrl"
                        }
                        
                        Log.d("ProfileViewModel", "✅ 头像上传成功")
                        Log.d("ProfileViewModel", "原始 avatarUrl: $avatarUrl")
                        Log.d("ProfileViewModel", "完整 URL: $fullUrl")
                        _successMessage.value = "头像上传成功"
                        
                        // ⭐ 关键修复：直接更新本地状态，不再调用 loadProfile()
                        // 原因：上传接口已返回最新 URL，无需再从服务器加载（避免覆盖和延迟）
                        val currentProfile = _profile.value
                        if (currentProfile != null) {
                            Log.d("ProfileViewModel", "📊 当前 profile: $currentProfile")
                            Log.d("ProfileViewModel", "📊 当前 avatar: ${currentProfile.avatar}")
                            
                            // ⭐ 新增：在 URL 后添加时间戳参数，强制 Coil 绕过缓存重新加载
                            val cacheBusterUrl = if (fullUrl.contains("?")) {
                                "$fullUrl&v=${System.currentTimeMillis()}"
                            } else {
                                "$fullUrl?v=${System.currentTimeMillis()}"
                            }
                            
                            val updatedProfile = currentProfile.copy(avatar = cacheBusterUrl)
                            Log.d("ProfileViewModel", "📊 更新后的 avatar: ${updatedProfile.avatar}")
                            Log.d("ProfileViewModel", "🔄 准备更新 _profile.value...")
                            
                            _profile.value = updatedProfile  // 触发 Compose 重组，界面立即刷新
                            
                            Log.d("ProfileViewModel", "✅ _profile.value 已更新")
                            Log.d("ProfileViewModel", "📊 验证更新：${_profile.value?.avatar}")
                            Log.d("ProfileViewModel", "✅ 本地头像已更新（带缓存破坏器）：$cacheBusterUrl")
                        } else {
                            Log.e("ProfileViewModel", "❌ currentProfile 为 null，无法更新头像")
                        }
                    } else {
                        Log.e("ProfileViewModel", "❌ 头像 URL 为空，data类型：${response.data?.javaClass}")
                        _errorMessage.value = "上传失败：未获取到头像 URL"
                    }
                } else {
                    Log.e("ProfileViewModel", "❌ 头像上传失败：${response.message}")
                    _errorMessage.value = response.message ?: "上传失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ uploadAvatar exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    // ⭐ 新增：压缩图片（对齐后端建议：500x500 或 800x800）
    private suspend fun compressImage(sourceFile: File): File = withContext(Dispatchers.IO) {
        try {
            // ⭐ 读取原始图片
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            
            // ⭐ Bug 5: 添加 bitmap null 检查
            if (bitmap == null || bitmap.isRecycled) {
                Log.e("ProfileViewModel", "❌ 图片解码失败")
                return@withContext sourceFile
            }
            
            // ⭐ 对齐后端建议：最大 500x500，质量 85%（平衡清晰度和上传速度）
            val maxSize = 500  // ⭐ 从 200 改为 500，提升清晰度
            val quality = 85
            
            var width = bitmap.width
            var height = bitmap.height
            
            // ⭐ 计算缩放比例（保持宽高比）
            var scale = 1.0f
            if (width > maxSize || height > maxSize) {
                scale = maxSize.toFloat() / maxOf(width, height)
                width = (width * scale).toInt()
                height = (height * scale).toInt()
            }
            
            // ⭐ 缩放图片
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }
            
            // ⭐ 压缩并保存为新文件（始终使用 JPEG 格式，兼容后端）
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            // ⭐ Bug 6: 确保父目录存在
            val parentDir = sourceFile.parentFile ?: File(sourceFile.parent)
            val compressedFile = File(parentDir, 
                "compressed_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(compressedFile)
            fos.write(outputStream.toByteArray())
            fos.close()
            
            Log.d("ProfileViewModel", "图片压缩完成：${sourceFile.length() / 1024}KB -> ${compressedFile.length() / 1024}KB, 尺寸：${width}x${height}")
            
            compressedFile
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "❌ 图片压缩失败", e)
            // ⭐ 如果压缩失败，返回原文件
            sourceFile
        }
    }

    /**
     * 修改昵称
     */
    fun changeNickname() {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                // ⭐ Bug 7: 昵称不能为空检查
                if (_nicknameInput.value.isBlank()) {
                    _errorMessage.value = "昵称不能为空"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val currentProfile = _profile.value ?: run {
                    _errorMessage.value = "未获取到用户信息"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ Bug 8: 昵称没有变化时不提交
                if (_nicknameInput.value.trim() == currentProfile.nickname?.trim()) {
                    _errorMessage.value = "昵称没有变化"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val updatedProfile = UserProfile(
                    id = currentProfile.id,
                    phone = currentProfile.phone,
                    nickname = _nicknameInput.value.trim(),  // ⭐ Bug 9: 去除前后空格
                    avatar = currentProfile.avatar,
                    realName = currentProfile.realName,
                    idCard = currentProfile.idCard,
                    verified = currentProfile.verified
                )
                
                val response = api.updateUserProfile(updatedProfile)  // ⭐ 修改：使用 updateUserProfile
                Log.d("ProfileViewModel", "changeNickname response: $response")
                if (response.isSuccess()) {
                    _successMessage.value = "昵称修改成功"
                    loadProfile()
                } else {
                    _errorMessage.value = response.message ?: "修改失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "changeNickname exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 修改密码
     */
    fun changePassword() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "=== changePassword 被调用 ===")
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            try {
                // ⭐ 新增：验证码校验
                if (codeInput.value.isBlank()) {
                    _errorMessage.value = "请输入验证码"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ 新增：密码格式校验
                val newPassword = newPasswordInput.value
                if (!isValidPassword(newPassword)) {
                    _errorMessage.value = "密码必须是 10 位，且包含字母和特殊符号"  // ⭐ Bug 3: 更新错误提示
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val phone = _profile.value?.phone
                if (phone.isNullOrBlank()) {
                    _errorMessage.value = "未获取到手机号"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ 修改：使用 ChangePasswordRequest
                val request = ChangePasswordRequest(
                    phone = phone,
                    code = codeInput.value,
                    newPassword = newPassword
                )
                
                Log.d("ProfileViewModel", "开始调用 changePassword API")
                Log.d("ProfileViewModel", "请求参数：phone=$phone, code=${codeInput.value}, newPassword=***")
                val response = api.changePassword(request)
                Log.d("ProfileViewModel", "changePassword response: $response")
                
                if (response.isSuccess()) {
                    _successMessage.value = "密码修改成功"
                    // 清空输入
                    _codeInput.value = ""
                    _newPasswordInput.value = ""
                } else {
                    _errorMessage.value = response.message ?: "修改失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "changePassword exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 实名认证
     */
    fun submitRealNameAuth() {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            // ⭐ Bug 10: 姓名和身份证号不能为空检查
            if (_realNameInput.value.isBlank()) {
                _errorMessage.value = "姓名不能为空"
                _isOperationLoading.value = false
                return@launch
            }
            
            if (_idCardInput.value.isBlank()) {
                _errorMessage.value = "身份证号不能为空"
                _isOperationLoading.value = false
                return@launch
            }
            
            if (!isValidIdCard(_idCardInput.value)) {
                _errorMessage.value = "身份证格式不正确"
                _isOperationLoading.value = false
                return@launch
            }
            
            try {
                val request = RealNameRequest(_realNameInput.value.trim(), _idCardInput.value.trim())  // ⭐ Bug 11: 去除空格
                val response = api.realNameAuth(request)
                Log.d("ProfileViewModel", "realNameAuth response: $response")
                if (response.isSuccess()) {
                    _successMessage.value = "实名认证成功"
                    loadProfile()
                } else {
                    _errorMessage.value = response.message ?: "认证失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "realNameAuth exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 添加紧急联系人
     */
    fun addEmergencyContact() {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                // ⭐ Bug 12: 联系人姓名和电话不能为空检查
                if (_contactNameInput.value.isBlank()) {
                    _errorMessage.value = "联系人姓名不能为空"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                if (_contactPhoneInput.value.isBlank()) {
                    _errorMessage.value = "联系人电话不能为空"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ Bug 13: 手机号格式验证
                if (!isValidPhone(_contactPhoneInput.value)) {
                    _errorMessage.value = "手机号格式不正确"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ 修改：添加 relationship 字段
                val contact = EmergencyContact(
                    name = _contactNameInput.value.trim(),
                    phone = _contactPhoneInput.value.trim(),
                    relationship = "其他"  // 默认关系为"其他"
                )
                val response = api.addEmergencyContact(contact)
                Log.d("ProfileViewModel", "addEmergencyContact response: $response")
                if (response.isSuccess()) {
                    _successMessage.value = "紧急联系人添加成功"
                    _contactNameInput.value = ""
                    _contactPhoneInput.value = ""
                    loadEmergencyContacts()
                } else {
                    _errorMessage.value = response.message ?: "添加失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "addEmergencyContact exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 删除紧急联系人
     */
    fun deleteEmergencyContact(id: Long) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.deleteEmergencyContact(id)
                Log.d("ProfileViewModel", "deleteEmergencyContact response: $response")
                if (response.isSuccess()) {
                    _successMessage.value = "删除成功"
                    loadEmergencyContacts()
                } else {
                    _errorMessage.value = response.message ?: "删除失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "deleteEmergencyContact exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    // 更新输入框
    fun updateNicknameInput(nickname: String) { _nicknameInput.value = nickname }
    fun updateCodeInput(code: String) { _codeInput.value = code }
    fun updateNewPasswordInput(password: String) { _newPasswordInput.value = password }
    fun updateRealNameInput(name: String) { _realNameInput.value = name }
    fun updateIdCardInput(idCard: String) { _idCardInput.value = idCard }
    fun updateContactNameInput(name: String) { _contactNameInput.value = name }
    fun updateContactPhoneInput(phone: String) { _contactPhoneInput.value = phone }
    
    // ⭐ 亲情守护相关方法
    fun updateElderPhoneInput(phone: String) { _elderPhoneInput.value = phone }
    fun updateElderNameInput(name: String) { _elderNameInput.value = name }
    fun updateElderIdCardInput(idCard: String) { _elderIdCardInput.value = idCard }  // ⭐ 新增
    fun updateRelationshipInput(relationship: String) { _relationshipInput.value = relationship }  // ⭐ 新增
    
    // ⭐ 新增：长辈昵称和密码输入更新（方案2）
    fun updateElderNicknameInput(nickname: String) { _elderNicknameInput.value = nickname }
    fun updateElderPasswordInput(password: String) { _elderPasswordInput.value = password }
    fun updateElderConfirmPasswordInput(confirmPassword: String) { _elderConfirmPasswordInput.value = confirmPassword }
    
    // ⭐ 新增：亲友信息输入更新
    fun updateGuardianNameInput(name: String) { _guardianNameInput.value = name }
    fun updateGuardianIdCardInput(idCard: String) { _guardianIdCardInput.value = idCard }
        
    // ⭐ 手机号格式验证（私有方法，统一使用）
    private fun isValidPhone(phone: String): Boolean {
        val regex = Regex("^1[3-9]\\d{9}$")
        return regex.matches(phone)
    }
    
    /**
     * 帮长辈注册账号（v2.0 新接口）⭐
     * POST /api/guard/register-elder
     */
    fun registerElder() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "🚀 === 开始帮长辈注册账号 ===")
            _isAddingElder.value = true
            _errorMessage.value = null
            _successMessage.value = null  // ⭐ 清空之前的消息
            
            // ⭐ 校验必填字段
            if (_elderNameInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈姓名"
                _isAddingElder.value = false
                return@launch
            }
            
            if (_elderIdCardInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈身份证号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidIdCard(_elderIdCardInput.value)) {
                _errorMessage.value = "身份证号格式不正确（18位，支持最后一位为X）"
                _isAddingElder.value = false
                return@launch
            }
            
            if (_elderPhoneInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈手机号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidPhone(_elderPhoneInput.value)) {
                _errorMessage.value = "长辈手机号格式不正确"
                _isAddingElder.value = false
                return@launch
            }
            
            // ⭐ 方案2：校验密码（必填）
            if (_elderPasswordInput.value.isBlank()) {
                _errorMessage.value = "请设置登录密码"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidPassword(_elderPasswordInput.value)) {
                _errorMessage.value = "密码必须是10位，且包含字母和特殊符号"
                _isAddingElder.value = false
                return@launch
            }
            
            // ⭐ 方案2：校验确认密码
            if (_elderConfirmPasswordInput.value != _elderPasswordInput.value) {
                _errorMessage.value = "两次输入的密码不一致"
                _isAddingElder.value = false
                return@launch
            }
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isAddingElder.value = false
                    return@launch
                }
                
                val request = RegisterElderRequest(
                    elderName = _elderNameInput.value.trim(),
                    elderIdCard = _elderIdCardInput.value.trim().uppercase(),
                    elderPhone = _elderPhoneInput.value.trim(),
                    nickname = if (_elderNicknameInput.value.isNotBlank()) _elderNicknameInput.value.trim() else null,  // ⭐ 方案2：昵称
                    password = _elderPasswordInput.value,  // ⭐ 方案2：密码
                    relationship = if (_relationshipInput.value.isNotBlank()) _relationshipInput.value.trim() else null
                )
                
                Log.d("ProfileViewModel", "📝 请求参数: $request")
                Log.d("ProfileViewModel", "🚀 调用 api.registerElder...")
                
                val response = api.registerElder(userId, request)
                Log.d("ProfileViewModel", "📥 收到响应: code=${response.code}, message=${response.message}, data=${response.data}")
                
                if (response.isSuccess()) {
                    Log.d("ProfileViewModel", "✅ 注册成功")
                    // ⭐ 优先使用后端返回的 data，其次使用 message
                    val successMsg = response.data ?: response.message ?: "长辈账号已创建成功"
                    _successMessage.value = successMsg
                    
                    // 清空所有输入
                    _elderNameInput.value = ""
                    _elderIdCardInput.value = ""
                    _elderPhoneInput.value = ""
                    _elderNicknameInput.value = ""  // ⭐ 方案2：清空昵称
                    _elderPasswordInput.value = ""  // ⭐ 方案2：清空密码
                    _elderConfirmPasswordInput.value = ""  // ⭐ 方案2：清空确认密码
                    _relationshipInput.value = ""
                    
                    // ⭐ 刷新用户信息
                    loadProfile()
                    
                    // ⭐ 新增：刷新"我的长辈"列表（因为注册成功后会自动绑定）
                    Log.d("ProfileViewModel", "🔄 开始刷新我的长辈列表...")
                    loadElderInfoList()
                    
                    Log.d("ProfileViewModel", "✅ 成功消息已设置: $_successMessage.value")
                } else {
                    Log.e("ProfileViewModel", "❌ 注册失败：${response.message}")
                    _errorMessage.value = response.message ?: "注册失败"
                    Log.d("ProfileViewModel", "❌ 错误消息已设置: $_errorMessage.value")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ registerElder exception", e)
                _errorMessage.value = e.message ?: "网络错误"
                Log.d("ProfileViewModel", "❌ 异常消息已设置: $_errorMessage.value")
            } finally {
                _isAddingElder.value = false
                Log.d("ProfileViewModel", "=== registerElder 执行完毕 ===")
            }
        }
    }
    
    /**
     * 绑定已有长辈账号（v2.0 新接口）⭐
     * POST /api/guard/bind-existing-elder
     */
    fun bindExistingElder() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "🚀 === 开始绑定已有长辈账号 ===")
            _isAddingElder.value = true
            _errorMessage.value = null
            _successMessage.value = null  // ⭐ 清空之前的消息
            
            // ⭐ 校验必填字段
            if (_elderPhoneInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈手机号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidPhone(_elderPhoneInput.value)) {
                _errorMessage.value = "长辈手机号格式不正确"
                _isAddingElder.value = false
                return@launch
            }
            
            if (_elderNameInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈姓名"
                _isAddingElder.value = false
                return@launch
            }
            
            if (_elderIdCardInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈身份证号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidIdCard(_elderIdCardInput.value)) {
                _errorMessage.value = "身份证号格式不正确（18位，支持最后一位为X）"
                _isAddingElder.value = false
                return@launch
            }
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isAddingElder.value = false
                    return@launch
                }
                
                val request = BindExistingElderRequest(
                    elderPhone = _elderPhoneInput.value.trim(),
                    elderName = _elderNameInput.value.trim(),
                    elderIdCard = _elderIdCardInput.value.trim().uppercase()
                )
                
                Log.d("ProfileViewModel", "📝 请求参数: $request")
                Log.d("ProfileViewModel", "🚀 调用 api.bindExistingElder...")
                
                val response = api.bindExistingElder(userId, request)
                Log.d("ProfileViewModel", "📥 收到响应: code=${response.code}, message=${response.message}, data=${response.data}")
                
                if (response.isSuccess()) {
                    Log.d("ProfileViewModel", "✅ 绑定成功")
                    // ⭐ 优先使用后端返回的 data，其次使用 message
                    val successMsg = response.data ?: response.message ?: "绑定成功"
                    _successMessage.value = successMsg
                    
                    // 清空所有输入
                    _elderNameInput.value = ""
                    _elderIdCardInput.value = ""
                    _elderPhoneInput.value = ""
                    _relationshipInput.value = ""
                    
                    // 刷新用户信息
                    loadProfile()
                    
                    // ⭐ 新增：刷新"我的长辈"列表
                    Log.d("ProfileViewModel", "🔄 开始刷新我的长辈列表...")
                    loadElderInfoList()
                    
                    Log.d("ProfileViewModel", "✅ 成功消息已设置: $_successMessage.value")
                } else {
                    Log.e("ProfileViewModel", "❌ 绑定失败：${response.message}")
                    _errorMessage.value = response.message ?: "绑定失败"
                    Log.d("ProfileViewModel", "❌ 错误消息已设置: $_errorMessage.value")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ bindExistingElder exception", e)
                _errorMessage.value = e.message ?: "网络错误"
                Log.d("ProfileViewModel", "❌ 异常消息已设置: $_errorMessage.value")
            } finally {
                _isAddingElder.value = false
                Log.d("ProfileViewModel", "=== bindExistingElder 执行完毕 ===")
            }
        }
    }
    
    /**
     * 添加长辈（旧接口，保留兼容）
     */
    fun addElder() {
        viewModelScope.launch {
            _isAddingElder.value = true
            _errorMessage.value = null
            
            // 校验必填字段
            if (_elderPhoneInput.value.isBlank()) {
                _errorMessage.value = "请输入长辈手机号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidPhone(_elderPhoneInput.value)) {
                _errorMessage.value = "长辈手机号格式不正确"
                _isAddingElder.value = false
                return@launch
            }
            
            // ⭐ 校验亲友姓名（必填）
            if (_guardianNameInput.value.isBlank()) {
                _errorMessage.value = "请输入亲友姓名"
                _isAddingElder.value = false
                return@launch
            }
            
            // ⭐ 校验亲友身份证号（必填，18位）
            if (_guardianIdCardInput.value.isBlank()) {
                _errorMessage.value = "请输入亲友身份证号"
                _isAddingElder.value = false
                return@launch
            }
            
            if (!isValidIdCard(_guardianIdCardInput.value)) {
                _errorMessage.value = "身份证号格式不正确（18位，支持最后一位为X）"
                _isAddingElder.value = false
                return@launch
            }
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isAddingElder.value = false
                    return@launch
                }
                
                val request = AddElderRequest(
                    elderPhone = _elderPhoneInput.value.trim(),
                    elderName = if (_elderNameInput.value.isNotBlank()) _elderNameInput.value.trim() else null,
                    guardianName = _guardianNameInput.value.trim(),
                    guardianIdCard = _guardianIdCardInput.value.trim().uppercase()
                )
                
                Log.d("ProfileViewModel", "开始添加长辈：长辈=${request.elderPhone}, 亲友=${request.guardianName}")
                Log.d("ProfileViewModel", "userId: $userId")
                Log.d("ProfileViewModel", "请求参数: $request")
                
                try {
                    Log.d("ProfileViewModel", "🚀 开始调用 api.addElder...")
                    val response = api.addElder(userId, request)
                    Log.d("ProfileViewModel", "📥 收到响应: code=${response.code}, message=${response.message}")
                    Log.d("ProfileViewModel", "isSuccess: ${response.isSuccess()}")
                    
                    if (response.isSuccess()) {
                        Log.d("ProfileViewModel", "✅ 添加长辈成功")
                        _successMessage.value = "添加成功，请通知长辈使用该手机号登录App"
                        // 清空所有输入
                        _elderPhoneInput.value = ""
                        _elderNameInput.value = ""
                        _guardianNameInput.value = ""
                        _guardianIdCardInput.value = ""
                        // 刷新用户信息
                        loadProfile()
                    } else {
                        Log.e("ProfileViewModel", "❌ 添加长辈失败：${response.message}")
                        _errorMessage.value = response.message ?: "添加失败"
                    }
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "❌ api.addElder 异常", e)
                    throw e  // 重新抛出，让外层 catch 捕获
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "addElder exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isAddingElder.value = false
            }
        }
    }

    /**
     * 一键解绑所有亲友
     */
    fun unbindAllGuardians() {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    _errorMessage.value = "用户未登录"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "开始一键解绑所有亲友")
                
                val response = api.unbindAllGuardians(userId)
                if (response.isSuccess()) {
                    _successMessage.value = "已解绑所有亲友"
                    loadProfile()
                } else {
                    _errorMessage.value = response.message ?: "解绑失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "unbindAllGuardians exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 获取我的亲友列表（长辈操作）
     * GET /api/guard/myGuardians - 后端返回列表
     */
    fun loadGuardianInfo() {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    Log.w("ProfileViewModel", "用户未登录，无法获取亲友列表")
                    return@launch
                }
                
                Log.d("ProfileViewModel", "开始获取亲友列表，userId=$userId")
                
                val response = api.getMyGuardians(userId)
                Log.d("ProfileViewModel", "API 响应：isSuccess=${response.isSuccess()}, data=${response.data}, message=${response.message}")
                
                if (response.isSuccess()) {
                    val dataList = response.data ?: emptyList()
                    _guardianInfoList.value = dataList
                    Log.d("ProfileViewModel", "✅ 亲友列表更新成功：${dataList.size} 个")
                    dataList.forEachIndexed { index, guardian ->
                        Log.d("ProfileViewModel", "  [$index] userId=${guardian.userId}, name=${guardian.name}, phone=${guardian.phone}, realName=${guardian.realName}")
                    }
                } else {
                    Log.w("ProfileViewModel", "❌ 获取亲友列表失败：${response.message}")
                    _guardianInfoList.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ loadGuardianInfo exception", e)
                _guardianInfoList.value = emptyList()
            }
        }
    }
    
    /**
     * ⭐ 新增：获取我的长辈列表（普通用户操作）
     * GET /api/guard/myElders
     */
    fun loadElderInfoList() {
        viewModelScope.launch {
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    Log.w("ProfileViewModel", "用户未登录，无法获取长辈列表")
                    return@launch
                }
                
                Log.d("ProfileViewModel", "开始获取长辈列表，userId=$userId")
                
                val response = api.getMyElders(userId)
                Log.d("ProfileViewModel", "API 响应：isSuccess=${response.isSuccess()}, data=${response.data}, message=${response.message}")
                
                if (response.isSuccess()) {
                    val dataList = response.data ?: emptyList()
                    _elderInfoList.value = dataList
                    Log.d("ProfileViewModel", "✅ 长辈列表更新成功：${dataList.size} 个")
                    dataList.forEachIndexed { index, elder ->
                        Log.d("ProfileViewModel", "  [$index] userId=${elder.userId}, name=${elder.name}, phone=${elder.phone}, status=${elder.status}")
                    }
                } else {
                    Log.w("ProfileViewModel", "❌ 获取长辈列表失败：${response.message}")
                    _elderInfoList.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ loadElderInfoList exception", e)
                _elderInfoList.value = emptyList()
            }
        }
    }
    
    /**
     * ⭐ 新增：解绑长辈（普通用户操作）
     * POST /api/guard/unbindOne/{guardId}
     */
    fun unbindElder(guardId: Long, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "🚀 === 开始解绑长辈 ===")
            _isAddingElder.value = true
            _errorMessage.value = null
            _successMessage.value = null
            
            try {
                val userId = tokenManager.getUserId()
                if (userId == null) {
                    onError("用户未登录")
                    _isAddingElder.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "📝 解绑参数: userId=$userId, guardId=$guardId")
                Log.d("ProfileViewModel", "🚀 调用 api.unbindOneGuardian...")
                
                val response = api.unbindOneGuardian(userId, guardId)
                Log.d("ProfileViewModel", "📥 收到响应: code=${response.code}, message=${response.message}")
                
                if (response.isSuccess()) {
                    Log.d("ProfileViewModel", "✅ 解绑成功")
                    _successMessage.value = "解绑成功"
                    
                    // 刷新长辈列表
                    loadProfile()
                    loadElderInfoList()
                    
                    Log.d("ProfileViewModel", "✅ 成功消息已设置: $_successMessage.value")
                    onSuccess()
                } else {
                    Log.e("ProfileViewModel", "❌ 解绑失败：${response.message}")
                    _errorMessage.value = response.message ?: "解绑失败"
                    onError(response.message ?: "解绑失败")
                    Log.d("ProfileViewModel", "❌ 错误消息已设置: $_errorMessage.value")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ unbindElder exception", e)
                _errorMessage.value = e.message ?: "网络错误"
                onError(e.message ?: "网络错误")
                Log.d("ProfileViewModel", "❌ 异常消息已设置: $_errorMessage.value")
            } finally {
                _isAddingElder.value = false
                Log.d("ProfileViewModel", "=== unbindElder 执行完毕 ===")
            }
        }
    }
    
    /**
     * ⭐ 新增：退出登录/切换账号
     */
    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "=== 开始退出登录 ===")
            try {
                // ⭐ 1. WebSocket 断开由 UI 层（MainActivity）处理
                // ProfileViewModel 只负责清除数据，不直接操作 WebSocket
                
                // ⭐ 2. 调用后端退出接口（清除后端缓存）- 使用超时保护
                try {
                    Log.d("ProfileViewModel", "📡 调用后端退出接口...")
                    kotlinx.coroutines.withTimeout(5000) {  // ⭐ 设置5秒超时
                        val response = api.logout()
                        if (response.isSuccess()) {
                            Log.d("ProfileViewModel", "✅ 后端退出成功")
                        } else {
                            Log.w("ProfileViewModel", "⚠️ 后端退出失败：${response.message}，继续清除本地数据")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "❌ 后端退出接口异常：${e.message}，继续清除本地数据", e)
                    // 即使后端接口失败，也要继续清除本地数据
                }
                
                // ⭐ 3. 清除本地 Token、user_id 和 guardMode
                tokenManager.clearToken()
                // ⭐ 修复：退出登录时需要显式清除 user_id 和 guardMode
                tokenManager.saveGuardMode(0)
                // ⭐ 新增：直接清除 user_id（clearToken 不再清除 user_id）
                val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove("user_id").apply()
                Log.d("ProfileViewModel", "已清除本地 Token、user_id 和 guardMode")
                
                // ⭐ 修复：退出登录时不切换图标，避免 Activity 重建
                // 图标将在下次启动时根据本地状态自动恢复
                // AppIconSwitcher.switchToDefaultIcon(context)  // 已注释
                Log.d("ProfileViewModel", "退出登录，保留当前图标状态")
                
                // ⭐ 4. 重置所有状态
                _profile.value = null
                _contacts.value = emptyList()
                _guardianInfoList.value = emptyList()
                _elderInfoList.value = emptyList()  // ⭐ 新增：清空长辈列表
                
                // ⭐ 5. 清空输入框
                _nicknameInput.value = ""
                _codeInput.value = ""
                _newPasswordInput.value = ""
                _realNameInput.value = ""
                _idCardInput.value = ""
                _contactNameInput.value = ""
                _contactPhoneInput.value = ""
                _elderPhoneInput.value = ""
                _elderNameInput.value = ""
                _elderIdCardInput.value = ""  // ⭐ 新增
                _elderNicknameInput.value = ""  // ⭐ 方案2：清空昵称
                _elderPasswordInput.value = ""  // ⭐ 方案2：清空密码
                _elderConfirmPasswordInput.value = ""  // ⭐ 方案2：清空确认密码
                _relationshipInput.value = ""  // ⭐ 新增
                _guardianNameInput.value = ""
                _guardianIdCardInput.value = ""
                
                Log.d("ProfileViewModel", "=== 退出登录成功 ===")
                
                // ⭐ 6. 调用回调，通知 UI 层跳转（UI 层负责重置 LoginViewModel）
                onLogoutComplete()
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "退出登录异常", e)
                _errorMessage.value = "退出登录失败：${e.message}"
                // ⭐ 即使异常也要尝试清除本地数据
                try {
                    tokenManager.clearToken()
                    // ⭐ 修复：退出登录时需要显式清除 user_id 和 guardMode
                    tokenManager.saveGuardMode(0)
                    val prefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().remove("user_id").apply()
                    // ⭐ 修复：异常时也不切换图标，避免 Activity 重建
                    // AppIconSwitcher.switchToDefaultIcon(context)  // 已注释
                    onLogoutComplete()
                } catch (e2: Exception) {
                    Log.e("ProfileViewModel", "清除本地数据也失败", e2)
                }
            }
        }
    }

    fun clearError() { _errorMessage.value = null }
    fun clearSuccess() { _successMessage.value = null }
    
    // ⭐ 新增：设置错误消息（供外部调用）
    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }
    
    // ⭐ 新增：修改昵称（简化版，直接传入参数）
    fun changeNickname(newNickname: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                if (newNickname.isBlank()) {
                    _errorMessage.value = "昵称不能为空"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val currentProfile = _profile.value ?: run {
                    _errorMessage.value = "未获取到用户信息"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                if (newNickname.trim() == currentProfile.nickname?.trim()) {
                    _errorMessage.value = "昵称没有变化"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val updatedProfile = UserProfile(
                    id = currentProfile.id,
                    phone = currentProfile.phone,
                    nickname = newNickname.trim(),
                    avatar = currentProfile.avatar,
                    realName = currentProfile.realName,
                    idCard = currentProfile.idCard,
                    verified = currentProfile.verified
                )
                
                val response = api.updateUserProfile(updatedProfile)
                if (response.isSuccess()) {
                    _successMessage.value = "昵称修改成功"
                    loadProfile()
                } else {
                    _errorMessage.value = response.message ?: "修改失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "changeNickname exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：修改密码（简化版，直接传入参数）
    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            try {
                if (oldPassword.isBlank() || newPassword.isBlank()) {
                    _errorMessage.value = "所有字段都不能为空"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                if (!isValidPassword(newPassword)) {
                    _errorMessage.value = "密码必须是10位，且包含字母和特殊符号"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val phone = _profile.value?.phone
                if (phone.isNullOrBlank()) {
                    _errorMessage.value = "未获取到手机号"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                // ⭐ 修复：必须使用用户输入的验证码，禁止硬编码
                val request = ChangePasswordRequest(
                    phone = phone,
                    code = _codeInput.value,  // ✅ 使用用户输入的验证码
                    newPassword = newPassword
                )
                
                val response = api.changePassword(request)
                if (response.isSuccess()) {
                    _successMessage.value = "密码修改成功"
                } else {
                    _errorMessage.value = response.message ?: "修改失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "changePassword exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：实名认证（简化版，直接传入参数）
    fun realNameAuth(realName: String, idCard: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            if (realName.isBlank()) {
                _errorMessage.value = "姓名不能为空"
                _isOperationLoading.value = false
                return@launch
            }
            
            if (idCard.isBlank()) {
                _errorMessage.value = "身份证号不能为空"
                _isOperationLoading.value = false
                return@launch
            }
            
            if (!isValidIdCard(idCard)) {
                _errorMessage.value = "身份证格式不正确"
                _isOperationLoading.value = false
                return@launch
            }
            
            try {
                val request = RealNameRequest(realName.trim(), idCard.trim())
                val response = api.realNameAuth(request)
                if (response.isSuccess()) {
                    _successMessage.value = "实名认证成功"
                    loadProfile()
                } else {
                    _errorMessage.value = response.message ?: "认证失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "realNameAuth exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }
    
    // ⭐ 新增：发送修改密码验证码
    fun sendVerifyCodeForPassword() {
        viewModelScope.launch {
            _isSendingCode.value = true
            _errorMessage.value = null
            
            try {
                val phone = _profile.value?.phone
                if (phone.isNullOrBlank()) {
                    _errorMessage.value = "未获取到手机号"
                    _isSendingCode.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "🚀 发送修改密码验证码到: $phone")
                val response = api.sendCode(phone)  // ⭐ 修复：使用 sendCode 而不是 sendVerifyCode
                
                if (response.isSuccess()) {
                    _successMessage.value = "验证码已发送，请注意查收"
                    Log.d("ProfileViewModel", "✅ 验证码发送成功")
                } else {
                    _errorMessage.value = response.message ?: "发送失败"
                    Log.e("ProfileViewModel", "❌ 验证码发送失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "sendVerifyCode exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isSendingCode.value = false
            }
        }
    }
    
    // ⭐ 新增：带验证码修改密码
    fun changePasswordWithCode(code: String, newPassword: String) {
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            
            try {
                if (code.isBlank()) {
                    _errorMessage.value = "请输入验证码"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                if (!isValidPassword(newPassword)) {
                    _errorMessage.value = "密码必须是10位，且包含字母和特殊符号"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val phone = _profile.value?.phone
                if (phone.isNullOrBlank()) {
                    _errorMessage.value = "未获取到手机号"
                    _isOperationLoading.value = false
                    return@launch
                }
                
                val request = ChangePasswordRequest(
                    phone = phone,
                    code = code,
                    newPassword = newPassword
                )
                
                Log.d("ProfileViewModel", "🚀 开始修改密码...")
                val response = api.changePassword(request)
                
                if (response.isSuccess()) {
                    _successMessage.value = "密码修改成功"
                    Log.d("ProfileViewModel", "✅ 密码修改成功")
                } else {
                    _errorMessage.value = response.message ?: "修改失败"
                    Log.e("ProfileViewModel", "❌ 密码修改失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "changePasswordWithCode exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isOperationLoading.value = false
            }
        }
    }
}