package org.inventivetalent.postboxapp.web

import android.util.Base64
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.inventivetalent.postboxapp.MainActivity
import java.security.MessageDigest
import java.util.*
import kotlin.experimental.and

class WebAuth {

    companion object {

        fun checkAuth(session: NanoHTTPD.IHTTPSession?): AuthStatus {
            if (session != null) {
                if (!session.headers.contains("authorization")) {
                    Log.w("WebAuth", "Denying access from client without authorization header")
                    return AuthStatus.UNAUTHORIZED
                }
                val auth = session.headers["authorization"]
                if (auth.isNullOrEmpty()) {
                    Log.w("WebAuth", "Denying access from client without authorization header")
                    return AuthStatus.UNAUTHORIZED
                }
                if (!auth.toLowerCase(Locale.ROOT).startsWith("basic")) {
                    Log.w("WebAuth", "Denying access from client with non-'Basic' auth")
                    return AuthStatus.UNAUTHORIZED
                }
                val decodedBytes: ByteArray
                try {
                    decodedBytes = Base64.decode(auth.substring("Basic".length), Base64.DEFAULT)
                } catch (e: Exception) {
                    Log.w("WebAuth", "Denying access from client with invalid Base64 header", e)

                    return AuthStatus.FORBIDDEN
                }
                val decoded = String(decodedBytes)
                if (!decoded.contains(":")) {
                    Log.w("WebAuth", "Denying access from client with invalid auth string")
                    return AuthStatus.FORBIDDEN
                }
                val split = decoded.split(":")
                val user = split[0]
                val pass = split[1]

                Log.i("WebAuth", "Login attempt from '$user' on ${session.uri}")

                val emailEntry = runBlocking {
                    return@runBlocking MainActivity.instance?.emailRepository?.getByNameOrAddress(
                        user
                    )
                }
                if (emailEntry == null) {
                    Log.w("WebAuth", "Denying access to '$user' who was not found in database")
                    return AuthStatus.FORBIDDEN
                }
                if (sha512(pass) != emailEntry.pass) {
                    Log.w(
                        "WebAuth",
                        "Denying access to '$user' who was found in database but provided the wrong password"
                    )
                    return AuthStatus.FORBIDDEN
                }

                Log.i(
                    "WebAuth",
                    "Granting access to '$user' (${emailEntry.address}) on ${session.uri}"
                )
                return AuthStatus.OK
            }
            return AuthStatus.UNAUTHORIZED
        }

        fun getUser(session: NanoHTTPD.IHTTPSession): String? {
            val auth = getUserPass(session) ?: return null
            return auth[0]
        }

        fun getUserPass(session: NanoHTTPD.IHTTPSession): List<String>? {
            if (!session.headers.contains("authorization")) {
                return null
            }
            val auth = session.headers["authorization"]
            if (auth.isNullOrEmpty()) {
                return null
            }
            if (!auth.toLowerCase(Locale.ROOT).startsWith("basic")) {
                return null
            }
            val decodedBytes: ByteArray
            try {
                decodedBytes = Base64.decode(auth.substring("Basic".length), Base64.DEFAULT)
            } catch (e: Exception) {
                return null
            }
            val decoded = String(decodedBytes)
            if (!decoded.contains(":")) {
                Log.w("WebAuth", "Denying access from client with invalid auth string")
                return null
            }
            val split = decoded.split(":")

            return listOf(split[0], split[1])
        }

        // https://stackoverflow.com/a/46510436/6257838
        fun sha512(s: String): String {
            val md = MessageDigest.getInstance("SHA-512")
            val digest = md.digest(s.toByteArray())
            val sb = StringBuilder()
            for (i in digest.indices) {
                sb.append(((digest[i] and 0xff.toByte()) + 0x100).toString(16).substring(1))
            }
            return sb.toString()
        }

        fun unauthorized(presentRealm: Boolean = true, content: String = "Unauthorized", mime: String = "text/plain"): NanoHTTPD.Response {
            val response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.UNAUTHORIZED,
                mime,
                content
            )
            if (presentRealm)
                response.addHeader("WWW-Authenticate", "Basic realm=\"PostBox Admin\"")
            return response
        }

        fun forbidden(content: String = "Forbidden", mime: String = "text/plain"): NanoHTTPD.Response {
            val response = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                mime,
                content
            )
            return response
        }

    }

    enum class AuthStatus(code: Int) {
        OK(200), UNAUTHORIZED(401) {
            override fun response() =
                unauthorized()
        },
        FORBIDDEN(403) {
            override fun response() =
                forbidden()
        };

        open fun response(): NanoHTTPD.Response = NanoHTTPD.newFixedLengthResponse("OK")
    }

}