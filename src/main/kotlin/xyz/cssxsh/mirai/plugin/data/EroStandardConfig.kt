package xyz.cssxsh.mirai.plugin.data

import xyz.cssxsh.pixiv.*

interface EroStandardConfig {

    val types: Set<WorkContentType>

    val marks: Long

    val pages: Int

    val tagExclude: Regex

    val userExclude: Set<Long>
}