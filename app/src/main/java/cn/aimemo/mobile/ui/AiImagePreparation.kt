package cn.aimemo.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

internal const val MAX_AI_IMAGE_COUNT = 4

internal data class AiImagePreparationProfile(
    val maxEdge: Int,
    val maxPixels: Long,
    val jpegQuality: Int,
)

internal val SCHEDULE_IMAGE_PROFILE = AiImagePreparationProfile(
    maxEdge = 2048,
    maxPixels = 4_000_000L,
    jpegQuality = 88,
)

internal val ACCOUNT_DOCUMENT_IMAGE_PROFILE = AiImagePreparationProfile(
    maxEdge = 4096,
    maxPixels = 8_000_000L,
    jpegQuality = 92,
)

internal fun prepareAiImage(
    context: Context,
    uri: Uri,
    profile: AiImagePreparationProfile,
): Pair<ByteArray, String> {
    require(profile.maxEdge in 1024..4096 && profile.maxPixels in 1_000_000L..8_000_000L)
    require(profile.jpegQuality in 70..95)
    val temporary = File.createTempFile("ai_memo_image_", ".source", context.cacheDir)
    try {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("相册没有返回可读取的图片，请重新选择")
        } catch (_: SecurityException) {
            error("图片读取权限已失效，请重新选择图片")
        }
        require(temporary.length() > 0L) { "这张图片没有有效内容，请重新选择" }
        require(temporary.length() <= MAX_IMAGE_SOURCE_BYTES) { "图片文件过大，请选择小于 30 MB 的图片" }

        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeModernImage(temporary, profile)
        } else {
            decodeLegacyImage(temporary, profile)
        }
        val (targetWidth, targetHeight) = targetDimensions(original.width, original.height, profile)
        val bitmap = if (targetWidth != original.width || targetHeight != original.height) {
            Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        } else {
            original
        }
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, profile.jpegQuality, output)) {
                "图片转换失败，请换一张图片重试"
            }
            output.toByteArray() to "image/jpeg"
        }.also {
            if (bitmap !== original) bitmap.recycle()
            original.recycle()
        }
    } finally {
        temporary.delete()
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
private fun decodeModernImage(file: File, profile: AiImagePreparationProfile): Bitmap = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val (width, height) = targetDimensions(info.size.width, info.size.height, profile)
        if (width != info.size.width || height != info.size.height) {
            decoder.setTargetSize(width, height)
        }
    }
} catch (_: Exception) {
    error("暂时无法解析这种图片格式，请换用 JPG、PNG、WebP 或系统截图")
}

private fun decodeLegacyImage(file: File, profile: AiImagePreparationProfile): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "暂时无法解析这种图片格式，请换用 JPG、PNG、WebP 或系统截图"
    }
    val (targetWidth, targetHeight) = targetDimensions(bounds.outWidth, bounds.outHeight, profile)
    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= targetWidth &&
        bounds.outHeight / (sampleSize * 2) >= targetHeight
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("暂时无法解析这种图片格式，请换用 JPG、PNG、WebP 或系统截图")
}

private fun targetDimensions(
    width: Int,
    height: Int,
    profile: AiImagePreparationProfile,
): Pair<Int, Int> {
    require(width > 0 && height > 0) { "图片尺寸无效" }
    val edgeScale = profile.maxEdge.toDouble() / maxOf(width, height).toDouble()
    val pixelCount = width.toDouble() * height.toDouble()
    val pixelScale = sqrt(profile.maxPixels.toDouble() / pixelCount)
    val scale = minOf(1.0, edgeScale, pixelScale)
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}

private const val MAX_IMAGE_SOURCE_BYTES = 30L * 1024L * 1024L
