package io.scanbot.sdk.plugin.cordova;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.preference.PreferenceManager;

import net.doo.snap.ScanbotSDKInitializer;
import net.doo.snap.persistence.DocumentStoreStrategy;
import net.doo.snap.process.OcrResult;
import net.doo.snap.process.TextRecognition;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.scanbot.sdk.plugin.cordova.utils.FileUtils;
import io.scanbot.sdk.plugin.cordova.utils.JsonArgs;
import io.scanbot.sdk.plugin.cordova.utils.LogUtils;


/**
 * Scanbot SDK Cordova Plugin
 */
public class ScanbotSdkPlugin extends ScanbotCordovaPluginBase {

    private static final String LOG_TAG = ScanbotSdkPlugin.class.getSimpleName();

    private static boolean isSdkInitialized = false;


    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }


    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        LOG.d(LOG_TAG, "execute: action=" + action + "; callbackId=" + callbackContext.getCallbackId());
        final JSONObject jsonArgs = (args.length() > 0 ? args.getJSONObject(0) : new JSONObject());
        debugLog("JSON args: " + jsonArgs.toString());

        if (action.equals("initializeSdk")) {
            initializeSdk(jsonArgs, callbackContext);
            return true;
        }

        if (!isSdkInitialized()) {
            final String errorMsg = "Scanbot SDK is not initialized. Please call the initializeSdk() function first.";
            errorLog(errorMsg);
            callbackContext.error(errorMsg);
            return true;
        }

        if (action.equals("documentDetection")) {
            try {
                documentDetection(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not perform document detection: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        if (action.equals("performOcr")) {
            try {
                performOcr(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not perform OCR: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        if (action.equals("getOcrConfigs")) {
            try {
                getOcrConfigs(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not get OCR configs: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        if (action.equals("applyImageFilter")) {
            try {
                applyImageFilter(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not apply image filter: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        if (action.equals("createPdf")) {
            try {
                createPdf(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not create PDF: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        if (action.equals("cleanup")) {
            try {
                cleanup(jsonArgs, callbackContext);
            } catch (final Exception e) {
                final String errorMsg = "Could not cleanup the temporary directory of Scanbot SDK Plugin: " + e.getMessage();
                errorLog(errorMsg, e);
                callbackContext.error(errorMsg);
            }
            return true;
        }

        return false;
    }

    private static synchronized void setSdkInitialized(final boolean flag) {
        isSdkInitialized = flag;
    }

    public static synchronized boolean isSdkInitialized() {
        return isSdkInitialized;
    }


    /**
     * Initializes Scanbot SDK.
     *
     * @param args Optional JSON Args:
     *             loggingEnabled: true,
     *             licenseKey: 'xyz..'
     * @throws JSONException
     */
    private void initializeSdk(final JSONObject args, final CallbackContext callbackContext) throws JSONException {
        LogUtils.setLoggingEnabled(getJsonArg(args, "loggingEnabled", false));

        if (isSdkInitialized()) {
            debugLog("SDK is already initialized.");
            callbackContext.success("SDK is already initialized.");
            return;
        }

        debugLog("Initializing Scanbot SDK ...");
        cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            final String licenseKey = getJsonArg(args, "licenseKey", null);

                            final Activity activity = cordova.getActivity();
                            final Application app = activity.getApplication();
                            final String callbackMessage;

                            final ScanbotSDKInitializer initializer = new ScanbotSDKInitializer();
                            initializer.withLogging(LogUtils.isLoggingEnabled());
                            if (licenseKey != null && !"".equals(licenseKey.trim()) && !"null".equals(licenseKey.toLowerCase())) {
                                initializer.license(app, licenseKey);
                                callbackMessage = "Scanbot SDK initialized.";
                            }
                            else {
                                callbackMessage = "Trial mode activated. You can now test all features for 60 seconds.";
                            }

                            initializer.initialize(app);

                            prepareOcrBlobs(callbackContext, callbackMessage);
                        } catch (final Exception e) {
                            final String errorMsg = "Error initializing Scanbot SDK: " +  e.getMessage();
                            errorLog(errorMsg, e);
                            callbackContext.error(errorMsg);
                        }
                    }
                });

    }
    
    private void prepareOcrBlobs(final CallbackContext callbackContext, final String callbackMessage) {
    	cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                // The PdfSdk instance and its members must be created from the main (UI) thread!
                // This is required by the native Scanbot SDK for Android.
                final ScanbotSdkWrapper.PdfSdk pdfSdk = new ScanbotSdkWrapper.PdfSdk(cordova.getActivity());

                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            sdkWrapper.prepareDefaultOcrBlobs(pdfSdk);

                            debugLog(callbackMessage);
                            setSdkInitialized(true);
                            callbackContext.success(callbackMessage);
                        } catch (final Exception e) {
                            final String errorMsg = "Error initializing Scanbot SDK: " +  e.getMessage();
                            errorLog(errorMsg, e);
                            callbackContext.error(errorMsg);
                        }
                    }
                });
            }
        });
    }

    private void documentDetection(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        final String imageFileUri = getImageFileUriArg(args);
        final int quality = getImageQualityArg(args);
        debugLog("Performing document detection on image: " + imageFileUri);
        debugLog("quality: " + quality);

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    final Bitmap sourceImage = loadImage(imageFileUri);
                    final ScanbotSdkWrapper.DocumentDetectionResult result = sdkWrapper.documentDetection(sourceImage, true);

                    debugLog("Document detection result: " + result.sdkDetectionResult);
                    debugLog("Document detection polygon: " + result.polygon);

                    final Uri resultImgUri;
                    if (result.documentImage != null) {
                        resultImgUri = sdkWrapper.storeImage(result.documentImage, quality);
                        debugLog("Stored document image: " + resultImgUri.toString());
                    } else {
                        resultImgUri = null;
                    }

                    callbackContext.success(new JsonArgs()
                            .put("detectionResult", sdkWrapper.sdkDocDetectionResultToJsString(result.sdkDetectionResult))
                            .put("imageFileUri", (resultImgUri != null ? resultImgUri.toString() : null))
                            .put("polygon", sdkWrapper.sdkPolygonToJson(result.polygon))
                            .jsonObj());
                } catch (final Exception e) {
                    final String errorMsg = "Could not perform document detection on image: " + imageFileUri;
                    errorLog(errorMsg, e);
                    callbackContext.error(errorMsg);
                }
            }
        });
    }


    private void applyImageFilter(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        final String imageFileUri = getImageFileUriArg(args);
        final String imageFilter = getImageFilterArg(args);
        final int quality = getImageQualityArg(args);
        debugLog("Applying image filter ("+imageFilter+") on image: " + imageFileUri);
        debugLog("quality: " + quality);

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    final Bitmap bitmap = loadImage(imageFileUri);
                    final Bitmap result = sdkWrapper.applyImageFilter(bitmap, sdkWrapper.jsImageFilterToSdkFilter(imageFilter));
                    final Uri resultImgUri = sdkWrapper.storeImage(result, quality);
                    debugLog("Stored filtered image: " + resultImgUri.toString());
                    callbackContext.success(new JsonArgs()
                            .put("imageFileUri", resultImgUri.toString())
                            .jsonObj());
                } catch (final Exception e) {
                    final String errorMsg = "Error applying filter on image: " + imageFileUri;
                    errorLog(errorMsg, e);
                    callbackContext.error(errorMsg);
                }
            }
        });
    }


    private void createPdf(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        final List<Uri> images = getImagesArg(args);
        debugLog("Creating PDF of " + images.size() + " images ...");

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                // The PdfSdk instance and its members must be created from the main (UI) thread!
                // This is required by the native Scanbot SDK for Android.
                final ScanbotSdkWrapper.PdfSdk pdfSdk = new ScanbotSdkWrapper.PdfSdk(cordova.getActivity());

                // But for the actual PDF generation (long running/blocking) we start a background thread from the ThreadPool:
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {

                        File tempPdfFile = null;
                        try {
                            tempPdfFile = sdkWrapper.createPdf(images, pdfSdk);
                            debugLog("Got temp PDF file from SDK: " + tempPdfFile);

                            final Uri pdfOutputUri = Uri.fromFile(FileUtils.generateRandomTempScanbotFile("pdf", cordova.getActivity()));
                            debugLog("Copying SDK temp file to plugin temp output file: " + pdfOutputUri);
                            FileUtils.copyFile(tempPdfFile, new File(pdfOutputUri.getPath()));

                            callbackContext.success(new JsonArgs()
                                    .put("pdfFileUri", pdfOutputUri.toString())
                                    .jsonObj());
                        }
                        catch(final Exception e) {
                            final String errorMsg = "Error creating PDF";
                            errorLog(errorMsg, e);
                            callbackContext.error(errorMsg);
                        }
                        finally {
                            if (tempPdfFile != null && tempPdfFile.exists()) {
                                debugLog("Deleting temp file: " + tempPdfFile);
                                tempPdfFile.delete();
                            }
                        }

                    }
                }); // end of backgroud thread (ThreadPool().execute)

            }
        }); // end of main UI thread (runOnUiThread)

    }

    private void performOcr(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        final List<Uri> images = getImagesArg(args);
        final List<String> languages = getLanguagesArg(args);
        final String outputFormat = getJsonArg(args, "outputFormat", "PDF_FILE");
        debugLog("Performing OCR on " + images.size() + " images ...");

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                // The PdfSdk instance and its members must be created from the main (UI) thread!
                // This is required by the native Scanbot SDK for Android.
                final ScanbotSdkWrapper.PdfSdk pdfSdk = new ScanbotSdkWrapper.PdfSdk(cordova.getActivity());
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(cordova.getActivity());
                final DocumentStoreStrategy documentStoreStrategy = new DocumentStoreStrategy(cordova.getActivity(), preferences);
                final TextRecognition textRecognition = pdfSdk.scanbotSDK.textRecognition();

                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        File tempPdfFile = null;
                        try {
                            // first check if requested languages are installed:
                            final List<String> check = new ArrayList<String>(languages);
                            check.removeAll(sdkWrapper.getInstalledOcrLanguages(pdfSdk));
                            if (!check.isEmpty()) {
                                final String errorMsg = "Missing OCR language files for languages: " + check.toString();
                                errorLog(errorMsg);
                                callbackContext.error(errorMsg);
                                return;
                            }

                            final OcrResult result = sdkWrapper.performOcr(languages, pdfSdk, textRecognition, images, outputFormat);
                            debugLog("Got OCR result from SDK: " + result);

                            JsonArgs jsonArgs = new JsonArgs();
                            if (outputFormat.equals("PLAIN_TEXT")) {
                                jsonArgs.put("plainText", result.recognizedText);

                            } else if (outputFormat.equals("PDF_FILE") || outputFormat.equals("FULL_OCR_RESULT")) {
                                tempPdfFile = documentStoreStrategy.getDocumentFile(result.sandwichedPdfDocument.getId(), result.sandwichedPdfDocument.getName());
                                debugLog("Got temp PDF file from SDK: " + tempPdfFile);

                                final Uri pdfOutputUri = Uri.fromFile(FileUtils.generateRandomTempScanbotFile("pdf", cordova.getActivity()));
                                debugLog("Copying SDK temp file to plugin temp output file: " + pdfOutputUri);
                                FileUtils.copyFile(tempPdfFile, new File(pdfOutputUri.getPath()));

                                jsonArgs.put("pdfFileUri", pdfOutputUri.toString());

                                if (outputFormat.equals("FULL_OCR_RESULT")) {
                                    jsonArgs.put("plainText", result.recognizedText);
                                }

                            } else {
                                jsonArgs.put("plainText", result.recognizedText);
                            }

                            callbackContext.success(jsonArgs.jsonObj());
                        } catch (final Exception e) {
                            final String errorMsg = "Could not perform OCR on images: " + images.toString();
                            errorLog(errorMsg, e);
                            callbackContext.error(errorMsg);
                        } finally {
                            if (tempPdfFile != null && tempPdfFile.exists()) {
                                debugLog("Deleting temp file: " + tempPdfFile);
                                tempPdfFile.delete();
                            }
                        }
                    }
                });
            }
        });
    }

    private void getOcrConfigs(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                // The PdfSdk instance and its members must be created from the main (UI) thread!
                // This is required by the native Scanbot SDK for Android.
                final ScanbotSdkWrapper.PdfSdk pdfSdk = new ScanbotSdkWrapper.PdfSdk(cordova.getActivity());

                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            final JsonArgs jsonResult = new JsonArgs();
                            final List<String> languages = sdkWrapper.getInstalledOcrLanguages(pdfSdk);
                            final File ocrBlobsDir = pdfSdk.blobManager.getOCRBlobsDirectory();

                            jsonResult.put("languageDataPath", Uri.fromFile(ocrBlobsDir).toString());
                            jsonResult.put("installedLanguages", new JSONArray(languages));
                            callbackContext.success(jsonResult.jsonObj());
                        } catch (final Exception e) {
                            final String errorMsg = "Could not get OCR configs";
                            errorLog(errorMsg, e);
                            callbackContext.error(errorMsg);
                        }
                    }
                });
            }
        });

    }

    private void cleanup(final JSONObject args, final CallbackContext callbackContext) throws JSONException, IOException {
        debugLog("Cleaning the temporary directory of Scanbot SDK Plugin ...");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FileUtils.cleanUpTempScanbotDirectory(cordova.getActivity());
                    callbackContext.success("Cleanup successfully done");
                } catch (final Exception e) {
                    final String errorMsg = "Could not cleanup the temporary directory of Scanbot SDK Plugin";
                    errorLog(errorMsg, e);
                    callbackContext.error(errorMsg);
                }
            }
        });

    }

}
