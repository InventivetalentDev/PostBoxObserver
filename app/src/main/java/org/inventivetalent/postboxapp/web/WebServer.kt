package org.inventivetalent.postboxapp.web

import androidx.annotation.RawRes
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.inventivetalent.postboxapp.BatteryInfo
import org.inventivetalent.postboxapp.MainActivity
import org.inventivetalent.postboxapp.R
import org.inventivetalent.postboxapp.web.WebAuth.Companion.checkAuth
import org.inventivetalent.postboxapp.web.WebAuth.Companion.forbidden
import org.inventivetalent.postboxapp.web.WebAuth.Companion.sha512
import org.inventivetalent.postboxapp.web.WebAuth.Companion.unauthorized
import org.json.JSONObject

class WebServer(port: Int) : NanoHTTPD(port) {


    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val method = session.method
        val uri = session.uri
        val params = session.parameters

        if ("/" == uri) {
            return fileResponse(R.raw.index)
        }

        if ("/dashboard" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }
            return fileResponse(R.raw.dashboard)
        }

        if ("/logout" == uri) {
            return if (session.headers.contains("authorization")) unauthorized(false) else redirect("/")
        }

        if ("/passwordchange" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }
            if (method == Method.POST) {
                session.parseBody(null)

                val currPass = params["currpass"]
                val newPass = params["newpass"]
                val newPassRepeat = params["newpassrepeat"]
                if (currPass != null && newPass != null && newPassRepeat != null) {
                    if (newPass != newPassRepeat) {
                        return newFixedLengthResponse("New Password Mismatch")
                    }
                    val user = WebAuth.getUser(session) ?: return unauthorized()
                    val emailEntry = runBlocking {
                        return@runBlocking MainActivity.instance?.emailRepository?.getByNameOrAddress(
                            user
                        )
                    } ?: return forbidden()

                    if (sha512(currPass[0]) != emailEntry.pass) {
                        return forbidden()
                    }
                    emailEntry.pass = sha512(newPass[0])
                    runBlocking {
                        MainActivity.instance?.emailRepository?.update(emailEntry)
                    }
                    return fileResponse(R.raw.passwordchanged)
                }
            }
            return fileResponse(R.raw.passwordchange)
        }

        if ("/api/info" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            val proximity = getProximity()
            val proximityTime = getProximityTime()
            val batteryInfo = getBatteryInfo()

            val json = JSONObject()
            json.put("time", System.currentTimeMillis())
            json.put("proximity", proximity)
            json.put("proximityTime", proximityTime)
            json.put("charging", batteryInfo.isCharging)
            json.put("battery", batteryInfo.batteryPct)

            val response = newFixedLengthResponse(json.toString())
            response.mimeType = "application/json"
            return response
        }

        return super.serve(session)
    }

    fun fileResponse(@RawRes file: Int): Response {
        return newChunkedResponse(
            Response.Status.OK,
            "text/html",
            MainActivity.instance?.resources?.openRawResource(
                file
            )
        )
    }

    fun redirect(location: String): Response {
        val response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
        response.addHeader("Location", location)
        return response
    }

    fun getProximity(): Float? {
        return runBlocking {
            return@runBlocking MainActivity.instance?.dataRepository?.get("proximity")
        }?.toFloatOrNull()
    }

    fun getProximityTime(): Long? {
        return runBlocking {
            return@runBlocking MainActivity.instance?.dataRepository?.get("proximityTime")
        }?.toLongOrNull()
    }

    fun getBatteryInfo() = BatteryInfo()

}