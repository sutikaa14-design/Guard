package com.guard.app
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
class GuardApi(private val c:Context){
 private val url="https://ukavxclgnthzwewvozgl.supabase.co/functions/v1/guard-api"
 private var token:String?=null
 private fun post(o:JSONObject):JSONObject{val x=URL(url).openConnection() as HttpURLConnection;x.requestMethod="POST";x.doOutput=true;x.setRequestProperty("Content-Type","application/json");token?.let{x.setRequestProperty("Authorization","Bearer $it")};x.outputStream.use{it.write(o.toString().toByteArray())};val body=(if(x.responseCode in 200..299)x.inputStream else x.errorStream).bufferedReader().readText();return JSONObject(body)}
 fun register(n:String,e:String,p:String)=post(JSONObject().put("action","register").put("full_name",n).put("email",e).put("password",p))
 fun login(e:String,p:String):JSONObject{val r=post(JSONObject().put("action","login").put("email",e).put("password",p));if(r.has("token"))token=r.getString("token");return r}
 fun child(n:String,y:Int)=post(JSONObject().put("action","create_child").put("full_name",n).put("birth_year",y))
 fun pairing(id:String)=post(JSONObject().put("action","create_pairing").put("child_id",id))
 fun pair(code:String)=post(JSONObject().put("action","pair_device").put("code",code).put("device_name","Android Child").put("app_version","0.1.0").put("device_identifier_hash",java.util.UUID.randomUUID().toString()))
}