/* Copyright 2021 CDL EML, TU Wien, Austria

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package at.tuwien.ict.eml.odd;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.common.util.concurrent.ListenableFuture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import at.tuwien.ict.eml.odd.customView.OverlayView;
import at.tuwien.ict.eml.odd.env.Boxplot;
import at.tuwien.ict.eml.odd.env.ImageUtils;
import at.tuwien.ict.eml.odd.detection.Detector;
import at.tuwien.ict.eml.odd.detection.FirebaseML;
import at.tuwien.ict.eml.odd.detection.TFLiteObjectDetection;
import at.tuwien.ict.eml.odd.env.YuvToRgbConverter;
import at.tuwien.ict.eml.odd.tracking.Tracker;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class CameraActivity extends AppCompatActivity {
    // region VARIABLES

    // private static final String TAG = "TF_OD_CAMERA_ACT_LOG";

    // properties from cloud - model specific
    private String remoteConfChosenModelLabel;
    private int remoteConfModelInputSize;
    private boolean remoteConfModelIsQuantized;
    private ArrayList<String> remoteConfLabelMapList = new ArrayList<>();

    // properties from app preferences
    private float prefDetectionConfidenceThreshold;
    private boolean prefCropModeContain;
    private int prefBoxplotValueSize;
    private boolean prefOneshotMode;

    private boolean analysisRunning;

    private final DetectorMode MODE = DetectorMode.TF_OD_API;

    private enum DetectorMode {
        TF_OD_API
    }

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.VIBRATE};
    private int PERMISSIONS_REQUEST;

    private Detector detector;
    private Tracker tracker;

    private long currentInferenceTimestamp;
    private Paint cropPreviewBoxes;
    private boolean prefShowConfidence;
    private String prefBoundingBoxColorMode;

    private Bitmap rgb_bitmap_analyze;
    private Bitmap rgb_bitmap_analyze_crop;
    private Matrix screenFrameToCrop;
    private Matrix cropToScreenFrame = new Matrix();

    private YuvToRgbConverter ytrConverter;

    private long[] inferencePSInterval = {0};
    private long lastProcessingTimeMs;
    private long[] lastInferenceTimestamp;

    private Executor executorAnalyze;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;

    ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;

    private OverlayView trackingOverlay;
    private OverlayView plotOverlay;
    @SuppressWarnings("rawtypes")
    private BottomSheetBehavior sheetBehavior;
    private View peekLayout;
    private TextView textViewInference;
    private TextView textViewInferencePS;
    private TextView textViewUsedModel;
    private TextView textViewBoxplotDesc;
    private TextView textViewCropSize;

    private ImageButton captureButton;

    private Boxplot boxPlot;
    private ArrayList<Integer> boxPlotValues = new ArrayList<>();

    //endregion

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                //startSettings();
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.changeModel:
                // return to modelChoose activity
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        executorAnalyze = newSingleThreadExecutor();

        // screen always on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // layout specific
        setContentView(R.layout.activity_camera);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        LinearLayout bottomSheetLayout = findViewById(R.id.bottom_sheet_include);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

        previewView = findViewById(R.id.previewView);
        peekLayout = findViewById(R.id.always_on_layout);
        textViewInference = findViewById(R.id.inference_value);
        plotOverlay = findViewById(R.id.boxplot_overlay);

        captureButton = findViewById(R.id.camera_capture_button);

        trackingOverlay = findViewById(R.id.trackingOverlay);

        textViewInferencePS = findViewById(R.id.stat_processing_val);
        textViewUsedModel = findViewById(R.id.stat_usedmodel_val);
        textViewBoxplotDesc = findViewById(R.id.stat_plot_description);
        textViewCropSize = findViewById(R.id.stat_cropSize_val);

        cropPreviewBoxes = new Paint();
        cropPreviewBoxes.setColor(Color.RED);
        cropPreviewBoxes.setStyle(Paint.Style.STROKE);
        cropPreviewBoxes.setStrokeWidth(2.0f);

        ytrConverter = new YuvToRgbConverter(getApplicationContext());

        // set random permission int
        Random rand = new Random();
        PERMISSIONS_REQUEST = rand.nextInt(100);

        if (!allPermissionsGranted(REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST);
        }

        // setup TFLite, Detector and Tracker
        // permission request is asynchronous, so this is called before permissions
        setup();

        // add listener to globalLayout - called when layout change
        peekLayout.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // remove on first change, so that its not called on every change
                        peekLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        // get and set the height of the layout
                        sheetBehavior.setPeekHeight(peekLayout.getMeasuredHeight());
                    }
                });
        sheetBehavior.setHideable(false);
    }

    /**
     * setup for camera start, boxplot, tracker and requesting Remote TFLite models
     */
    protected void setup() {
        setupTFLite();
        setupTracker();
        setupBoxplot();
    }

    /**
     * setup the TFLit Object Detector
     */
    private void setupTFLite() {
        // get intent from last activity
        Intent intent = getIntent();

        // extract modelConfigJSON and chosenModelLabel from intent from modelChooser activity
        String remoteConfModelConfigJSON = intent.getStringExtra("modelConfigJSON");
        remoteConfChosenModelLabel = intent.getStringExtra("chosenModelLabel");

        try {
            remoteConfLabelMapList = FirebaseML.extractLabelMap(remoteConfModelConfigJSON, remoteConfChosenModelLabel);
            remoteConfModelInputSize = FirebaseML.extractInputSize(remoteConfModelConfigJSON, remoteConfChosenModelLabel);
            remoteConfModelIsQuantized = FirebaseML.extractQuantized(remoteConfModelConfigJSON, remoteConfChosenModelLabel);
        } catch (JSONException e) {
            finish();
            e.printStackTrace();
        }
        // get model file from previous activity
        File modelFile = new File(intent.getStringExtra("modelFilePath"));

        // config of the detector - only one time needed
        try {
            detector = TFLiteObjectDetection.create(
                    getApplicationContext(),
                    modelFile,
                    remoteConfLabelMapList,
                    remoteConfModelInputSize,
                    remoteConfModelIsQuantized
            );
        } catch (IOException e) {
            finish();
            e.printStackTrace();
        }

        // set dynamic textViews for model size and used model
        textViewUsedModel.setText(remoteConfChosenModelLabel);
        textViewCropSize.setText(String.format("%d x %d", remoteConfModelInputSize, remoteConfModelInputSize));
    }

    /**
     * setup a Tracker instance and register the custom drawCallback
     */
    private void setupTracker() {
        // onetime config of the tracker
        tracker = new Tracker(getApplicationContext(), remoteConfLabelMapList.size());
        trackingOverlay.addCallback(new OverlayView.DrawCallback() {
            @Override
            public void drawCallback(Canvas canvas) {
                tracker.draw(canvas);
            }
        });
    }

    /**
     * setup a Boxplot instance and register the custom drawCallback
     */
    private void setupBoxplot() {
        //config of the boxplot
        boxPlot = new Boxplot(
                ResourcesCompat.getColor(getResources(), R.color.eml_plot_highlight, null),
                ResourcesCompat.getColor(getResources(), R.color.eml_blue_green, null),
                ResourcesCompat.getColor(getResources(), R.color.eml_blue_green, null),
                ResourcesCompat.getColor(getResources(), R.color.eml_blue, null),
                getResources().getDimension(R.dimen.stat_fontSize),
                getResources().getDimension(R.dimen.stat_boxplot_linewidth));
        plotOverlay = findViewById(R.id.boxplot_overlay);
        plotOverlay.addCallback(new OverlayView.DrawCallback() {
            @Override
            public void drawCallback(Canvas canvas) {
                boxPlot.drawBoxplot(canvas);
            }
        });
        //plotOverlay.postInvalidate();
        // set text label
        textViewBoxplotDesc.setText(String.format("Inference Boxplot (n = %s)", prefBoxplotValueSize));
    }

    /**
     * creates an cameraProvider object and then calls cameraRunning
     */
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                // availability of camera provider is now guaranteed
                cameraProvider = cameraProviderFuture.get();
                // bind camera
                bindCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // should never reach this section, because currently no exceptions thrown
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * binds preview, capture and analyzer use cases to the
     *
     * @param cameraProvider processCameraProvider
     */
    @SuppressLint("RestrictedApi")
    void bindCamera(@NonNull ProcessCameraProvider cameraProvider) {
        // create preview object
        Preview preview = new Preview.Builder()
                //.setTargetResolution(new Size(600, 300))
                .build();

        // create camera selector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // create image analysis for the further processing of the preview pictures
        // set the minimum preview size
        // TODO try different target sizes to see which resolutions
        ImageAnalysis analyzer = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(new Size(2 * remoteConfModelInputSize, 2 * remoteConfModelInputSize))
                .build();

        // sets up an image analyzer to receive a data stream from the camera
        analyzer.setAnalyzer(executorAnalyze, continuousRunning);
        // creates new handler thread and handler
        //analyzer.setAnalyzer(CameraXExecutors.newHandlerExecutor(handler), continuousRunning);

        imageCapture =
                new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .setTargetResolution(new Size(2 * remoteConfModelInputSize, (int) (2.0f * remoteConfModelInputSize * 4.0f / 3.0f)))
                        .build();

        // bind layout surface to preview
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // unbind previous configs
        cameraProvider.unbindAll();
        // bind selector, preview object and analyzer for the lifetime together
        lastInferenceTimestamp = new long[]{System.currentTimeMillis()};
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview, analyzer);

    }

    /**
     * gets called when a new analyzer picture is ready to be processed
     */
    private final ImageAnalysis.Analyzer continuousRunning = new ImageAnalysis.Analyzer() {
        @SuppressLint({"UnsafeExperimentalUsageError", "UnsafeOptInUsageError"})
        @Override
        public void analyze(@NonNull ImageProxy image) {
            int imageWidth;
            int imageHeight;
            int imageRotDegreesTemp;

            if (image.getImage() != null) {
                imageWidth = image.getImage().getWidth();
                imageHeight = image.getImage().getHeight();
                imageRotDegreesTemp = image.getImageInfo().getRotationDegrees();
            } else {
                image.close();
                return;
            }

            Size trackerFrameSize;
            if (prefOneshotMode) {
                // enable usage of the whole image width beyond screen size with original aspect ratio
                trackerFrameSize = new Size(trackingOverlay.getHeight() * imageHeight / imageWidth, trackingOverlay.getHeight());
            } else {
                // in analysis mode the tracker size is just the visible space
                trackerFrameSize = new Size(trackingOverlay.getWidth(), trackingOverlay.getHeight());
            }
            int cropBorderTop = (trackerFrameSize.getHeight() - trackerFrameSize.getWidth()) / 2;
            int cropBorderBottom = cropBorderTop + trackerFrameSize.getWidth();

            // config the visible dimensions for the tracker
            tracker.setFrameConfiguration(
                    trackerFrameSize.getWidth(),
                    trackerFrameSize.getHeight(),
                    cropBorderTop,
                    cropBorderBottom,
                    prefShowConfidence,
                    prefBoundingBoxColorMode
            );

            // draw the initial frame on tracker
            // this is done for drawing the black boxes
            trackingOverlay.postInvalidate();

            // if analysis mode is disabled, post invalidate and exit
            if (prefOneshotMode) {
                image.close();
                return;
            }

            // convert the image from the camera feed to the rgb format
            if (rgb_bitmap_analyze == null) {
                rgb_bitmap_analyze = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            }
            ytrConverter.yuvToRgb(image.getImage(), rgb_bitmap_analyze);

            // crop original camera image with respect of the largest crop with the given aspect ratio
            // creates a frame out of the analyzer image with the highest possible resolution
            rgb_bitmap_analyze_crop = ImageUtils.ConvertPreviewBitmapToModelInput(
                    rgb_bitmap_analyze,
                    trackerFrameSize.getWidth(),
                    trackerFrameSize.getHeight(),
                    imageRotDegreesTemp,
                    remoteConfModelInputSize,
                    true,
                    prefCropModeContain
            );

            // configure a transformation matrix for mapping the crop image back to the visible frame
            screenFrameToCrop = ImageUtils.getTransformationMatrix(
                    trackerFrameSize.getWidth(),
                    trackerFrameSize.getHeight(),
                    remoteConfModelInputSize,
                    remoteConfModelInputSize,
                    0,
                    prefCropModeContain);
            screenFrameToCrop.invert(cropToScreenFrame);

            // measure the inference time
            // ********************************************************
            final long startTime = SystemClock.uptimeMillis();
            final List<Detector.Recognition> results =
                    detector.recognizeImage(rgb_bitmap_analyze_crop);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
            // ********************************************************

            // calculate and post boxplot and stats async
            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            // add inference time to dataset
                            boxPlotValues.add((int) lastProcessingTimeMs);
                            // set the inference in the stat panel
                            textViewInference.setText(String.format("%d ms", (int) lastProcessingTimeMs));

                            if (boxPlotValues.size() >= prefBoxplotValueSize) {
                                // when dataset is big enough, LIFO
                                boxPlot.setBoxplotData((ArrayList<Integer>) boxPlotValues.clone());
                                // remove first element
                                boxPlotValues.remove(0);
                            }

                            // increase inference counter
                            inferencePSInterval[0]++;
                            // recalculate inference FPS every second
                            currentInferenceTimestamp = System.currentTimeMillis();
                            if (currentInferenceTimestamp - lastInferenceTimestamp[0] >= 1000) {
                                //update Boxplot
                                plotOverlay.postInvalidate();
                                float fps = 1000.0f * (float) (inferencePSInterval[0]) / (float) (currentInferenceTimestamp - lastInferenceTimestamp[0]);
                                // set inference textView in UI
                                textViewInferencePS.setText(String.format("%.2f", fps));
                                //Log.d(TAG, "Detection Pipeline Performance: " + fps + " FPS");
                                // reset
                                inferencePSInterval[0] = 0;
                                lastInferenceTimestamp[0] = currentInferenceTimestamp;
                            }
                        }
                    });

            //final Canvas canvas = new Canvas(rgb_bitmap_analyze_crop);
            float minimumConfidence;
            switch (MODE) {
                case TF_OD_API:
                    minimumConfidence = prefDetectionConfidenceThreshold;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + MODE);
            }

            final List<Detector.Recognition> mappedRecognitions = new ArrayList<>();

            for (final Detector.Recognition result : results) {
                final RectF location = result.getLocation();
                if (location != null && result.getConfidence() >= minimumConfidence) {
                    //canvas.drawRect(location, cropPreviewBoxes);
                    cropToScreenFrame.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                }
            }

            tracker.trackResults(mappedRecognitions, startTime);
            trackingOverlay.postInvalidate();
            //Log.d(TAG+"_time", "Inference time: " + lastProcessingTimeMs + "ms");
            //close to continue to next frame and recycle variables
            image.close();
        }
    };

    /**
     * take a photo via the camerax capture image functionality
     */
    private final View.OnClickListener captureListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            // vibrate on button push
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            }

            // create file for saving the captured image
            File file = new File(getOutputDirectory(),
                    "Image_" + System.currentTimeMillis() + "_" + remoteConfChosenModelLabel + ".jpg");

            // create output files options
            ImageCapture.OutputFileOptions outputFileOptions =
                    new ImageCapture.OutputFileOptions.Builder(file).build();

            // capture the picture
            imageCapture.takePicture(outputFileOptions, executorAnalyze, new ImageCapture.OnImageSavedCallback() {
                @SuppressWarnings("SuspiciousNameCombination")
                @Override
                public void onImageSaved(@NonNull @NotNull ImageCapture.OutputFileResults outputFileResults) {
                    // vibrate when image saved
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
                    }

                    // get the uri of the saved image
                    Uri savedUri = outputFileResults.getSavedUri();
                    if (savedUri == null) {
                        savedUri = Uri.fromFile(file);
                    }

                    // create Bitmap from the saved image in full resolution
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inMutable = true;
                    Bitmap rgb_bitmap_capture = BitmapFactory.decodeFile(savedUri.getPath(), opt);

                    // get image info
                    int imageWidth = rgb_bitmap_capture.getWidth();
                    int imageHeight = rgb_bitmap_capture.getHeight();
                    int imageRotDegree = ImageUtils.getExifRotation(savedUri);

                    // cuts out the visible frame size out of the whole captured picture
                    Bitmap rgb_bitmap_capture_result;
                    rgb_bitmap_capture_result = ImageUtils.ConvertPreviewBitmapToModelInput(
                            rgb_bitmap_capture,
                            imageHeight,
                            imageWidth,
                            imageRotDegree,
                            remoteConfModelInputSize,
                            false,
                            false
                    );

                    // crop original camera image with respect of the largest crop with the given aspect ratio
                    // creates a frame out of the analyzer which will be analyzed
                    Bitmap rgb_bitmap_capture_crop;
                    rgb_bitmap_capture_crop = ImageUtils.ConvertPreviewBitmapToModelInput(
                            rgb_bitmap_capture,
                            imageHeight,
                            imageWidth,
                            imageRotDegree,
                            remoteConfModelInputSize,
                            true,
                            prefCropModeContain
                    );

                    // save crop image for debugging
                    /*File file_crop = new File(getOutputDirectory(),
                            "Image_" + System.currentTimeMillis() + "_"+ remoteConfChosenModelLabel + "_crop.jpg");
                    if (file_crop.exists())
                        file_crop.delete();
                    try {
                        FileOutputStream out = new FileOutputStream(file_crop);
                        rgb_bitmap_capture_crop.compress(Bitmap.CompressFormat.JPEG, 100, out);
                        out.flush();
                        out.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/

                    // configure a transformation matrix for mapping the crop image back to the visible frame
                    Matrix cropCaptureToScreenFrame = new Matrix();
                    Matrix screenFrameToCropCapture = ImageUtils.getTransformationMatrix(
                            rgb_bitmap_capture_result.getWidth(),
                            rgb_bitmap_capture_result.getHeight(),
                            remoteConfModelInputSize,
                            remoteConfModelInputSize,
                            0,
                            prefCropModeContain);
                    screenFrameToCropCapture.invert(cropCaptureToScreenFrame);

                    // measure the inference time
                    // ********************************************************
                    final long startTime = SystemClock.uptimeMillis();
                    final List<Detector.Recognition> results =
                            detector.recognizeImage(rgb_bitmap_capture_crop);
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    // ********************************************************

                    // creates a canvas on the cropped image
                    final Canvas canvas = new Canvas(rgb_bitmap_capture_crop);

                    // set the minimum confidence according to the chosen mode
                    float minimumConfidence;
                    switch (MODE) {
                        case TF_OD_API:
                            minimumConfidence = prefDetectionConfidenceThreshold;
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + MODE);
                    }

                    // declares a list for the recognitions
                    final List<Detector.Recognition> mappedRecognitions = new ArrayList<>();

                    // computes all results from the inference
                    for (final Detector.Recognition result : results) {
                        final RectF location = result.getLocation();
                        if (location != null && result.getConfidence() >= minimumConfidence) {
                            // draws the recognized object onto cropped bitmap
                            canvas.drawRect(location, cropPreviewBoxes);
                            // maps the recognized object onto frame
                            // and add it to the mapped recognitions
                            cropCaptureToScreenFrame.mapRect(location);
                            result.setLocation(location);
                            mappedRecognitions.add(result);
                        }
                    }
                    // creates a canvas onto the result frame
                    Canvas captureCanvas = new Canvas();
                    captureCanvas.setBitmap(rgb_bitmap_capture_result);

                    // configuration of a new tracker
                    Tracker trackerCapture = new Tracker(getApplicationContext(), remoteConfLabelMapList.size());
                    trackerCapture.setCropBox(prefCropModeContain);
                    trackerCapture.setTrackingVisible(true);
                    trackerCapture.setFrameConfiguration(
                            rgb_bitmap_capture_result.getWidth(),
                            rgb_bitmap_capture_result.getHeight(),
                            (rgb_bitmap_capture_result.getHeight() - Math.min(rgb_bitmap_capture_result.getWidth(), rgb_bitmap_capture_result.getHeight())) / 2,
                            (rgb_bitmap_capture_result.getHeight() - Math.min(rgb_bitmap_capture_result.getWidth(), rgb_bitmap_capture_result.getHeight())) / 2 + Math.min(rgb_bitmap_capture_result.getWidth(), rgb_bitmap_capture_result.getHeight()),
                            prefShowConfidence,
                            prefBoundingBoxColorMode);
                    // draw the recognized objects onto the new canvas
                    trackerCapture.trackResults(mappedRecognitions, startTime);
                    trackerCapture.draw(captureCanvas);

                    //Log.d(TAG+"_time", "Inference time: " + lastProcessingTimeMs + "ms");
                    //close to continue to next frame and recycle variables

                    // save the visible frame with the tracking layer to memory
                    File file_fill = new File(getOutputDirectory(),
                            "Image_" + System.currentTimeMillis() + "_" + remoteConfChosenModelLabel + "_detected.jpg");
                    if (file_fill.exists())
                        file_fill.delete();
                    try {
                        FileOutputStream out = new FileOutputStream(file_fill);
                        rgb_bitmap_capture_result.compress(Bitmap.CompressFormat.JPEG, 100, out);

                        out.flush();
                        out.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // register all created images from the app memory into the android media gallery
                    String mimeType = MimeTypeMap.getFileExtensionFromUrl(savedUri.toString());
                    MediaScannerConnection.scanFile(getApplicationContext(),
                            new String[]{savedUri.getPath(), file_fill.getPath()},
                            new String[]{mimeType}, new MediaScannerConnection.OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(String path, Uri uri) {
                                    // TODO Toast
                                }
                            });

                    // Open the generated Image in the android image viewer
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(file_fill.getPath()), "image/*");
                    startActivity(intent);
                }

                @Override
                public void onError(@NonNull @NotNull ImageCaptureException exception) {

                }
            });

        }
    };

    /**
     * get the output directory of the external media storage of this app
     *
     * @return path to the app external media storage
     */
    private File getOutputDirectory() {
        Context appContext = getApplicationContext();
        File mediaDir = Arrays.stream(appContext.getExternalMediaDirs()).findFirst().get();
        if (mediaDir != null) {
            mediaDir = new File(mediaDir, "EML Object Detection");
            mediaDir.mkdirs();
        }
        if (mediaDir != null && mediaDir.exists()) {
            return mediaDir;
        } else {
            return appContext.getFilesDir();
        }
    }

    /**
     * check if all needed permissions are granted
     *
     * @param permissions string array of the needed permissions
     * @return True if all permissions are granted
     */
    private boolean allPermissionsGranted(final String[] permissions) {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * check the results of the permission request
     * if all requested permissions are given call startCamera, else exit app
     */
    @Override
    public void onRequestPermissionsResult(
            final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // check if PermissionsResult is the requested one
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(permissions)) {
                // when permissions now granted start camera
                startCamera();
            } else {
                // when not granted close app and display message
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        // fetch preferences
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // set the crop box in the tracker according the preferences
        prefCropModeContain = pref.getString("crop_mode",
                getString(R.string.pref_cropMode_cover_value))
                .equals(getString(R.string.pref_cropMode_contain_value));
        tracker.setCropBox(prefCropModeContain);

        prefDetectionConfidenceThreshold = pref.getInt("confidence_threshold", 50) / 100.0f;
        prefBoxplotValueSize = pref.getInt("boxplot_number_samples", 50);
        prefBoundingBoxColorMode = pref.getString("boundingBoxColorMode", "classes");
        prefShowConfidence = pref.getBoolean("show_confidence", false);

        // set visibility of the capture button, bottomsheet and tracker
        prefOneshotMode = pref.getBoolean("oneshot_enable", false);
        tracker.setTrackingVisible(!prefOneshotMode);
        if (prefOneshotMode) {
            captureButton.setVisibility(View.VISIBLE);
            findViewById(R.id.bottom_sheet_include).setVisibility(View.INVISIBLE);
            captureButton.setOnClickListener(captureListener);
        } else {
            captureButton.setVisibility(View.INVISIBLE);
            findViewById(R.id.bottom_sheet_include).setVisibility(View.VISIBLE);
            captureButton.setOnClickListener(null);
        }

        // set boxplot label
        textViewBoxplotDesc.setText(String.format("Inference Boxplot (n = %d)", prefBoxplotValueSize));

        // set nnapi usage
        detector.setUseNNAPI(pref.getBoolean("nnapi_enable", true));
        // det number of used threads for the detection
        detector.setNumThreads(pref.getInt("number_threads", 4));

        // create camera again after destroying it on pause
        if (allPermissionsGranted(REQUIRED_PERMISSIONS)) {
            startCamera();
        }
    }

    @Override
    public synchronized void onPause() {
        // unbind from camera destroys analyze que in executor
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        super.onPause();
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
    }

}