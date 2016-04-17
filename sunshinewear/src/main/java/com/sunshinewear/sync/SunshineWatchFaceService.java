package com.sunshinewear.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.sunshinewear.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class SunshineWatchFaceService extends CanvasWatchFaceService {
  private static final Typeface TYPEFACE_NORMAL =
      Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    private final String TAG = Engine.class.getSimpleName();

    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;
    private static final int MSG_UPDATE_TIME = 0;

    private Paint mBackgroundPaint = null;
    private Calendar mCalendar = null;
    private Paint mColonPaint = null;
    private float mColonWidth = 0f;
    private Paint mDate = null;
    private Paint mHourPaint = null;
    private boolean mLowBitAmbient = false;
    private Paint mMinutePaint = null;
    private boolean mRegisteredTimeZoneReceiver = false;
    final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };
    final Handler mUpdateTimeHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            invalidate();
            if (shouldTimerBeRunning()) {
              mUpdateTimeHandler.sendEmptyMessageDelayed(
                      MSG_UPDATE_TIME,
                      INTERACTIVE_UPDATE_RATE_MS -
                          (System.currentTimeMillis() % INTERACTIVE_UPDATE_RATE_MS));
            }
            break;
          default:
            break;
        }
      }
    };
    private float mXDistanceOffset = 0f;
    private float mXOffset = 0f;
    private float mYOffset = 0f;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      Log.d(TAG, "On created");

      // Initialize.
      mBackgroundPaint = new Paint();
      mCalendar = Calendar.getInstance();

      // Set dimensions.
      mYOffset = getResources().getDimension(R.dimen.y_offset);

      // Set paints.
      mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_time));
      mColonPaint = createTextPaint(Color.WHITE, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
      mDate = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.digital_date));
      mHourPaint = createTextPaint(Color.WHITE, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
      mMinutePaint = createTextPaint(Color.WHITE);
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);

      Log.d(TAG, "Properties: " + properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false));

      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();

      Log.d(TAG, "Ambient: " + isInAmbientMode());

      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);

      Log.d(TAG, "Ambient mode: " + inAmbientMode);

      if (mLowBitAmbient) {
        boolean antiAlias = !inAmbientMode;
        mColonPaint.setAntiAlias(antiAlias);
        mHourPaint.setAntiAlias(antiAlias);
        mMinutePaint.setAntiAlias(antiAlias);
      }
      invalidate();
      updateTimer();
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));

      // Set dimensions.
      mXDistanceOffset =
          getResources().getDimension(
              insets.isRound() ?
                  R.dimen.distance_x_offset_round :
                  R.dimen.distance_x_offset);
      mXOffset = getResources().getDimension(R.dimen.x_offset);
      float textSize = getResources().getDimension(insets.isRound()
          ? R.dimen.text_size_round : R.dimen.text_size);
      mColonPaint.setTextSize(textSize);
      mColonWidth = mColonPaint.measureText(":");
      mDate.setTextSize(textSize / 2);
      mHourPaint.setTextSize(textSize);
      mMinutePaint.setTextSize(textSize);
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      if (isInAmbientMode()) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
      }

      // Draw hours.
      String hourString;
      boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFaceService.this);
      long now = System.currentTimeMillis();
      mCalendar.setTimeInMillis(now);
      float x = mXOffset;
      if (is24Hour) {
        hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
      } else {
        int hour = mCalendar.get(Calendar.HOUR);
        if (hour == 0) {
          hour = 12;
        }
        hourString = String.valueOf(hour);
      }
      canvas.drawText(hourString, x, mYOffset, mHourPaint);
      x += mHourPaint.measureText(hourString);

      // Draw colon.
      canvas.drawText(":", x, mYOffset, mColonPaint);
      x += mColonWidth;

      // Draw minutes.
      String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
      canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
      x += mMinutePaint.measureText(minuteString);

      if (getPeekCardPosition().isEmpty()) {
        int color = ContextCompat.getColor(getApplicationContext(), R.color.digital_date);
        mDate.setColor(isInAmbientMode() ? Color.WHITE : color);
        canvas.drawText(
            new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault()).format(now).toUpperCase(),
            mXDistanceOffset,
            mYOffset + getResources().getDimension(R.dimen.line_height),
            mDate);
      }
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      super.onSurfaceChanged(holder, format, width, height);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      Log.d(TAG, "Visibility: " + visible);

      if (visible) {
        registerReceiver();
        mCalendar.setTimeZone(TimeZone.getDefault());
      } else {
        unregisterReceiver();
      }

      updateTimer();
    }

    private Paint createTextPaint(int defaultInteractiveColor) {
      return createTextPaint(defaultInteractiveColor, TYPEFACE_NORMAL);
    }

    private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
      Paint paint = new Paint();
      paint.setColor(defaultInteractiveColor);
      paint.setTypeface(typeface);
      paint.setAntiAlias(true);
      return paint;
    }

    private String formatTwoDigitNumber(int hour) {
      return String.format("%02d", hour);
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
    }

    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }
  }
}
