package io.scanbot.sdk.plugin.cordova;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import io.scanbot.sdk.plugin.cordova.utils.ImageUtils;
import io.scanbot.sdk.plugin.cordova.utils.LogUtils;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base abstract class for Scanbot Cordova Plugins
 */
public abstract class ScanbotCordovaPluginBase extends CordovaPlugin {

    protected ScanbotSdkWrapper sdkWrapper;


    protected abstract String getLogTag();


    /**
     * Called after plugin construction and fields have been initialized.
     */
    @Override
    protected void pluginInitialize() {
        sdkWrapper = new ScanbotSdkWrapper(cordova.getActivity());
    }

    /**
     * Called when the Activity is being destroyed (e.g. if a plugin calls out to an
     * external Activity and the OS kills the CordovaActivity in the background).
     * The plugin should save its state in this method only if it is awaiting the
     * result of an external Activity and needs to preserve some information so as
     * to handle that result; onRestoreStateForActivityResult() will only be called
     * if the plugin is the recipient of an Activity result
     *
     * @return  Bundle containing the state of the plugin or null if state does not
     *          need to be saved
     */
    @Override
    public Bundle onSaveInstanceState() {
        final Bundle state = new Bundle();
        //state.putBoolean("loggingEnabled", loggingEnabled);
        return state;
    }

    /**
     * Called when a plugin is the recipient of an Activity result after the
     * CordovaActivity has been destroyed. The Bundle will be the same as the one
     * the plugin returned in onSaveInstanceState()
     *
     * @param state             Bundle containing the state of the plugin
     * @param callbackContext   Replacement Context to return the plugin result to
     */
    @Override
    public void onRestoreStateForActivityResult(final Bundle state, final CallbackContext callbackContext) {
        this.sdkWrapper = new ScanbotSdkWrapper(cordova.getActivity());
    }


    protected String getImageFileUriArg(final JSONObject args) throws JSONException {
        final String imageFileUri = getJsonArg(args, "imageFileUri", null);
        if (imageFileUri != null && !"".equals(imageFileUri.trim())) {
            return Uri.parse(imageFileUri).toString();
        }
        final String errorMsg = "Missing required JSON argument: imageFileUri";
        errorLog(errorMsg);
        throw new JSONException(errorMsg);
    }

    protected String getImageFilterArg(final JSONObject args) throws JSONException {
        final String imageFilter = getJsonArg(args, "imageFilter", null);
        if (imageFilter != null && !"".equals(imageFilter.trim())) {
            return imageFilter;
        }
        final String errorMsg = "Missing required JSON argument: imageFilter";
        errorLog(errorMsg);
        throw new JSONException(errorMsg);
    }

    protected List<Uri> getImagesArg(final JSONObject args) throws JSONException {
        final List<Uri> result = new ArrayList<Uri>();
        for (final String imageFileUri: getJsonArg(args, "images")) {
            result.add(Uri.parse(imageFileUri));
        }
        return result;
    }

    protected List<String> getLanguagesArg(final JSONObject args) throws JSONException {
        return getJsonArg(args, "languages");
    }

    protected int getImageQualityArg(final JSONObject args) throws JSONException {
        int quality = getJsonArg(args, "quality", ImageUtils.JPEG_QUALITY);
        if (quality <= 0 || quality > 100) {
            quality = ImageUtils.JPEG_QUALITY;
        }
        return quality;
    }

    protected boolean getJsonArg(final JSONObject args, final String key, final boolean defaultValue) throws JSONException {
        if (args.has(key)) {
            return args.getBoolean(key);
        }
        return defaultValue;
    }

    protected String getJsonArg(final JSONObject args, final String key, final String defaultValue) throws JSONException {
        if (args.has(key)) {
            return args.getString(key);
        }
        return defaultValue;
    }

    protected int getJsonArg(final JSONObject args, final String key, final int defaultValue) throws JSONException {
        if (args.has(key)) {
            return args.getInt(key);
        }
        return defaultValue;
    }

    protected double getJsonArg(final JSONObject args, final String key, final double defaultValue) throws JSONException {
        if (args.has(key)) {
            return args.getDouble(key);
        }
        return defaultValue;
    }

    protected List<String> getJsonArg(final JSONObject args, final String arrayKey) throws JSONException {
        final List<String> result = new ArrayList<String>();
        if (args.has(arrayKey)) {
            final JSONArray jsonArray = args.getJSONArray(arrayKey);
            for (int i=0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
        }
        return result;
    }

    protected Bundle getJsonArgAsStringMap(final JSONObject args, final String mapKey) throws JSONException {
        final Bundle bundle = new Bundle();
        if (!args.has(mapKey)) {
            return bundle;
        }

        final JSONObject mapObj = args.getJSONObject(mapKey);
        final Iterator<String> itr = mapObj.keys();
        while (itr.hasNext()) {
            final String key = itr.next();
            bundle.putString(key, mapObj.getString(key));
        }
        return bundle;
    }

    protected int getJsonArgAsColorInt(final JSONObject args, final String key, final int defaultValue) throws JSONException {
        if (args.has(key)) {
            try {
                return Color.parseColor(args.getString(key));
            }
            catch (final Exception e) {
                errorLog("Invalid color value: " + args.getString(key), e);
            }
        }
        return defaultValue;
    }

    protected void debugLog(final String msg) {
        LogUtils.debugLog(getLogTag(), msg);
    }

    protected void errorLog(final String msg) {
        LogUtils.errorLog(getLogTag(), msg);
    }

    protected void errorLog(final String msg, final Throwable e) {
        LogUtils.errorLog(getLogTag(), msg, e);
    }

    /**
     * Loads an image by file path or file URI.
     * @param imageFile Must be a valid file path or a valid file URI.
     * @return
     * @throws IOException
     */
    protected Bitmap loadImage(final String imageFile) throws IOException {
        if (imageFile != null && !"".equals(imageFile.trim())) {
            if (imageFile.contains("://")) {
                // looks like an URI
                final Uri uri = Uri.parse(imageFile);
                return sdkWrapper.loadImage(uri);
            }
            // must be a path:
            return sdkWrapper.loadImage(imageFile);
        }
        throw new IllegalArgumentException("Invalid imageFile. Must be a file URI or a file path: " + imageFile);
    }

}
