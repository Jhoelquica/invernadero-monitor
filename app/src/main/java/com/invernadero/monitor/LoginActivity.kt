package com.invernadero.monitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var rootView: View
    private lateinit var loadingOverlay: View
    private lateinit var googleSignInButton: Button

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnEmailAction: MaterialButton
    private lateinit var tvToggleMode: TextView
    private lateinit var tvForgotPassword: TextView

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            goToMain()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        credentialManager = CredentialManager.create(this)
        bindViews()

        googleSignInButton.setOnClickListener { launchGoogleSignIn() }
        btnEmailAction.setOnClickListener { onEmailActionClicked() }
        tvToggleMode.setOnClickListener { toggleMode() }
        tvForgotPassword.setOnClickListener { sendPasswordReset() }

        updateModeUi()
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            goToMain()
        }
    }

    private fun bindViews() {
        rootView = findViewById(R.id.login_root)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        googleSignInButton = findViewById(R.id.btnGoogleSignIn)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnEmailAction = findViewById(R.id.btnEmailAction)
        tvToggleMode = findViewById(R.id.tvToggleMode)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    // ---------- Correo y contraseña ----------

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        updateModeUi()
    }

    private fun updateModeUi() {
        btnEmailAction.setText(if (isRegisterMode) R.string.login_btn_register else R.string.login_btn_signin)
        tvToggleMode.setText(if (isRegisterMode) R.string.login_toggle_to_signin else R.string.login_toggle_to_register)
        tvForgotPassword.visibility = if (isRegisterMode) View.INVISIBLE else View.VISIBLE
    }

    private fun onEmailActionClicked() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val password = etPassword.text?.toString().orEmpty()
        if (!validateForm(email, password)) return

        setLoading(true)
        val task = if (isRegisterMode) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }
        task.addOnCompleteListener(this) { result ->
            setLoading(false)
            if (result.isSuccessful) {
                goToMain()
            } else {
                Log.e(TAG, "Falló autenticación con correo/contraseña", result.exception)
                showMessage(mapAuthError(result.exception))
            }
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        tilEmail.error = null
        tilPassword.error = null
        var valid = true

        if (email.isEmpty()) {
            tilEmail.error = getString(R.string.login_error_email_required)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.login_error_email_invalid)
            valid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = getString(R.string.login_error_password_required)
            valid = false
        } else if (password.length < 6) {
            tilPassword.error = getString(R.string.login_error_password_short)
            valid = false
        }

        return valid
    }

    private fun sendPasswordReset() {
        val email = etEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = getString(R.string.login_error_email_invalid)
            return
        }
        tilEmail.error = null

        setLoading(true)
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this) { result ->
            setLoading(false)
            if (result.isSuccessful) {
                showMessage(getString(R.string.login_reset_sent, email))
            } else {
                showMessage(mapAuthError(result.exception))
            }
        }
    }

    private fun mapAuthError(exception: Exception?): String = when (exception) {
        is FirebaseAuthInvalidUserException -> getString(R.string.auth_error_user_not_found)
        is FirebaseAuthInvalidCredentialsException -> getString(R.string.auth_error_invalid_credentials)
        is FirebaseAuthUserCollisionException -> getString(R.string.auth_error_email_in_use)
        is FirebaseAuthWeakPasswordException -> getString(R.string.auth_error_weak_password)
        is FirebaseNetworkException -> getString(R.string.auth_error_network)
        is FirebaseAuthException -> if (exception.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
            getString(R.string.auth_error_not_enabled)
        } else {
            getString(R.string.login_error_generic)
        }
        else -> getString(R.string.login_error_generic)
    }

    // ---------- Google Sign-In ----------

    private fun launchGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = this@LoginActivity,
                    request = request
                )
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                setLoading(false)
                Log.e(TAG, "Error al obtener credencial", e)
                showMessage(getString(R.string.login_error, e.message))
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
        } else {
            setLoading(false)
            showMessage(getString(R.string.login_error_unsupported_credential))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    goToMain()
                } else {
                    Log.e(TAG, "Falló la autenticación con Firebase", task.exception)
                    showMessage(getString(R.string.login_error_generic))
                }
            }
    }

    // ---------- Comunes ----------

    private fun showMessage(message: String) {
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun setLoading(loading: Boolean) {
        loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        googleSignInButton.isEnabled = !loading
        btnEmailAction.isEnabled = !loading
        tvToggleMode.isEnabled = !loading
        tvForgotPassword.isEnabled = !loading
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        applyFadeTransition()
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
