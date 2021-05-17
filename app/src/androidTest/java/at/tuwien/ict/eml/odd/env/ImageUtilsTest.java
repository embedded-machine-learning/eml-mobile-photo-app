package at.tuwien.ict.eml.odd.env;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.junit.Test;

import static org.junit.Assert.*;

public class ImageUtilsTest {
    private final int TF_MODEL_INPUT_SIZE = 300;


    @Test
    public void getContainTransformationMatrix() {
        RectF rect = new RectF(0,0,300,300);
        RectF frameRect = new RectF(0,0,320,640);

        Matrix frameToCrop;
        Matrix cropToFrame = new Matrix();

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);

        Bitmap bitmap = Bitmap.createBitmap(TF_MODEL_INPUT_SIZE, TF_MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canv = new Canvas(bitmap);
        canv.drawRect(rect, paint);

        frameToCrop = ImageUtils.getTransformationMatrix(
                320, 640,
                TF_MODEL_INPUT_SIZE,
                TF_MODEL_INPUT_SIZE,
                0,
                true);
        frameToCrop.invert(cropToFrame);

        Bitmap frame = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888);
        Canvas canvFrame = new Canvas(frame);
        cropToFrame.mapRect(rect);
        canvFrame.drawRect(rect, paint);

    }
}