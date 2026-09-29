package com.guard.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
class GuardApi(c:Context){
 private val p=c.getSharedPreferences("guard",0); private val url="https://ukavxclgnthzwewvozgl.supabase.co/functions/v1/guard-api"
 private var token:String? get()=p.getString("token",null); set(v){p.edit().putString("token",v).apply()}
 var deviceToken:String? get()=p.getString("device_token",null); private set(v){p.edit().putString("device_token",v).apply()}
 var deviceId:String? get()=p.getString("device_id",null); private set(v){p.edit().putString("device_id",v).apply()}
 private fun post(o:JSONObject,device:Boolean=false):JSONObject{val x=URL(url).openConnection() as HttpURLConnection;x.requestMethod="POST";x.doOutput=true;x.connectTimeout=15000;x.readTimeout=20000;x.setRequestProperty("Content-Type","application/json");if(!device)token?.let{x.setRequestProperty("Authorization","Bearer $it")};if(device)deviceToken?.let{o.put("device_token",it)};x.outputStream.use{it.write(o.toString().toByteArray())};val s=(if(x.responseCode in 200..299)x.inputStream else x.errorStream).bufferedReader().use{it.readText()};return JSONObject(s)}
 fun register(n:String,e:String,pw:String)=post(JSONObject().put("action","register").put("full_name",n).put("email",e).put("password",pw))
 fun login(e:String,pw:String)=post(JSONObject().put("action","login").put("email",e).put("password",pw)).also{it.optString("token").takeIf{v->v.isNotBlank()}?.let{v->token=v}}
 fun logout()=post(JSONObject().put("action","logout")).also{token=null}
 fun child(n:String,y:Int)=post(JSONObject().put("action","create_child").put("full_name",n).put("birth_year",y))
 fun pairing(id:String)=post(JSONObject().put("action","create_pairing").put("child_id",id))
 fun dashboard()=post(JSONObject().put("action","parent_dashboard"))
 fun command(id:String,c:String)=post(JSONObject().put("action","create_command").put("device_id",id).put("command",c))
 fun pair(code:String)=post(JSONObject().put("action","pair_device").put("code",code).put("device_name","Android Child").put("app_version","1.1.0")).also{it.optString("device_token").takeIf{v->v.isNotBlank()}?.let{v->deviceToken=v;deviceId=it.getJSONObject("device").getString("id")}}
 fun heartbeat(b:Int)=post(JSONObject().put("action","device_heartbeat").put("battery_percent",b),true)
 fun location(lat:Double,lon:Double,acc:Float)=post(JSONObject().put("action","device_location").put("latitude",lat).put("longitude",lon).put("accuracy_m",acc),true)
 fun commands()=post(JSONObject().put("action","poll_commands"),true)
 fun complete(id:String,ok:Boolean)=post(JSONObject().put("action","complete_command").put("command_id",id).put("status",if(ok)"COMPLETED" else "FAILED"),true)
 fun usage(a:JSONArray)=post(JSONObject().put("action","device_usage").put("apps",a),true)
}