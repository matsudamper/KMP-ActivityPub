package net.matsudamper.kmp.activitypub.imagestorage.s3

import java.net.URI

/**
 * @param endpoint null なら AWS の S3。MinIO などに向けるときに指定する
 * @param stsEndpoint null なら AWS の STS
 * @param audience STS に渡すトークンの aud。IAM の OIDC プロバイダに登録した Audience と揃える
 */
class S3ImageStorageConfig(
    val endpoint: URI?,
    val stsEndpoint: URI?,
    val region: String,
    val bucket: String,
    val roleArn: String,
    val roleSessionName: String,
    val audience: String,
    val pathStyleAccess: Boolean,
)
