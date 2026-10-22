package com.hkjustar.monitor.okhttp.room

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hkjustar.monitor.okhttp.data.MonitorData

@Dao
interface MonitorDao {
    @Query("SELECT * FROM monitor WHERE id > :lastId ORDER BY id DESC")
    fun queryByLastIdForAndroid(lastId: Long): LiveData<MutableList<MonitorData>>

    @Query("SELECT * FROM monitor ORDER BY id DESC LIMIT :limit OFFSET :offset")
    fun queryByOffsetForAndroid(limit: Int, offset: Int): LiveData<MutableList<MonitorData>>

    @Query("SELECT * FROM monitor")
    fun queryAllForAndroid(): LiveData<MutableList<MonitorData>>

    @Query("SELECT * FROM monitor WHERE path LIKE '%' || :keyword || '%' and id > :lastId ORDER BY id DESC")
    fun queryByLastId(keyword : String,lastId: Long): MutableList<MonitorData>

    @Query("SELECT * FROM monitor WHERE id > :lastId ORDER BY id DESC")
    fun queryByLastId(lastId: Long): MutableList<MonitorData>
    @Query("SELECT * FROM monitor WHERE path LIKE '%' || :keyword || '%' ORDER BY id DESC LIMIT :limit OFFSET :offset")
    fun queryByOffset(keyword : String , limit: Int, offset: Int): MutableList<MonitorData>

    @Query("""
        SELECT * FROM monitor
        WHERE ((:keyword = '' OR :keyword IS NULL) OR path LIKE '%' || :keyword || '%' OR url LIKE '%' || :keyword || '%')
          AND ((:content = '' OR :content IS NULL) OR responseBody LIKE '%' || :content || '%' OR requestBody LIKE '%' || :content || '%')
        ORDER BY id DESC LIMIT :limit OFFSET :offset
    """)
    fun queryByOffsetWithContent(keyword: String, content: String, limit: Int, offset: Int): MutableList<MonitorData>

    @Query("""
        SELECT * FROM monitor
        WHERE ((:keyword = '' OR :keyword IS NULL) OR path LIKE '%' || :keyword || '%' OR url LIKE '%' || :keyword || '%')
          AND ((:content = '' OR :content IS NULL) OR responseBody LIKE '%' || :content || '%' OR requestBody LIKE '%' || :content || '%')
          AND id > :lastId
        ORDER BY id DESC
    """)
    fun queryByLastIdWithContent(keyword: String, content: String, lastId: Long): MutableList<MonitorData>

    @Query("SELECT * FROM monitor")
    fun queryAll(): MutableList<MonitorData>

    @Insert
    fun insert(data: MonitorData)

    @Update
    fun update(data: MonitorData)

    @Query("DELETE FROM monitor WHERE id NOT IN (SELECT id FROM monitor ORDER BY id DESC LIMIT :maxLimit)")
    fun trimExceedingLimit(maxLimit: Int)

    @Query("DELETE FROM monitor")
    fun deleteAll()
}
