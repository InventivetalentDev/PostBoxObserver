package org.inventivetalent.postboxapp

import android.util.Log
import com.mailjet.client.ClientOptions
import com.mailjet.client.MailjetClient
import com.mailjet.client.MailjetRequest
import com.mailjet.client.MailjetResponse
import com.mailjet.client.resource.Emailv31
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


class EmailSender {

    companion object{

        suspend fun getApiKey(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_API_KEY")
        }

        suspend fun getApiSecret(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_API_SECRET")
        }

        suspend fun getSender(): String? {
            return MainActivity.instance?.dataRepository?.get("MAILJET_SENDER")
        }

        fun sendEmail(to: String, toName: String, subject: String, content: String, from: String = "__config__") {
            GlobalScope.launch {
                sendEmailBlocking(to, toName, subject, content, from)
            }
        }

        suspend fun sendEmailBlocking(to: String, toName: String, subject: String, content: String, from0: String = "__config__") {
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
                from =  sender
            }

            val client: MailjetClient
            val request: MailjetRequest
            val response: MailjetResponse
            client = MailjetClient(
                apiKey,
                apiSecret,
                ClientOptions("v3.1")
            )
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
                                    Emailv31.Message.TO, JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("Email", to)
                                                .put("Name", toName)
                                        )
                                )
                                .put(Emailv31.Message.SUBJECT, subject)
                                .put(Emailv31.Message.TEXTPART, content)
                        )
                )
            response = client.post(request)
            println(response.data)
            System.out.println(response.getStatus())
        }

    }

}