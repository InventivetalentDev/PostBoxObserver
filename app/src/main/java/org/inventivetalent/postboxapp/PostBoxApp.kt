package org.inventivetalent.postboxapp

import android.app.Application

class PostBoxApp : Application() {

    companion object {
        var instance: PostBoxApp? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

}