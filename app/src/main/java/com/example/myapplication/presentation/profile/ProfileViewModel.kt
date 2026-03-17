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

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        Log.d("ProfileViewModel", "init called")
        loadProfile()
        loadEmergencyContacts()
    }

    fun loadProfile() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loadProfile started")
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.getUserProfile()
                Log.d("ProfileViewModel", "getUserProfile response: $response")
                if (response.isSuccess()) {
                    Log.d("ProfileViewModel", "profile data: ${response.data}")
                    _profile.value = response.data
                } else {
                    Log.e("ProfileViewModel", "loadProfile error: ${response.message}")
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "loadProfile exception", e)
                _errorMessage.value = e.message ?: "网络错误"
            } finally {
                _isLoading.value = false
                Log.d("ProfileViewModel", "loadProfile finished")
            }
        }
    }

    fun loadEmergencyContacts() {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "loadEmergencyContacts started")
            try {
                val response = api.getEmergencyContacts()
                Log.d("ProfileViewModel", "getEmergencyContacts response: $response")
                if (response.isSuccess()) {
                    _contacts.value = response.data ?: emptyList()
                    Log.d("ProfileViewModel", "contacts: ${_contacts.value}")
                } else {
                    Log.e("ProfileViewModel", "loadEmergencyContacts error: ${response.message}")
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "loadEmergencyContacts exception", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun addEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            Log.d("ProfileViewModel", "addEmergencyContact: $name, $phone")
            _isLoading.value = true
            try {
                val contact = EmergencyContact(name = name, phone = phone)
                val response = api.addEmergencyContact(contact)
                Log.d("ProfileViewModel", "addEmergencyContact response: $response")
                if (response.isSuccess()) {
                    loadEmergencyContacts()
                } else {
                    _errorMessage.value = response.message
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "addEmergencyContact exception", e)
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}