package org.inventivetalent.postboxapp.database.repositories

import androidx.lifecycle.LiveData
import org.inventivetalent.postboxapp.database.daos.EmailDao
import org.inventivetalent.postboxapp.database.entities.Email

class EmailRepository(private val emailDao: EmailDao) {

    val allEmails: LiveData<List<Email>> = emailDao.getAll()

    suspend fun insert(vararg emails: Email) {
        emailDao.insert(*emails)
    }

    suspend fun getByAddress(address: String): Email? {
        return emailDao.getByAddress(address)
    }

}