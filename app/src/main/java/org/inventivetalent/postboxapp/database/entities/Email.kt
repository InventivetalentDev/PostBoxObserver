package org.inventivetalent.postboxapp.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "emails", indices = [Index("address",unique = true)])
class Email {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @ColumnInfo
    var address: String? = null

    @ColumnInfo(index = true)
    var name: String? = null

    @ColumnInfo
    var pass: String? = null

}
