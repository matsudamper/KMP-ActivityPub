package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage

internal class FakeImageStorage : ImageStorage {
    val images: MutableMap<ImagePath, ImageStorage.GetResult.Success> = mutableMapOf()

    override fun put(request: ImageStorage.PutRequest): ImageStorage.PutResult {
        val bytes = request.inputStream.readBytes()
        if (bytes.isEmpty()) return ImageStorage.PutResult.Empty
        if (bytes.size > request.maxBytes) return ImageStorage.PutResult.PayloadTooLarge
        images[request.path] = ImageStorage.GetResult.Success(bytes = bytes, contentType = request.contentType)
        return ImageStorage.PutResult.Success(request.path)
    }

    override fun get(request: ImageStorage.GetRequest): ImageStorage.GetResult = images[request.path] ?: ImageStorage.GetResult.NotFound

    override fun delete(request: ImageStorage.DeleteRequest): ImageStorage.DeleteResult {
        images.remove(request.path)
        return ImageStorage.DeleteResult.Success
    }

    override fun buildDisplayUrl(request: ImageStorage.DisplayUrlRequest): String = "https://storage.example/${request.path.value}"
}
