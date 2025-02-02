package xyz.cssxsh.mirai.plugin.command

import kotlinx.coroutines.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.descriptor.*
import net.mamoe.mirai.console.util.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*

object PixivBoomCommand : SimpleCommand(
    owner = PixivHelperPlugin,
    "boom", "射爆", "社保", "[炸弹]",
    description = "PIXIV色图爆炸指令"
), PixivHelperCommand {

    @OptIn(ConsoleExperimentalApi::class, ExperimentalCommandDescriptors::class)
    override val prefixOptional: Boolean = true

    @Handler
    suspend fun UserCommandSender.handle(limit: Int = EroChunk, word: String = "") = withHelper {
        val artworks = when {
            word.isEmpty() -> {
                ArtWorkInfo.random(level = 0, marks = 0, age = EroAgeLimit, limit = limit)
            }
            RankMode.values().any { it.name == word.uppercase() } -> {
                val mode = RankMode.valueOf(word.uppercase())
                val flow = getRank(mode = mode)
                val result = ArrayList<ArtWorkInfo>(limit)

                flow.collect { list ->
                    list.write().replicate()
                    for (illust in list) {
                        if (result.size >= limit) break
                        result.add(illust.toArtWorkInfo())
                    }
                }

                result
            }
            word.toLongOrNull() != null -> {
                ArtWorkInfo.user(uid = word.toLong()).shuffled().take(limit)
            }
            else -> {
                ArtWorkInfo.tag(word = word, marks = EroStandard.marks, fuzzy = false, age = TagAgeLimit, limit = limit)
            }
        }

        if (artworks.isEmpty()) return@withHelper "列表为空".toPlainText()

        PixivEroCommand += artworks

        val current = System.currentTimeMillis()

        sendMessage("开始将${artworks.size}个作品合成转发消息，请稍后...")

        val list = artworks.sortedBy { it.pid }.map { artwork ->
            val sender = (subject as? User) ?: (subject as Group).members.random()

            async {
                try {
                    val illust = getIllustInfo(pid = artwork.pid, flush = false)
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = illust.createAt.toEpochSecond().toInt(),
                        message = buildMessageByIllust(illust = illust)
                    )
                } catch (e: Throwable) {
                    logger.warning({ "BOOM BUILD 错误" }, e)
                    ForwardMessage.Node(
                        senderId = sender.id,
                        senderName = sender.nameCardOrNick,
                        time = artwork.created.toInt(),
                        message = "[${artwork.pid}]构建失败 ${e.message}".toPlainText()
                    )
                }
            }
        }.awaitAll()

        val millis = System.currentTimeMillis() - current

        logger.info { "BOOM BUILD ${word.ifEmpty { "RANDOM" }} ${list.size} in ${millis}ms 完成" }

        RawForwardMessage(list).render {
            title = "${word.ifEmpty { "随机" }}的快递"
        }
    }
}