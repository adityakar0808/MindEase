package com.example.mindease.auth

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindease.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: AuthViewModel = viewModel(),
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: () -> Unit = {}
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var acceptTerms by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val errorMessage by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val navigateToLogin by viewModel.navigateToLogin.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Animation states
    val animationState = remember { Animatable(0f) }
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        animationState.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
    }

    // Password validation
    val isPasswordValid = password.length >= 6
    val doPasswordsMatch = password == confirmPassword && confirmPassword.isNotEmpty()
    val isEmailValid = email.contains("@") && email.contains(".")
    val isFormValid = name.isNotBlank() &&
            isEmailValid &&
            isPasswordValid &&
            doPasswordsMatch &&
            acceptTerms

    // Google Sign-In setup
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    viewModel.firebaseAuthWithGoogle(it)
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google sign-up failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Handle error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    // Handle success message and navigation
    LaunchedEffect(successMessage) {
        successMessage?.let {
            showSuccessDialog = true
            viewModel.clearMessages()
        }
    }

    // Handle navigation to login
    LaunchedEffect(navigateToLogin) {
        if (navigateToLogin) {
            viewModel.onNavigatedToLogin()
            onNavigateToLogin()
        }
    }

    // Handle successful Google sign-up
    LaunchedEffect(currentUser) {
        currentUser?.let {
            if (!it.isAnonymous) {
                onSignupSuccess()
            }
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Verification Email Sent!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "We've sent a verification email to:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF718096)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D3748)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Please check your email and click the verification link to activate your account. After verification, you can log in using your credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF718096)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToLogin()
                    }
                ) {
                    Text(
                        "Go to Login",
                        color = Color(0xFF764ba2),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSuccessDialog = false }
                ) {
                    Text(
                        "OK",
                        color = Color(0xFF718096)
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF764ba2),
                        Color(0xFF667eea),
                        Color(0xFF2D3748)
                    ),
                    radius = 1200f
                )
            )
    ) {
        // Animated background elements
        FloatingElement(
            modifier = Modifier
                .offset((-70).dp, (-30).dp)
                .alpha(pulseAlpha),
            size = 220.dp
        )

        FloatingElement(
            modifier = Modifier
                .offset(280.dp, 120.dp)
                .alpha(pulseAlpha * 0.8f),
            size = 180.dp
        )

        FloatingElement(
            modifier = Modifier
                .offset((-40).dp, 500.dp)
                .alpha(pulseAlpha * 0.6f),
            size = 140.dp
        )

        FloatingElement(
            modifier = Modifier
                .offset(200.dp, 600.dp)
                .alpha(pulseAlpha * 0.4f),
            size = 100.dp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .scale(animationState.value)
                .alpha(animationState.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Login",
                        tint = Color.White
                    )
                }
            }

            // App logo and title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color.White, Color(0xFFF0F9FF))
                                ),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "MindEase",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "Begin your wellness journey",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Main card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Welcome text
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF2D3748),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Join us and start your mindful journey",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF718096),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Form fields
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(600)
                        ) + fadeIn(animationSpec = tween(600))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Name field
                            EnhancedTextField(
                                value = name,
                                onValueChange = { name = it.trim() },
                                label = "Full Name",
                                leadingIcon = Icons.Default.Person,
                                isValid = name.isNotBlank(),
                                supportingText = if (name.isEmpty()) {
                                    "Please enter your full name"
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Email field
                            EnhancedTextField(
                                value = email,
                                onValueChange = { email = it.trim().lowercase() },
                                label = "Email Address",
                                leadingIcon = Icons.Default.Email,
                                isValid = isEmailValid,
                                supportingText = if (email.isNotEmpty() && !isEmailValid) {
                                    "Please enter a valid email address"
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Password field
                            EnhancedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                leadingIcon = Icons.Default.Lock,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible }
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                            tint = Color(0xFF764ba2)
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                isValid = isPasswordValid,
                                supportingText = if (password.isNotEmpty() && !isPasswordValid) {
                                    "Password must be at least 6 characters"
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Confirm Password field
                            EnhancedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = "Confirm Password",
                                leadingIcon = Icons.Default.Lock,
                                trailingIcon = {
                                    IconButton(
                                        onClick = { confirmPasswordVisible = !confirmPasswordVisible }
                                    ) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                            tint = Color(0xFF764ba2)
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                isValid = doPasswordsMatch,
                                supportingText = if (confirmPassword.isNotEmpty() && !doPasswordsMatch) {
                                    "Passwords do not match"
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Password strength indicator
                    if (password.isNotEmpty()) {
                        PasswordStrengthIndicator(password = password)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Terms and conditions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptTerms,
                            onCheckedChange = { acceptTerms = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF764ba2)
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "I agree to the Terms of Service and Privacy Policy",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF718096),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Signup button
                    EnhancedButton(
                        text = "Create Account",
                        onClick = {
                            if (isFormValid) {
                                viewModel.signup(email, password, name)
                            }
                        },
                        isLoading = isLoading,
                        enabled = isFormValid,
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF764ba2)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Divider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        Text(
                            text = "or",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color(0xFF718096)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Google Sign-Up button
                    Button(
                        onClick = {
                            val signInIntent: Intent = googleSignInClient.signInIntent
                            googleSignInLauncher.launch(signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF2D3748)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        // Google icon placeholder
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4285F4),
                                            Color(0xFFEA4335),
                                            Color(0xFFFBBC05),
                                            Color(0xFF34A853)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Sign up with Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login link
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Already have an account? ",
                            color = Color(0xFF718096)
                        )
                        TextButton(
                            onClick = onNavigateToLogin,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Sign In",
                                color = Color(0xFF764ba2),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun FloatingElement(
    modifier: Modifier = Modifier,
    size: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                Color.White.copy(alpha = 0.08f),
                CircleShape
            )
            .blur(18.dp)
    )
}

@Composable
private fun EnhancedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isValid: Boolean = true,
    supportingText: String? = null
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (value.isNotEmpty()) {
                        if (isValid) Color(0xFF10B981) else Color(0xFFEF4444)
                    } else Color(0xFF718096)
                )
            },
            trailingIcon = if (value.isNotEmpty() && trailingIcon == null) {
                {
                    Icon(
                        imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isValid) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else trailingIcon,
            visualTransformation = visualTransformation,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF764ba2),
                focusedLabelColor = Color(0xFF764ba2),
                unfocusedBorderColor = if (value.isNotEmpty()) {
                    if (isValid) Color(0xFF10B981) else Color(0xFFEF4444)
                } else Color(0xFFE2E8F0),
                unfocusedLabelColor = Color(0xFF718096)
            ),
            isError = value.isNotEmpty() && !isValid,
            singleLine = true
        )

        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun EnhancedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    backgroundColor: Color = Color(0xFF764ba2)
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Creating Account...",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(password: String) {
    val strength = calculatePasswordStrength(password)
    val strengthColor = when (strength) {
        in 0..2 -> Color(0xFFEF4444) // Red - Weak
        in 3..4 -> Color(0xFFF59E0B) // Yellow - Medium
        else -> Color(0xFF10B981) // Green - Strong
    }
    val strengthText = when (strength) {
        in 0..2 -> "Weak"
        in 3..4 -> "Medium"
        else -> "Strong"
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Strength: $strengthText",
                style = MaterialTheme.typography.bodySmall,
                color = strengthColor,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { (strength / 5f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = strengthColor,
            trackColor = Color(0xFFE2E8F0)
        )
    }
}

private fun calculatePasswordStrength(password: String): Int {
    var strength = 0

    if (password.length >= 8) strength++
    if (password.any { it.isUpperCase() }) strength++
    if (password.any { it.isLowerCase() }) strength++
    if (password.any { it.isDigit() }) strength++
    if (password.any { !it.isLetterOrDigit() }) strength++

    return strength
}