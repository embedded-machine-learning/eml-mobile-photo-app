/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.
Modifications Copyright 2021 CDL EML, TU Wien, Austria

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

package at.tuwien.ict.eml.odd.tracking;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;

import at.tuwien.ict.eml.odd.env.ImageUtils;
import at.tuwien.ict.eml.odd.env.BorderedText;
import at.tuwien.ict.eml.odd.detection.Detector.Recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class Tracker {
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static final ArrayList<Integer> colors = new ArrayList<>();
  final List<Pair<Float, RectF>> screenRects = new LinkedList<>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<>();
  private final Paint boxPaint = new Paint();
  private final Paint framePaint = new Paint();
  private float textSizePx;
  private BorderedText borderedText;
  private int frameWidth;
  private int frameHeight;
  private int cropBorderTop;
  private int cropBorderBottom;
  private boolean showConfidence;
  private boolean showCropFrame;
  private String boundingBoxColorMode;
  private boolean isVisible;

  public Tracker(final Context context, int classes) {
    isVisible = true;

    // generate random colors
    float[] hsv_value = new float[3];
    hsv_value[1] = 1.0f;
    hsv_value[2] = 0.7f;
    for (int i=0; i<classes; i++){
      hsv_value[0] = (360.0f/(classes-1)*i);
      colors.add(Color.HSVToColor(hsv_value));
    }
    Collections.shuffle(colors);

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(12.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    framePaint.setColor(Color.parseColor("#000000"));
    framePaint.setAlpha(180);
    framePaint.setStyle(Style.FILL);
  }

  public synchronized void setTrackingVisible(boolean visible){
    if(!visible){
      trackedObjects.clear();
    }
    this.isVisible = visible;
  }

  public synchronized void setCropBox(boolean cropBox) {
    this.showCropFrame = cropBox;
  }

  public synchronized void setFrameConfiguration(
      final int width,
      final int height,
      final int cropBorderTop,
      final int cropBorderBottom,
      boolean showConfidence,
      String boundingBoxColorMode) {
    this.frameWidth = width;
    this.frameHeight = height;
    this.cropBorderTop = cropBorderTop;
    this.cropBorderBottom = cropBorderBottom;
    this.showConfidence = showConfidence;
    this.boundingBoxColorMode = boundingBoxColorMode;
    this.boxPaint.setStrokeWidth(frameWidth/70.0f);
    textSizePx = frameWidth/25.0f;
    this.borderedText = new BorderedText(textSizePx);
  }

  public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
    //logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  /**
   * Draw all tracked objects on the canvas
   * @param canvas The canvas to draw on
   */
  @SuppressWarnings("SuspiciousNameCombination")
  public synchronized void draw(final Canvas canvas) {
    // if crop mode true draw the 1x1 crop frame box on the preview canvas
    if(showCropFrame){
      RectF cropRect, cropRect_top, cropRect_bottom;
      if(frameWidth < frameHeight){
        cropRect_top = new RectF(
                0,
                0,
                frameWidth,
                cropBorderTop);
        cropRect_bottom = new RectF(
                0,
                cropBorderBottom,
                frameWidth,
                frameHeight);
      } else {
        cropRect_top = new RectF(
                0,
                0,
                cropBorderTop,
                frameHeight);
        cropRect_bottom = new RectF(
                cropBorderBottom,
                0,
                frameWidth,
                frameHeight);
      }

      canvas.drawRect(cropRect_top, framePaint);
      canvas.drawRect(cropRect_bottom, framePaint);
    }

    if(!isVisible) {
      return;
    }
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      boxPaint.setColor(recognition.color);
      boxPaint.setAlpha(200);

      //float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 15.0f;
      //canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
      canvas.drawRect(trackedPos, boxPaint);

      String labelString = !TextUtils.isEmpty(recognition.title) ?
              String.format("%s", recognition.title) : "";
      if (showConfidence) {
        labelString = String.format("%s%s", labelString, String.format(Locale.ENGLISH," %.1f%%", (100 * recognition.detectionConfidence)));
      }
      borderedText.drawText(
              canvas, trackedPos.left, trackedPos.top, labelString, boxPaint);
    }
  }

  /**
   * Process the list of Recognitions for further processing.
   * @param results List of given Recognitions
   */
  private void processResults(final List<Recognition> results) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<>();

    screenRects.clear();

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }

      if (result.getLocation().width() < MIN_SIZE || result.getLocation().height() < MIN_SIZE) {
        continue;
      }

      rectsToTrack.add(new Pair<>(result.getConfidence(), result));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      return;
    }

    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();

      // link the recognized objects to colors
      if(boundingBoxColorMode.equals("confidence")){
        int colorIndex = (int)(trackedRecognition.detectionConfidence*20); //outputs 0 to 20
        trackedRecognition.color = Color.parseColor("#" + String.format("%02X", Math.max(0, 255 - colorIndex * 12)) + String.format("%02X", Math.min(255, colorIndex * 12)) + "00");
      } else {
        trackedRecognition.color = colors.get(potential.second.getClassId());
      }
      trackedObjects.add(trackedRecognition);
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
}
