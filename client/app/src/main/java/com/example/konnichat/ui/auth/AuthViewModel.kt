package com.example.konnichat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.konnichat.core.state.Resource
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    // LiveData cho trạng thái Kết nối (Dùng ở Splash)
    private val _connectState = MutableLiveData<Resource<Boolean>>()
    val connectState: LiveData<Resource<Boolean>> = _connectState

    // LiveData cho trạng thái Login
    private val _loginState = MutableLiveData<Resource<UserDto>>()
    val loginState: LiveData<Resource<UserDto>> = _loginState

    // LiveData cho trạng thái Register
    private val _registerState = MutableLiveData<Resource<Boolean>>()
    val registerState: LiveData<Resource<Boolean>> = _registerState

    // Gọi hàm kết nối
    fun connectDefault() {
        _connectState.value = Resource.Loading()
        viewModelScope.launch {
            val result = repository.connectToServer()
            _connectState.value = result
        }
    }

    // Gọi hàm đăng nhập
    fun login(email: String, pass: String) {
        // Kiểm tra dữ liệu đầu vào cơ bản
        if (email.isBlank() || pass.isBlank()) {
            _loginState.value = Resource.Error("Vui lòng nhập đầy đủ thông tin")
            return
        }

        _loginState.value = Resource.Loading()
        viewModelScope.launch {
            val result = repository.login(email, pass)
            _loginState.value = result
        }
    }

    // Gọi hàm đăng ký
    fun register(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _registerState.value = Resource.Error("Vui lòng nhập đầy đủ thông tin")
            return
        }

        _registerState.value = Resource.Loading()
        viewModelScope.launch {
            val result = repository.register(name, email, pass)
            _registerState.value = result
        }
    }
}