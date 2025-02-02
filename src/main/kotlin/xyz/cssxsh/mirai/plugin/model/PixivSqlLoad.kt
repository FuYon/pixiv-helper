package xyz.cssxsh.mirai.plugin.model

import net.mamoe.mirai.utils.*
import org.hibernate.*
import xyz.cssxsh.hibernate.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.pixiv.*
import xyz.cssxsh.pixiv.apps.*
import xyz.cssxsh.pixiv.fanbox.*
import java.sql.*
import javax.persistence.criteria.*
import kotlin.reflect.full.*

// region SqlConfiguration

/**
 * @see [Throwable.cause]
 */
internal inline fun <reified T> Throwable.findIsInstance(): T? {
    var next: Throwable? = this
    while (next !is T?) {
        next = next?.cause
    }
    return next
}

/**
 * @see [Throwable.findIsInstance]
 */
internal fun Throwable.findSQLException() = findIsInstance<SQLException>()

internal fun <R> useSession(lock: Any? = null, block: (session: Session) -> R): R {
    return if (lock == null) {
        PixivHibernateLoader.factory.openSession().use(block)
    } else {
        synchronized(lock) {
            PixivHibernateLoader.factory.openSession().use(block)
        }
    }
}

internal fun sqlite(): String {
    return useSession { session ->
        session.getDatabaseMetaData().url.substringAfter("jdbc:sqlite:", "")
    }
}

internal fun PixivEntity.replicate(mode: ReplicationMode = ReplicationMode.OVERWRITE) {
    val entity = this
    useSession(entity::class.companionObjectInstance) { session ->
        session.transaction.begin()
        try {
            session.replicate(entity, mode)
            session.transaction.commit()
        } catch (cause: Throwable) {
            session.transaction.rollback()
            throw cause
        }
    }
}

// endregion

// region ArtWorkInfo

internal fun ArtWorkInfo.SQL.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
    }.uniqueResult()
}

internal fun ArtWorkInfo.SQL.eros(age: AgeLimit): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("age"), age.ordinal)
            )
    }.uniqueResult()
}

internal fun ArtWorkInfo.SQL.eros(type: WorkContentType): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("type"), type.ordinal)
            )
    }.uniqueResult()
}

internal fun ArtWorkInfo.SQL.eros(sanity: SanityLevel): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                equal(artwork.get<Int>("sanity"), sanity.ordinal)
            )
    }.uniqueResult()
}

internal operator fun ArtWorkInfo.SQL.contains(pid: Long): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<Long>("pid"), pid))
    }.uniqueResult() > 0
}

internal operator fun ArtWorkInfo.SQL.get(id: Long): ArtWorkInfo? = useSession { session ->
    session.find(ArtWorkInfo::class.java, id)
}

internal fun ArtWorkInfo.SQL.list(ids: List<Long>): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(artwork.get<Long>("pid").`in`(ids))
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.interval(
    range: LongRange,
    marks: Long,
    pages: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last),
                lt(artwork.get("bookmarks"), marks),
                gt(artwork.get("pages"), pages)
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.deleted(range: LongRange): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isTrue(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last)
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.type(range: LongRange, type: WorkContentType): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                between(artwork.get("pid"), range.first, range.last),
                equal(artwork.get<Int>("type"), type.ordinal)
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.nocache(range: LongRange): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        val files = with(criteria.subquery(FileInfo::class.java)) {
            val file = from(FileInfo::class.java)
            select(file).where(equal(artwork.get<Long>("pid"), file.get<Long>("pid")))
        }
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                between(artwork.get("pid"), range.first, range.last),
                exists(files).not()
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.user(uid: Long): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
            )
    }.list().orEmpty()
}

internal fun ArtWorkInfo.SQL.tag(
    word: String,
    marks: Long,
    fuzzy: Boolean,
    age: AgeLimit,
    limit: Int
): List<ArtWorkInfo> = useSession { session ->
    val names = word.split(delimiters = TAG_DELIMITERS).filter { it.isNotBlank() }
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        val tag = { name: String ->
            criteria.subquery(Long::class.java).also { sub ->
                val info = sub.from(ArtWorkInfo::class.java)
                val records = artwork.joinList<ArtWorkInfo, TagRecord>("tags")
                sub.select(records.get("tid"))
                    .where(
                        equal(info.get<Long>("pid"), artwork.get<Long>("pid")),
                        or(
                            like(records.get("name"), if (fuzzy) "%$name%" else name),
                            like(records.get("translated"), if (fuzzy) "%$name%" else name)
                        )
                    )
            }
        }

        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                le(artwork.get<Int>("age"), age.ordinal),
                gt(artwork.get<Long>("bookmarks"), marks),
                *names.map { name -> exists(tag(name)) }.toTypedArray()
            )
            .orderBy(asc(rand()))
    }.setMaxResults(limit).list().orEmpty()
}

internal fun ArtWorkInfo.SQL.random(
    level: Int,
    marks: Long,
    age: AgeLimit,
    limit: Int
): List<ArtWorkInfo> = useSession { session ->
    session.withCriteria<ArtWorkInfo> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(artwork)
            .where(
                isFalse(artwork.get("deleted")),
                isTrue(artwork.get("ero")),
                le(artwork.get<Int>("age"), age.ordinal),
                gt(artwork.get<Int>("sanity"), level),
                gt(artwork.get<Long>("bookmarks"), marks)
            )
            .orderBy(asc(rand()))
    }.setMaxResults(limit).list().orEmpty()
}

internal fun ArtWorkInfo.SQL.delete(pid: Long, comment: String): Int = useSession(ArtWorkInfo) { session ->
    session.transaction.begin()
    try {
        val total = session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<Long>("pid"), pid)
                )
        }.executeUpdate()
        session.transaction.commit()
        total
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

internal fun ArtWorkInfo.SQL.deleteUser(uid: Long, comment: String): Int = useSession(ArtWorkInfo) { session ->
    session.transaction.begin()
    try {
        val total = session.withCriteriaUpdate<ArtWorkInfo> { criteria ->
            val artwork = criteria.from(ArtWorkInfo::class.java)
            criteria.set("caption", comment).set("deleted", true)
                .where(
                    isFalse(artwork.get("deleted")),
                    equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), uid)
                )
        }.executeUpdate()
        session.transaction.commit()
        total
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

internal fun SimpleArtworkInfo.toArtWorkInfo(caption: String = "") = ArtWorkInfo(
    pid = pid,
    title = title,
    caption = caption,
    author = UserBaseInfo(uid, name)
)

internal fun IllustInfo.toArtWorkInfo(author: UserBaseInfo = user.toUserBaseInfo()) = ArtWorkInfo(
    pid = pid,
    title = title,
    caption = caption,
    created = createAt.toEpochSecond(),
    pages = pageCount,
    sanity = sanityLevel.ordinal,
    type = type.ordinal,
    width = width,
    height = height,
    bookmarks = totalBookmarks ?: 0,
    comments = totalComments ?: 0,
    view = totalView ?: 0,
    age = age.ordinal,
    ero = isEro(),
    deleted = false,
    author = author
)

internal fun IllustInfo.saveTagRecords() {
    for (tag in tags) {
        TagRecord(name = tag.name, translated = tag.translatedName).replicate(ReplicationMode.IGNORE)
    }
}

internal fun IllustInfo.replicate() {
    if (pid == 0L) return
    saveTagRecords()
    useSession(ArtWorkInfo) { session ->
        session.transaction.begin()
        try {
            val artwork = toArtWorkInfo()
            artwork.tags = tags.mapNotNull { session.get(TagRecord::class.java, it.name) }
            session.replicate(artwork, ReplicationMode.OVERWRITE)
            session.transaction.commit()
            logger.info { "作品(${pid})<${createAt}>[${user.id}][${type}][${title}][${pageCount}]{${totalBookmarks}}信息已记录" }
        } catch (cause: Throwable) {
            session.transaction.rollback()
            logger.warning({ "作品(${pid})信息记录失败" }, cause)
            throw cause
        }
    }
}

internal fun Collection<IllustInfo>.replicate() {
    if (isEmpty()) return
    for (info in this) {
        info.saveTagRecords()
    }
    logger.verbose { "作品(${first().pid..last().pid})[${size}]信息即将更新" }
    useSession(ArtWorkInfo) { session ->
        session.transaction.begin()
        try {
            val users = HashMap<Long, UserBaseInfo>()

            for (info in this@replicate) {
                if (info.pid == 0L) continue
                val author = users.getOrPut(info.user.id) { info.user.toUserBaseInfo() }
                val artwork = info.toArtWorkInfo(author)
                artwork.tags = info.tags.mapNotNull { session.get(TagRecord::class.java, it.name) }
                session.replicate(artwork, ReplicationMode.OVERWRITE)
            }
            session.transaction.commit()
            logger.verbose { "作品{${first().pid..last().pid}}[${size}]信息已更新" }
        } catch (cause: Throwable) {
            session.transaction.rollback()
            logger.warning({ "作品{${first().pid..last().pid}}[${size}]信息记录失败" }, cause)
            throw cause
        }
    }
}

// endregion

// region UserInfo

internal fun UserInfo.toUserBaseInfo() = UserBaseInfo(id, name, account)

internal fun UserInfo.count(): Long = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val artwork = criteria.from(ArtWorkInfo::class.java)
        criteria.select(count(artwork))
            .where(equal(artwork.get<UserBaseInfo>("author").get<Long>("uid"), id))
    }.uniqueResult()
}

internal operator fun UserBaseInfo.SQL.get(account: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(equal(user.get<String?>("account"), account))
    }.uniqueResult()
}

internal fun UserBaseInfo.SQL.like(name: String): UserBaseInfo? = useSession { session ->
    session.withCriteria<UserBaseInfo> { criteria ->
        val user = criteria.from(UserBaseInfo::class.java)
        criteria.select(user)
            .where(like(user.get("name"), name))
    }.list().singleOrNull()
}

private val ScreenError = listOf("", "https", "http")

internal fun UserDetail.twitter(): String? {
    user.toUserBaseInfo().replicate()
    val screen = profile.twitterAccount?.takeUnless { it in ScreenError }
        ?: listOfNotNull(profile.twitterUrl, profile.webpage, user.comment)
            .firstNotNullOfOrNull { URL_TWITTER_SCREEN.find(it) }?.value
        ?: return null

    Twitter(screen, user.id).replicate()

    return screen
}

internal fun CreatorDetail.twitter(): String? {
    val screen = (profileLinks + description)
        .firstNotNullOfOrNull { URL_TWITTER_SCREEN.find(it) }?.value
        ?: return null

    Twitter(screen, user.userId).replicate()

    return screen
}

internal operator fun Twitter.SQL.get(screen: String): Twitter? = useSession { session ->
    session.find(Twitter::class.java, screen)
}

internal operator fun Twitter.SQL.get(uid: Long): List<Twitter> = useSession { session ->
    session.withCriteria<Twitter> { criteria ->
        val twitter = criteria.from(Twitter::class.java)
        criteria.select(twitter)
            .where(equal(twitter.get<Long>("uid"), uid))
    }.list().orEmpty()
}

// endregion

// region FileInfo

internal operator fun FileInfo.SQL.get(hash: String): List<FileInfo> = useSession { session ->
    session.withCriteria<FileInfo> { criteria ->
        val file = criteria.from(FileInfo::class.java)
        criteria.select(file)
            .where(like(file.get("md5"), hash))
    }.list().orEmpty()
}

internal operator fun FileInfo.SQL.get(pid: Long): List<FileInfo> = useSession { session ->
    session.withCriteria<FileInfo> { criteria ->
        val file = criteria.from(FileInfo::class.java)
        criteria.select(file)
            .where(equal(file.get<Long>("pid"), pid))
    }.list().orEmpty()
}

internal fun List<FileInfo>.replicate(): Unit = useSession(FileInfo) { session ->
    session.transaction.begin()
    try {
        for (item in this) session.replicate(item, ReplicationMode.OVERWRITE)
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

// endregion

// region Statistic

internal operator fun StatisticTaskInfo.SQL.contains(pair: Pair<String, Long>): Boolean = useSession { session ->
    session.withCriteria<Long> { criteria ->
        val (name, pid) = pair
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(count(task))
            .where(
                equal(task.get<Long>("pid"), pid),
                like(task.get("task"), name)
            )
    }.uniqueResult() > 0
}

internal fun StatisticTaskInfo.SQL.last(name: String): StatisticTaskInfo? = useSession { session ->
    session.withCriteria<StatisticTaskInfo> { criteria ->
        val task = criteria.from(StatisticTaskInfo::class.java)
        criteria.select(task)
            .where(like(task.get("task"), name))
            .orderBy(desc(task.get<Long>("timestamp")))
    }.setMaxResults(1).uniqueResult()
}

internal fun StatisticTagInfo.SQL.user(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("sender"), id))
    }.list().orEmpty()
}

internal fun StatisticTagInfo.SQL.group(id: Long): List<StatisticTagInfo> = useSession { session ->
    session.withCriteria<StatisticTagInfo> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(tag)
            .where(equal(tag.get<Long>("group"), id))
    }.list().orEmpty()
}

@Suppress("UNCHECKED_CAST")
internal fun StatisticTagInfo.SQL.top(limit: Int): List<Pair<String, Int>> = useSession { session ->
    session.withCriteria<Pair<*, *>> { criteria ->
        val tag = criteria.from(StatisticTagInfo::class.java)
        criteria.select(construct(Pair::class.java, tag.get<String>("tag"), count(tag)))
            .groupBy(tag.get<String>("tag"))
            .orderBy(desc(count(tag)))
    }.setMaxResults(limit).list().orEmpty() as List<Pair<String, Int>>
}

internal fun StatisticEroInfo.SQL.user(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("sender"), id))
    }.list().orEmpty()
}

internal fun StatisticEroInfo.SQL.group(id: Long): List<StatisticEroInfo> = useSession { session ->
    session.withCriteria<StatisticEroInfo> { criteria ->
        val ero = criteria.from(StatisticEroInfo::class.java)
        criteria.select(ero)
            .where(equal(ero.get<Long>("group"), id))
    }.list().orEmpty()
}

internal fun StatisticUserInfo.SQL.list(range: LongRange): List<StatisticUserInfo> = useSession { session ->
    session.withCriteria<StatisticUserInfo> { criteria ->
        val record = criteria.from(StatisticUserInfo::class.java)
        criteria.select(record)
            .where(
                gt(record.get<Long>("ero"), range.first),
                lt(record.get<Long>("count"), range.last)
            )
            .orderBy(asc(record.get<Long>("uid")))
    }.list()
}

internal fun AliasSetting.SQL.delete(alias: String): Unit = useSession(AliasSetting) { session ->
    val record = session.get(AliasSetting::class.java, alias) ?: return@useSession
    session.transaction.begin()
    try {
        session.delete(record)
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
    }
}

internal fun AliasSetting.SQL.all(): List<AliasSetting> = useSession { session ->
    session.withCriteria<AliasSetting> { criteria ->
        val alias = criteria.from(AliasSetting::class.java)
        criteria.select(alias)
    }.list().orEmpty()
}

internal operator fun AliasSetting.SQL.get(name: String): AliasSetting? = useSession { session ->
    session.find(AliasSetting::class.java, name)
}

internal fun PixivSearchResult.associate(): Unit = useSession(PixivSearchResult) { session ->
    session.transaction.begin()
    try {
        val info = session.find(ArtWorkInfo::class.java, pid)
        if (uid == 0L && info != null) {
            title = info.title
            uid = info.author.uid
            name = info.author.name
            session.replicate(this@associate, ReplicationMode.OVERWRITE)
        } else {
            session.replicate(this@associate, ReplicationMode.IGNORE)
        }
        session.transaction.commit()
    } catch (cause: Throwable) {
        session.transaction.rollback()
        throw cause
    }
}

internal operator fun PixivSearchResult.SQL.get(hash: String): PixivSearchResult? = useSession { session ->
    session.find(PixivSearchResult::class.java, hash)
}

internal fun PixivSearchResult.SQL.noCached(): List<PixivSearchResult> = useSession { session ->
    session.withCriteria<PixivSearchResult> { criteria ->
        val search = criteria.from(PixivSearchResult::class.java)
        val artwork = search.join<PixivSearchResult, ArtWorkInfo?>("artwork", JoinType.LEFT)
        criteria.select(search)
            .where(artwork.isNull)
    }.list().orEmpty()
}

// endregion
