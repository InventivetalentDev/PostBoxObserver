package org.inventivetalent.postboxapp.database.daos

import androidx.lifecycle.LiveData
import androidx.room.*
import org.inventivetalent.postboxapp.database.entities.Email

@Dao
interface EmailDao {

    @Query("SELECT COUNT(id) FROM emails")
    fun size(): LiveData<Int>

    @Query("SELECT COUNT(id) FROM emails")
    fun getSize(): Int


    @Query("SELECT * FROM emails ")
    fun getAll(): List<Email>

    @Query("SELECT * FROM emails WHERE id=:id LIMIT 1")
    fun getById(id: Int): Email?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUpsert(vararg emails: Email)

}
