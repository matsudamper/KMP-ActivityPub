package net.matsudamper.kmp.activitypub.crypto

import kotlin.system.exitProcess

object AdminPasswordHashCli {
    /**
     * シェルの履歴と `ps` に残らないよう標準入力から受ける
     */
    @JvmStatic
    fun main(args: Array<String>) {
        System.err.println("hash化するパスワードを入力してください")
        val password = readlnOrNull()?.trim()

        if (password.isNullOrEmpty()) {
            System.err.println("パスワードを標準入力に 1 行で渡すこと")
            exitProcess(1)
        }

        println(PasswordHash.create(password).encode())
    }
}
