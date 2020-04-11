package org.inventivetalent.postboxapp.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "data", indices = [Index(value = ["key"], unique = true)])
class Data {

    constructor(key: String?, value: String?) {
        this.key = key
        this.value = value
    }

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @ColumnInfo
    var key: String? = null

    @ColumnInfo
    var value: String? = null

}
