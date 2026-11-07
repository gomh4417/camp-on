package com.example.pleos_demo

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal fun JSONObject.objects(key:String):List<JSONObject> {
    val a=optJSONArray(key)?:return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}
internal fun JSONObject.label(key:String)=optString(key).takeUnless { it.isBlank() || it=="null" }?:"—"

internal class JourneySession(context:Context) {
    private val prefs=context.getSharedPreferences("camp_on_journey",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    private var accessToken=prefs.getString("token","").orEmpty()
    var serverUrl by mutableStateOf(prefs.getString("server","http://10.0.2.2:8787")!!)
        private set
    var vehicleId by mutableStateOf(prefs.getString("vehicle","princess")!!)
        private set
    var camping by mutableStateOf(prefs.getBoolean("camping",false))
        private set
    var state by mutableStateOf<JSONObject?>(null)
        private set
    var receivedAt by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf("")
        private set
    var message by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    val fresh get()=state!=null && error.isBlank() && System.currentTimeMillis()-receivedAt in 0 until 10_000
    val editable get()=fresh && !busy && !refreshing
    val vehicles get()=state?.objects("vehicles").orEmpty()
    val todos get()=state?.objects("todos").orEmpty()
    val requests get()=state?.objects("requests").orEmpty()
    val memos get()=state?.objects("memos").orEmpty()
    val groupName get()=state?.optJSONObject("group")?.label("name")?:"함께 떠나는 캠핑"
    val destination get()=state?.optJSONObject("group")?.label("destination")?:"—"
    val myVehicle get()=vehicles.firstOrNull { it.optString("id")==vehicleId }
    val arrived get()=fresh && myVehicle?.optString("status")=="도착"
    fun name(id:String)=vehicles.firstOrNull { it.optString("id")==id }?.label("name")?:"그룹원"
    fun selectVehicle(id:String) {
        if(busy || vehicles.none { it.optString("id")==id })return
        vehicleId=id;prefs.edit().putString("vehicle",id).apply()
    }
    fun connect(value:String):Boolean {
        if(busy || refreshing)return false
        val url=runCatching { URI(value.trim().trimEnd('/')) }.getOrNull()
        if(url==null || url.host.isNullOrBlank() || url.userInfo!=null || url.rawQuery!=null || url.fragment!=null ||
            !url.path.isNullOrEmpty() ||
            !(url.scheme=="https" || url.scheme=="http" && url.host in listOf("10.0.2.2","127.0.0.1","localhost"))) {
            message="HTTPS 주소 또는 에뮬레이터 로컬 서버 주소를 입력하세요";return false
        }
        if(serverUrl!=url.toString()) { accessToken="";prefs.edit().remove("token").apply() }
        serverUrl=url.toString();state=null;receivedAt=0;error="";message=""
        prefs.edit().putString("server",serverUrl).apply()
        return true
    }
    fun enterCamp(s:CampSession):Boolean {
        if(!arrived || !s.parkingState.allowsControl) { message="내 차량 도착과 주차 상태를 확인하세요";return false }
        // A campsite is not the return destination. No HVAC or session actuation here.
        s.edit("campSite",destination)
        camping=true;prefs.edit().putBoolean("camping",true).apply()
        return true
    }
    private suspend fun request(method:String,path:String,payload:JSONObject?=null):JSONObject=withContext(Dispatchers.IO) {
        val connection=URI(serverUrl+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method
            connection.connectTimeout=3000;connection.readTimeout=5000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            if(accessToken.isNotEmpty()) connection.setRequestProperty("Authorization","Bearer $accessToken")
            if(payload!=null) {
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in 200..299) { "서버 응답 ${connection.responseCode}" }
            val bytes=connection.inputStream.use { it.readBytes() }
            check(bytes.size<=1_048_576) { "그룹 응답 크기 초과" }
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private fun accept(payload:JSONObject) {
        val next=payload.optJSONObject("state")?:payload
        check(next.optJSONObject("group")!=null && next.optJSONArray("vehicles")!=null) { "그룹 응답 형식 확인 필요" }
        state=next;receivedAt=System.currentTimeMillis();error=""
    }
    suspend fun join(code:String,role:String):Boolean {
        if(busy || refreshing || role !in listOf("princess","tralala","tiramisu"))return false
        busy=true
        return try { mutex.withLock {
            val result=request("POST","/api/join",JSONObject().put("code",code.trim()).put("vehicleId",role))
            val token=result.getString("token")
            check(token.isNotBlank())
            accept(result)
            accessToken=token;vehicleId=role
            prefs.edit().putString("token",token).putString("vehicle",role).apply()
            message="모바일 그룹과 연결됐어요";true
        } } catch(e:CancellationException) { throw e }
        catch(_:Exception) { message="서버 주소와 초대 코드를 확인해 주세요";false }
        finally { busy=false }
    }
    suspend fun refresh() {
        if(busy || refreshing)return
        refreshing=true
        try { mutex.withLock { accept(request("GET","/api/state")) } }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { error="그룹 서버 연결을 확인해주세요" }
        finally { refreshing=false }
    }
    private suspend fun change(method:String,path:String,payload:JSONObject?=null):Boolean {
        if(!editable)return false
        busy=true
        return try { mutex.withLock { accept(request(method,path,payload));message="그룹에 반영했어요";true } }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { error="요청 결과 확인 필요";message="상태를 새로고침한 뒤 확인해주세요";false }
        finally { busy=false }
    }
    private fun encoded(id:String)=URLEncoder.encode(id,"UTF-8")
    suspend fun completeTodo(id:String,done:Boolean)=change("PATCH","/api/todos/${encoded(id)}",JSONObject().put("done",done))
    suspend fun addRequest(id:String)=change("POST","/api/requests/${encoded(id)}/todo")
    suspend fun removeRequest(id:String)=change("DELETE","/api/requests/${encoded(id)}")
    suspend fun memo(text:String):Boolean {
        if(text.isBlank() || text.length>500)return false
        return change("POST","/api/memos",JSONObject().put("vehicleId",vehicleId).put("text",text.trim()))
    }
}
