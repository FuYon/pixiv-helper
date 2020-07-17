package mirai

import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.UnionKind
import net.mamoe.mirai.console.pure.MiraiConsolePureLoader

object RunMirai {

    // 执行 gradle task: runMiraiConsole 来自动编译, shadow, 复制, 并启动 pure console.

    @JvmStatic
    fun main(args: Array<String>) {
        // 默认在 /test 目录下运行
        MiraiConsolePureLoader.main(args)
    }
}