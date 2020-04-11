package org.inventivetalent.postboxapp.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.inventivetalent.postboxapp.database.entities.Data

@Dao
interface DataDao {

    @Query("SELECT * FROM data ")
    fun getAll(): LiveData<List<Data>>

    @Query("SELECT * FROM data WHERE `key`=:key LIMIT 1")
    fun get(key: String): Data?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(data: Data)

}
