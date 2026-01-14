package com.example.konnichat.ui.auth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.example.konnichat.App
import com.example.konnichat.R
import com.example.konnichat.core.state.Resource
import com.example.konnichat.databinding.ActivityRegisterBinding
import com.example.konnichat.ui.base.BaseActivity

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val viewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as App).authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.tvLoginLink.setOnClickListener {
            finish() // Quay lại Login
        }

        binding.btnSignUp.setOnClickListener {
            val name = binding.etSignUpName.text.toString().trim()
            val email = binding.etSignUpEmail.text.toString().trim()
            val pass = binding.etSignUpPassword.text.toString().trim()
            val confirmPass = binding.etSignUpConfirmPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_input_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(this, getString(R.string.error_password_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Gọi ViewModel
            viewModel.register(name, email, pass)
        }
    }

    private fun setupObservers() {
        viewModel.registerState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnSignUp.isEnabled = false
                    binding.btnSignUp.text = getString(R.string.action_register_loading)
                }
                is Resource.Success -> {
                    binding.btnSignUp.isEnabled = true
                    binding.btnSignUp.text = getString(R.string.action_register)

                    Toast.makeText(this, getString(R.string.msg_register_success), Toast.LENGTH_LONG).show()
                    finish() // Đăng ký xong thì quay về Login để người dùng đăng nhập
                }
                is Resource.Error -> {
                    binding.btnSignUp.isEnabled = true
                    binding.btnSignUp.text = getString(R.string.action_register)
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
