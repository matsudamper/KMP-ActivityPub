package net.matsudamper.kmp.activitypub.crypto

import kotlin.system.exitProcess

/**
 * 引数ではなく標準入力から受けるのは、シェルの履歴と `ps` に平文で残るため。
 * 前後の空白は落とさない。ログインでは入力をそのまま照合するので、落とすと入れたパスワードで通らなくなる
 */
fun main() {
    System.err.println("hash化するパスワードを入力してください")
    val password = readlnOrNull()

    if (password.isNullOrEmpty()) {
        System.err.println("パスワードを標準入力に 1 行で渡すこと")
        // 正常終了すると、続けて INSERT したときに空のハッシュで登録されてしまう
        exitProcess(1)
    }

    println(PasswordHashType.Current.encoder.encode(password))
}
