package xyz.cssxsh.mirai.plugin.command

import io.ktor.http.*
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.message.data.*
import xyz.cssxsh.mirai.plugin.*
import xyz.cssxsh.mirai.plugin.data.*
import xyz.cssxsh.mirai.plugin.model.*
import xyz.cssxsh.pixiv.*
import java.time.*

object PixivTaskCommand : CompositeCommand(
    owner = PixivHelperPlugin,
    "task",
    description = "PIXIV定时器",
    overrideContext = PixivCommandArgumentContext
) {
    private const val TASK_DURATION = 3 * 60

    private const val MINUTE = 60 * 1000L

    private suspend fun CommandSenderOnMessage<*>.setTask(block: BuildTask) = withHelper {
        val (name, task) = block()
        PixivHelperScheduler.setTimerTask(name = name, info = task)
        "定时任务${name}已添加，间隔${task.interval / MINUTE}min"
    }

    @SubCommand
    @Description("推送用户新作品")
    suspend fun CommandSenderOnMessage<*>.user(uid: Long, duration: Int = TASK_DURATION) = setTask {
        "User($uid)[${contact}]" to
            TimerTask.User(uid = uid, interval = duration * MINUTE, delegate = contact.delegate)
    }

    @SubCommand
    @Description("推送排行榜新作品")
    suspend fun CommandSenderOnMessage<*>.rank(mode: RankMode) = setTask {
        "Rank($mode)[${contact}]" to
            TimerTask.Rank(mode = mode, delegate = contact.delegate)
    }

    @SubCommand
    @Description("推送关注用户作品")
    suspend fun CommandSenderOnMessage<*>.follow(duration: Int = TASK_DURATION) = setTask {
        "Follow(${info().user.uid})[${contact}]" to
            TimerTask.Follow(interval = duration * MINUTE, delegate = contact.delegate)
    }

    @SubCommand
    @Description("推送推荐作品")
    suspend fun CommandSenderOnMessage<*>.recommended(duration: Int = TASK_DURATION) = setTask {
        "Recommended(${info().user.uid})[${contact}]" to
            TimerTask.Recommended(interval = duration * MINUTE, delegate = contact.delegate)
    }

    @SubCommand
    @Description("定时备份任务")
    suspend fun CommandSenderOnMessage<*>.backup(duration: Int = TASK_DURATION) = setTask {
        "Backup" to
            TimerTask.Backup(interval = duration * MINUTE, delegate = contact.delegate)
    }

    @SubCommand
    @Description("推送，从url链接获取")
    suspend fun CommandSenderOnMessage<*>.web(pattern: String, link: String, duration: Int = TASK_DURATION) = setTask {
        val url = Url(link)
        loadWeb(url = Url(link), regex = pattern.toRegex()).let {
            check(it.isNotEmpty()) { "来自${url}加载的作品ID应该不为空" }
            sendMessage("来自${url}加载得到${it}，定时任务将添加")
        }
        "WEB(${url.host})<${pattern}>[${contact}]" to TimerTask.Web(
            interval = duration * MINUTE,
            delegate = contact.delegate,
            url = link,
            pattern = pattern
        )
    }

    @SubCommand
    @Description("推送热门标签")
    suspend fun CommandSenderOnMessage<*>.trending(duration: Int = TASK_DURATION, times: Int = 1) = setTask {
        "Trending[${contact}]" to
            TimerTask.Trending(interval = duration * MINUTE, delegate = contact.delegate, times = times)
    }

    @SubCommand
    @Description("推送，删除")
    suspend fun  CommandSenderOnMessage<*>.delete(name: String) = sendMessage {
        PixivHelperScheduler.removeTimerTask(name)
        "定时任务${name}已删除".toPlainText()
    }

    @SubCommand
    @Description("查看任务详情")
    suspend fun CommandSenderOnMessage<*>.detail() = withHelper {
        buildMessageChain {
            PixivTaskData.tasks.forEach { (name, task) ->
                appendLine("名称: $name , 间隔: ${task.interval / MINUTE}min")
                StatisticTaskInfo.last(name)?.let { record ->
                    val time = OffsetDateTime.ofInstant(Instant.ofEpochSecond(record.timestamp), ZoneId.systemDefault())
                    appendLine("最后播放作品ID ${record.pid} 时间 $time")
                }
            }
        }
    }
}