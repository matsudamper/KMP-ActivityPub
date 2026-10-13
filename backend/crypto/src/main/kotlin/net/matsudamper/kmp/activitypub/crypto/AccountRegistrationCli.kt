package net.matsudamper.kmp.activitypub.crypto

import java.nio.file.Path
import kotlin.system.exitProcess

object AccountRegistrationCli {
    /**
     * パスワードはシェルの履歴と `ps` に残らないよう標準入力から受ける。照合は入力そのままなので前後の空白は落とさない
     */
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size !in 1..2) {
            System.err.println("使い方: ユーザー名 [DBのパス]")
            exitProcess(1)
        }

        val username = args[0]
        val databasePath = Path.of(args.getOrNull(1) ?: DEFAULT_DATABASE_PATH)

        System.err.println("登録するパスワードを入力してください")
        val password = readlnOrNull().orEmpty()

        try {
            AccountRegistration.register(
                databasePath = databasePath,
                username = username,
                password = password,
            )
        } catch (exception: AccountRegistrationException) {
            System.err.println(exception.message)
            exitProcess(1)
        }

        println("$username を登録した")
    }

    private const val DEFAULT_DATABASE_PATH = "data/kmp-activitypub.db"
}
