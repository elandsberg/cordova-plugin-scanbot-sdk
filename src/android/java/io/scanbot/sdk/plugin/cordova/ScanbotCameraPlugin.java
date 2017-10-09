package io.scanbot.sdk.plugin.cordova;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;

import io.scanbot.sdk.plugin.cordova.utils.JsonArgs;
import static io.scanbot.sdk.plugin.cordova.ScanbotConstants.*;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;


/**
 * Scanbot SDK Camera UI Cordova Plugin
 */
public class ScanbotCameraPlugin extends ScanbotCordovaPluginBase {

    private static final String LOG_TAG = ScanbotCameraPlugin.class.getSimpleName();

    private static final int REQ_CODE_CAM_PERMISSIONS = 4711;
    private static final int REQ_CODE_EDIT_IMG_PERMISSIONS = 4712;

    private static final String EXTRAS_JSON_ARGS = "EXTRAS_JSON_ARGS";

    private final static String[] REQUIRED_CAM_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private final static String[] REQUIRED_EDIT_IMG_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };


    private CallbackContext callbackContext;
    private JSONObject jsonArgs;


    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }


    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        LOG.d(LOG_TAG, "execute: action=" + action + "; callbackId=" + callbackContext.getCallbackId());

        this.jsonArgs = (args.length() > 0 ? args.getJSONObject(0) : new JSONObject());
        debugLog("JSON args: " + jsonArgs.toString());

        // First check if SDK is initialized:
        if (!ScanbotSdkPlugin.isSdkInitialized()) {
            final String errorMsg = "Scanbot SDK is not initialized. Please call the ScanbotSdk.initializeSdk() function first.";
            errorLog(errorMsg);
            callbackContext.error(errorMsg);
            return true;
        }

        this.callbackContext = callbackContext;

        if (action.equals("startCamera")) {
            checkPermissionsAndStartCamera();
            return true;
        }

        if (action.equals("dismissCamera")) {
            final Intent closeIntent = new Intent(ACTION_DISMISS_SB_CAMERA);
            this.cordova.getActivity().sendBroadcast(closeIntent);
            return true;
        }

        if (action.equals("startCropping")) {
            checkPermissionsAndStartEditImage();
            return true;
        }

        return false;
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
        final Bundle state = super.onSaveInstanceState();
        if (this.jsonArgs != null) {
            state.putString(EXTRAS_JSON_ARGS, this.jsonArgs.toString());
        }
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
        super.onRestoreStateForActivityResult(state, callbackContext);

        this.callbackContext = callbackContext;

        if (state.containsKey(EXTRAS_JSON_ARGS)) {
            try {
                this.jsonArgs = new JSONObject(state.getString(EXTRAS_JSON_ARGS));
            } catch (final JSONException e) {
                errorLog("Could not restore JSON args", e);
            }
        }
    }


    /**
     * Called when an activity you launched exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode   The request code originally supplied to startActivityForResult(),
     *                      allowing you to identify who this result came from.
     * @param resultCode    The integer result code returned by the child activity through its setResult().
     * @param intent        An Intent, which can return result data to the caller (various data can be
     *                      attached to Intent "extras").
     */
    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        debugLog("onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode != REQUEST_SB_CAMERA && requestCode != REQUEST_SB_EDIT_IMAGE) {
            return;
        }

        switch (resultCode) {
            case RESULT_SB_CAM_PICTURE_TAKEN:
                this.handleCamPictureTakenResult(intent);
                return;
            case RESULT_SB_EDIT_IMAGE:
                this.handleEditImageResult(intent);
                return;
            default:
                return;
        }
    }

    /**
     * Checks/requests camera permissions and starts Scanbot Camera UI.
     *
     * @throws JSONException
     */
    private void checkPermissionsAndStartCamera() throws JSONException {
        // check required permissions
        for (final String p: REQUIRED_CAM_PERMISSIONS) {
            if (!this.cordova.hasPermission(p)) {
                //this.cordova.requestPermission(this, REQ_CODE_PERMISSIONS, Manifest.permission.CAMERA);
                this.cordova.requestPermissions(this, REQ_CODE_CAM_PERMISSIONS, REQUIRED_CAM_PERMISSIONS);
                return;
            }
        }

        startScanbotCameraActivity();
    }


    /**
     * Checks/requests storage permissions and starts Edit Image UI.
     *
     * @throws JSONException
     */
    private void checkPermissionsAndStartEditImage() throws JSONException {
        // check required permissions
        for (final String p: REQUIRED_EDIT_IMG_PERMISSIONS) {
            if (!this.cordova.hasPermission(p)) {
                this.cordova.requestPermissions(this, REQ_CODE_EDIT_IMG_PERMISSIONS, REQUIRED_CAM_PERMISSIONS);
                return;
            }
        }

        startScanbotEditImageActivity();
    }


    @Override
    public void onRequestPermissionResult(final int requestCode, final String[] permissions, final int[] grantResults) throws JSONException {
        for (final int r: grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                final String errorMsg = "Some permission was not granted.";
                errorLog(errorMsg);
                callbackContext.error(errorMsg);
                return;
            }
        }

        switch (requestCode) {
            case REQ_CODE_CAM_PERMISSIONS:
                startScanbotCameraActivity();
                break;
            case REQ_CODE_EDIT_IMG_PERMISSIONS:
                startScanbotEditImageActivity();
                break;
            default:
                return;
        }
    }

    /**
     * Starts Scanbot Camera Scan UI.
     *
     * @throws JSONException
     */
    private void startScanbotCameraActivity() throws JSONException {
        final Bundle textResBundle = getJsonArgAsStringMap(jsonArgs, "textResBundle");
        final int edgeColor = getJsonArgAsColorInt(jsonArgs, "edgeColor", DEFAUL_EDGE_COLOR);
        final int quality = getImageQualityArg(jsonArgs);
        final int sampleSize = getJsonArg(jsonArgs, "sampleSize", 1);
        final boolean autoSnapping = getJsonArg(jsonArgs, "autoSnappingEnabled", DEFAULT_AUTOSNAPPING_ENABLED);
        final double autoSnappingSensitivity = getJsonArg(jsonArgs, "autoSnappingSensitivity", DEFAULT_AUTOSNAPPING_SENSITIVITY);

        debugLog("textResBundle: " + textResBundle);
        debugLog("edgeColor: " + edgeColor);
        debugLog("quality: " + quality);
        debugLog("sampleSize: " + sampleSize);
        debugLog("autoSnapping: " + autoSnapping);
        debugLog("autoSnappingSensitivity: " + autoSnappingSensitivity);

        final Intent intent = new Intent(this.cordova.getActivity(), ScanbotCameraActivity.class);
        final Bundle extras = new Bundle();
        extras.putBundle(EXTRAS_ARG_TEXT_RES_MAP, textResBundle);
        extras.putInt(EXTRAS_ARG_EDGE_COLOR, edgeColor);
        extras.putInt(EXTRAS_ARG_JPG_QUALITY, quality);
        extras.putInt(EXTRAS_ARG_SAMPLE_SIZE, sampleSize);
        extras.putBoolean(EXTRAS_ARG_AUTOSNAPPING_ENABLED, autoSnapping);
        extras.putDouble(EXTRAS_ARG_AUTOSNAPPING_SENSITIVITY, autoSnappingSensitivity);
        intent.putExtras(extras);

        this.cordova.setActivityResultCallback(this);
        this.cordova.startActivityForResult(this, intent, REQUEST_SB_CAMERA);
    }


    /**
     * Starts Scanbot Cropping UI.
     *
     * @throws JSONException
     */
    private void startScanbotEditImageActivity() throws JSONException {
        final String imageFileUri = getImageFileUriArg(jsonArgs);
        final int edgeColor = getJsonArgAsColorInt(jsonArgs, "edgeColor", DEFAUL_EDGE_COLOR);
        final int quality = getImageQualityArg(jsonArgs);

        debugLog("Starting edit image UI on image: " + imageFileUri);
        debugLog("edgeColor: " + edgeColor);
        debugLog("quality: " + quality);

        final Intent intent = new Intent(this.cordova.getActivity(), ScanbotEditImageActivity.class);

        final Bundle extras = new Bundle();
        extras.putString(EXTRAS_ARG_IMAGE_FILE_URI, imageFileUri);
        extras.putInt(EXTRAS_ARG_EDGE_COLOR, edgeColor);
        extras.putInt(EXTRAS_ARG_JPG_QUALITY, quality);
        intent.putExtras(extras);

        this.cordova.setActivityResultCallback(this);
        this.cordova.startActivityForResult(this, intent, REQUEST_SB_EDIT_IMAGE);
    }


    private void handleCamPictureTakenResult(final Intent intent) {
        final String imageFileUri = intent.getStringExtra(EXTRAS_ARG_IMAGE_FILE_URI);
        final String originalImageFileUri = intent.getStringExtra(EXTRAS_ARG_ORIGINAL_IMAGE_FILE_URI);
        debugLog( "Got doc image file URI from Scanbot Camera UI Activity: " + imageFileUri);
        debugLog( "Got original image file URI from Scanbot Camera UI Activity: " + originalImageFileUri);
        final JSONObject result = new JsonArgs()
                .put("imageFileUri", imageFileUri)
                .put("originalImageFileUri", originalImageFileUri)
                .jsonObj();
        this.callbackContext.success(result);
    }


    private void handleEditImageResult(final Intent intent) {
        final String imageFileUri = intent.getStringExtra(EXTRAS_ARG_IMAGE_FILE_URI);
        final List<PointF> polygon = intent.getParcelableArrayListExtra(EXTRAS_ARG_DETECTED_POLYGON);
        debugLog( "Got image file URI from Scanbot Edit Image Activity: " + imageFileUri);
        debugLog( "Got polygon from Scanbot Edit Image Activity: " + polygon);
        final JSONObject result = new JsonArgs()
                .put("imageFileUri", imageFileUri)
                .put("polygon", sdkWrapper.sdkPolygonToJson(polygon))
                .jsonObj();
        this.callbackContext.success(result);
    }


}
