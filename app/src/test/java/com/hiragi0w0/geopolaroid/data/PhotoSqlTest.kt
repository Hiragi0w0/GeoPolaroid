package com.hiragi0w0.geopolaroid.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Room の @Query と同じ SQL を実 SQLite（sqlite-jdbc）で実行し、条件・並び順・集計を検証する。
 * スキーマは PhotoEntity から Room が生成するものと同じ列構成。
 */
class PhotoSqlTest {
    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use {
            it.execute(
                "CREATE TABLE IF NOT EXISTS `photos` (`photoId` TEXT NOT NULL, `recordDate` TEXT NOT NULL, " +
                    "`imageUri` TEXT NOT NULL, `requestedAtUtc` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `placeName` TEXT, " +
                    "`saveState` TEXT NOT NULL, PRIMARY KEY(`photoId`))",
            )
            it.execute(
                "CREATE INDEX IF NOT EXISTS `index_photos_recordDate_saveState_requestedAtUtc_photoId` " +
                    "ON `photos` (`recordDate`, `saveState`, `requestedAtUtc`, `photoId`)",
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private fun insert(id: String, date: String, at: Long, state: String) {
        db.prepareStatement("INSERT INTO photos VALUES (?, ?, ?, ?, 'Asia/Tokyo', 1.0, 2.0, NULL, ?)").use {
            it.setString(1, id)
            it.setString(2, date)
            it.setString(3, "content://$id")
            it.setLong(4, at)
            it.setString(5, state)
            it.executeUpdate()
        }
    }

    /** Room 形式の :name パラメータを位置パラメータに置き換えて実行する。 */
    private fun <T> query(sql: String, vararg args: Pair<String, Any>, read: (ResultSet) -> T): List<T> {
        val (jdbcSql, order) = toJdbc(sql)
        return db.prepareStatement(jdbcSql).use { st ->
            order.forEachIndexed { i, name -> st.setObject(i + 1, args.toMap().getValue(name)) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
        }
    }

    private fun update(sql: String, vararg args: Pair<String, Any>): Int {
        val (jdbcSql, order) = toJdbc(sql)
        return db.prepareStatement(jdbcSql).use { st ->
            order.forEachIndexed { i, name -> st.setObject(i + 1, args.toMap().getValue(name)) }
            st.executeUpdate()
        }
    }

    private fun toJdbc(sql: String): Pair<String, List<String>> {
        val names = mutableListOf<String>()
        val replaced = Regex(":(\\w+)").replace(sql) { names += it.groupValues[1]; "?" }
        return replaced to names
    }

    private fun countActive(date: String) =
        query(PhotoSql.COUNT_ACTIVE, "recordDate" to date) { it.getInt(1) }.single()

    @Test
    fun savedByDateIsFilteredAndOrdered() {
        insert("b", "2026-10-22", 200, "SAVED")
        insert("a2", "2026-10-22", 100, "SAVED")
        insert("a1", "2026-10-22", 100, "SAVED")
        insert("p", "2026-10-22", 50, "PENDING")
        insert("other", "2026-10-21", 10, "SAVED")
        val ids = query(PhotoSql.SELECT_SAVED_BY_DATE, "recordDate" to "2026-10-22") { it.getString("photoId") }
        assertEquals(listOf("a1", "a2", "b"), ids)
    }

    @Test
    fun activeCountIncludesPendingAndSavedOfThatDateOnly() {
        insert("s", "2026-10-22", 1, "SAVED")
        insert("p", "2026-10-22", 2, "PENDING")
        insert("x", "2026-10-21", 3, "SAVED")
        assertEquals(2, countActive("2026-10-22"))
        assertEquals(0, countActive("2026-10-23"))
    }

    @Test
    fun savedDatesAreDistinctNewestFirstAndSkipPendingOnlyDays() {
        insert("a", "2026-10-20", 1, "SAVED")
        insert("b", "2026-10-21", 2, "SAVED")
        insert("c", "2026-10-21", 3, "SAVED")
        insert("d", "2026-10-19", 4, "PENDING")
        val rows = query(PhotoSql.SELECT_SAVED_DATES) { it.getString("recordDate") to it.getInt("photoCount") }
        assertEquals(listOf("2026-10-21" to 2, "2026-10-20" to 1), rows)
    }

    @Test
    fun markSavedOnlyAffectsPending() {
        insert("p", "2026-10-22", 1, "PENDING")
        assertEquals(1, update(PhotoSql.MARK_SAVED, "photoId" to "p"))
        assertEquals(0, update(PhotoSql.MARK_SAVED, "photoId" to "p"))
        assertEquals(listOf("p"), query(PhotoSql.SELECT_SAVED_BY_DATE, "recordDate" to "2026-10-22") { it.getString(1) })
    }

    @Test
    fun deletePendingAndExcludeSavedDoNotCrossStates() {
        insert("p", "2026-10-22", 1, "PENDING")
        insert("s", "2026-10-22", 2, "SAVED")
        assertEquals(0, update(PhotoSql.DELETE_PENDING, "photoId" to "s"))
        assertEquals(0, update(PhotoSql.DELETE_SAVED, "photoId" to "p"))
        assertEquals(1, update(PhotoSql.DELETE_SAVED, "photoId" to "s"))
        assertEquals(1, update(PhotoSql.DELETE_PENDING, "photoId" to "p"))
        assertEquals(0, countActive("2026-10-22"))
    }

    @Test
    fun pendingListAndFindByIdAndPlaceName() {
        insert("p2", "2026-10-22", 20, "PENDING")
        insert("p1", "2026-10-21", 10, "PENDING")
        insert("s", "2026-10-22", 5, "SAVED")
        assertEquals(listOf("p1", "p2"), query(PhotoSql.SELECT_PENDING) { it.getString("photoId") })

        assertEquals(1, update(PhotoSql.UPDATE_PLACE_NAME, "placeName" to "福岡市", "photoId" to "s"))
        val place = query(PhotoSql.SELECT_BY_ID, "photoId" to "s") { it.getString("placeName") }
        assertEquals(listOf("福岡市"), place)
        assertEquals(emptyList<String>(), query(PhotoSql.SELECT_BY_ID, "photoId" to "none") { it.getString(1) })
    }
}
