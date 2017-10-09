package io.scanbot.sdk.plugin.cordova;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import static io.scanbot.sdk.plugin.cordova.ScanbotConstants.*;

import io.scanbot.sdk.plugin.cordova.utils.ImageUtils;
import io.scanbot.sdk.plugin.cordova.utils.LogUtils;
import io.scanbot.sdk.plugin.cordova.utils.ResourcesUtils;
import io.scanbot.sdk.plugin.cordova.widget.ShutterDrawable;

import net.doo.snap.camera.AutoSnappingController;
import net.doo.snap.camera.CameraOpenCallback;
import net.doo.snap.camera.ContourDetectorFrameHandler;
import net.doo.snap.camera.PictureCallback;
import net.doo.snap.camera.ScanbotCameraView;
import net.doo.snap.lib.detector.DetectionResult;
import net.doo.snap.ui.PolygonView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Scanbot SDK Camera Activity for Document Scanning UI with user guidance.
 */
public class ScanbotCameraActivity extends AppCompatActivity implements PictureCallback,
        ContourDetectorFrameHandler.ResultHandler {

    private static final String LOG_TAG = ScanbotCameraActivity.class.getSimpleName();

    private static final float SCALE_DEFAULT = 1f;
    private static final float TAKE_PICTURE_PRESSED_SCALE = 0.8f;
    private static final float TAKE_PICTURE_OVERSHOOT_TENSION = 8f;

    private static final long CAMERA_OPEN_DELAY_MS = 300L;

    private final Executor executor = Executors.newSingleThreadExecutor();

    private ScanbotSdkWrapper sdkWrapper;

    private int screenOrientation;

    private ImageButton cancelButton;

    private ScanbotCameraView cameraView;
    private ContourDetectorFrameHandler contourDetectorFrameHandler;
    private PolygonView polygonView;
    private AutoSnappingController autoSnappingController;
    private Toast userGuidanceToast;
    private ImageView rotateDeviceImageView;
    private ImageButton snapImageButton;
    private ShutterDrawable shutterDrawable;
    private CheckBox flashToggle;
    private CheckBox autosnapToggle;

    private ProgressBar processPictureProgressBar;

    private int drawable_ui_cam_rotation_v = 0,
                drawable_ui_cam_rotation_h = 0;

    // Optional text resources bundle, passed as a simple string map from the client/app.
    private Bundle textResBundle;

    private int edgeColor = DEFAUL_EDGE_COLOR;

    private int jpgQuality = ImageUtils.JPEG_QUALITY;

    private int sampleSize = 1; // 1 means original size (no downscale)

    private boolean autoSnappingEnabled = true;
    
    private AtomicBoolean userGuidanceEnabled = new AtomicBoolean(false);

    private double autoSnappingSensitivity = DEFAULT_AUTOSNAPPING_SENSITIVITY;

    private ScanbotCameraBroadcastReceiver broadcastReceiver;


    private View findViewById(final String id) {
        final int idInt = ResourcesUtils.getResId("id", id, this);
        return findViewById(idInt);
    }

    protected void setupUIButtons() {
        cancelButton = (ImageButton) findViewById("cancelCameraButton");
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        snapImageButton = (ImageButton) findViewById("snapImageButton");
        snapImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // my simple current solution:
                cameraView.takePicture(false);
            }
        });
        snapImageButton.setOnTouchListener(new View.OnTouchListener() {

            private final Interpolator downInterpolator = new DecelerateInterpolator();
            private final Interpolator upInterpolator = new OvershootInterpolator(TAKE_PICTURE_OVERSHOOT_TENSION);

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        snapImageButton.animate()
                                .scaleX(TAKE_PICTURE_PRESSED_SCALE)
                                .scaleY(TAKE_PICTURE_PRESSED_SCALE)
                                .setInterpolator(downInterpolator)
                                .start();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        snapImageButton.animate()
                                .scaleX(SCALE_DEFAULT)
                                .scaleY(SCALE_DEFAULT)
                                .setInterpolator(upInterpolator)
                                .start();
                        break;
                }

                return false;
            }
        });

        shutterDrawable = new ShutterDrawable(this);
        snapImageButton.setImageDrawable(shutterDrawable);

        flashToggle = (CheckBox) findViewById("flashToggle");
        flashToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                setFlashEnabled(checked);
            }
        });

        autosnapToggle = (CheckBox) findViewById("autosnapToggle");
        autosnapToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                setAutoSnapEnabled(checked);
            }
        });
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(ResourcesUtils.getResId("layout", "scanbot_camera_view", this));

        broadcastReceiver = new ScanbotCameraBroadcastReceiver();
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_DISMISS_SB_CAMERA));

        sdkWrapper = new ScanbotSdkWrapper(this);

        getSupportActionBar().hide();

        textResBundle = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_TEXT_RES_MAP) ?
                savedInstanceState.getBundle(EXTRAS_ARG_TEXT_RES_MAP) :
                getIntent().getExtras().getBundle(EXTRAS_ARG_TEXT_RES_MAP));

        edgeColor = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_EDGE_COLOR) ?
                savedInstanceState.getInt(EXTRAS_ARG_EDGE_COLOR) :
                getIntent().getExtras().getInt(EXTRAS_ARG_EDGE_COLOR));

        jpgQuality = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_JPG_QUALITY) ?
                savedInstanceState.getInt(EXTRAS_ARG_JPG_QUALITY) :
                getIntent().getExtras().getInt(EXTRAS_ARG_JPG_QUALITY));

        autoSnappingEnabled = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_AUTOSNAPPING_ENABLED) ?
                savedInstanceState.getBoolean(EXTRAS_ARG_AUTOSNAPPING_ENABLED) :
                getIntent().getExtras().getBoolean(EXTRAS_ARG_AUTOSNAPPING_ENABLED));

        autoSnappingSensitivity = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_AUTOSNAPPING_SENSITIVITY) ?
                savedInstanceState.getDouble(EXTRAS_ARG_AUTOSNAPPING_SENSITIVITY) :
                getIntent().getExtras().getDouble(EXTRAS_ARG_AUTOSNAPPING_SENSITIVITY));

        sampleSize = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_SAMPLE_SIZE) ?
                savedInstanceState.getInt(EXTRAS_ARG_SAMPLE_SIZE) :
                getIntent().getExtras().getInt(EXTRAS_ARG_SAMPLE_SIZE));

        drawable_ui_cam_rotation_v = ResourcesUtils.getResId("drawable", "ui_cam_rotation_v", this);
        drawable_ui_cam_rotation_h = ResourcesUtils.getResId("drawable", "ui_cam_rotation_h", this);

        setupUIButtons();

        cameraView = (ScanbotCameraView) findViewById("scanbotCameraView");
        cameraView.setCameraOpenCallback(new CameraOpenCallback() {
            @Override
            public void onCameraOpened() {
                cameraView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cameraView.continuousFocus();
                    }
                }, CAMERA_OPEN_DELAY_MS);
            }
        });

        contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView);

        polygonView = (PolygonView) findViewById("scanbotPolygonView");
        polygonView.setStrokeColor(edgeColor);
        polygonView.setStrokeWidth(7.0f); // not implemented in iOS SDK yet. so we use a fix value here.

        contourDetectorFrameHandler.addResultHandler(polygonView);
        contourDetectorFrameHandler.addResultHandler(this);

        autoSnappingController = AutoSnappingController.attach(cameraView, contourDetectorFrameHandler);
        autoSnappingController.setSensitivity((float) autoSnappingSensitivity);

        cameraView.addPictureCallback(this);

        userGuidanceToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        userGuidanceToast.setGravity(Gravity.CENTER, 0, 0);
        rotateDeviceImageView = (ImageView) findViewById("rotateDeviceImageView");

        screenOrientation = getResources().getConfiguration().orientation;

        processPictureProgressBar = (ProgressBar) findViewById("processPictureProgressBar");

        setAutoSnapEnabled(autoSnappingEnabled);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (textResBundle != null) {
            outState.putBundle(EXTRAS_ARG_TEXT_RES_MAP, textResBundle);
        }
        outState.putInt(EXTRAS_ARG_EDGE_COLOR, edgeColor);
        outState.putInt(EXTRAS_ARG_JPG_QUALITY, jpgQuality);
        outState.putInt(EXTRAS_ARG_SAMPLE_SIZE, sampleSize);
        outState.putBoolean(EXTRAS_ARG_AUTOSNAPPING_ENABLED, autoSnappingEnabled);
        outState.putDouble(EXTRAS_ARG_AUTOSNAPPING_SENSITIVITY, autoSnappingSensitivity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
        this.userGuidanceEnabled.set(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
        
        this.userGuidanceEnabled.set(false);
        resetUserGuidanceUi(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    public void onPictureTaken(byte[] image, int imageOrientation) {
        debugLog("Picture was taken. imageOrientation = " + imageOrientation);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lockScreenOrientation();
                processPictureProgressBar.setVisibility(View.VISIBLE);
                resetUserGuidanceUi(true);
                flashToggle.setVisibility(View.GONE);
                shutterDrawable.setActive(false);
                snapImageButton.setVisibility(View.GONE);
                cancelButton.setVisibility(View.GONE);
                autosnapToggle.setVisibility(View.GONE);
                polygonView.setVisibility(View.GONE);
            }
        });

        // process picture in a background task:
        new ProcessTakenPictureTask().executeOnExecutor(executor, image, imageOrientation);
    }

    private void lockScreenOrientation() {
        if (screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public boolean handleResult(final ContourDetectorFrameHandler.DetectedFrame result) {
        //debugLog("Detection result: " + result.detectionResult);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // this.detectionHelper.onResult(result.detectionResult);
                showUserGuidance(result.detectionResult);
            }
        });

        return false; // typically you need to return false
    }


    private String getTextResValue(final String key, final String defaultValue) {
        return (textResBundle != null && textResBundle.containsKey(key) ?
                textResBundle.getString(key) : defaultValue);
    }


    private void resetUserGuidanceUi(boolean cancelToast) {
        rotateDeviceImageView.setVisibility(View.GONE);
        //moveCloser.setVisibility(View.GONE);
        shutterDrawable.setActive(false);

        if (cancelToast) {
            userGuidanceToast.cancel();
        }
    }


    private void showUserGuidance(final DetectionResult result) {
        if (!isAutoSnapEnabled()) {
            return;
        }
        if (!this.userGuidanceEnabled.get()) {
            return;
        }

        resetUserGuidanceUi(false);
        switch (result) {
            case OK:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_do_not_move", "Don't move"));
                userGuidanceToast.show();
                shutterDrawable.setActive(true);
                break;
            case OK_BUT_BAD_ASPECT_RATIO:
                userGuidanceToast.cancel();
                final int imageResource = (screenOrientation == Configuration.ORIENTATION_LANDSCAPE ?
                        drawable_ui_cam_rotation_v : drawable_ui_cam_rotation_h);
                rotateDeviceImageView.setImageResource(imageResource);
                rotateDeviceImageView.setVisibility(View.VISIBLE);
                break;
            case OK_BUT_TOO_SMALL:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_move_closer", "Move closer"));
                userGuidanceToast.show();
                break;
            case OK_BUT_BAD_ANGLES:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_bad_angles", "Perspective"));
                userGuidanceToast.show();
                break;
            case ERROR_NOTHING_DETECTED:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_nothing_detected", "No Document"));
                userGuidanceToast.show();
                break;
            case ERROR_TOO_NOISY:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_too_noisy", "Background too noisy"));
                userGuidanceToast.show();
                break;
            case ERROR_TOO_DARK:
                userGuidanceToast.setText(getTextResValue("autosnapping_hint_too_dark", "Poor light"));
                userGuidanceToast.show();
                break;
            default:
                userGuidanceToast.cancel();
                break;
        }
    }


    private void setAutoSnapEnabled(boolean enabled) {
        resetUserGuidanceUi(true);

        autoSnappingController.setEnabled(enabled);
        contourDetectorFrameHandler.setEnabled(enabled);

        int image_resid = ResourcesUtils.getResId("drawable", "ui_scan_automatic_active", this);
        if (enabled) {
            shutterDrawable.startAnimation();
            polygonView.setVisibility(View.VISIBLE);
        } else {
            shutterDrawable.stopAnimation();
            polygonView.setVisibility(View.GONE);
            image_resid = ResourcesUtils.getResId("drawable", "ui_scan_automatic", this);
        }
        autosnapToggle.setBackgroundResource(image_resid);
    }

    private boolean isAutoSnapEnabled() {
        return autoSnappingController.isEnabled();
    }

    protected void setFlashEnabled(final boolean enabled) {
        cameraView.useFlash(enabled);
    }


    private void debugLog(final String msg) {
        LogUtils.debugLog(LOG_TAG, msg);
    }

    private void errorLog(final String msg) {
        LogUtils.errorLog(LOG_TAG, msg);
    }

    private void errorLog(final String msg, final Throwable e) {
        LogUtils.errorLog(LOG_TAG, msg, e);
    }


    class ProcessTakenPictureTask extends AsyncTask<Object, Void, ProcessTakenPictureResult> {

        @Override
        protected ProcessTakenPictureResult doInBackground(Object... params) {
            final byte[] image = (byte[]) params[0];
            final int imageOrientation = (Integer) params[1];
            final int quality = jpgQuality;
            final int inSampleSize = sampleSize;

            DetectionResult sdkDetectionResult = DetectionResult.ERROR_NOTHING_DETECTED;
            List<PointF> polygonF = Collections.emptyList();
            Uri originalImgUri = null, documentImgUri = null;
            try {
                // decode original image:
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = inSampleSize;
                Bitmap originalBitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);

                // rotate original image if required:
                if (imageOrientation > 0) {
                    debugLog("Rotating original picture ...");
                    final Matrix matrix = new Matrix();
                    matrix.setRotate(imageOrientation, originalBitmap.getWidth() / 2f, originalBitmap.getHeight() / 2f);
                    originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, false);
                }

                // store original image:
                originalImgUri = sdkWrapper.storeImage(originalBitmap, quality);
                debugLog("Original picture was stored as file: " + originalImgUri);

                debugLog("Processing document detection ...");
                final ScanbotSdkWrapper.DocumentDetectionResult docDetectionResult = sdkWrapper.documentDetection(originalBitmap, true);
                sdkDetectionResult = docDetectionResult.sdkDetectionResult;
                polygonF = docDetectionResult.polygon;

                if (docDetectionResult.documentImage != null) {
                    documentImgUri = sdkWrapper.storeImage(docDetectionResult.documentImage, quality);
                    debugLog("Detected and cropped document picture was stored as file: " + documentImgUri);
                    docDetectionResult.documentImage.recycle();
                }
            }
            catch(final Exception e) {
                errorLog("Could not process image: " + e.getMessage(), e);
            }

            return new ProcessTakenPictureResult(originalImgUri, imageOrientation,
                    documentImgUri, sdkDetectionResult, polygonF);
        }

        @Override
        protected void onPostExecute(final ProcessTakenPictureResult result) {
            if (!isCancelled()) {
                final Bundle extras = new Bundle();
                extras.putString(EXTRAS_ARG_ORIGINAL_IMAGE_FILE_URI, result.originalImgUri.toString());
                extras.putInt(EXTRAS_ARG_IMAGE_ORIENTATION, result.imageOrientation);
                extras.putString(EXTRAS_ARG_IMAGE_FILE_URI, result.documentImgUri.toString());
                extras.putParcelableArrayList(EXTRAS_ARG_DETECTED_POLYGON, (ArrayList<PointF>) result.polygonF);
                extras.putString(EXTRAS_ARG_DETECTION_RESULT, result.sdkDetectionResult.name());
                final Intent intent = new Intent();
                intent.putExtras(extras);
                setResult(RESULT_SB_CAM_PICTURE_TAKEN, intent);
                // close activity
                finish();
            }
        }
    }


    class ProcessTakenPictureResult {
        final Uri originalImgUri;
        final int imageOrientation;
        final Uri documentImgUri;
        final DetectionResult sdkDetectionResult;
        final List<PointF> polygonF;

        ProcessTakenPictureResult(final Uri originalImgUri,
                                  final int imageOrientation,
                                  final Uri documentImgUri,
                                  final DetectionResult sdkDetectionResult,
                                  final List<PointF> polygonF) {
            this.originalImgUri = originalImgUri;
            this.imageOrientation = imageOrientation;
            this.documentImgUri = documentImgUri;
            this.sdkDetectionResult = sdkDetectionResult;
            this.polygonF = polygonF;
        }
    }


    class ScanbotCameraBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (intent.getAction().equals(ACTION_DISMISS_SB_CAMERA)) {
                ScanbotCameraActivity.this.finish();
            }
        }
    }

}
