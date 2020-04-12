package org.inventivetalent.postboxapp.database.repositories

import androidx.lifecycle.LiveData
import org.inventivetalent.postboxapp.database.daos.EmailDao
import org.inventivetalent.postboxapp.database.entities.Email

class EmailRepository(private val emailDao: EmailDao) {

    val size: LiveData<Int> = emailDao.size()
    val allEmails: LiveData<List<Email>> = emailDao.getAll()

    suspend fun insert(vararg emails: Email) {
        emailDao.insert(*emails)
    }

    suspend fun update(vararg emails: Email) {
        emailDao.update(*emails)
    }

    suspend fun getByAddress(address: String): Email? {
        return emailDao.getByAddress(address)
    }


    suspend fun getByName(name: String): Email? {
        return emailDao.getByName(name)
    }

    suspend fun getByNameOrAddress(query: String): Email? {
        return emailDao.getByNameOrAddress(query)
    }

}