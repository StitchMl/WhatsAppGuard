package com.codex.whatsappguard

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private val devicePolicyManager: DevicePolicyManager
        get() = getSystemService(DevicePolicyManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 30f
            setTextColor(Color.rgb(20, 24, 28))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            setText(R.string.main_description)
            textSize = 16f
            setTextColor(Color.rgb(65, 72, 80))
            setPadding(0, dp(12), 0, dp(18))
        })

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(20, 24, 28))
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusText)

        root.addView(primaryButton(R.string.open_accessibility_settings) {
            openAccessibilitySettingsWithDisclosure()
        })

        root.addView(primaryButton(R.string.enable_device_admin) {
            requestDeviceAdmin()
        })

        root.addView(primaryButton(R.string.apply_managed_protection) {
            applyDeviceOwnerProtections()
        })

        root.addView(appInfoButton {
            val uri = Uri.parse("package:$packageName")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        })

        root.addView(TextView(this).apply {
            setText(R.string.main_note)
            textSize = 14f
            setTextColor(Color.rgb(90, 96, 104))
            setPadding(0, dp(22), 0, 0)
        })

        scroll.addView(root)
        return scroll
    }

    private fun updateStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val adminActive = devicePolicyManager.isAdminActive(adminComponent())
        val deviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
        statusText.text = buildString {
            appendLine(if (accessibilityEnabled) {
                getString(R.string.status_accessibility_enabled)
            } else {
                getString(R.string.status_accessibility_disabled)
            })
            appendLine(if (adminActive) {
                getString(R.string.status_device_admin_enabled)
            } else {
                getString(R.string.status_device_admin_disabled)
            })
            append(if (deviceOwner) {
                getString(R.string.status_managed_enabled)
            } else {
                getString(R.string.status_managed_disabled)
            })
        }
    }

    private fun primaryButton(labelRes: Int, action: () -> Unit): Button {
        return Button(this).apply {
            setText(labelRes)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(31, 168, 85))
            setOnClickListener { action() }
        }
    }

    private fun appInfoButton(action: () -> Unit): Button {
        return Button(this).apply {
            setText(R.string.open_android_app_info)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 24, 28))
            setOnClickListener { action() }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, GuardAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { TextUtils.equals(it, expected) }
    }

    private fun openAccessibilitySettingsWithDisclosure() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_message)
            .setPositiveButton(R.string.accessibility_disclosure_accept) { _, _ ->
                prefs.edit().putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation)
            )
        }
        startActivity(intent)
    }

    private fun applyDeviceOwnerProtections() {
        if (!devicePolicyManager.isDeviceOwnerApp(packageName)) {
            Toast.makeText(
                this,
                getString(R.string.managed_protection_requires_owner),
                Toast.LENGTH_LONG
            ).show()
            updateStatus()
            return
        }

        val admin = adminComponent()
        runCatching {
            devicePolicyManager.setUninstallBlocked(admin, packageName, true)
            devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            devicePolicyManager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.setPermittedAccessibilityServices(admin, listOf(packageName))
        }.onSuccess {
            Toast.makeText(this, getString(R.string.managed_protection_applied), Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.managed_protection_error, it.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
        updateStatus()
    }

    private fun adminComponent(): ComponentName {
        return ComponentName(this, AdminReceiver::class.java)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val PREFS_NAME = "whatsapp_guard_preferences"
        const val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
    }
}
