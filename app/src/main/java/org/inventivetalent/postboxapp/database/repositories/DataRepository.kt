package org.inventivetalent.postboxapp.database.repositories

import org.inventivetalent.postboxapp.database.daos.DataDao
import org.inventivetalent.postboxapp.database.entities.Data

class DataRepository(private val dataDao: DataDao) {


    suspend fun set(key: String, value: String) {
        dataDao.set(Data(key,value))
    }

    suspend fun get(key: String): String? {
        return dataDao.get(key)?.value
    }

}