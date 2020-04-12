package org.inventivetalent.postboxapp.web

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.util.Log
import androidx.annotation.RawRes
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.inventivetalent.postboxapp.BatteryInfo
import org.inventivetalent.postboxapp.MainActivity
import org.inventivetalent.postboxapp.SensorBackgroundService
import org.inventivetalent.postboxapp.web.WebAuth.Companion.checkAuth
import org.inventivetalent.postboxapp.web.WebAuth.Companion.forbidden
import org.inventivetalent.postboxapp.web.WebAuth.Companion.getUser
import org.inventivetalent.postboxapp.web.WebAuth.Companion.sha512
import org.inventivetalent.postboxapp.web.WebAuth.Companion.unauthorized
import org.json.JSONObject


class WebServer(port: Int) : NanoHTTPD(port) {


    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val method = session.method
        val uri = session.uri
        val params = session.parameters

        Log.i("WebServer","${method.name} $uri")
        Log.i("WebServer", params.toString())


        if ("/" == uri) {
            return fileResponse(org.inventivetalent.postboxapp.R.raw.index)
        }
        if ("/logout" == uri) {
            return if (session.headers.contains("authorization")) unauthorized(false) else redirect(
                "/"
            )
        }

        if ("/dashboard" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }
            val format = getPostBoxInfo()
            return fileResponse(org.inventivetalent.postboxapp.R.raw.dashboard, format)
        }

        if ("/users" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            val emails = runBlocking {
                return@runBlocking MainActivity.instance?.emailRepository?.getAll()
            }
            var content = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<title>Users</title>" +
                    "</head>" +
                    "<body>"

            emails?.forEach {
                content += "<div>" +
                        "<a href='/useredit?id=${it.id}'>${it.name}</a>" +
                        "</div>"
            }

            content += "</body>"
            return newFixedLengthResponse(content)
        }
        if ("/useredit" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            if (getUser(session) != "admin") {
                return forbidden()
            }

            var format: MutableMap<String, Any?> = mutableMapOf("email" to "", "name" to "")
            if (params.contains("id")) {// Viewing/Updating existing user
                val id = params["id"]?.get(0)?.toInt()
                if (id != null) {
                    val emailEntry = runBlocking {
                        return@runBlocking MainActivity.instance?.emailRepository?.getById(id)
                    } ?: return notFound()

                    if (method == Method.POST) {
                        session.parseBody(null)
                    }

                    format["email"] = emailEntry.address
                    format["name"] = emailEntry.name
                    format["id"] = id
                    if (method == Method.POST && params.contains("email") && params.contains("name")) {
                        val newEmail = params["email"]?.get(0)?.toString()
                        val newName = params["name"]?.get(0)?.toString()
                        if (emailEntry.name == "admin" && newName != "admin") {
                            return newFixedLengthResponse(
                                Response.Status.BAD_REQUEST,
                                "text/plain",
                                "Cannot change admin username"
                            )
                        }
                        emailEntry.address = newEmail
                        emailEntry.name = newName
                        runBlocking {
                            MainActivity.instance?.emailRepository?.update(emailEntry)
                        }
                        return redirect("/useredit?id=$id")
                    }
                }
            }
            return fileResponse(org.inventivetalent.postboxapp.R.raw.useredit, format)
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
                    return fileResponse(org.inventivetalent.postboxapp.R.raw.passwordchanged)
                }
            }
            return fileResponse(org.inventivetalent.postboxapp.R.raw.passwordchange)
        }

        if ("/api/info" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            val json = JSONObject(getPostBoxInfo())
            val response = newFixedLengthResponse(json.toString())
            response.mimeType = "application/json"
            return response
        }

        return super.serve(session)
    }

    fun getPostBoxInfo(): Map<String, Any?> {
        val info = HashMap<String, Any?>()
        val proximity = getProximity()
        val proximityTime = getProximityTime()
        info["proximity"] = proximity
        info["proximityTime"] = proximityTime
        val postBoxFull = (proximity != null && proximity < 2)
        info["postBoxFull"] = postBoxFull
        val postBoxFullRecently =
            postBoxFull && proximityTime != null && (System.currentTimeMillis() - proximityTime < 300000)
        info["postBoxFullRecently"] = postBoxFullRecently
        val batteryInfo = getBatteryInfo()
        info["battery"] = batteryInfo.batteryPct
        info["charging"] = batteryInfo.isCharging
        info["serviceRunning"] = isServiceRunning()
        return info
    }

    fun fileResponse(@RawRes file: Int, mime: String = "text/html"): Response {
        return newChunkedResponse(
            Response.Status.OK,
            mime,
            MainActivity.instance?.resources?.openRawResource(
                file
            )
        )
    }

    fun fileResponse(
        @RawRes file: Int, format: Map<String, Any?>,
        mime: String = "text/html"
    ): Response {
        var content = MainActivity.instance?.resources?.openRawResource(file)?.bufferedReader()
            .use { it?.readText() }
        format.forEach { (k, v) -> content = content?.replace("$$k", v.toString()) }
        return newFixedLengthResponse(Response.Status.OK, mime, content)
    }


    fun redirect(location: String): Response {
        val response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
        response.addHeader("Location", location)
        return response
    }

    fun notFound(msg: String = "Not Found"): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", msg)
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

    // Based on https://stackoverflow.com/a/5921190/6257838
    fun isServiceRunning():Boolean {
        val manager = MainActivity.instance?.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Integer.MAX_VALUE)) {
            if (SensorBackgroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

}