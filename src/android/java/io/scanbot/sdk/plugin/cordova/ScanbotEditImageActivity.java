package io.scanbot.sdk.plugin.cordova;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.View;
import android.widget.ProgressBar;

import io.scanbot.sdk.plugin.cordova.utils.ImageUtils;
import io.scanbot.sdk.plugin.cordova.utils.LogUtils;
import net.doo.snap.lib.detector.ContourDetector;
import net.doo.snap.lib.detector.DetectionResult;
import net.doo.snap.lib.detector.Line2D;
import net.doo.snap.ui.EditPolygonImageView;
import net.doo.snap.ui.MagnifierView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.scanbot.sdk.plugin.cordova.ScanbotConstants.*;

import io.scanbot.sdk.plugin.cordova.utils.ResourcesUtils;


/**
 * Scanbot SDK Edit Image Activity
 */
public class ScanbotEditImageActivity extends AppCompatActivity {

    private static final String LOG_TAG = ScanbotEditImageActivity.class.getSimpleName();

    private static final String POLYGON = "polygon";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private ScanbotSdkWrapper sdkWrapper;

    private ActionBar actionBar;
    private Uri imageUri;
    private EditPolygonImageView editPolygonImageView;
    private MagnifierView scanbotMagnifierView;
    private ProgressBar processImageProgressBar;
    private View cancelBtn, doneBtn;

    private int edgeColor = DEFAUL_EDGE_COLOR;
    private int jpgQuality = ImageUtils.JPEG_QUALITY;

    private int screenOrientation;


    private View findViewById(final String id) {
        final int idInt = ResourcesUtils.getResId("id", id, this);
        return findViewById(idInt);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(ResourcesUtils.getResId("layout", "scanbot_edit_image_view", this));

        actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setCustomView(ResourcesUtils.getResId("layout", "action_bar_edit_polygon_view", this));

        sdkWrapper = new ScanbotSdkWrapper(this);

        edgeColor = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_EDGE_COLOR) ?
                savedInstanceState.getInt(EXTRAS_ARG_EDGE_COLOR) :
                getIntent().getExtras().getInt(EXTRAS_ARG_EDGE_COLOR));

        jpgQuality = (savedInstanceState != null && savedInstanceState.containsKey(EXTRAS_ARG_JPG_QUALITY) ?
                savedInstanceState.getInt(EXTRAS_ARG_JPG_QUALITY) :
                getIntent().getExtras().getInt(EXTRAS_ARG_JPG_QUALITY));

        editPolygonImageView = (EditPolygonImageView) findViewById("scanbotEditImageView");
        editPolygonImageView.setEdgeColor(edgeColor);
        editPolygonImageView.setEdgeWidth(7.0f); // not implemented in iOS SDK yet. so we use a fix value here.

        scanbotMagnifierView = (MagnifierView) findViewById("scanbotMagnifierView");

        processImageProgressBar = (ProgressBar) findViewById("processImageProgressBar");

        screenOrientation = getResources().getConfiguration().orientation;

        // get saved polygon (e.g. on screen rotation):
        final List<PointF> polygonArg = getPolygonFromSavedInstanceState(savedInstanceState);

        // get input image URI:
        final String imageFileUri = getIntent().getExtras().getString(EXTRAS_ARG_IMAGE_FILE_URI);
        imageUri = Uri.parse(imageFileUri);

        cancelBtn = findViewById("cancelButton");
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        doneBtn = findViewById("doneButton");
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelBtn.setEnabled(false);
                doneBtn.setEnabled(false);
                applyEditChanges();
            }
        });

        new InitImageViewTask().executeOnExecutor(executor, imageUri, polygonArg);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(POLYGON, (ArrayList<PointF>) editPolygonImageView.getPolygon());
        outState.putInt(EXTRAS_ARG_EDGE_COLOR, edgeColor);
        outState.putInt(EXTRAS_ARG_JPG_QUALITY, jpgQuality);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private List<PointF> getPolygonFromSavedInstanceState(final Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(POLYGON)) {
            return savedInstanceState.getParcelableArrayList(POLYGON);
        }
        return Collections.EMPTY_LIST;
    }


    private void applyEditChanges() {
        lockScreenOrientation();
        processImageProgressBar.setVisibility(View.VISIBLE);
        cancelBtn.setVisibility(View.GONE);
        doneBtn.setVisibility(View.GONE);

        new ApplyEditChangesTask().executeOnExecutor(executor, imageUri, editPolygonImageView.getPolygon());
    }


    private void lockScreenOrientation() {
        if (screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }


    private ArrayList<PointF> getDefaultPolygon() {
        return new ArrayList<PointF>() {
            {
                add(new PointF(0, 0));
                add(new PointF(1, 0));
                add(new PointF(1f, 1f));
                add(new PointF(0, 1));
            }
        };
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



    class InitImageViewTask extends AsyncTask<Object, Void, InitImageResult> {

        @Override
        protected InitImageResult doInBackground(Object... params) {
            final Uri imageUri = (Uri) params[0];
            final List<PointF> polygonArg = (List<PointF>) params[1];

            Pair<List<Line2D>, List<Line2D>> linesPair = null;
            List<PointF> polygon = null; // detected polygon

            try {
                final ContourDetector detector = new ContourDetector();

                debugLog("Resizing image for preview ...");
                final Bitmap originalBitmap = ImageUtils.loadImage(imageUri, ScanbotEditImageActivity.this);
                final Bitmap resizedBitmap = ImageUtils.resizeImage(originalBitmap, 1000, 1000);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // important! first set the image and then the detected polygon and lines!
                        editPolygonImageView.setImageBitmap(resizedBitmap);
                        // set up the MagnifierView every time when editPolygonView is set with a new image.
                        scanbotMagnifierView.setupMagnifier(editPolygonImageView);
                    }
                });

                final DetectionResult detectionResult = detector.detect(resizedBitmap);
                linesPair = new Pair<List<Line2D>, List<Line2D>>(detector.getHorizontalLines(), detector.getVerticalLines());
                switch (detectionResult) {
                    case OK:
                    case OK_BUT_BAD_ANGLES:
                    case OK_BUT_TOO_SMALL:
                    case OK_BUT_BAD_ASPECT_RATIO:
                        polygon = detector.getPolygonF();
                        debugLog("Detected polygon: " + polygon);
                        break;
                    default:
                        polygon = getDefaultPolygon();
                        break;
                }
            } catch (final Exception e) {
                errorLog("Could not init polygon image view on image: " + imageUri, e);
            }

            if (polygonArg != null && !polygonArg.isEmpty()) {
                debugLog("Using polygon from arguments: " + polygonArg);
                polygon = polygonArg;
            }

            if (linesPair != null) {
                debugLog("Detected horizontal lines: " + linesPair.first);
                debugLog("Detected vertical lines: " + linesPair.second);
            }

            return new InitImageResult(linesPair, polygon);
        }

        @Override
        protected void onPostExecute(final InitImageResult result) {
            editPolygonImageView.setPolygon(result.polygonF);

            if (result.linesPair != null) {
                editPolygonImageView.setLines(result.linesPair.first, result.linesPair.second);
            }
        }
    }


    class InitImageResult {
        final Pair<List<Line2D>, List<Line2D>> linesPair;
        final List<PointF> polygonF;

        InitImageResult(final Pair<List<Line2D>, List<Line2D>> linesPair,
                        final List<PointF> polygonF) {
            this.linesPair = linesPair;
            this.polygonF = polygonF;
        }
    }


    class ApplyEditChangesTask extends AsyncTask<Object, Void, ApplyEditChangesTaskResult> {

        @Override
        protected ApplyEditChangesTaskResult doInBackground(Object... params) {
            final Uri imageUri = (Uri) params[0];
            final List<PointF> polygon = (List<PointF>) params[1];
            final int quality = jpgQuality;

            debugLog("Cropping/warping with polygon: " + polygon);

            if (!isCancelled()) {
                try {
                    final Bitmap originalBitmap = ImageUtils.loadImage(imageUri, ScanbotEditImageActivity.this);
                    final Bitmap resultImg = sdkWrapper.cropAndWarpImage(originalBitmap, polygon, true);
                    final Uri resultImgUri = sdkWrapper.storeImage(resultImg, quality);
                    debugLog("Stored cropped image as: " + resultImgUri.toString());
                    return new ApplyEditChangesTaskResult(resultImgUri, polygon);
                } catch (final Exception e) {
                    errorLog("Could not process changes on image", e);
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(final ApplyEditChangesTaskResult result) {
            if (!isCancelled() && result != null) {
                final Intent intent = new Intent();
                intent.putExtra(EXTRAS_ARG_IMAGE_FILE_URI, result.imageUri.toString());
                intent.putExtra(EXTRAS_ARG_DETECTED_POLYGON, (ArrayList<PointF>) result.polygon);
                setResult(RESULT_SB_EDIT_IMAGE, intent);
                finish();
            }
        }
    }


    class ApplyEditChangesTaskResult {
        final Uri imageUri;
        final List<PointF> polygon;

        ApplyEditChangesTaskResult(final Uri imageUri, final List<PointF> polygon) {
            this.imageUri = imageUri;
            this.polygon = polygon;
        }
    }


}
