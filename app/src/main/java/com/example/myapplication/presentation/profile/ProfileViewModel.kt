package com.example.myapplication.presentation.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.network.RetrofitClient
import com.example.myapplication.data.model.EmergencyContact
import com.example.myapplication.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val api = RetrofitClient.instance

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

    init {
        Log.d("ProfileViewModel", "init called")
        loadProfile()
        loadEmergencyContacts()
    }

    /**
     * 加载个人资料
     */
    fun loadProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loadProfile started")
            _isProfileLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.getUserProfile()
                Log.d("ProfileViewModel", "getUserProfile response: $response")
                if (response.isSuccess()) {
                    _profile.value = response.data
                } else {
                    Log.e("ProfileViewModel", "loadProfile error: ${response.message}")
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "loadProfile exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isProfileLoading.value = false
                Log.d("ProfileViewModel", "loadProfile finished")
            }
        }
    }

    /**
     * 加载紧急联系人列表
     */
    fun loadEmergencyContacts() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loadEmergencyContacts started")
            _isContactsLoading.value = true
            try {
                val response = api.getEmergencyContacts()
                Log.d("ProfileViewModel", "getEmergencyContacts response: $response")
                if (response.isSuccess()) {
                    _contacts.value = response.data ?: emptyList()
                } else {
                    Log.e("ProfileViewModel", "loadEmergencyContacts error: ${response.message}")
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "loadEmergencyContacts exception", e)
                _errorMessage.value = e.message
            } finally {
                _isContactsLoading.value = false
            }
        }
    }

    /**
     * 添加紧急联系人
     */
    fun addEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "addEmergencyContact: $name, $phone")
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                val contact = EmergencyContact(name = name, phone = phone)
                val response = api.addEmergencyContact(contact)
                Log.d("ProfileViewModel", "addEmergencyContact response: $response")
                if (response.isSuccess()) {
                    // 添加成功后重新加载联系人列表
                    loadEmergencyContacts()
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "addEmergencyContact exception", e)
                _errorMessage.value = e.message
            } finally {
                _isOperationLoading.value = false
            }
        }
    }

    /**
     * 修改昵称
     */
    fun updateNickname(newNickname: String) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "updateNickname: $newNickname")
            _isOperationLoading.value = true
            _errorMessage.value = null
            try {
                val currentProfile = _profile.value
                if (currentProfile == null) {
                    _errorMessage.value = "当前用户资料为空"
                    return@launch
                }
                val updatedProfile = currentProfile.copy(nickname = newNickname)
                val response = api.updateUserProfile(updatedProfile)
                Log.d("ProfileViewModel", "updateUserProfile response: $response")
                if (response.isSuccess()) {
                    // 更新本地数据
                    _profile.value = updatedProfile
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "updateNickname exception", e)
                _errorMessage.value = e.message
            } finally {
                _isOperationLoading.value = false
            }
        }
    }
}