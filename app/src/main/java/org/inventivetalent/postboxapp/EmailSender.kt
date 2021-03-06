package org.inventivetalent.postboxapp

import android.util.Log
import androidx.annotation.RawRes
import com.mailjet.client.ClientOptions
import com.mailjet.client.MailjetClient
import com.mailjet.client.MailjetRequest
import com.mailjet.client.MailjetResponse
import com.mailjet.client.resource.Emailv31
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.lang.IllegalArgumentException


class EmailSender {

    companion object {

        suspend fun getApiKey(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_API_KEY")
        }

        suspend fun getApiSecret(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_API_SECRET")
        }

        suspend fun getSender(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_SENDER")
        }

        fun sendEmail(
            to: Map<String, String?>, subject: String,
            @RawRes contentFile: Int, contentFormat: Map<String, Any?> = mapOf(),
            from: String = "__config__"
        ) {
            var content: String? = MainActivity.instance?.resources?.openRawResource(contentFile)?.bufferedReader()
                .use { it?.readText() }
            contentFormat.forEach { (k, v) -> content = content?.replace("$$k$", v.toString()) }
            sendEmail(to, subject, content!!, from)
        }

        fun sendEmail(to: Map<String, String?>, subject: String, content: String, from: String = "__config__") {
            GlobalScope.launch {
                sendEmailBlocking(to, subject, content, from)
            }
        }

        suspend fun sendEmailBlocking(to: Map<String, String?>, subject: String, content: String, from0: String = "__config__") {
            val apiKey = getApiKey()
            if (apiKey == null) {
                Log.w("EmailSender", "Missing API Key, can't send email.")
                return
            }
            val apiSecret = getApiSecret()
            if (apiSecret == null) {
                Log.w("EmailSender", "Missing API Secret, can't send email.")
                return
            }
            var from = from0
            if (from == "__config__") {
                val sender = getSender()
                if (sender == null) {
                    Log.w("EmailSender", "Missing Sender email, can't send email.")
                    return
                }
                from = sender
            }

            val client: MailjetClient
            val request: MailjetRequest
            val response: MailjetResponse
            client = MailjetClient(
                apiKey,
                apiSecret,
                ClientOptions("v3.1")
            )

            Log.i("EmailSender", "Sending email to ${to.size} receivers:")

            val toArray = JSONArray()
            to.forEach { (k, v) ->
                Log.i("EmailSender", k)
                val obj = JSONObject()
                    .put("Email", k)
                if (v != null) obj.put("Name", v)
                toArray.put(obj)
            }

            request = MailjetRequest(Emailv31.resource)
                .property(
                    Emailv31.MESSAGES, JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    Emailv31.Message.FROM, JSONObject()
                                        .put("Email", from)
                                        .put("Name", "PostBox")
                                )
                                .put(
                                    Emailv31.Message.TO, toArray
                                )
                                .put(Emailv31.Message.SUBJECT, subject)
                                .put(Emailv31.Message.HTMLPART, content)
                        )
                )
            response = client.post(request)
            println(response.data)
            System.out.println(response.getStatus())
        }

    }

}