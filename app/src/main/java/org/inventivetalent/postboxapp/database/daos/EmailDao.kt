package org.inventivetalent.postboxapp.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.inventivetalent.postboxapp.database.entities.Email

@Dao
interface EmailDao {

    @Query("SELECT COUNT(id) FROM emails")
    fun size(): LiveData<Int>

    @Query("SELECT * FROM emails ")
    fun getAll(): LiveData<List<Email>>

    @Query("SELECT * FROM emails WHERE `address`=:address LIMIT 1")
    fun getByAddress(address: String): Email?

    @Query("SELECT * FROM emails WHERE `name`=:name LIMIT 1")
    fun getByName(name: String): Email?

    @Query("SELECT * FROM emails WHERE `name`=:query OR `address`=:query LIMIT 1")
    fun getByNameOrAddress(query: String): Email?

    @Update
    fun update(vararg emails: Email)

    @Insert
    fun insert(vararg emails: Email)

}
