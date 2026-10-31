/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Query
import androidx.room3.RewriteQueriesToDropUnusedColumns
import androidx.room3.rxjava3.RxDaoReturnTypeConverters
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity

@Dao
@DaoReturnTypeConverters(RxDaoReturnTypeConverters::class)
abstract class StreamHistoryDAO : BasicDAO<StreamHistoryEntity> {

    @Query("SELECT * FROM stream_history")
    abstract override fun getAll(): Flowable<List<StreamHistoryEntity>>

    @Query("DELETE FROM stream_history")
    abstract override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<StreamHistoryEntity>> {
        throw UnsupportedOperationException()
    }

    @get:Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY access_date DESC")
    abstract val history: Flowable<MutableList<StreamHistoryEntry>>

    @get:Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY uid ASC")
    abstract val historySortedById: Flowable<MutableList<StreamHistoryEntry>>

    @Query("SELECT * FROM stream_history WHERE stream_id = :streamId ORDER BY access_date DESC LIMIT 1")
    abstract fun getLatestEntry(streamId: Long): StreamHistoryEntity?

    @Query(
        "SELECT s.* FROM streams s INNER JOIN stream_history h ON s.uid = h.stream_id " +
            "ORDER BY h.access_date DESC LIMIT :limit"
    )
    abstract fun getRecentlyWatchedStreams(limit: Int): Flowable<MutableList<StreamEntity>>

    @Query("DELETE FROM stream_history WHERE stream_id = :streamId")
    abstract fun deleteStreamHistory(streamId: Long): Int

    // Select the latest entry and watch count for each stream id on history table
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM streams

        INNER JOIN (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history
            GROUP BY stream_id
        )
        ON uid = stream_id

        LEFT JOIN (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state )
        ON uid = stream_id_alias
        """
    )
    abstract fun getStatistics(): Flowable<MutableList<StreamStatisticsEntry>>
}
