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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Class for generating and drawing a boxplot onto a given canvas
 */
public class Boxplot{
    private Paint plotPaint;
    private Paint axisPaint;
    private Paint messagePaint;
    private Paint whiskerPaint;
    private Paint medianPaint;
    private Paint medianTextPaint;
    private Paint connPaint;

    private ArrayList<Integer> data;

    private float median;
    private float firstQuart;
    private float thirdQuart;
    private float minWhisker;
    private float maxWhisker;

    private float linewidth;

    /**
     * Boxplot constructor
     * @param rectColor     The color of the middle rectangle
     * @param whiskerColor  The color of the min and max whiskers
     * @param medianColor   The color of the median line
     * @param connColor     The color of the horizontal connection line
     * @param textSize      The Textsize of the boxplot markings
     * @param linewidth     The General linewidth of the boxplot
     */
    public Boxplot(final int rectColor, final int whiskerColor, final int medianColor, final int connColor, final float textSize, final float linewidth) {
        this.linewidth = linewidth;

        plotPaint = new Paint();
        plotPaint.setColor(rectColor);
        plotPaint.setStyle(Paint.Style.STROKE);
        plotPaint.setStrokeWidth(linewidth);

        connPaint = new Paint();
        connPaint.setColor(connColor);
        connPaint.setStyle(Paint.Style.STROKE);
        connPaint.setStrokeWidth(linewidth);
        connPaint.setPathEffect(new DashPathEffect(new float[]{25.0f, 25.0f},0.0f));

        axisPaint = new Paint();
        axisPaint.setColor(whiskerColor);
        axisPaint.setTextSize(textSize);

        whiskerPaint = new Paint(plotPaint);
        whiskerPaint.setColor(whiskerColor);
        whiskerPaint.setStrokeWidth(linewidth*2);

        messagePaint = new Paint();
        messagePaint.setColor(Color.WHITE);
        messagePaint.setTextSize(textSize);

        //generated
        medianPaint = new Paint(plotPaint);
        medianPaint.setColor(medianColor);
        medianPaint.setStrokeWidth(linewidth*2);

        medianTextPaint = new Paint(axisPaint);
        medianTextPaint.setColor(medianColor);

    }

    /**
     * Copy the data into the boxplot
     * @param data Data to be processed
     */
    public final void setBoxplotData(ArrayList<Integer> data){
        if(data != null){
            this.data = (ArrayList<Integer>) data.clone();
            this.processData();
        }
    }

    /**
     * Calculates all needed values for drawing the boxplot
     */
    private void processData(){
        data.sort(Comparator.naturalOrder());
        minWhisker = Collections.min(data);
        maxWhisker = Collections.max(data);
        median = median(data);
        firstQuart = quartile(1, data);
        thirdQuart = quartile(3, data);
    }

    /**
     * Draw the boxplot onto canvas
     * @param canvas The canvas to draw on
     */
    public final void drawBoxplot(Canvas canvas){
        final float canvasSizeX = canvas.getWidth();
        final float canvasSizeY = canvas.getHeight();

        // set the vertical ratio between the elements
        float plotHeight = (float) (canvasSizeY*0.5);
        final float whiskerPadding = 0.25f*plotHeight;
        final float axisBaseline = (float) (canvasSizeY*0.75);
        final float medianBaseline = canvasSizeY;

        // message until the first drawing
        if(data == null){
            canvas.drawText("Gathering data ...", canvasSizeX/2-axisPaint.measureText("Gathering data ...")/2, plotHeight/2, messagePaint);
            return;
        }

        final float canvas_xmin = linewidth/2;
        final float canvas_ymin = linewidth/2;
        final float canvas_max = canvasSizeX - linewidth/2;
        final float canvas_median = ((median-minWhisker)/Math.abs(maxWhisker-minWhisker))*canvasSizeX;
        final float canvas_firstQuart = ((firstQuart-minWhisker)/Math.abs(maxWhisker-minWhisker))*canvasSizeX;
        final float canvas_thirdQuart = ((thirdQuart-minWhisker)/Math.abs(maxWhisker-minWhisker))*canvasSizeX;

        final String whiskerMinText = (int)minWhisker + " ms";
        final String whiskerMaxText = (int)maxWhisker + " ms";
        final String medianText = (int)median + " ms";

        // conn
        canvas.drawLine(canvas_firstQuart,plotHeight/2, canvas_xmin, plotHeight/2, connPaint);
        canvas.drawLine(canvas_thirdQuart,plotHeight/2, canvasSizeX, plotHeight/2, connPaint);
        // rect
        canvas.drawRect(canvas_firstQuart, canvas_ymin,canvas_thirdQuart,plotHeight,plotPaint);
        // Whisker
        canvas.drawLine(canvas_xmin,whiskerPadding, canvas_xmin, plotHeight-whiskerPadding, whiskerPaint);
        canvas.drawLine(canvas_max,whiskerPadding, canvas_max, plotHeight-whiskerPadding, whiskerPaint);
        // median
        canvas.drawLine(canvas_median,canvas_ymin, canvas_median, plotHeight, medianPaint);
        // axis
        canvas.drawText(whiskerMinText, 0, axisBaseline, axisPaint);
        canvas.drawText(whiskerMaxText, canvasSizeX-axisPaint.measureText(whiskerMaxText), axisBaseline, axisPaint);
        canvas.drawText(medianText, canvas_median-axisPaint.measureText(medianText)/2, medianBaseline, medianTextPaint);
    }

    /** 
     * Calculates the median
     */
    private static Integer median(ArrayList<Integer> data){
        Integer median;
        // even
        if (data.size() % 2 == 0)
            median = data.get(data.size() / 2);
        else // odd
            median = data.get(data.size()/2);
        return median;
    }

    /**
     * Calculates the requested quartile
     * @param quartileOrder The requested quartile
     * @param data The data
     * @return The boundary of the requested quartile
     */
    private static float quartile(Integer quartileOrder, ArrayList<Integer> data){
        float index = (float) quartileOrder/4*(data.size()+1);
        if (index == (int)index){
            return data.get((int)index);
        }else{
            float test1 = (float)(data.get((int)index));
            float test2 = (float)(data.get((int)(index) + 1));
            return (int)((test1 + test2)/2);
        }
    }
}