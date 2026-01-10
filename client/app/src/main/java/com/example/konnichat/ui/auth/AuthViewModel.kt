package com.example.konnichat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.example.konnichat.core.state.Resource
import com.example.konnichat.core.utils.ValidationUtils
import com.example.konnichat.data.remote.dto.UserDto
import com.example.konnichat.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    // LiveData cho trạng thái Kết nối (Dùng ở Splash)
    private val _connectState = MutableLiveData<Resource<Boolean>>()
    val connectState: LiveData<Resource<Boolean>> = _connectState

    // LiveData cho trạng thái Login
    private val _loginState = MutableLiveData<Resource<UserDto>>()
    val loginState: LiveData<Resource<UserDto>> = _loginState

    // LiveData cho trạng thái Register
    private val _registerState = MutableLiveData<Resource<Boolean>>()
    val registerState: LiveData<Resource<Boolean>> = _registerState

    private val _logoutState = MutableLiveData<Resource<Boolean>>()
    val logoutState: LiveData<Resource<Boolean>> = _logoutState

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

        if (!ValidationUtils.isValidName(name)) {
            _registerState.value = Resource.Error("Tên không hợp lệ (Tối đa 63 ký tự)")
            return
        }

        // 2. Validate Email
        if (!ValidationUtils.isValidEmail(email)) {
            _registerState.value = Resource.Error("Email không đúng định dạng hoặc quá dài")
            return
        }

        // 3. Validate Password
        if (!ValidationUtils.isValidPassword(pass)) {
            _registerState.value = Resource.Error("Mật khẩu ít nhất 8 ký tự, gồm chữ hoa, thường, số và ký tự đặc biệt")
            return
        }

        _registerState.value = Resource.Loading()
        viewModelScope.launch {
            val result = repository.register(name, email, pass)
            _registerState.value = result
        }
    }

    fun logout() {
        _logoutState.value = Resource.Loading()
        viewModelScope.launch {
            repository.logout()
            _logoutState.value = Resource.Success(true)
        }
    }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}