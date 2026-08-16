package dev.trove.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val position: Int = 0,
    /** Nested folders: parent folder id, null = top level. */
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["feedId"]),
        Index(value = ["feedItemId"]),
    ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val siteName: String? = null,
    val byline: String? = null,
    val excerpt: String? = null,
    val contentHtml: String? = null,
    val contentText: String? = null,
    val leadImageUrl: String? = null,
    val faviconUrl: String? = null,
    val folderId: Long? = null,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val readProgress: Float = 0f,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val publishedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val fetchedAt: Long = 0L,
    /** True once images have been downloaded for offline reading. */
    val offlineReady: Boolean = false,
    /** JSON map: original image URL -> local absolute path. */
    val localImages: String? = null,
    /** When the offline copy was downloaded (retention window). */
    val offlineAt: Long = 0L,
    /** Source feed (RSS) this article came from. */
    val feedId: Long? = null,
    /** Feed item guid — dedupe key for feed imports. */
    val feedItemId: String? = null,
    /** True once the user added it to their Library (feed items start unsaved). */
    val saved: Boolean = true,
    /** True once the full article page was fetched and extracted. */
    val fullContent: Boolean = true,
)

@Entity(
    tableName = "article_tags",
    primaryKeys = ["articleId", "tagId"],
    indices = [Index(value = ["tagId"])],
)
data class ArticleTagCrossRef(
    val articleId: Long,
    val tagId: Long,
)

/** Full-text index over article title + text (kept in sync by Room). */
@Fts4(contentEntity = ArticleEntity::class)
@Entity(tableName = "articleFts")
data class ArticleFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val title: String = "",
    val contentText: String = "",
)

data class ArticleWithTags(
    @Embedded val article: ArticleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ArticleTagCrossRef::class,
            parentColumn = "articleId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity> = emptyList(),
    @Relation(parentColumn = "folderId", entityColumn = "id")
    val folder: FolderEntity? = null,
)

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val articleCount: Int,
    val parentName: String? = null,
)

data class TagWithCount(
    @Embedded val tag: TagEntity,
    val articleCount: Int,
)

// ---------------------------------------------------------------------------
// RSS feeds
// ---------------------------------------------------------------------------

@Entity(tableName = "feed_categories")
data class FeedCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true), Index(value = ["categoryId"])],
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val siteUrl: String? = null,
    val categoryId: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastFetchedAt: Long = 0L,
    val fetchFailed: Boolean = false,
)

data class FeedWithCount(
    @Embedded val feed: FeedEntity,
    val articleCount: Int,
    val unreadCount: Int,
)

@Entity(
    tableName = "highlights",
    indices = [Index(value = ["articleId"])],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    /** The exact highlighted text (selection). */
    val text: String,
    val colorIndex: Int = 0,
    /** Block index within the article content, and character range in
     *  that block's plain text. */
    val blockIndex: Int = 0,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
