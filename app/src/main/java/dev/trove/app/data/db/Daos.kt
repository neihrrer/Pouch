package dev.trove.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles WHERE id = :id")
    fun observeById(id: Long): Flow<ArticleWithTags?>

    @Transaction
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE feedItemId = :itemId LIMIT 1")
    suspend fun getByFeedItemId(itemId: String): ArticleEntity?

    @Query("UPDATE articles SET feedId = NULL WHERE feedId = :feedId")
    suspend fun unlinkFeed(feedId: Long)

    @Query("SELECT * FROM articles WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(article: ArticleEntity): Long

    @Update
    suspend fun update(article: ArticleEntity)

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE articles SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE articles SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query(
        """
        UPDATE articles SET readProgress = :progress,
        scrollIndex = :scrollIndex, scrollOffset = :scrollOffset,
        isRead = CASE WHEN :progress >= 0.85 THEN 1 ELSE isRead END
        WHERE id = :id
        """
    )
    suspend fun savePosition(id: Long, progress: Float, scrollIndex: Int, scrollOffset: Int)

    @Query("UPDATE articles SET offlineReady = :ready, localImages = :map, offlineAt = :at WHERE id = :id")
    suspend fun setOffline(id: Long, ready: Boolean, map: String?, at: Long)

    @Query("SELECT * FROM articles")
    suspend fun getAll(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE saved = 1")
    suspend fun getAllSaved(): List<ArticleEntity>

    @Query("SELECT id FROM articles WHERE saved = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomSavedId(): Long?

    @Query("SELECT * FROM highlights WHERE articleId = :articleId ORDER BY createdAt")
    fun observeHighlights(articleId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE articleId = :articleId ORDER BY createdAt")
    suspend fun getHighlights(articleId: Long): List<HighlightEntity>

    @Insert
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE articleId = :articleId")
    suspend fun deleteHighlightsForArticle(articleId: Long)

    @Delete
    suspend fun delete(article: ArticleEntity)

    /** Library articles only (feed items excluded until saved). */
    @Transaction
    @Query("SELECT * FROM articles WHERE saved = 1 ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ArticleWithTags>>

    /** All items of one feed, saved or not — for feed browsing. */
    @Transaction
    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY addedAt DESC")
    fun observeByFeed(feedId: Long): Flow<List<ArticleWithTags>>

    @Query("UPDATE articles SET saved = 1, addedAt = :now WHERE id = :id")
    suspend fun saveFeedItem(id: Long, now: Long)

    @Query("UPDATE articles SET fullContent = 1 WHERE id = :id")
    suspend fun setFullContent(id: Long)

    @Query("SELECT * FROM articles WHERE feedId IS NOT NULL ORDER BY addedAt DESC")
    @Transaction
    fun observeAllFeedItems(): Flow<List<ArticleWithTags>>

    @Query("DELETE FROM articles WHERE saved = 0 AND feedId IS NOT NULL AND addedAt < :cutoff")
    suspend fun deleteOldFeedItems(cutoff: Long)

    @Transaction
    @Query("SELECT * FROM articles WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<ArticleWithTags>>

    @Transaction
    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN article_tags at ON at.articleId = a.id
        WHERE at.tagId = :tagId
        ORDER BY a.addedAt DESC
        """
    )
    fun observeByTag(tagId: Long): Flow<List<ArticleWithTags>>

    @Transaction
    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN articleFts f ON f.rowid = a.id
        WHERE articleFts MATCH :query AND a.saved = 1
        ORDER BY a.addedAt DESC
        """
    )
    fun search(query: String): Flow<List<ArticleWithTags>>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE saved = 1 AND isRead = 0")
    fun observeUnreadCount(): Flow<Int>
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY position, createdAt")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY position, createdAt")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query(
        """
        SELECT f.*, (SELECT COUNT(*) FROM articles a WHERE a.folderId = f.id) AS articleCount
        FROM folders f ORDER BY f.position, f.createdAt
        """
    )
    fun observeAllWithCount(): Flow<List<FolderWithCount>>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT MAX(position) FROM folders")
    suspend fun maxPosition(): Int?
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<TagEntity>

    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM article_tags at WHERE at.tagId = t.id) AS articleCount
        FROM tags t ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeAllWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM article_tags WHERE articleId = :articleId")
    suspend fun getArticleTagRefs(articleId: Long): List<ArticleTagCrossRef>

    @Query("SELECT t.* FROM tags t INNER JOIN article_tags at ON at.tagId = t.id WHERE at.articleId = :articleId")
    suspend fun getTagsForArticle(articleId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<ArticleTagCrossRef>)

    @Query("DELETE FROM article_tags WHERE articleId = :articleId")
    suspend fun clearArticleRefs(articleId: Long)

    @Query("DELETE FROM article_tags WHERE tagId = :tagId")
    suspend fun clearRefsForTag(tagId: Long)
}

@Dao
interface FeedCategoryDao {

    @Query("SELECT * FROM feed_categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FeedCategoryEntity>>

    @Query("SELECT * FROM feed_categories ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<FeedCategoryEntity>

    @Query("SELECT * FROM feed_categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): FeedCategoryEntity?

    @Insert
    suspend fun insert(category: FeedCategoryEntity): Long

    @Delete
    suspend fun delete(category: FeedCategoryEntity)

    @Query("UPDATE feeds SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearFeedCategory(categoryId: Long)
}

@Dao
interface FeedDao {

    @Query(
        """
        SELECT f.*, (SELECT COUNT(*) FROM articles a WHERE a.feedId = f.id) AS articleCount,
        (SELECT COUNT(*) FROM articles a WHERE a.feedId = f.id AND a.isRead = 0) AS unreadCount
        FROM feeds f ORDER BY f.title COLLATE NOCASE
        """
    )
    fun observeAllWithCount(): Flow<List<FeedWithCount>>

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): FeedEntity?

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): FeedEntity?

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<FeedEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Update
    suspend fun update(feed: FeedEntity)

    @Delete
    suspend fun delete(feed: FeedEntity)
}
