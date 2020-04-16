package org.inventivetalent.postboxapp.web

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.annotation.RawRes
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.inventivetalent.postboxapp.BatteryInfo
import org.inventivetalent.postboxapp.EmailSender
import org.inventivetalent.postboxapp.MainActivity
import org.inventivetalent.postboxapp.R
import org.inventivetalent.postboxapp.database.entities.Email
import org.inventivetalent.postboxapp.service.NotificationBackgroundService
import org.inventivetalent.postboxapp.service.SensorBackgroundService
import org.inventivetalent.postboxapp.web.WebAuth.Companion.checkAuth
import org.inventivetalent.postboxapp.web.WebAuth.Companion.forbidden
import org.inventivetalent.postboxapp.web.WebAuth.Companion.getUser
import org.inventivetalent.postboxapp.web.WebAuth.Companion.sha512
import org.inventivetalent.postboxapp.web.WebAuth.Companion.unauthorized
import org.json.JSONObject
import java.lang.RuntimeException
import java.util.*
import kotlin.collections.HashMap


class WebServer(port: Int) : NanoHTTPD(port) {

    companion object {

        val FULL_TIMEOUT: Long = 320000

        var address: String = "0.0.0.0"

        fun getPostBoxInfo(): MutableMap<String, Any?> {
            val info = HashMap<String, Any?>()
            info["address"] = address
            info["port"] = getPort()
            val proximity = getProximity()
            val proximityTime = getProximityTime()
            info["proximity"] = proximity
            info["proximityTime"] = proximityTime
            info["proximityTimeFormatted"] = dateFormat(proximityTime)
            val postBoxFull = (proximity != null && proximity < 2)
            info["postBoxFull"] = postBoxFull
            val postBoxFullRecently =
                postBoxFull && proximityTime != null && (System.currentTimeMillis() - proximityTime < FULL_TIMEOUT)
            info["postBoxFullRecently"] = postBoxFullRecently
            val batteryInfo = getBatteryInfo()
            info["battery"] = batteryInfo.batteryPct
            info["charging"] = batteryInfo.isCharging
            info["sensorServiceRunning"] = isServiceRunning(SensorBackgroundService::class.java)
            info["notificationServiceRunning"] =
                isServiceRunning(NotificationBackgroundService::class.java)
            info["deviceName"] = getDeviceName()
            val packageInfo = getPackageInfo()
            info["appVersion"] = packageInfo.versionName
            info["appVersionCode"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode
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

        fun getDeviceName(): String {
            // https://medium.com/capital-one-tech/how-to-get-an-android-device-nickname-d5eab12f4ced
            val bluetoothName = Settings.Secure.getString(MainActivity.instance?.contentResolver, "bluetooth_name")
            if (bluetoothName != null) return bluetoothName
            val deviceName = Settings.Secure.getString(MainActivity.instance?.contentResolver, "device_name")
            if (deviceName != null) return deviceName
            val lockScreenName = Settings.Secure.getString(MainActivity.instance?.contentResolver, "lock_screen_owner_info")
            if (lockScreenName != null) return lockScreenName
            return Build.MODEL
        }

        fun getPort() = 8090

        // Based on https://stackoverflow.com/a/5921190/6257838
        fun <T> isServiceRunning(clazz: Class<T>): Boolean {
            val manager =
                MainActivity.instance?.getSystemService(ACTIVITY_SERVICE) as ActivityManager?
            for (service in manager!!.getRunningServices(Integer.MAX_VALUE)) {
                if (clazz.name == service.service.className) {
                    return true
                }
            }
            return false
        }

        fun getPackageInfo(): PackageInfo {
            try {
                return MainActivity.instance?.packageManager?.getPackageInfo(MainActivity.instance?.packageName!!, 0)!!
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)// shouldn't be possible to get here
            }
        }

        fun dateFormat(): String {
            return dateFormat(Date().time)
        }

        fun dateFormat(time: Long?): String = DateFormat.format("dd-MM-yyyy HH:mm:ss", time ?: 0).toString()

    }

    override fun start() {
        super.start()
    }

    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val method = session.method
        val uri = session.uri
        val params = session.parameters

        Log.i("WebServer", "${method.name} $uri")
        Log.i("WebServer", params.toString())


        if ("/" == uri) {
            return fileResponse(R.raw.index, baseFormat())
        }
        if ("/logout" == uri) {
            return if (session.headers.contains("authorization")) unauthorized(
                false,
                "<html><head><script>location.reload();</script></head></html>",
                mime = "text/html"
            ) else redirect(
                "/"
            )
        }

        if ("/dashboard" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }
            val format = baseFormat(getPostBoxInfo())
            format["username"] = getUser(session)
            if (getUser(session) == "admin") {
                format["extraLinks"] = "<a href=\"/users\">Manage Users</a><br/>\n" +
                        "<a href=\"/settings\">System Settings</a><br/>"
            } else {
                format["extraLinks"] = ""
            }
            return fileResponse(R.raw.dashboard, format)
        }

        if ("/mysettings" == uri) {
            val emailEntry = runBlocking {
                return@runBlocking MainActivity.instance?.emailRepository?.getByNameOrAddress(getUser(session)!!)
            } ?: return notFound()
            return redirect("/useredit?id=${emailEntry.id}")
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
                    "<body>" +
                    "<a href=\"/dashboard\"><b>&lt;-</b></a>\n" +
                    "<br/>"

            val loggedInUsername = getUser(session)

            emails?.forEach {
                if (it.name == loggedInUsername || loggedInUsername == "admin") {
                    content += "<div>" +
                            "<a href='/useredit?id=${it.id}'>${it.name}</a>" +
                            "</div>"
                }
            }

            if (loggedInUsername == "admin") {
                content += "<br/><div>" +
                        "<a href='/useredit'>Add New User</a>" +
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

            val loggedInUsername = getUser(session)

            val format: MutableMap<String, Any?> = baseFormat(
                mapOf(
                    "email" to "",
                    "name" to "",
                    "receiveEmails" to true,
                    "id" to -1,
                    "extraMessage" to "",
                    "buttonMessage" to "Update User"
                )
            )
            val id = params["id"]?.get(0)?.toInt()
            println("id: $id")
            if (id != null && id > 0) {// Viewing/Updating existing user
                val emailEntry = runBlocking {
                    return@runBlocking MainActivity.instance?.emailRepository?.getById(id)
                } ?: return notFound()

                if (loggedInUsername != "admin" && emailEntry.name != loggedInUsername) {// Allow edits on own account & by admin on all
                    return forbidden()
                }

                if (method == Method.POST) {
                    session.parseBody(null)
                }

                format["email"] = emailEntry.address
                format["name"] = emailEntry.name
                format["receiveEmails"] = emailEntry.receiveEmails
                format["id"] = id
                format["buttonMessage"] = if (emailEntry.name != loggedInUsername) "Update User" else "Update Your Info"
                if (method == Method.POST && params.contains("email") && params.contains("name") && params.contains("receiveEmails")) {
                    val newEmail = params["email"]?.get(0)?.toString()
                    val newName = params["name"]?.get(0)?.toString()
                    val newReceiveEmails = params["receiveEmails"]?.get(0)?.toBoolean()
                    if (emailEntry.name == "admin" && newName != "admin") {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Cannot change admin username"
                        )
                    }
                    emailEntry.address = newEmail
                    emailEntry.name = newName
                    emailEntry.receiveEmails = newReceiveEmails ?: true
                    runBlocking {
                        MainActivity.instance?.emailRepository?.update(emailEntry)
                    }
                    return redirect("/useredit?id=$id#updated")
                }
            } else {// Creating new user
                if (loggedInUsername != "admin") {
                    return forbidden()
                }

                if (method == Method.POST) {
                    session.parseBody(null)
                }

                format["buttonMessage"] = "Add New User"
                if (method == Method.POST && params.contains("email") && params.contains("name") && params.contains("receiveEmails")) {
                    val newEmail = params["email"]?.get(0)?.toString()
                    val newName = params["name"]?.get(0)?.toString()
                    val newReceiveEmails = params["receiveEmails"]?.get(0)?.toBoolean()
                    if (newName == null || newName.length < 2) {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Please choose a longer name"
                        )
                    }
                    if (newEmail == null || newEmail.length < 2 || !newEmail.contains("@")) {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Please provide a valid email address"
                        )
                    }

                    if (newName == "admin") {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Cannot create another admin account"
                        )
                    }

                    val existingNameEntry = runBlocking {
                        return@runBlocking MainActivity.instance?.emailRepository?.getByName(newName)
                    }
                    if (existingNameEntry != null) {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Please choose a different name"
                        )
                    }
                    val existingMailEntry = runBlocking {
                        return@runBlocking MainActivity.instance?.emailRepository?.getByAddress(newEmail)
                    }
                    if (existingMailEntry != null) {
                        return newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "text/plain",
                            "Please use a different email address"
                        )
                    }

                    Log.i("WebServer", "Adding new user $newName ($newEmail)")

                    val newId = runBlocking {
                        return@runBlocking MainActivity.instance?.emailRepository?.nextId()
                    } ?: throw IllegalStateException("no next id for user")
                    val newEntry = Email()
                    newEntry.receiveEmails = newReceiveEmails ?: true
                    newEntry.isAdmin = false
                    newEntry.name = newName
                    newEntry.address = newEmail
                    newEntry.id = newId
                    val pass = randomString(8)
                    newEntry.pass = sha512(pass)

                    runBlocking {
                        MainActivity.instance?.emailRepository?.insert(newEntry)
                    }

                    format["email"] = newEntry.address
                    format["name"] = newEntry.name
                    format["receiveEmails"] = newEntry.receiveEmails
                    format["receive_emails"] = if (newEntry.receiveEmails) "checked" else ""
                    format["id"] = newEntry.id
                    format["extraMessage"] = "User created.<br/>" +
                            "New Password for $newName is <code>$pass</code><br/>" +
                            "Make sure to change it!"
                    return fileResponse(R.raw.useredit, format)
                }
                return fileResponse(R.raw.useredit, format)
            }
            return fileResponse(R.raw.useredit, format)
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
                    return fileResponse(R.raw.passwordchanged, baseFormat())
                }
            }
            return fileResponse(R.raw.passwordchange, baseFormat())
        }

        if ("/settings" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            if (getUser(session) != "admin") {
                return forbidden()
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
                return redirect("/settings#updated")
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

            val info = getPostBoxInfo()
            info["username"] = getUser(session)
            val json = JSONObject(info)
            val response = newFixedLengthResponse(json.toString())
            response.mimeType = "application/json"
            return response
        }

        if ("/api/testmail" == uri) {
            val a = checkAuth(session)
            if (a != WebAuth.AuthStatus.OK) {
                return a.response()
            }

            val loggedInUsername = getUser(session)
            val emailEntry = runBlocking {
                return@runBlocking MainActivity.instance?.emailRepository?.getByName(loggedInUsername!!)
            } ?: return notFound()

            EmailSender.sendEmail(
                mapOf(
                    (emailEntry.address!!) to (emailEntry.name ?: "PostBox Operator")
                ),
                "Test Mail",
                "This is a test email from the PostBox App! ${System.currentTimeMillis()}"
            )
            return newFixedLengthResponse("Email sent!")
        }

        if ("/favicon.ico" == uri) {
            return fileResponse(R.raw.favicon, mime = "image/x-icon")
        }
        if ("/style.css" == uri) {
            return fileResponse(R.raw.style, mime = "text/css")
        }


        Log.w("WebServer", "Failed to respond to URI $uri")
        return super.serve(session)
    }

    fun baseFormat(def: Map<String, Any?> = HashMap()): MutableMap<String, Any?> {
        val map = HashMap(def)
        map["styles"] =
            "<link href=\"https://fonts.googleapis.com/icon?family=Material+Icons\" rel=\"stylesheet\">" +
                    "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/css/materialize.min.css\">\n" +
                    "<link rel=\"stylesheet\" href=\"style.css\">" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        map["scripts"] = ""
        "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/materialize/1.0.0/js/materialize.min.js\"></script>" +
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
        format.forEach { (k, v) -> content = content?.replace("$$k$", v.toString()) }
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

    // https://www.baeldung.com/kotlin-random-alphanumeric-string
    fun randomString(len: Int): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..len)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }


}