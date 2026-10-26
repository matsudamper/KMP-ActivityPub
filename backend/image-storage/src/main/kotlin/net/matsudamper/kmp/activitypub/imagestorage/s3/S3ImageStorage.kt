package net.matsudamper.kmp.activitypub.imagestorage.s3

import java.time.Duration
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.shared.AccountId
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

class S3ImageStorage(
    private val stsCredentialProvider: StsCredentialProvider,
    private val config: S3ImageStorageConfig,
) : ImageStorage {
    /**
     * 大きさの分からないストリームを一時ファイルに書かずに送るため、マルチパートで少しずつ送る
     */
    override fun put(request: ImageStorage.PutRequest): ImageStorage.PutResult {
        val key = objectKey(request.path)
        val s3Client =
            runCatching { createS3Client(request.ownerAccountId) }
                .getOrElse { return ImageStorage.PutResult.Failure(it) }
        return s3Client.use {
            val uploadId =
                runCatching {
                    s3Client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                            .bucket(config.bucket)
                            .key(key)
                            .contentType(request.contentType)
                            .build(),
                    ).uploadId()
                }.getOrElse { return@use ImageStorage.PutResult.Failure(it) }

            val result =
                runCatching { uploadParts(s3Client = s3Client, key = key, uploadId = uploadId, request = request) }
                    .getOrElse { ImageStorage.PutResult.Failure(it) }
            if (result !is ImageStorage.PutResult.Success) {
                runCatching {
                    s3Client.abortMultipartUpload(
                        AbortMultipartUploadRequest.builder()
                            .bucket(config.bucket)
                            .key(key)
                            .uploadId(uploadId)
                            .build(),
                    )
                }
            }
            result
        }
    }

    override fun get(request: ImageStorage.GetRequest): ImageStorage.GetResult =
        runCatching {
            createS3Client(request.ownerAccountId).use { s3Client ->
                val response =
                    s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                            .bucket(config.bucket)
                            .key(objectKey(request.path))
                            .build(),
                    )
                ImageStorage.GetResult.Success(
                    bytes = response.asByteArray(),
                    contentType = response.response().contentType(),
                )
            }
        }.getOrElse { cause ->
            if (cause is NoSuchKeyException) {
                ImageStorage.GetResult.NotFound
            } else {
                ImageStorage.GetResult.Failure(cause)
            }
        }

    override fun delete(request: ImageStorage.DeleteRequest): ImageStorage.DeleteResult =
        runCatching {
            createS3Client(request.ownerAccountId).use { s3Client ->
                s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                        .bucket(config.bucket)
                        .key(objectKey(request.path))
                        .build(),
                )
            }
        }.fold(
            onSuccess = { ImageStorage.DeleteResult.Success },
            onFailure = { ImageStorage.DeleteResult.Failure(it) },
        )

    override fun buildDisplayUrl(request: ImageStorage.DisplayUrlRequest): String {
        val credentials = stsCredentialProvider.assume(request.ownerAccountId)
        return S3Presigner.builder()
            .endpointOverride(config.endpoint)
            .region(Region.of(config.region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(s3Configuration())
            .build()
            .use { presigner ->
                presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                        .signatureDuration(DISPLAY_URL_VALIDITY)
                        .getObjectRequest(
                            GetObjectRequest.builder()
                                .bucket(config.bucket)
                                .key(objectKey(request.path))
                                .build(),
                        )
                        .build(),
                ).url().toString()
            }
    }

    private fun uploadParts(
        s3Client: S3Client,
        key: String,
        uploadId: String,
        request: ImageStorage.PutRequest,
    ): ImageStorage.PutResult {
        val buffer = ByteArray(MULTIPART_PART_SIZE)
        val completedParts = buildList {
            var totalBytes = 0L
            while (true) {
                val bytesRead = request.inputStream.readNBytes(buffer, 0, buffer.size)
                if (bytesRead == 0) break

                totalBytes += bytesRead
                if (totalBytes > request.maxBytes) {
                    return ImageStorage.PutResult.PayloadTooLarge
                }

                val partNumber = size + 1
                val response = s3Client.uploadPart(
                    UploadPartRequest.builder()
                        .bucket(config.bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength(bytesRead.toLong())
                        .build(),
                    RequestBody.fromBytes(buffer.copyOf(bytesRead)),
                )
                add(CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build())

                if (bytesRead < buffer.size) break
            }
        }
        if (completedParts.isEmpty()) {
            return ImageStorage.PutResult.Empty
        }

        s3Client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                .bucket(config.bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build(),
        )
        return ImageStorage.PutResult.Success(path = request.path)
    }

    private fun createS3Client(accountId: AccountId): S3Client {
        val credentials = stsCredentialProvider.assume(accountId)
        return S3Client.builder()
            .endpointOverride(config.endpoint)
            .region(Region.of(config.region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(s3Configuration())
            .build()
    }

    private fun s3Configuration(): S3Configuration =
        S3Configuration.builder()
            .pathStyleAccessEnabled(config.pathStyleAccess)
            .build()

    private fun objectKey(path: ImagePath): String = "img/${path.value}"

    private companion object {
        /**
         * S3 はマルチパートの最後以外のパートが 5MiB 未満だと完了を断る
         */
        const val MULTIPART_PART_SIZE = 5 * 1024 * 1024

        val DISPLAY_URL_VALIDITY: Duration = Duration.ofHours(1)
    }
}
