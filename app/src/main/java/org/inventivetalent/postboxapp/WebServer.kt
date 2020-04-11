package org.inventivetalent.postboxapp

import fi.iki.elonen.NanoHTTPD

class WebServer(port:Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession?): Response {
        if (session == null) return super.serve(session)
        val uri = session.uri

        if ("/" == uri) {
            return newFixedLengthResponse("Hello World!")
        }

        return super.serve(session)
    }

}