package com.guard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var api: GuardApi
    private lateinit var root: LinearLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        api = GuardApi(this)
        home()
    }

    private fun field(hint: String) = EditText(this).apply { this.hint = hint }

    private fun screen(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 40, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 26f
        })
        setContentView(root)
    }

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

    private fun home() {
        screen("GUARD\nAnak bebas. Orang tua tenang.")
        root.addView(button("Daftar Orang Tua") { register() })
        root.addView(button("Login Orang Tua") { login() })
        root.addView(button("Pasangkan Perangkat Anak") { pair() })
    }

    private fun register() {
        screen("Daftar Orang Tua")
        val name = field("Nama lengkap")
        val email = field("Email")
        val password = field("Password")
        password.inputType = 129
        listOf(name, email, password).forEach(root::addView)
        root.addView(button("Daftar") {
            request {
                api.register(name.text.toString(), email.text.toString(), password.text.toString())
            }
        })
    }

    private fun login() {
        screen("Login Orang Tua")
        val email = field("Email")
        val password = field("Password")
        password.inputType = 129
        root.addView(email)
        root.addView(password)
        root.addView(button("Masuk") {
            request {
                val result = api.login(email.text.toString(), password.text.toString())
                if (result.has("token")) runOnUiThread { dashboard() }
                result
            }
        })
    }

    private fun dashboard() {
        screen("Dashboard Orang Tua")
        root.addView(button("Refresh") {
            request { api.dashboard() }
        })
        root.addView(button("Tambah Anak") { addChild() })
        root.addView(button("Keluar") {
            request {
                api.logout()
                runOnUiThread { home() }
                JSONObject()
            }
        })
        request { api.dashboard() }
    }

    private fun addChild() {
        screen("Tambah Anak")
        val name = field("Nama anak")
        val year = field("Tahun lahir")
        year.inputType = 2
        root.addView(name)
        root.addView(year)
        root.addView(button("Buat Profil") {
            request {
                api.child(name.text.toString(), year.text.toString().toIntOrNull() ?: 2015)
            }
        })
    }

    private fun pair() {
        screen("Pairing Perangkat Anak")
        val code = field("Kode pairing 6 digit")
        code.inputType = 2
        root.addView(code)
        root.addView(button("Hubungkan") {
            request {
                val result = api.pair(code.text.toString())
                if (result.has("device_token")) {
                    runOnUiThread { requestLocation() }
                }
                result
            }
        })
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(this, Intent(this, DeviceService::class.java))
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                22
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 22 &&
            results.any { it == PackageManager.PERMISSION_GRANTED }
        ) {
            ContextCompat.startForegroundService(this, Intent(this, DeviceService::class.java))
        }
    }

    private fun request(action: () -> JSONObject) {
        Thread {
            try {
                val result = action()
                runOnUiThread {
                    Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
