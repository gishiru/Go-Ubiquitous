package com.sunshinewear.sync;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.util.Log;
import android.view.SurfaceHolder;

public class SunshineWatchFaceService extends CanvasWatchFaceService {
  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    private final String TAG = Engine.class.getSimpleName();

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      Log.d(TAG, "On created");
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);

      Log.d(TAG, "Properties: " + properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false));
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();

      Log.d(TAG, "Ambient: " + isInAmbientMode());
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);

      Log.d(TAG, "Ambient mode: " + inAmbientMode);
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      super.onDraw(canvas, bounds);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      Log.d(TAG, "Visibility: " + visible);
    }
  }
}
