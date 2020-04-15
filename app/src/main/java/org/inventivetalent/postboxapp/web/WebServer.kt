package org.inventivetalent.postboxapp.web

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.util.Log
import androidx.annotation.RawRes
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.inventivetalent.postboxapp.BatteryInfo
import org.inventivetalent.postboxapp.EmailSender
import org.inventivetalent.postboxapp.MainActivity
import org.inventivetalent.postboxapp.R
import org.inventivetalent.postboxapp.service.NotificationBackgroundService
import org.inventivetalent.postboxapp.service.SensorBackgroundService
import org.inventivetalent.postboxapp.web.WebAuth.Companion.checkAuth
import org.inventivetalent.postboxapp.web.WebAuth.Companion.forbidden
import org.inventivetalent.postboxapp.web.WebAuth.Companion.getUser
import org.inventivetalent.postboxapp.web.WebAuth.Companion.sha512
import org.inventivetalent.postboxapp.web.WebAuth.Companion.unauthorized
import org.json.JSONObject


class WebServer(port: Int) : NanoHTTPD(port) {


    companion object{

        fun getPostBoxInfo(): Map<String, Any?> {
            val info = HashMap<String, Any?>()
            val proximity = getProximity()
            val proximityTime = getProximityTime()
            info["proximity"] = proximity
            info["proximityTime"] = proximityTime
            val postBoxFull = (proximity != null && proximity < 2)
            info["postBoxFull"] = postBoxFull
            val postBoxFullRecently =
                postBoxFull && proximityTime != null && (System.currentTimeMillis() - proximityTime < 320000)
            info["postBoxFullRecently"] = postBoxFullRecently
            val batteryInfo = getBatteryInfo()
            info["battery"] = batteryInfo.batteryPct
            info["charging"] = batteryInfo.isCharging
            info["sensorServiceRunning"] = isServiceRunning(SensorBackgroundService::class.java)
            info["notificationServiceRunning"] = isServiceRunning(NotificationBackgroundService::class.java)
            return info
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
        fun <T> isServiceRunning(clazz: Class<T>): Boolean {
            val manager = MainActivity.instance?.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
            for (service in manager!!.getRunningServices(Integer.MAX_VALUE)) {
                if (clazz.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val method = session.method
        val uri = session.uri
        val params = session.parameters

        Log.i("WebServer", "${method.name} $uri")
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

            var format: MutableMap<String, Any?> = baseFormat(mapOf("email" to "", "name" to ""))
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
            }else{
                //TODO: new user
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

        if ("/settings" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            if (method == Method.POST) {
                session.parseBody(null)

                val mailjetkey = params["mailjetkey"]?.get(0)
                val mailjetsecret = params["mailjetsecret"]?.get(0)
                val emailsender = params["emailsender"]?.get(0)

                runBlocking {
                    if (mailjetkey != null) {
                        MainActivity.instance?.setData("MAILJET_API_KEY", mailjetkey)
                    }
                    if (mailjetsecret != null) {
                        MainActivity.instance?.setData("MAILJET_API_SECRET", mailjetsecret)
                    }
                    if (emailsender != null) {
                        MainActivity.instance?.setData("MAILJET_SENDER", emailsender)
                    }
                }
                return redirect("/settings")
            }

            val format = baseFormat()
            val apiKey = runBlocking {
                return@runBlocking EmailSender.getApiKey()
            }
            val apiSecret = runBlocking {
                return@runBlocking EmailSender.getApiSecret()
            }
            val sender = runBlocking {
                return@runBlocking EmailSender.getSender()
            }
            format["mailjetkey"] = apiKey ?: ""
            format["mailjetsecret"] = apiSecret ?: ""
            format["emailsender"] = sender ?: ""

            return fileResponse(R.raw.settings, format)
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

        if ("/api/testmail" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            val emailEntry = runBlocking {
                return@runBlocking MainActivity.instance?.emailRepository?.getByName("admin")
            } ?: return notFound()

            EmailSender.sendEmail( mapOf((emailEntry.address!!) to (emailEntry.name ?: "PostBox Operator")), "Test Mail", "This is a test email from the PostBox App! ${System.currentTimeMillis()}")
            return newFixedLengthResponse("Email sent!")
        }

        if ("/favicon.ico" == uri) {
            return fileResponse(R.raw.favicon)
        }


        return super.serve(session)
    }

    fun baseFormat(def: Map<String, Any?> = HashMap()): MutableMap<String, Any?> {
        val map =  HashMap(def)
        map["styles"] = ""
        map["scripts"] = ""
        return map
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



}