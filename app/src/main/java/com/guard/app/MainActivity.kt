package com.guard.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private fun tv(text:String,size:Float=18f)=TextView(this).apply{this.text=text;textSize=size;setPadding(24,18,24,18)}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);showHome()}
    private fun showHome(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,40,24,24);setBackgroundColor(0xFFF7F9FC.toInt())}
        root.addView(tv("GUARD",32f)); root.addView(tv("Anak bebas. Orang tua tenang.",16f))
        val p=Button(this).apply{text="Mode Orang Tua";setOnClickListener{showParent()}}
        val c=Button(this).apply{text="Mode Anak";setOnClickListener{showChild()}}
        root.addView(p);root.addView(c);setContentView(root)
    }
    private fun showParent(){
        root.removeAllViews();root.addView(tv("Orang Tua",28f))
        val name=EditText(this).apply{hint="Nama anak"};root.addView(name)
        val b=Button(this).apply{text="Buat Profil Anak & Pairing Code";setOnClickListener{
            val code=(100000..999999).random().toString()
            root.addView(tv("Kode pairing: $code",24f))
            root.addView(makeQr(code))
        }};root.addView(b)
        root.addView(Button(this).apply{text="Kembali";setOnClickListener{showHome()}})
    }
    private fun showChild(){
        root.removeAllViews();root.addView(tv("Perangkat Anak",28f))
        val code=EditText(this).apply{hint="Masukkan 6 digit kode";inputType=2};root.addView(code)
        root.addView(Button(this).apply{text="Hubungkan";setOnClickListener{Toast.makeText(this@MainActivity,"Pairing dikirim ke backend GUARD.",Toast.LENGTH_LONG).show()}})
        root.addView(Button(this).apply{text="Kembali";setOnClickListener{showHome()}})
    }
    private fun makeQr(value:String):ImageView{
        val matrix:BitMatrix=MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,600,600)
        val bmp=Bitmap.createBitmap(600,600,Bitmap.Config.RGB_565)
        for(x in 0 until 600)for(y in 0 until 600)bmp.setPixel(x,y,if(matrix[x,y])0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        return ImageView(this).apply{setImageBitmap(bmp);adjustViewBounds=true}
    }
}