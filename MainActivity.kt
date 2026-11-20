package com.example.voucherchecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { Screen() } }
}
private val client = OkHttpClient()
private val jsonType = "application/json; charset=utf-8".toMediaType()

@Composable fun Screen() {
 var portal by remember { mutableStateOf("") }
 var sid by remember { mutableStateOf("") }
 var code by remember { mutableStateOf("") }
 var status by remember { mutableStateOf("Portal URL၊ Session ID၊ Voucher Code ထည့်ပါ။") }
 var plan by remember { mutableStateOf("—") }
 var duration by remember { mutableStateOf("—") }
 var balance by remember { mutableStateOf("—") }
 var expiry by remember { mutableStateOf("—") }
 var checked by remember { mutableStateOf("—") }
 var busy by remember { mutableStateOf(false) }
 val scope = rememberCoroutineScope()
 MaterialTheme(colorScheme = darkColorScheme(primary = Color.Cyan, background = Color(0xFF080B12), surface = Color(0xFF172033))) {
  Column(Modifier.fillMaxSize().background(Color(0xFF080B12)).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text("VOUCHER CODE CHECKER", color = Color.Magenta, style = MaterialTheme.typography.headlineSmall)
   Text("ကိုယ်ပိုင် / ခွင့်ပြုချက်ရှိသော Portal အတွက်သာ", color = Color.Cyan)
   OutlinedTextField(portal, { portal=it }, label={Text("Portal URL (https://...)")}, modifier=Modifier.fillMaxWidth())
   OutlinedTextField(sid, { sid=it }, label={Text("Session ID")}, modifier=Modifier.fillMaxWidth())
   OutlinedTextField(code, { code=it }, label={Text("Voucher Code")}, modifier=Modifier.fillMaxWidth())
   Button(onClick={
    scope.launch {
     busy=true; status="စစ်ဆေးနေသည်..."
     val r=withContext(Dispatchers.IO) { checkCode(portal.trim(),sid.trim(),code.trim()) }
     busy=false; status=r[0]
     if(r.size>=6 && r[0].startsWith("OK")) {
      plan=r[1]; duration=r[2]; balance=r[3]; expiry=r[4]
      checked=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date())
     }
    }
   }, enabled=!busy && portal.isNotBlank() && sid.isNotBlank() && code.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text(if(busy) "စစ်ဆေးနေသည်..." else "ကုဒ်စစ်မည်") }
   Text(status, color=Color.Cyan)
   Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Line("PLAN",plan); Line("DURATION",duration); Line("BALANCE",balance); Line("EXPIRES AT",expiry); Line("CHECKED AT",checked)
    }
   }
   Text("အချိန်အချက်အလက်များသည် Portal API က ပြန်ပေးသည့် Data ပေါ်မူတည်ပါသည်။", color=Color.LightGray)
  }
 }
}
@Composable private fun Line(a:String,b:String) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(a,color=Color.Cyan); Text(b,color=Color.White) } }

private fun get(o:JSONObject, keys:List<String>):String? {
 for(k in keys) { val v=o.opt(k); if(v!=null && v!=JSONObject.NULL && v.toString().isNotBlank()) return v.toString() }
 return null
}
private fun date(v:String?):String {
 if(v==null) return "မရရှိပါ"
 val n=v.toLongOrNull() ?: return v
 val ms=if(n>9999999999L)n else n*1000
 return SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date(ms))
}
private fun checkCode(portal:String,sid:String,code:String):List<String> = try {
 require(portal.startsWith("https://")) { "HTTPS Portal URL ထည့်ပါ။" }
 val host=URL(portal).host
 val payload=JSONObject().apply { put("accessCode",code); put("sessionId",sid); put("apiVersion",1); put("authCode","ABCD") }
 val req=Request.Builder().url(portal.trimEnd('/')+"/api/auth/voucher/?lang=en_US")
  .header("Content-Type","application/json").header("Origin",portal)
  .post(payload.toString().toRequestBody(jsonType)).build()
 client.newCall(req).execute().use { response ->
  val raw=response.body?.string().orEmpty()
  if(!response.isSuccessful) return listOf("Portal HTTP Error: ${response.code}")
  val body=JSONObject(raw)
  val valid=raw.contains("\"success\":true",true)||raw.contains("\"authResult\":\"1\"",true)||raw.contains("logonUrl",true)
  if(!valid) return listOf("Voucher မမှန်ပါ သို့မဟုတ် Portal က လက်မခံပါ။")
  val result=body.optJSONObject("result") ?: JSONObject()
  val balanceObj=try {
   val b=Request.Builder().url(portal.trimEnd('/')+"/api/auth/balance/getBalance/$sid").header("Origin",portal).get().build()
   client.newCall(b).execute().use { x -> if(x.isSuccessful) JSONObject(x.body?.string().orEmpty()).optJSONObject("result") ?: JSONObject() else JSONObject() }
  } catch(_:Exception) { JSONObject() }
  val p=get(balanceObj,listOf("profileName","planName","packageName")) ?: get(result,listOf("profileName","planName","packageName")) ?: "မရရှိပါ"
  val d=get(result,listOf("totalMinutes","durationMinutes","planMinutes","minutes","validityMinutes"))?.plus(" မိနစ်") ?: "မရရှိပါ"
  val bal=get(balanceObj,listOf("remainingMinutes","remainMinutes","balanceMinutes","leftMinutes","remainingTime","remainTime","balance","remain")) ?: "မရရှိပါ"
  val exp=get(balanceObj,listOf("expireTime","expiredTime","endTime","expiresAt","expiryTime","validUntil")) ?: get(result,listOf("expireTime","expiredTime","endTime","expiresAt","expiryTime","validUntil"))
  listOf("OK - Voucher စစ်ဆေးမှု အောင်မြင်ပါသည်။",p,d,bal,date(exp),"")
 }
} catch(e:Exception) { listOf("Error: ${e.message ?: "မသိရှိသော ပြဿနာ"}") }
