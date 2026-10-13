package net.matsudamper.kmp.activitypub.crypto

import kotlin.system.exitProcess

/**
 * シェルの履歴と `ps` に残らないよう標準入力から受ける。照合は入力そのままなので前後の空白は落とさない
 */
fun main() {
    System.err.println("hash化するパスワードを入力してください")
    val password = readlnOrNull()

    if (password.isNullOrEmpty()) {
        System.err.println("パスワードを標準入力に 1 行で渡すこと")
        // 空のハッシュで INSERT されないよう失敗にする
        exitProcess(1)
    }

    println(PasswordHashType.Current.encoder.encode(password))
}
