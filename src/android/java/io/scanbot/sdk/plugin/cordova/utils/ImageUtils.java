package io.scanbot.sdk.plugin.cordova.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import net.doo.snap.util.FileChooserUtils;
import net.doo.snap.util.bitmap.BitmapUtils;

import java.io.IOException;


public final class ImageUtils {

    /** Default JPG image quality */
    public static final int JPEG_QUALITY = 95;

    private ImageUtils() {}

    public static Bitmap loadImage(final String imageFilePath) {
        return BitmapUtils.decodeQuietly(imageFilePath, null);
    }

    public static Bitmap loadImage(final Uri imageUri, final Context context) throws IOException {
        return loadImage(FileChooserUtils.getPath(context, imageUri));
    }


    public static Bitmap resizeImage(final Bitmap originalImage, final float width, final float height) {
        final float oldWidth = originalImage.getWidth();
        final float oldHeight = originalImage.getHeight();

        final float scaleFactor;
        if (oldWidth > oldHeight) {
            scaleFactor = width / oldWidth;
        }
        else {
            scaleFactor = height / oldHeight;
        }

        final float newHeight = oldHeight * scaleFactor;
        final float newWidth = oldWidth * scaleFactor;

        return Bitmap.createScaledBitmap(originalImage, (int)newWidth, (int)newHeight, false);
    }

}
