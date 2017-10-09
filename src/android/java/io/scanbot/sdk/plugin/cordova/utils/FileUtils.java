package io.scanbot.sdk.plugin.cordova.utils;


import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import io.scanbot.sdk.plugin.cordova.ScanbotSdkPlugin;
import org.apache.cordova.LOG;

import java.io.*;
import java.util.UUID;

public final class FileUtils {

    private static final String LOG_TAG = FileUtils.class.getSimpleName();


    private FileUtils() {}


    public static File getTempScanbotDirectory(final Context context) throws IOException {
        final File cacheDir;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // SD Card Mounted
            cacheDir = context.getExternalCacheDir();
        }
        else {
            // Use internal storage
            cacheDir = context.getCacheDir();
        }

        final File scanbotTempDir = new File(cacheDir, "sbsdk-temp");
        scanbotTempDir.mkdirs();
        // create it and make sure it exists
        if (!scanbotTempDir.isDirectory()) {
            throw new IOException("Can't create/get temporary cache directory: " + scanbotTempDir.getAbsolutePath());
        }
        return scanbotTempDir;
    }


    public static File generateRandomTempScanbotFile(final String extension, final Context context) throws IOException {
        final File tempDir = getTempScanbotDirectory(context);
        //final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        //final String imageFileName = "scanbot_" + timeStamp + "." + extension;
        final String imageFileName = UUID.randomUUID().toString() + "." + extension;
        return new File(tempDir, imageFileName);
    }



    public static void cleanUpTempScanbotDirectory(final Activity activity) throws IOException {
        final File tempDir = getTempScanbotDirectory(activity);
        LOG.d(LOG_TAG, "Deleting content of temp directory: " + tempDir.getAbsolutePath());
        cleanDirectory(tempDir);
    }


    public static File getExternalStorageDirectory(final String directoryName) throws IOException {
        final File externalFilesDir = Environment.getExternalStorageDirectory();
        if (externalFilesDir == null) {
            throw new IOException("Can't get external storage directory");
        }

        final File result = new File(externalFilesDir, directoryName);
        if (!result.exists() && !result.mkdir()) {
            throw new IOException("Can't create sub folder in external storage directory");
        }

        return result;
    }

    public static void copyFile(final File source, final File target) throws IOException {
        final InputStream in = new FileInputStream(source);
        final OutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }


    private static void cleanDirectory(final File directory) throws IOException {
        final File[] files = verifiedListFiles(directory);

        IOException exception = null;
        for (final File file : files) {
            try {
                forceDelete(file);
            } catch (final IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    private static File[] verifiedListFiles(File directory) throws IOException {
        if (!directory.exists()) {
            final String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            final String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        final File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }
        return files;
    }

    private static void forceDelete(final File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            final boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                throw new IOException("Could not delete file: " + file);
            }
        }
    }

    private static void deleteDirectory(final File directory) throws IOException {
        if (!directory.exists()) { return; }

        cleanDirectory(directory);

        if (!directory.delete()) {
            throw new IOException("Could not delete directory: " + directory);
        }
    }
}
