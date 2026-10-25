package net.matsudamper.kmp.activitypub.imagestorage.s3

import java.time.Duration
import net.matsudamper.kmp.activitypub.oidc.WebIdentityTokenIssuer
import net.matsudamper.kmp.activitypub.shared.AccountId
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.AssumeRoleWithWebIdentityRequest

/**
 * このサーバーを OIDC の IdP として STS に信頼させ、アカウントごとの一時的な認証情報を受け取る。
 * 長期のアクセスキーをサーバーに置かずに済む
 */
class StsCredentialProvider(
    private val tokenIssuer: WebIdentityTokenIssuer,
    private val config: S3ImageStorageConfig,
) {
    fun assume(accountId: AccountId): AwsSessionCredentials {
        val webIdentityToken = tokenIssuer.issue(
            subject = accountId.value.toString(),
            name = accountId.value.toString(),
            audience = config.audience,
            ttl = WEB_IDENTITY_TOKEN_TTL,
        )
        return StsClient.builder()
            .apply {
                if (config.stsEndpoint != null) {
                    endpointOverride(config.stsEndpoint)
                }
            }
            .region(Region.of(config.region))
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .build()
            .use { stsClient ->
                val credentials = stsClient.assumeRoleWithWebIdentity(
                    AssumeRoleWithWebIdentityRequest.builder()
                        .roleArn(config.roleArn)
                        .roleSessionName(config.roleSessionName)
                        .webIdentityToken(webIdentityToken)
                        .durationSeconds(SESSION_DURATION.seconds.toInt())
                        .build(),
                ).credentials()
                AwsSessionCredentials.create(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken(),
                )
            }
    }

    private companion object {
        /**
         * STS に渡してすぐ使うので短くてよい
         */
        val WEB_IDENTITY_TOKEN_TTL: Duration = Duration.ofMinutes(5)

        /**
         * STS は 900〜43200 秒の範囲外を受け付けない
         */
        val SESSION_DURATION: Duration = Duration.ofHours(1)
    }
}
