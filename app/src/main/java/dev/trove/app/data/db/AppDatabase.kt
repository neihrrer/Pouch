package dev.trove.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArticleEntity::class,
        FolderEntity::class,
        TagEntity::class,
        ArticleTagCrossRef::class,
        ArticleFts::class,
        FeedEntity::class,
        FeedCategoryEntity::class,
        HighlightEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun feedDao(): FeedDao
    abstract fun feedCategoryDao(): FeedCategoryDao

    companion object {
        /** v2: offline images — `offlineReady` flag + `localImages` JSON map. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN offlineReady INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN localImages TEXT")
            }
        }

        /** v3: offline retention — `offlineAt` download timestamp. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN offlineAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4: nested folders (`folders.parentId`) and the RSS reader
         * (feeds, feed_categories tables + article feed linkage).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN parentId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS feed_categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS feeds (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "url TEXT NOT NULL, title TEXT NOT NULL, siteUrl TEXT, " +
                        "categoryId INTEGER, addedAt INTEGER NOT NULL, " +
                        "lastFetchedAt INTEGER NOT NULL DEFAULT 0, " +
                        "fetchFailed INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_feeds_url ON feeds (url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feeds_categoryId ON feeds (categoryId)")
                db.execSQL("ALTER TABLE articles ADD COLUMN feedId INTEGER")
                db.execSQL("ALTER TABLE articles ADD COLUMN feedItemId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_articles_feedId ON articles (feedId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_articles_feedItemId ON articles (feedItemId)")
            }
        }

        /**
         * v7: highlights become precise text ranges (block + offsets).
         * Legacy paragraph-level highlights are incompatible — cleared.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE highlights ADD COLUMN blockIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE highlights ADD COLUMN startOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE highlights ADD COLUMN endOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM highlights")
            }
        }

        /** v6: full-content flag + article highlights. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN fullContent INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS highlights (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "articleId INTEGER NOT NULL, text TEXT NOT NULL, " +
                        "colorIndex INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_articleId ON highlights (articleId)")
            }
        }

        /** v5: Library flag — feed items are stored unsaved until added. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN saved INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "trove.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
    }
}
