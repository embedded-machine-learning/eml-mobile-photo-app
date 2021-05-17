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

package at.tuwien.ict.eml.odd.customView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

/**
 * This custom view class provides a interface to register callbacks for drawing on its canvas
 */
public class OverlayView extends View {
    private final List<DrawCallback> callbacks = new LinkedList<>();

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * method to register a draw callback
     * @param callback A callback which will be called on postInvalidate
     */
    public void addCallback(final DrawCallback callback) {
        callbacks.add(callback);
    }

    /**
     * When postInvalidate gets called onto this view, all draw callbacks get called
     * @param canvas Canvas onto draw
     */
    @SuppressLint("MissingSuperCall")
    @Override
    public synchronized void draw(final Canvas canvas) {
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }
    }

    /**
     * Interface defining the callback for client classes.
     */
    public interface DrawCallback {
        void drawCallback(final Canvas canvas);
    }
}