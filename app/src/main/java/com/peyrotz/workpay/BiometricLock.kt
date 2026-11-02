package com.peyrotz.workpay

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * בדיקה האם ניתן להשתמש בטביעת אצבע במכשיר הזה.
 * מחזיר false אם אין חומרה או אם החומרה לא מגיבה (מונע קריסה).
 */
object BiometricHelper {
    fun canUse(ctx: Context): Boolean {
        return try {
            val bm = BiometricManager.from(ctx)
            bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun LockScreen(
    primary: Color,
    onUnlocked: () -> Unit
) {
    val ctx = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempting by remember { mutableStateOf(false) }

    fun triggerAuth() {
        val activity = ctx.findFragmentActivity()
        if (activity == null) {
            // אין Activity - פותחים בכל מקרה כדי לא לתקוע
            onUnlocked()
            return
        }
        attempting = true
        val executor = ContextCompat.getMainExecutor(ctx)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    attempting = false
                    errorMessage = null
                    onUnlocked()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    attempting = false
                    // ביטול מצד המשתמש - לא נפתח, אבל לא מציגים שגיאה
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        // Stay locked
                    } else {
                        errorMessage = errString.toString()
                    }
                }
                override fun onAuthenticationFailed() {
                    attempting = false
                    errorMessage = "זיהוי נכשל, נסה שוב"
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("נעילת אבטחה")
            .setSubtitle("הזן טביעת אצבע לפתיחת האפליקציה")
            .setNegativeButtonText("ביטול")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    // ניסיון אוטומטי כשהמסך נפתח
    LaunchedEffect(Unit) {
        triggerAuth()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.padding(top = 24.dp))
            Text(
                "האפליקציה נעולה",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "הזן טביעת אצבע לפתיחה",
                fontSize = 15.sp,
                color = Color.Gray
            )
            if (errorMessage != null) {
                Spacer(Modifier.padding(top = 12.dp))
                Text(
                    errorMessage!!,
                    color = Color(0xFFD32F2F),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.padding(top = 24.dp))
            Button(
                onClick = { triggerAuth() },
                enabled = !attempting
            ) {
                Text("נסה שוב")
            }
        }
    }
}
