package com.guard.app

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.code.scanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var api: GuardApi
    private lateinit var root: LinearLayout
    private var busy=false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        api=GuardApi(this)
        if(api.token!=null) dashboard() else home()
    }

    private fun screen(title:String, sub:String="") {
        val scroll=ScrollView(this)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,36,28,36);setBackgroundColor(Color.rgb(248,250,252))}
        scroll.addView(root);setContentView(scroll)
        root.addView(TextView(this).apply{text=title;textSize=28f;setTextColor(Color.rgb(15,23,42));setPadding(0,0,0,8)})
        if(sub.isNotBlank()) root.addView(TextView(this).apply{text=sub;textSize=15f;setTextColor(Color.rgb(71,85,105));setPadding(0,0,0,20)})
    }

    private fun label(s:String,size:Float=15f)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.rgb(30,41,59));setPadding(0,10,0,10)}
    private fun field(h:String,secret:Boolean=false)=EditText(this).apply{hint=h;if(secret)inputType=0x81;setPadding(18,8,18,8)}
    private fun btn(s:String,a:()->Unit)=Button(this).apply{text=s;isAllCaps=false;setOnClickListener{if(!busy)a()};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,5,0,5)}}

    private fun request(a:()->JSONObject,ok:((JSONObject)->Unit)?=null){
        busy=true
        Thread{try{val r=a();runOnUiThread{busy=false;ok?.invoke(r)?:toast("Berhasil")}}catch(e:Exception){runOnUiThread{busy=false;toast(e.message?:"Terjadi kesalahan")}}}.start()
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()

    private fun home(){
        screen("GUARD","Anak bebas. Orang tua tenang.")
        root.addView(label("Perlindungan keluarga yang transparan: lokasi, penggunaan aplikasi, waktu layar, zona aman, dan kontrol perangkat.",16f))
        root.addView(btn("Daftar Orang Tua"){register()})
        root.addView(btn("Login Orang Tua"){login()})
        root.addView(btn("Pasangkan Perangkat Anak"){pair()})
    }

    private fun register(){
        screen("Daftar Orang Tua","Akun aplikasi aktif setelah pendaftaran.")
        val n=field("Nama lengkap");val e=field("Email");val p=field("Nomor telepon (opsional)");val pw=field("Password minimal 8 karakter",true)
        listOf(n,e,p,pw).forEach(root::addView)
        root.addView(btn("Daftar"){
            if(n.text.length<2||!e.text.contains("@")||pw.text.length<8)toast("Periksa data dan password.")
            else request({api.register(n.text.toString(),e.text.toString(),pw.text.toString(),p.text.toString())}){login(e.text.toString())}
        })
        root.addView(btn("Kembali"){home()})
    }

    private fun login(prefill:String=""){
        screen("Login Orang Tua")
        val e=field("Email");e.setText(prefill);val pw=field("Password",true)
        root.addView(e);root.addView(pw)
        root.addView(btn("Masuk"){request({api.login(e.text.toString(),pw.text.toString())}){dashboard()}})
        root.addView(btn("Daftar akun"){register()});root.addView(btn("Kembali"){home()})
    }

    private fun dashboard(){
        screen("Dashboard Orang Tua","Kelola anak, perangkat, lokasi, penggunaan, aturan, zona, dan perintah.")
        root.addView(btn("Refresh"){dashboard()})
        root.addView(btn("Tambah Profil Anak"){addChild()})
        root.addView(btn("Notifikasi"){notifications()})
        root.addView(btn("Aturan Waktu Layar"){chooseRuleChild()})
        root.addView(btn("Zona Aman"){chooseGeofenceChild()})
        root.addView(btn("Keluar"){request({api.logout()}){home()}})
        request({api.dashboard()}){renderDashboard(it)}
    }

    private fun renderDashboard(r:JSONObject){
        val cs=r.optJSONArray("children")?:JSONArray();val ds=r.optJSONArray("devices")?:JSONArray()
        root.addView(label("ANAK & PERANGKAT",19f))
        if(cs.length()==0)root.addView(label("Belum ada profil anak."))
        for(i in 0 until cs.length()){
            val c=cs.getJSONObject(i);val cid=c.optString("id");val name=c.optString("full_name");root.addView(label("👤 "+name,19f))
            var found=false
            for(j in 0 until ds.length()){
                val d=ds.getJSONObject(j);if(d.optString("child_id")!=cid)continue
                found=true;val did=d.optString("id");val bat=d.optInt("battery_percent",-1)
                root.addView(label("📱 "+d.optString("device_name")+" • "+d.optString("status")+" • baterai "+if(bat>=0)bat.toString()+"%" else "-"))
                root.addView(label("Terakhir aktif: "+d.optString("last_seen_at","-")))
                root.addView(btn("Pairing QR"){createPairing(cid,name)})
                root.addView(btn("Riwayat Lokasi"){locationHistory(did,name)})
                root.addView(btn("Penggunaan Aplikasi"){usageHistory(did,name)})
                root.addView(btn("Bunyikan Perangkat"){command(did,"PLAY_SOUND")})
                root.addView(btn("Minta Lokasi Terbaru"){command(did,"LOCATE")})
                root.addView(btn("Kunci Perangkat"){command(did,"LOCK")})
                root.addView(btn("Setup Perlindungan Anak"){childSetup()})
            }
            if(!found)root.addView(btn("Pasangkan Perangkat untuk "+name){createPairing(cid,name)})
        }
    }

    private fun addChild(){
        screen("Tambah Profil Anak")
        val n=field("Nama anak");val y=field("Tahun lahir");y.inputType=2;root.addView(n);root.addView(y)
        root.addView(btn("Simpan"){request({api.child(n.text.toString(),y.text.toString().toIntOrNull()?:2015)}){dashboard()}})
        root.addView(btn("Kembali"){dashboard()})
    }

    private fun createPairing(id:String,name:String){
        request({api.createPairing(id)}){r->
            val code=r.optString("code");screen("Pairing — "+name,"Kode berlaku 15 menit.")
            root.addView(label(code,38f).apply{gravity=Gravity.CENTER})
            try{
                val m=MultiFormatWriter().encode(code,BarcodeFormat.QR_CODE,720,720);val b=Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888)
                for(x in 0 until 720)for(y in 0 until 720)b.setPixel(x,y,if(m[x,y])Color.BLACK else Color.WHITE)
                root.addView(ImageView(this).apply{setImageBitmap(b);layoutParams=LinearLayout.LayoutParams(-1,720)})
            }catch(_:Exception){}
            root.addView(label("Pada perangkat anak pilih Pasangkan Perangkat Anak lalu scan QR atau masukkan kode."))
            root.addView(btn("Kembali"){dashboard()})
        }
    }

    private fun pair(){
        screen("Pasangkan Perangkat Anak","Lakukan pada perangkat anak dengan persetujuan pengguna.")
        root.addView(btn("Scan QR Pairing"){
            GmsBarcodeScanning.getClient(this).startScan().addOnSuccessListener{it.rawValue?.let(::pairCode)}
                .addOnFailureListener{toast(it.message?:"Scanner gagal")}
        })
        val c=field("Atau kode 6 digit");c.inputType=2;root.addView(c)
        root.addView(btn("Hubungkan"){pairCode(c.text.toString())});root.addView(btn("Kembali"){home()})
    }

    private fun pairCode(code:String){
        if(code.length!=6){toast("Kode harus 6 digit");return}
        request({api.pair(code)}){childSetup()}
    }

    private fun childSetup(){
        screen("Setup Perlindungan Anak","Semua izin Android dilakukan secara terbuka. Tidak ada kamera/mikrofon tersembunyi.")
        val fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        root.addView(label("Lokasi: "+if(fine)"✓ aktif" else "belum aktif"))
        root.addView(btn("Izinkan lokasi"){if(!fine)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),22)else openSettings()})
        root.addView(btn("Buka pengaturan lokasi GUARD"){openSettings()})
        val usage=try{val op=getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager;op.checkOpNoThrow("android:get_usage_stats",android.os.Process.myUid(),packageName)==android.app.AppOpsManager.MODE_ALLOWED}catch(_:Exception){false}
        root.addView(label("Penggunaan aplikasi: "+if(usage)"✓ aktif" else "belum aktif"))
        root.addView(btn("Aktifkan Akses Penggunaan Aplikasi"){startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))})
        val dp=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager;val comp=ComponentName(this,GuardAdminReceiver::class.java);val admin=dp.isAdminActive(comp)
        root.addView(label("Device Admin: "+if(admin)"✓ aktif" else "belum aktif"))
        root.addView(btn("Aktifkan Device Admin"){startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,comp).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"GUARD menggunakan Device Admin untuk perintah kunci perangkat dari orang tua."))})
        root.addView(label("Kontrol waktu layar: "+if(accessibilityEnabled())"✓ aktif" else "belum aktif"))
        root.addView(btn("Aktifkan Kontrol Waktu Layar"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        root.addView(btn("Jalankan GUARD"){startService()})
        root.addView(btn("Selesai"){home()})
    }

    private fun accessibilityEnabled():Boolean{
        val s=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false
        return s.split(':').any{it.contains("GuardAccessibilityService",true)}
    }
    private fun startService(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            ContextCompat.startForegroundService(this,Intent(this,DeviceService::class.java));toast("GUARD aktif")
        }else toast("Aktifkan lokasi terlebih dahulu")
    }
    private fun openSettings(){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName)))}
    private fun command(id:String,c:String){request({api.command(id,c)}){toast("Perintah "+c+" dikirim")}}

    private fun locationHistory(id:String,name:String){
        request({api.locationHistory(id)}){r->screen("Lokasi — "+name,"100 titik terakhir")
            val a=r.optJSONArray("locations")?:JSONArray();if(a.length()==0)root.addView(label("Belum ada riwayat."))
            for(i in 0 until a.length()){val x=a.getJSONObject(i);root.addView(label(x.optString("recorded_at")+"\n"+x.optDouble("latitude")+", "+x.optDouble("longitude")+" • akurasi "+x.optDouble("accuracy_m",-1)+" m"))}
            root.addView(btn("Kembali"){dashboard()})
        }
    }

    private fun usageHistory(id:String,name:String){
        request({api.usageHistory(id)}){r->screen("Penggunaan — "+name)
            val a=r.optJSONArray("usage")?:JSONArray();if(a.length()==0)root.addView(label("Belum ada data."))
            for(i in 0 until a.length()){val x=a.getJSONObject(i);root.addView(label(x.optString("app_name")+"\n"+x.optString("usage_date")+" • "+x.optLong("usage_ms")/60000+" menit"))}
            root.addView(btn("Kembali"){dashboard()})
        }
    }

    private fun notifications(){
        request({api.notifications()}){r->screen("Notifikasi");val a=r.optJSONArray("notifications")?:JSONArray()
            if(a.length()==0)root.addView(label("Tidak ada notifikasi."))
            for(i in 0 until a.length()){val x=a.getJSONObject(i);root.addView(label(x.optString("title")+"\n"+x.optString("body")+"\n"+x.optString("created_at")));if(x.isNull("read_at"))root.addView(btn("Tandai dibaca"){request({api.markNotification(x.optString("id"))}){notifications()}})}
            root.addView(btn("Kembali"){dashboard()})
        }
    }

    private fun chooseRuleChild(){
        request({api.dashboard()}){r->val a=r.optJSONArray("children")?:JSONArray();screen("Aturan Waktu Layar","Pilih anak")
            for(i in 0 until a.length()){val c=a.getJSONObject(i);root.addView(btn(c.optString("full_name")){rules(c.optString("id"),c.optString("full_name"))})}
            root.addView(btn("Kembali"){dashboard()})
        }
    }
    private fun rules(id:String,name:String){
        screen("Waktu Layar — "+name)
        request({api.rules(id)}){r->val a=r.optJSONArray("rules")?:JSONArray()
            for(i in 0 until a.length()){val x=a.getJSONObject(i);root.addView(label(x.optString("rule_type")+" • hari "+x.optInt("day_of_week")+" • "+x.optInt("start_minute",-1)+"-"+x.optInt("end_minute",-1)+" • "+x.optInt("limit_minutes",-1)+" menit"))}
            root.addView(btn("Tambah Batas Harian"){createRule(id,"DAILY_LIMIT")});root.addView(btn("Tambah Waktu Istirahat"){createRule(id,"DOWNTIME")});root.addView(btn("Kembali"){dashboard()})
        }
    }
    private fun createRule(id:String,type:String){
        screen("Buat Aturan");val day=field("Hari -1=semua, 0=Minggu…6=Sabtu");day.inputType=2;root.addView(day)
        if(type=="DAILY_LIMIT"){val lim=field("Batas menit");lim.inputType=2;root.addView(lim);root.addView(btn("Simpan"){request({api.createRule(id,type,day.text.toString().toIntOrNull()?:-1,null,null,lim.text.toString().toIntOrNull()?:60)}){rules(id,"Anak")}})}
        else{val st=field("Mulai menit sejak 00:00");val en=field("Selesai menit sejak 00:00");st.inputType=2;en.inputType=2;root.addView(st);root.addView(en);root.addView(btn("Simpan"){request({api.createRule(id,type,day.text.toString().toIntOrNull()?:-1,st.text.toString().toIntOrNull(),en.text.toString().toIntOrNull(),null)}){rules(id,"Anak")}})}
        root.addView(btn("Batal"){dashboard()})
    }

    private fun chooseGeofenceChild(){
        request({api.dashboard()}){r->val a=r.optJSONArray("children")?:JSONArray();screen("Zona Aman","Pilih anak")
            for(i in 0 until a.length()){val c=a.getJSONObject(i);root.addView(btn(c.optString("full_name")){geofences(c.optString("id"),c.optString("full_name"))})}
            root.addView(btn("Kembali"){dashboard()})
        }
    }
    private fun geofences(id:String,name:String){
        screen("Zona Aman — "+name)
        request({api.geofences(id)}){r->val a=r.optJSONArray("geofences")?:JSONArray()
            for(i in 0 until a.length()){val x=a.getJSONObject(i);val gid=x.optString("id");val on=x.optBoolean("enabled");root.addView(label(x.optString("name")+" • radius "+x.optDouble("radius_m")+" m\n"+x.optDouble("latitude")+", "+x.optDouble("longitude")+" • "+if(on)"aktif" else "nonaktif"));root.addView(btn(if(on)"Nonaktifkan" else "Aktifkan"){request({api.updateGeofence(gid,!on)}){geofences(id,name)}});root.addView(btn("Hapus"){request({api.deleteGeofence(gid)}){geofences(id,name)}})}
            root.addView(btn("Tambah Zona"){createGeofence(id,name)});root.addView(btn("Kembali"){dashboard()})
        }
    }
    private fun createGeofence(id:String,name:String){
        screen("Tambah Zona Aman");val n=field("Nama zona");val la=field("Latitude");val lo=field("Longitude");val ra=field("Radius meter");ra.setText("200");listOf(n,la,lo,ra).forEach(root::addView)
        root.addView(btn("Simpan"){request({api.createGeofence(id,n.text.toString(),la.text.toString().toDouble(),lo.text.toString().toDouble(),ra.text.toString().toDouble())}){geofences(id,name)}});root.addView(btn("Batal"){geofences(id,name)})
    }
    override fun onRequestPermissionsResult(c:Int,p:Array<out String>,r:IntArray){super.onRequestPermissionsResult(c,p,r);if(c==22&&r.any{it==PackageManager.PERMISSION_GRANTED})startService()}
}
