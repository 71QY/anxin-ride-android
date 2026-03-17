package com.example.myapplication.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.MyApplication
import com.example.myapplication.core.network.RetrofitClient
import com.example.myapplication.data.model.LoginRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val api = RetrofitClient.instance
    private val tokenManager = MyApplication.tokenManager

    // 输入字段
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code

    // 状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess

    fun updatePhone(phone: String) { _phone.value = phone }
    fun updateCode(code: String) { _code.value = code }

    fun sendCode() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.sendCode(_phone.value)
                if (!response.isSuccess()) {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络错误"
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
                val request = LoginRequest(_phone.value, _code.value)
                val response = api.login(request)
                if (response.isSuccess() && response.data != null) {
                    tokenManager.saveToken(response.data.token, response.data.userId)
                    _loginSuccess.value = true
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isLoading.value = false
            }
        }
    }
}