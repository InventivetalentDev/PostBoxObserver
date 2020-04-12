package org.inventivetalent.postboxapp

import android.util.Log
import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class EmailSender {

    companion object{

        suspend fun getApiKey(): String? {
            return MainActivity.instance?.dataRepository?.get("SENDGRID_API_KEY")
        }

        fun sendEmail(mail: Mail) {
            GlobalScope.launch {
               sendEmailBlocking(mail)
            }
        }

        suspend fun sendEmailBlocking(mail: Mail) {
            val apiKey = getApiKey()
            if(apiKey==null) return

            val sendGrid = SendGrid(apiKey)
            val request = Request()
            try {
                request.method = Method.POST
                request.endpoint = "mail/send"
                request.body =mail.build()
                val response = sendGrid.api(request)
                println(response.statusCode)
                println(response.body)
                println(response.headers)
            } catch (e: Exception) {
                Log.w("EmailSender", "Email Send Exception", e)
            }
        }

    }

}