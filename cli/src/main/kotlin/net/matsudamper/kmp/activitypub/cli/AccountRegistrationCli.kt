package net.matsudamper.kmp.activitypub.cli

import java.nio.file.Path
import kotlin.system.exitProcess

object AccountRegistrationCli {
    /**
     * ユーザー名とパスワードはシェルの履歴と `ps` に残らないよう、聞いたあと標準入力から受ける。
     * パスワードの照合は入力そのままなので前後の空白は落とさない
     */
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isNotEmpty()) {
            System.err.println("引数は受け取らない")
            exitProcess(1)
        }

        System.err.println("ユーザー名を入力してください")
        val username = readlnOrNull().orEmpty()

        System.err.println("登録するパスワードを入力してください")
        val password = readlnOrNull().orEmpty()

        try {
            AccountRegistration.register(
                databasePath = Path.of(DEFAULT_DATABASE_PATH),
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
