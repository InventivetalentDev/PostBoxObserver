package org.inventivetalent.postboxapp.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.inventivetalent.postboxapp.database.entities.Email

@Dao
interface EmailDao {

    @Query("SELECT * FROM emails ")
    fun getAll(): LiveData<List<Email>>

    @Query("SELECT * FROM emails WHERE `address`=:address LIMIT 1")
    fun getByAddress(address: String): Email?

    @Insert
    fun insert(vararg emails: Email)

}
