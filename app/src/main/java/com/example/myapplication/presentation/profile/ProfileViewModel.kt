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
import com.example.myapplication.data.model.ChangePasswordRequest
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.RealNameRequest
import com.example.myapplication.data.model.UserProfile
import com.example.myapplication.domain.repository.IOrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val api: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    // 个人资料数据及加载状态
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    // 紧急联系人列表及加载状态
    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _isContactsLoading = MutableStateFlow(false)
    val isContactsLoading: StateFlow<Boolean> = _isContactsLoading.asStateFlow()

    // 操作状态（添加联系人、修改昵称时的独立加载状态）
    private val _isOperationLoading = MutableStateFlow(false)
    val isOperationLoading: StateFlow<Boolean> = _isOperationLoading.asStateFlow()

    // 全局错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 全局成功消息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // 发送验证码状态
    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    // 修改昵称输入
    private val _nicknameInput = MutableStateFlow("")
    val nicknameInput: StateFlow<String> = _nicknameInput.asStateFlow()

    // 修改密码输入
    private val _codeInput = MutableStateFlow("")
    val codeInput: StateFlow<String> = _codeInput.asStateFlow()

    private val _newPasswordInput = MutableStateFlow("")
    val newPasswordInput: StateFlow<String> = _newPasswordInput.asStateFlow()

    // 实名认证输入
    private val _realNameInput = MutableStateFlow("")
    val realNameInput: StateFlow<String> = _realNameInput.asStateFlow()

    private val _idCardInput = MutableStateFlow("")
    val idCardInput: StateFlow<String> = _idCardInput.asStateFlow()

    // 紧急联系人输入
    private val _contactNameInput = MutableStateFlow("")
    val contactNameInput: StateFlow<String> = _contactNameInput.asStateFlow()

    private val _contactPhoneInput = MutableStateFlow("")
    val contactPhoneInput: StateFlow<String> = _contactPhoneInput.asStateFlow()

    init {
        Log.d("ProfileViewModel", "init called")
        // ⭐ 修改：延迟加载，确保 Token 已准备好
        viewModelScope.launch {
            delay(1000)  // 增加到 1 秒延迟
            loadProfile()
            loadEmergencyContacts()
        }
    }

    // ⭐ 修改：密码格式验证 - 至少 10 位数，必须包含字母和特殊符号
    fun isValidPassword(password: String): Boolean {
        if (password.length < 10) return false
        val hasLetter = password.any { it.isLetter() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        return hasLetter && hasSymbol
    }

    // 身份证格式验证
    fun isValidIdCard(idCard: String): Boolean {
        val regex = Regex("^[1-9]\\d{5}(18|19|20)?\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}(\\d|X|x)$")
        return regex.matches(idCard)
    }

    /**
     * 等待 Token 就绪（最多尝试 5 次，每次间隔 300ms）
     */
    private suspend fun waitForToken(): String? {
        var token = tokenManager.getTokenSync()
        var attempts = 0
        val maxAttempts = 5
        
        Log.d("ProfileViewModel", "第 1 次获取 Token: ${if (token != null) "exists" else "null"}")
        
        while (token.isNullOrBlank() && attempts < maxAttempts) {
            delay(300)
            token = tokenManager.getTokenSync()
            attempts++
            Log.d("ProfileViewModel", "等待 Token，尝试 $attempts/$maxAttempts, token=${if (token != null) "exists" else "null"}")
        }
        
        if (token.isNullOrBlank()) {
            Log.e("ProfileViewModel", "❌ Token 为空")
            return null
        }
        
        Log.d("ProfileViewModel", "✅ 获取到 Token，长度：${token.length}")
        return token
    }

    /**
     * 加载个人资料
     */
    fun loadProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "=== loadProfile 开始执行 ===")
            _isProfileLoading.value = true
            _errorMessage.value = null
            try {
                // ⭐ 修改：使用封装的方法等待 Token
                val token = waitForToken()
                if (token.isNullOrBlank()) {
                    _errorMessage.value = "请先登录"
                    _isProfileLoading.value = false
                    return@launch
                }
                
                Log.d("ProfileViewModel", "开始调用 getUserProfile API")
                
                val response = api.getUserProfile()
                Log.d("ProfileViewModel", "getUserProfile 响应:")
                Log.d("ProfileViewModel", "  code=${response.code}")
                Log.d("ProfileViewModel", "  message=${response.message}")
                
                // ⭐ Bug 修复：捕获可能的类型转换异常
                val profileData = try {
                    response.data
                } catch (e: ClassCastException) {
                    Log.e("ProfileViewModel", "❌ 类型转换失败，data 可能不是 UserProfile 类型", e)
                    null
                } catch (e: Exception) {
                    Log.e("ProfileViewModel", "❌ 解析 data 失败", e)
                    null
                }
                
                if (profileData == null) {
                    Log.e("ProfileViewModel", "❌ data 解析失败，可能 JSON 格式不匹配")
                    _errorMessage.value = "数据格式错误，请联系管理员检查接口"
                    _isProfileLoading.value = false
                    return@launch
                }
                
                if (response.isSuccess()) {
                    _profile.value = profileData
                    _nicknameInput.value = profileData?.nickname ?: ""
                    Log.d("ProfileViewModel", "✅ 用户信息加载成功")
                    Log.d("ProfileViewModel", "  ID: ${profileData?.id}")
                    Log.d("ProfileViewModel", "  手机号：${profileData?.phone}")
                    Log.d("ProfileViewModel", "  昵称：${profileData?.nickname}")
                    Log.d("ProfileViewModel", "  头像：${profileData?.avatar}")
                    Log.d("ProfileViewModel", "  实名：${profileData?.realName}")
                    Log.d("ProfileViewModel", "  认证状态：verified=${profileData?.verified}")
                } else {
                    Log.e("ProfileViewModel", "❌ loadProfile 失败：${response.message}")
                    _errorMessage.value = response.message ?: "加载失败"
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "❌ loadProfile 异常", e)
                // ⭐ Bug 修复：打印完整的异常堆栈
                _errorMessage.value = "${e.message ?: "网络错误"}\n请检查后端接口返回的数据格式"
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
                val phone = tokenManager.getTokenSync()?.let { 
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
        viewModelScope.launch {
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                // ⭐ 修改：先压缩图片，然后自动适配头像大小
                val compressedFile = compressImage(file)
                
                // ⭐ TODO：这里可以添加手动裁剪功能，使用图片编辑库
                // 目前先使用自动压缩到 200x200
                
                val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData(
                    "avatar",
                    compressedFile.name,
                    requestBody
                )
                
                Log.d("ProfileViewModel", "开始上传头像，文件大小：${compressedFile.length() / 1024}KB")
                
                val response = api.uploadAvatar(filePart)
                Log.d("ProfileViewModel", "uploadAvatar response: code=${response.code}, message=${response.message}")
                
                if (response.isSuccess()) {
                    // ⭐ 修改：处理后端返回的数据（兼容字符串和对象）
                    val dataObj: Any? = response.data
                    val avatarUrl: String? = when {
                        dataObj is String -> {
                            // 如果 data 是字符串，直接作为 URL
                            Log.d("ProfileViewModel", "✅ data 是字符串: $dataObj")
                            dataObj
                        }
                        dataObj is Map<*, *> -> {
                            // 如果 data 是对象，提取 avatarUrl 字段
                            @Suppress("UNCHECKED_CAST")
                            val dataMap = dataObj as? Map<String, Any>
                            val url = dataMap?.get("avatarUrl") as? String
                            Log.d("ProfileViewModel", "✅ data 是对象，avatarUrl: $url")
                            url
                        }
                        else -> {
                            Log.e("ProfileViewModel", "❌ data 类型未知: ${dataObj?.javaClass}")
                            null
                        }
                    }
                    
                    if (!avatarUrl.isNullOrBlank()) {
                        Log.d("ProfileViewModel", "✅ 头像上传成功：$avatarUrl")
                        _successMessage.value = "头像上传成功"
                        
                        // ⭐ 新增：直接更新本地头像，立即显示
                        val currentProfile = _profile.value
                        if (currentProfile != null) {
                            val updatedProfile = currentProfile.copy(avatar = avatarUrl)
                            _profile.value = updatedProfile
                            Log.d("ProfileViewModel", "✅ 本地头像已更新：$avatarUrl")
                        }
                        
                        // ⭐ 重新加载个人资料，确保数据同步
                        loadProfile()
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

    // ⭐ 新增：压缩图片（仿照 B 站头像规则）
    private suspend fun compressImage(sourceFile: File): File = withContext(Dispatchers.IO) {
        try {
            // ⭐ 读取原始图片
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            
            // ⭐ Bug 5: 添加 bitmap null 检查
            if (bitmap == null || bitmap.isRecycled) {
                Log.e("ProfileViewModel", "❌ 图片解码失败")
                return@withContext sourceFile
            }
            
            // ⭐ B 站头像规则：最大 200x200，质量 85%
            val maxSize = 200
            val quality = 85
            
            var width = bitmap.width
            var height = bitmap.height
            
            // ⭐ 计算缩放比例
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
            
            // ⭐ 压缩并保存到新文件
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            // ⭐ Bug 6: 确保父目录存在
            val parentDir = sourceFile.parentFile ?: File(sourceFile.parent)
            val compressedFile = File(parentDir, 
                "compressed_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(compressedFile)
            fos.write(outputStream.toByteArray())
            fos.close()
            
            Log.d("ProfileViewModel", "图片压缩完成：${sourceFile.length() / 1024}KB -> ${compressedFile.length() / 1024}KB")
            
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

    // ⭐ 新增：手机号格式验证
    fun isValidPhone(phone: String): Boolean {
        val regex = Regex("^1[3-9]\\d{9}$")
        return regex.matches(phone)
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

    fun clearError() { _errorMessage.value = null }
    fun clearSuccess() { _successMessage.value = null }
}