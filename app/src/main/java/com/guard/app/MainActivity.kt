package com.guard.app
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
class MainActivity:AppCompatActivity(){
 private lateinit var api:GuardApi;private lateinit var root:LinearLayout
 override fun onCreate(b:Bundle?){super.onCreate(b);api=GuardApi(this);home()}
 private fun f(h:String)=EditText(this).apply{hint=h}
 private fun base(t:String){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,40,28,20)};root.addView(TextView(this).apply{text=t;textSize=26f});setContentView(root)}
 private fun btn(t:String,fn:()->Unit)=Button(this).apply{text=t;setOnClickListener{fn()}}
 private fun home(){base("GUARD\nAnak bebas. Orang tua tenang.");root.addView(btn("Daftar Orang Tua"){register()});root.addView(btn("Login Orang Tua"){login()});root.addView(btn("Pasangkan Perangkat Anak"){pair()})}
 private fun register(){base("Daftar Orang Tua");val n=f("Nama lengkap");val e=f("Email");val p=f("Password");p.inputType=129;listOf(n,e,p).forEach{root.addView(it)};root.addView(btn("Daftar"){req{api.register(n.text.toString(),e.text.toString(),p.text.toString())}})}
 private fun login(){base("Login Orang Tua");val e=f("Email");val p=f("Password");p.inputType=129;root.addView(e);root.addView(p);root.addView(btn("Masuk"){req{val r=api.login(e.text.toString(),p.text.toString());runOnUiThread{if(r.has("token"))dashboard()};r}})}
 private fun dashboard(){base("Dashboard Orang Tua");root.addView(btn("Sinkronkan anak & perangkat"){req{api.dashboard()}});root.addView(btn("Tambah Anak"){addChild()});root.addView(btn("Keluar"){req{api.logout();home()}})}
 private fun addChild(){base("Tambah Anak");val n=f("Nama anak");val y=f("Tahun lahir");y.inputType=2;root.addView(n);root.addView(y);root.addView(btn("Buat Profil"){req{api.child(n.text.toString(),y.text.toString().toIntOrNull()?:2015)}})}
 private fun pair(){base("Pairing Perangkat Anak");val c=f("Kode pairing 6 digit");c.inputType=2;root.addView(c);root.addView(btn("Hubungkan"){req{val r=api.pair(c.text.toString());if(r.has("device_token")){requestLocation()};r}})}
 private fun requestLocation(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){ContextCompat.startForegroundService(this,Intent(this,DeviceService::class.java))}else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),22)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==22&&g.isNotEmpty()&&g[0]==PackageManager.PERMISSION_GRANTED)ContextCompat.startForegroundService(this,Intent(this,DeviceService::class.java))}
 private fun req(fn:()->JSONObject){Thread{try{val r=fn();runOnUiThread{Toast.makeText(this,r.toString(),Toast.LENGTH_LONG).show()}}catch(e:Exception){runOnUiThread{Toast.makeText(this,e.message?:"Error",Toast.LENGTH_LONG).show()}}}.start()}
}