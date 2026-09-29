package com.guard.app
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
class MainActivity:AppCompatActivity(){
 lateinit var root:LinearLayout;lateinit var api:GuardApi
 override fun onCreate(b:Bundle?){super.onCreate(b);api=GuardApi(this);home()}
 fun f(h:String)=EditText(this).apply{hint=h}
 fun home(){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(TextView(this).apply{text="GUARD\nAnak bebas. Orang tua tenang.";textSize=28f;setPadding(24,40,24,30)});root.addView(Button(this).apply{text="Daftar Orang Tua";setOnClickListener{register()}});root.addView(Button(this).apply{text="Login Orang Tua";setOnClickListener{login()}});root.addView(Button(this).apply{text="Pairing Perangkat Anak";setOnClickListener{pair()}});setContentView(root)}
 fun register(){root.removeAllViews();val n=f("Nama"),e=f("Email"),p=f("Password");listOf(n,e,p).forEach{root.addView(it)};root.addView(Button(this).apply{text="Daftar";setOnClickListener{go{api.register(n.text.toString(),e.text.toString(),p.text.toString())}}})}
 fun login(){root.removeAllViews();val e=f("Email"),p=f("Password");listOf(e,p).forEach{root.addView(it)};root.addView(Button(this).apply{text="Login";setOnClickListener{go{api.login(e.text.toString(),p.text.toString())}}})}
 fun pair(){root.removeAllViews();val c=f("6 digit kode pairing");root.addView(c);root.addView(Button(this).apply{text="Hubungkan";setOnClickListener{go{api.pair(c.text.toString())}}})}
 fun go(fn:()->JSONObject){Thread{try{val r=fn();runOnUiThread{Toast.makeText(this,r.toString(),Toast.LENGTH_LONG).show()}}catch(e:Exception){runOnUiThread{Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}}}.start()}
}