package org.inventivetalent.postboxapp.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emails")
class Email {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @ColumnInfo(index = true)
    var address: String? = null

}
