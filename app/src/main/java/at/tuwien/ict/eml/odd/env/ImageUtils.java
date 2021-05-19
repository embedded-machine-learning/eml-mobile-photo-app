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

package at.tuwien.ict.eml.odd.env;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;

import android.net.Uri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class ImageUtils {

    /**
     * Cuts out the visible screen area of a bitmap which is defined through the aspect-dimensions.
     * and crops this (if scaleToDstSize is True) to the model input size.
     * @param src Source bitmap
     * @param aspectDstWidth Width of the visible area.
     * @param aspectDstHeight Height of the visible area.
     * @param applyRotationToSrc Rotation information of the source bitmap.
     * @param dstSize Model input size in pixel.
     * @param scaleToDstSize When True scale it to the dstSize, when false just cut out the visible screen area out of the bitmap.
     * @param cropModeContain When True, crop with a 1:1 aspect ratio, if not clinch the whole visible screen area to the dstSize.
     * @return The processed image.
     */
    public static Bitmap ConvertPreviewBitmapToModelInput(
            final Bitmap src,
            final int aspectDstWidth,
            final int aspectDstHeight,
            final int applyRotationToSrc,
            final int dstSize,
            final boolean scaleToDstSize,
            final boolean cropModeContain) {

        final Matrix matrix = new Matrix();

        ArrayList<Integer> resultList = getVisibleFrameSize(
                src.getWidth(),
                src.getHeight(),
                aspectDstWidth,
                aspectDstHeight,
                applyRotationToSrc
        );

        if (applyRotationToSrc != 0) {
            matrix.postRotate((float) applyRotationToSrc);
        }

        int minSquare = Math.min(resultList.get(0), resultList.get(1));
        int newWidth = cropModeContain ? minSquare : resultList.get(0);
        int newHeight = cropModeContain ? minSquare : resultList.get(1);

        final boolean transpose = (Math.abs(applyRotationToSrc) + 90) % 180 == 0;
        float srcWidth = (transpose) ? src.getHeight() : src.getWidth();
        float srcHeight = (transpose) ? src.getWidth() : src.getHeight();

        int newX = (int) (Math.abs(newWidth - srcWidth) / 2);
        int newY = (int) (Math.abs(newHeight - srcHeight) / 2);

        if (scaleToDstSize) {
            float scaleFactorX = (float) (dstSize) / (float) (newWidth);
            float scaleFactorY = (float) (dstSize) / (float) (newHeight);
            matrix.postScale(scaleFactorX, scaleFactorY);
        }

        return Bitmap.createBitmap(
                src,
                transpose ? newY : newX,
                transpose ? newX : newY,
                transpose ? newHeight : newWidth,
                transpose ? newWidth : newHeight,
                matrix, true
        );
    }

    /**
     * Calculates the part of the visible screen area of an image with size relative to the source image.
     * @param srcWidth Width of the source image.
     * @param srcHeight Height of the source image.
     * @param aspectDstWidth Width of the screen.
     * @param aspectDstHeight Width of the screen.
     * @param applyRotationToSrc Rotation information of the src Bitmap.
     * @return ArrayList with the calculated visible image part.
     */
    public static ArrayList<Integer> getVisibleFrameSize(
            final int srcWidth,
            final int srcHeight,
            final int aspectDstWidth,
            final int aspectDstHeight,
            final int applyRotationToSrc) {

        final boolean transpose = (Math.abs(applyRotationToSrc) + 90) % 180 == 0;
        float tmpSrcWidth = (transpose) ? srcHeight : srcWidth;
        float tmpSrcHeight = (transpose) ? srcWidth : srcHeight;

        int newWidth, newHeight;
        float scaleWidth = (float) aspectDstWidth / tmpSrcWidth;
        float scaleHeight = (float) aspectDstHeight / tmpSrcHeight;

        if (scaleWidth >= scaleHeight) {
            newHeight = (int) (tmpSrcWidth * (float) aspectDstHeight / (float) aspectDstWidth);
            newWidth = (int) tmpSrcWidth;
        } else {
            newWidth = (int) (tmpSrcHeight * (float) aspectDstWidth / (float) aspectDstHeight);
            newHeight = (int) tmpSrcHeight;
        }

        ArrayList<Integer> resultList = new ArrayList<>();
        resultList.add(newWidth);
        resultList.add(newHeight);
        return resultList;
    }

    /**
     * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
     * Modifications Copyright 2021 CDL EML, TU Wien, Austria
     *
     * Calculates the transformation matrix to transform a image with [srcWidth x srcHeight] and
     * the rotation applyRotation to [dstWidth x dstHeight] und the optionally the maintenance
     * of a 1:1 aspect ratio
     *
     * @param srcWidth Width of the source image.
     * @param srcHeight Height of the source image.
     * @param dstWidth Width of the destination image.
     * @param dstHeight Height of the destination image.
     * @param applyRotation Rotation information of the src Bitmap.
     * @param containDstAspect When True, crop with a 1:1 aspect ratio, if not clinch the image
     * @return The transformation Matrix
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean containDstAspect) {
        final Matrix matrix = new Matrix();

        matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);
        if (applyRotation != 0) {
            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (containDstAspect) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }
        matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        return matrix;
    }

    /**
     * Reads the rotation information of the image saved in the exif info.
     *
     * @param uri The uri of the image
     * @return The rotation of the image in degree
     */
    public static int getExifRotation(final Uri uri) {
        ExifInterface exifInterface = null;
        int rotationDegrees;
        try {
            exifInterface = new ExifInterface(uri.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        rotationDegrees = 0;
        switch (Objects.requireNonNull(exifInterface).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotationDegrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotationDegrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotationDegrees = 270;
                break;
        }
        return rotationDegrees;
    }

}