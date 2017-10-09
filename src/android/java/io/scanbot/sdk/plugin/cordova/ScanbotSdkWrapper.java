package io.scanbot.sdk.plugin.cordova;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;

import io.scanbot.sdk.plugin.cordova.utils.FileUtils;
import io.scanbot.sdk.plugin.cordova.utils.JsonArgs;
import io.scanbot.sdk.plugin.cordova.utils.LogUtils;
import net.doo.snap.ScanbotSDK;
import net.doo.snap.blob.BlobFactory;
import net.doo.snap.blob.BlobManager;
import net.doo.snap.entity.Blob;
import net.doo.snap.entity.Document;
import net.doo.snap.entity.OcrStatus;
import net.doo.snap.entity.Page;
import net.doo.snap.entity.SnappingDraft;
import net.doo.snap.entity.Language;
import net.doo.snap.lib.detector.ContourDetector;
import net.doo.snap.lib.detector.DetectionResult;
import net.doo.snap.persistence.PageFactory;
import net.doo.snap.persistence.cleanup.Cleaner;
import net.doo.snap.process.DocumentProcessingResult;
import net.doo.snap.process.DocumentProcessor;
import net.doo.snap.process.TextRecognition;
import net.doo.snap.process.draft.DocumentDraftExtractor;
import net.doo.snap.process.util.DocumentDraft;
import net.doo.snap.process.OcrResult;
import net.doo.snap.util.FileChooserUtils;
import net.doo.snap.util.bitmap.BitmapUtils;
import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Scanbot SDK Wrapper for the Cordova Plugin
 */
public class ScanbotSdkWrapper {

    private static final String LOG_TAG = ScanbotSdkWrapper.class.getSimpleName();


    // ImageFilter mapping: JS constants => SDK
    private static final Map<String, Integer> imageFilterMapping = new HashMap<String, Integer>();
    static {
        imageFilterMapping.put("NONE",              ContourDetector.IMAGE_FILTER_NONE);
        imageFilterMapping.put("COLOR_ENHANCED",    ContourDetector.IMAGE_FILTER_COLOR_ENHANCED);
        imageFilterMapping.put("GRAYSCALE",         ContourDetector.IMAGE_FILTER_GRAY);
        imageFilterMapping.put("BINARIZED",         ContourDetector.IMAGE_FILTER_BINARIZED);
    }

    // Document Detection Result mapping: SDK => JS constants
    private static final Map<DetectionResult, String> docDetectionResultMapping = new HashMap<DetectionResult, String>();
    static {
        docDetectionResultMapping.put(DetectionResult.OK,                       "OK");
        docDetectionResultMapping.put(DetectionResult.OK_BUT_BAD_ANGLES,        "OK_BUT_BAD_ANGLES");
        docDetectionResultMapping.put(DetectionResult.OK_BUT_BAD_ASPECT_RATIO,  "OK_BUT_BAD_ASPECT_RATIO");
        docDetectionResultMapping.put(DetectionResult.OK_BUT_TOO_SMALL,         "OK_BUT_TOO_SMALL");
        docDetectionResultMapping.put(DetectionResult.ERROR_TOO_DARK,           "ERROR_TOO_DARK");
        docDetectionResultMapping.put(DetectionResult.ERROR_TOO_NOISY,          "ERROR_TOO_NOISY");
        docDetectionResultMapping.put(DetectionResult.ERROR_NOTHING_DETECTED,   "ERROR_NOTHING_DETECTED");
    }


    private final Context context;


    public ScanbotSdkWrapper(final Context context) {
        this.context = context;
    }


    public Bitmap loadImage(final String imageFilePath) throws IOException {
        final Bitmap bitmap = BitmapUtils.decodeQuietly(imageFilePath, null);
        if (bitmap == null) {
            throw new IOException("Could not load image. Bitmap is null.");
        }
        return bitmap;
    }


    public Bitmap loadImage(final Uri imageUri) throws IOException {
        return this.loadImage(FileChooserUtils.getPath(this.context, imageUri));
    }


    public File storeImageAsFile(final Bitmap image, final int quality) throws IOException {
        final File pictureFile = FileUtils.generateRandomTempScanbotFile("jpg", this.context);
        final FileOutputStream fos = new FileOutputStream(pictureFile);
        image.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        fos.close();
        return pictureFile;
    }


    public Uri storeImage(final Bitmap image, final int quality) throws IOException {
        return Uri.fromFile(storeImageAsFile(image, quality));
    }


    public DocumentDetectionResult documentDetection(final Bitmap bitmap, final boolean releaseBitmap) {
        debugLog("Applying document detection on bitmap...");

        final ContourDetector detector = new ContourDetector();
        final DetectionResult sdkDetectionResult = detector.detect(bitmap);
        final List<PointF> polygon = detector.getPolygonF();

        final Bitmap documentImage;
        if (releaseBitmap) {
            /*
             * This operation crops original bitmap and creates a new one. Old bitmap is recycled
             * and can't be used anymore!!
             */
            documentImage = detector.processImageAndRelease(bitmap, polygon, ContourDetector.IMAGE_FILTER_NONE);
        }
        else {
            documentImage = detector.processImageF(bitmap, polygon, ContourDetector.IMAGE_FILTER_NONE);
        }

        return new DocumentDetectionResult(sdkDetectionResult, polygon, documentImage);
    }


    /**
     * Copy default OCR blob files (provided by the plugin package) from assets directory to the
     * Scanbot SDK internal OCR directory.
     *
     * The AndroidManifest.xml must define the source for assets like:
     *  <meta-data android:name="ocr_blobs_path" android:value="scanbot-sdk/di/tessdata/" />
     *  <meta-data android:name="language_classifier_blob_path" android:value="scanbot-sdk/di/" />
     *
     * Language specific OCR blobs (e.g. osd.traineddata, eng.traineddata, deu.traineddata, etc)
     * are NOT part of the plugin package and must be provided by the App!
     *
     * @param pdfSdk
     * @throws IOException
     */
    public void prepareDefaultOcrBlobs(final PdfSdk pdfSdk) throws IOException {
        debugLog("Checking/preparing OCR language detector blobs...");
        // copy languageClassifier2.bin from assets to SDK internal OCR blobs directory
        for (final Blob blob : pdfSdk.blobFactory.languageDetectorBlobs()) {
            if (!pdfSdk.blobManager.isBlobAvailable(blob)) {
                pdfSdk.blobManager.fetch(blob, false);
            }
        }

        debugLog("Checking/preparing PDF blob...");
        // copy pdf.ttf from assets to SDK internal OCR blobs directory
        final Blob pdfBlob = new Blob(new File(pdfSdk.blobManager.getOCRBlobsDirectory(), "pdf.ttf").getPath(), "pdf.ttf");
        if (!pdfSdk.blobManager.isBlobAvailable(pdfBlob)) {
            pdfSdk.blobManager.fetch(pdfBlob, false);
        }
    }


    public List<String> getInstalledOcrLanguages(final PdfSdk pdfSdk) throws IOException {
        debugLog("Detecting installed OCR languages...");

        final List<String> installedLanguages = new ArrayList<String>();
        final Set<Language> allLanguagesWithAvailableOcrBlobs = pdfSdk.blobManager.getAllLanguagesWithAvailableOcrBlobs();
        for (final Language language : allLanguagesWithAvailableOcrBlobs) {
            installedLanguages.add(language.getIso1Code());
        }
        return installedLanguages;
    }


    public OcrResult performOcr(final List<String> languagesIsoCodes,
                                final PdfSdk pdfSdk,
                                TextRecognition textRecognition,
                                final List<Uri> images,
                                String outputFormat) throws IOException {
        debugLog("Performing OCR...");

        List<Language> languages = new ArrayList<Language>();
        for (String languageIsoCode : languagesIsoCodes) {
            languages.add(Language.languageByIso(languageIsoCode));
        }
        for (Language language : languages) {
            Collection<Blob> ocrBlobs = pdfSdk.blobFactory.ocrLanguageBlobs(language);
            for (Blob blob : ocrBlobs) {
                if (!pdfSdk.blobManager.isBlobAvailable(blob)) {
                    throw new IOException("OCR blobs for selected languages were not found.");
                }
            }
        }

        List<Page> pages = new ArrayList<Page>();
        for (Uri image : images) {
            pages.add(pdfSdk.pageFactory.buildPage(new File(image.getPath())));
        }

        if (outputFormat.equals("PLAIN_TEXT")) {
            return textRecognition
                    .withoutPDF(
                            languages,
                            pages
                    ).recognize();
        } else {
            Document document = new Document();
            document.setName("document.pdf");
            document.setOcrStatus(OcrStatus.PENDING);
            document.setId("id");
            return textRecognition
                    .withPDF(
                            languages,
                            document,
                            pages
                    ).recognize();
        }
    }


    public Bitmap cropAndWarpImage(final Bitmap bitmap, final List<PointF> polygon, final boolean releaseBitmap) {
        final ContourDetector detector = new ContourDetector();
        if (releaseBitmap) {
            /*
             * This operation crops original bitmap and creates a new one. Old bitmap is recycled
             * and can't be used anymore!!
             */
            return detector.processImageAndRelease(bitmap, polygon, ContourDetector.IMAGE_FILTER_NONE);
        }
        else {
            return detector.processImageF(bitmap, polygon, ContourDetector.IMAGE_FILTER_NONE);
        }
    }


    public Bitmap applyImageFilter(final Bitmap bitmap, final int imageFilter) {
        debugLog("Applying image filter on bitmap...");

        final ContourDetector detector = new ContourDetector();
        final List<PointF> polygon = new ArrayList<PointF>();

        return detector.processImageAndRelease(bitmap, polygon, imageFilter);
    }


    public File createPdf(final List<Uri> images, final PdfSdk pdfSdk) throws IOException {
        try {
            final SnappingDraft snappingDraft = new SnappingDraft();
            snappingDraft.setDocumentName(UUID.randomUUID().toString());

            for (final Uri imageUri: images) {
                final String path = FileChooserUtils.getPath(context, imageUri);
                final File file = new File(path);
                //debugLog("Creating a page of image file: " + file);
                final Page page = pdfSdk.pageFactory.buildPage(file);
                //page.setRotationType(RotationType.getByDegrees(imageOrientation));
                snappingDraft.addPage(page);
            }

            // Convert SnappingDraft to one/many DocumentDraft:
            final DocumentDraft[] documentDrafts = pdfSdk.documentDraftExtractor.extract(snappingDraft);
            //debugLog("Number of extracted documentDrafts: " + documentDrafts.length);

            // Current implementation creates a single document: all images will be stored as one PDF document file.
            // So we assume documentDrafts array contains only one entry:
            final DocumentDraft draft = documentDrafts[0];
            final DocumentProcessingResult result = pdfSdk.documentProcessor.processDocument(draft);
            return result.getDocumentFile();
        }
        finally {
            //debugLog("SDK cleanUp...");
            pdfSdk.sdkCleaner.cleanUp();
        }
    }


    public JSONArray sdkPolygonToJson(final List<PointF> polygon) {
        final JSONArray result = new JSONArray();
        for (final PointF p: polygon) {
            final JsonArgs jsonPoint = new JsonArgs();
            jsonPoint.put("x", p.x).put("y", p.y);
            result.put(jsonPoint.jsonObj());
        }
        return result;
    }


    public int jsImageFilterToSdkFilter(final String imageFilter) {
        if (imageFilterMapping.containsKey(imageFilter)) {
            return imageFilterMapping.get(imageFilter);
        }
        throw new IllegalArgumentException("Unsupported imageFilter: " + imageFilter);
    }


    public String sdkDocDetectionResultToJsString(final DetectionResult detectionResult) {
        if (docDetectionResultMapping.containsKey(detectionResult)) {
            return docDetectionResultMapping.get(detectionResult);
        }
        errorLog("Got unsupported DetectionResult from SDK: " + detectionResult);
        return detectionResult.name();
    }


    private void debugLog(final String msg) {
        LogUtils.debugLog(LOG_TAG, msg);
    }

    private void errorLog(final String msg) {
        LogUtils.errorLog(LOG_TAG, msg);
    }


    public static final class DocumentDetectionResult {
        public final DetectionResult sdkDetectionResult;
        public final List<PointF> polygon = new ArrayList<PointF>();
        public final Bitmap documentImage;

        public DocumentDetectionResult(final DetectionResult sdkDetectionResult,
                                       final List<PointF> polygon,
                                       final Bitmap documentImage) {
            this.sdkDetectionResult = sdkDetectionResult;
            if (polygon != null) {
                this.polygon.addAll(polygon);
            }
            this.documentImage = documentImage;
        }
    }


    public static final class PdfSdk {
        public final ScanbotSDK scanbotSDK;
        public final PageFactory pageFactory;
        public final DocumentProcessor documentProcessor;
        // scanbotSDK.documentDraftExtractor() returns default DocumentDraftExctactor which treats every snappingDraft as single document.
        // You can change this behaviour by either implementing your own extractor or using one which is coming with SDK (see MultipleDocumentsDraftExtractor)
        public final DocumentDraftExtractor documentDraftExtractor;
        public final Cleaner sdkCleaner;
        public final BlobManager blobManager;
        public final BlobFactory blobFactory;

        public PdfSdk(final Activity activity) {
            // The following instances must be created from the main (UI) thread!
            // This is required by the native Scanbot SDK for Android.
            this.scanbotSDK = new ScanbotSDK(activity);
            this.pageFactory = scanbotSDK.pageFactory();
            this.documentProcessor = scanbotSDK.documentProcessor();
            this.documentDraftExtractor = scanbotSDK.documentDraftExtractor();
            this.sdkCleaner = scanbotSDK.cleaner();
            this.blobManager = scanbotSDK.blobManager();
            this.blobFactory = scanbotSDK.blobFactory();
        }
    }


}
