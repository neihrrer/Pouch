package dev.trove.app.data

import dev.trove.app.R
import dev.trove.app.data.db.AppDatabase
import dev.trove.app.data.db.ArticleEntity
import dev.trove.app.data.db.ArticleTagCrossRef
import dev.trove.app.data.db.FeedCategoryEntity
import dev.trove.app.data.db.FeedEntity
import dev.trove.app.data.db.FolderEntity
import dev.trove.app.data.db.HighlightEntity
import dev.trove.app.data.db.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full-library backup: folders, tags, saved articles (with tags, folder,
 * read/favorite state) and feed subscriptions. Plain JSON.
 */
class BackupRepository(private val context: android.content.Context, private val db: AppDatabase) {

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val a = db.articleDao()
        val f = db.folderDao()
        val t = db.tagDao()
        val fd = db.feedDao()
        val c = db.feedCategoryDao()

        val folders = f.getAll()
        val tags = t.getAll()
        val categories = c.getAll()

        val root = JSONObject().put("app", "pouch").put("version", 1)
        root.put("folders", JSONArray().also { arr ->
            folders.forEach { it -> arr.put(JSONObject().put("name", it.name).put("color", it.colorIndex)) }
        })
        root.put("tags", JSONArray().also { arr ->
            tags.forEach { it -> arr.put(JSONObject().put("name", it.name).put("color", it.colorIndex)) }
        })
        root.put("feeds", JSONArray().also { arr ->
            fd.getAll().forEach { feed ->
                arr.put(JSONObject()
                    .put("title", feed.title).put("url", feed.url)
                    .put("category", feed.categoryId?.let { cid -> categories.firstOrNull { it.id == cid }?.name } ?: JSONObject.NULL))
            }
        })
        root.put("articles", JSONArray().also { arr ->
            a.getAllSaved().forEach { art ->
                arr.put(JSONObject()
                    .put("url", art.url).put("title", art.title)
                    .put("siteName", art.siteName ?: JSONObject.NULL)
                    .put("excerpt", art.excerpt ?: JSONObject.NULL)
                    .put("contentHtml", art.contentHtml ?: JSONObject.NULL)
                    .put("contentText", art.contentText ?: JSONObject.NULL)
                    .put("leadImage", art.leadImageUrl ?: JSONObject.NULL)
                    .put("favicon", art.faviconUrl ?: JSONObject.NULL)
                    .put("folder", art.folderId?.let { fid -> folders.firstOrNull { x -> x.id == fid }?.name } ?: JSONObject.NULL)
                    .put("tags", JSONArray().also { ta -> t.getTagsForArticle(art.id).forEach { ta.put(it.name) } })
                    .put("read", art.isRead).put("favorite", art.isFavorite)
                    .put("addedAt", art.addedAt)
                    .put("highlights", JSONArray().also { ha ->
                        a.getHighlights(art.id).forEach { h ->
                            ha.put(JSONObject()
                                .put("text", h.text).put("color", h.colorIndex)
                                .put("block", h.blockIndex)
                                .put("start", h.startOffset).put("end", h.endOffset))
                        }
                    }))
            }
        })
        root.toString(1)
    }

    /** Imports a backup. Returns a summary string. */
    suspend fun import(json: String): String = withContext(Dispatchers.IO) {
        val a = db.articleDao()
        val f = db.folderDao()
        val t = db.tagDao()
        val fd = db.feedDao()
        val c = db.feedCategoryDao()

        val root = JSONObject(json)
        val folderIds = mutableMapOf<String, Long>()
        (root.optJSONArray("folders") ?: JSONArray()).let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = f.insert(FolderEntity(name = o.getString("name"), colorIndex = o.optInt("color")))
                folderIds[o.getString("name")] = id
            }
        }
        val tagIds = mutableMapOf<String, Long>()
        (root.optJSONArray("tags") ?: JSONArray()).let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                tagIds[o.getString("name")] = t.insert(TagEntity(name = o.getString("name"), colorIndex = o.optInt("color")))
            }
        }
        val categoryIds = mutableMapOf<String, Long>()
        (root.optJSONArray("feeds") ?: JSONArray()).let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val catName = if (o.isNull("category")) null else o.getString("category")
                val catId = catName?.let { name ->
                    categoryIds.getOrPut(name) {
                        c.getByName(name)?.id ?: c.insert(FeedCategoryEntity(name = name))
                    }
                }
                fd.insert(FeedEntity(title = o.getString("title"), url = o.getString("url"), categoryId = catId))
            }
        }
        var articleCount = 0
        (root.optJSONArray("articles") ?: JSONArray()).let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.getString("url")
                if (a.getByUrl(url) != null) continue
                val folderId = if (o.isNull("folder")) null else folderIds[o.getString("folder")]
                val id = a.insert(ArticleEntity(
                    url = url,
                    title = o.getString("title"),
                    siteName = if (o.isNull("siteName")) null else o.getString("siteName"),
                    excerpt = if (o.isNull("excerpt")) null else o.getString("excerpt"),
                    contentHtml = if (o.isNull("contentHtml")) null else o.getString("contentHtml"),
                    contentText = if (o.isNull("contentText")) null else o.getString("contentText"),
                    leadImageUrl = if (o.isNull("leadImage")) null else o.getString("leadImage"),
                    faviconUrl = if (o.isNull("favicon")) null else o.getString("favicon"),
                    folderId = folderId,
                    isRead = o.optBoolean("read"), isFavorite = o.optBoolean("favorite"),
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                ))
                val tagNames = o.optJSONArray("tags")
                if (tagNames != null && tagNames.length() > 0) {
                    val refs = mutableListOf<ArticleTagCrossRef>()
                    for (j in 0 until tagNames.length()) {
                        val tid = tagIds[tagNames.getString(j)] ?: continue
                        refs += ArticleTagCrossRef(articleId = id, tagId = tid)
                    }
                    t.insertRefs(refs)
                }
                val highlights = o.optJSONArray("highlights")
                if (highlights != null) {
                    for (j in 0 until highlights.length()) {
                        val h = highlights.getJSONObject(j)
                        a.insertHighlight(HighlightEntity(
                            articleId = id,
                            text = h.getString("text"),
                            colorIndex = h.optInt("color"),
                            blockIndex = h.optInt("block"),
                            startOffset = h.optInt("start"),
                            endOffset = h.optInt("end"),
                        ))
                    }
                }
                articleCount++
            }
        }
        context.getString(R.string.snack_backup_summary, articleCount, folderIds.size, tagIds.size)
    }
}
