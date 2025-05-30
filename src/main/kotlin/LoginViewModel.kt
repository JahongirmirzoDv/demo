import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

sealed class LoginResult {
    object Success : LoginResult()
    data class Error(val exception: Throwable) : LoginResult()
    object Loading : LoginResult()
    object Idle : LoginResult()
}

class LoginViewModel(
    private val supabaseClient: SupabaseClient
) : ViewModel() {
    private val logger: Logger = LogManager.getLogger(LoginViewModel::class.java)

    private val _loginState = MutableStateFlow<LoginResult>(LoginResult.Idle)
    val loginState: StateFlow<LoginResult> = _loginState

    val sessionStatus = supabaseClient.auth.sessionStatus
    val loginAlert = MutableStateFlow<String?>(null)
    val statusFlow = supabaseClient.auth.mfa.statusFlow

    fun login(email: String, password: String) {
        _loginState.value = LoginResult.Loading
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }.onSuccess { 
                logger.info("Login successful for user: $email")
                _loginState.value = LoginResult.Success
            }.onFailure { exception ->
                logger.error("Login failed for user: $email", exception)
                _loginState.value = LoginResult.Error(exception)
                loginAlert.value = "There was an error while logging in. Check your credentials and try again."
            }
        }
    }
}
