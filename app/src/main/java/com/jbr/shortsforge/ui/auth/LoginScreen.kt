package com.jbr.shortsforge.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.currentUser) {
        if (state.currentUser != null) onAuthSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF121212)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("ShortsForge", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34C759))
            Text(
                if (isRegisterMode) "Create your account" else "Welcome back",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(4.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        focusManager.clearFocus()
                        if (!isRegisterMode) viewModel.signIn(email, password)
                    }
                ),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            // Confirm password (register only)
            if (isRegisterMode) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && confirmPassword != password)
                            Text("Passwords don't match", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
            }

            // Error
            state.error?.let { err ->
                Text(
                    err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }

            val canSubmit = email.contains("@") && password.length >= 6 &&
                    (!isRegisterMode || confirmPassword == password)

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.clearError()
                    if (isRegisterMode) viewModel.register(email, password)
                    else viewModel.signIn(email, password)
                },
                enabled = canSubmit && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (isRegisterMode) "Create Account" else "Sign In",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                    )
                }
            }

            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                viewModel.clearError()
                confirmPassword = ""
            }) {
                Text(
                    if (isRegisterMode) "Already have an account? Sign in"
                    else "Don't have an account? Register",
                    color = Color(0xFF34C759), fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor       = Color(0xFF34C759),
    focusedLabelColor        = Color(0xFF34C759),
    focusedLeadingIconColor  = Color(0xFF34C759),
    unfocusedBorderColor     = Color.White.copy(alpha = 0.2f),
    unfocusedLabelColor      = Color.White.copy(alpha = 0.5f),
    cursorColor              = Color(0xFF34C759),
    focusedTextColor         = Color.White,
    unfocusedTextColor       = Color.White
)
