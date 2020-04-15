package org.inventivetalent.postboxapp.database.repositories

import androidx.lifecycle.LiveData
import org.inventivetalent.postboxapp.database.daos.EmailDao
import org.inventivetalent.postboxapp.database.entities.Email

class EmailRepository(private val emailDao: EmailDao) {

    val size: LiveData<Int> = emailDao.size()

    suspend fun insert(vararg emails: Email) {
        emailDao.insert(*emails)
    }

    suspend fun insertUpsert(vararg emails: Email) {
        emailDao.insertUpsert(*emails)
    }

    suspend fun update(vararg emails: Email) {
        emailDao.update(*emails)
    }

    suspend fun getAll(): List<Email> {
        return emailDao.getAll()
    }

    suspend fun getById(id: Int): Email? {
        return emailDao.getById(id)
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

    suspend fun nextId(): Int {
        return emailDao.nextId()
    }

}