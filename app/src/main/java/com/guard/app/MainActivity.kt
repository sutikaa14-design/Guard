package com.guard.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var api: GuardApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = GuardApi(this)
        home()
    }

    private fun field(hint: String) = EditText(this).apply { this.hint = hint }

    private fun home() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "GUARD\nAnak bebas. Orang tua tenang."
            textSize = 28f
            setPadding(24, 40, 24, 30)
        })
        root.addView(Button(this).apply {
            text = "Daftar Orang Tua"
            setOnClickListener { register() }
        })
        root.addView(Button(this).apply {
            text = "Login Orang Tua"
            setOnClickListener { login() }
        })
        root.addView(Button(this).apply {
            text = "Pairing Perangkat Anak"
            setOnClickListener { pair() }
        })
        setContentView(root)
    }

    private fun register() {
        root.removeAllViews()
        val name = field("Nama")
        val email = field("Email")
        val password = field("Password")
        password.inputType = 129
        root.addView(name)
        root.addView(email)
        root.addView(password)
        root.addView(Button(this).apply {
            text = "Daftar"
            setOnClickListener {
                request { api.register(name.text.toString(), email.text.toString(), password.text.toString()) }
            }
        })
    }

    private fun login() {
        root.removeAllViews()
        val email = field("Email")
        val password = field("Password")
        password.inputType = 129
        root.addView(email)
        root.addView(password)
        root.addView(Button(this).apply {
            text = "Login"
            setOnClickListener {
                request { api.login(email.text.toString(), password.text.toString()) }
            }
        })
    }

    private fun pair() {
        root.removeAllViews()
        val code = field("6 digit kode pairing")
        code.inputType = 2
        root.addView(code)
        root.addView(Button(this).apply {
            text = "Hubungkan"
            setOnClickListener {
                request { api.pair(code.text.toString()) }
            }
        })
    }

    private fun request(fn: () -> JSONObject) {
        Thread {
            try {
                val result = fn()
                runOnUiThread {
                    Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "Terjadi kesalahan", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}