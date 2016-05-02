package com.example.android.sunshine.app.sync;

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
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.example.android.sunshine.app.R;

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

  private class Engine extends CanvasWatchFaceService.Engine implements
      GoogleApiClient.ConnectionCallbacks,
      GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
    private final String TAG = Engine.class.getSimpleName();

    // Keys.
    private static final String KEY_HIGH = "high";
    private static final String KEY_LOW = "low";
    private static final String KEY_TIMESTAMP = "request_key";
    private static final String KEY_WEATHER_ID = "weather_id";
    private static final int MSG_UPDATE_TIME = 0;
    private static final String WEATHER_INFO = "/weather-info";
    private static final String WEATHER_INFO_REQUEST = "/weather-info-request";

    // Numeric.
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;
    private float mColonWidth = 0f;
    private float mXDistanceOffset = 0f;
    private float mXOffset = 0f;
    private float mYOffset = 0f;

    // Objects.
    private Calendar mCalendar = null;
    private GoogleApiClient mGoogleApiClient = null;
    private String mHigh = null;
    private String mLow = null;
    private boolean mLowBitAmbient = false;
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

    // Views.
    private Paint mBackgroundPaint = null;
    private Paint mColonPaint = null;
    private Paint mDatePaint = null;
    private Paint mHighPaint = null;
    private Paint mHourPaint = null;
    private Paint mLowPaint = null;
    private Paint mMinutePaint = null;

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
      mDatePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.digital_date));
      mHighPaint = createTextPaint(Color.YELLOW, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
      mHourPaint = createTextPaint(Color.WHITE, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
      mLowPaint = createTextPaint(Color.BLUE);
      mMinutePaint = createTextPaint(Color.WHITE);

      // Build Google API Client.
      mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
          .addConnectionCallbacks(this)
          .addOnConnectionFailedListener(this)
          .addApi(Wearable.API)
          .build();
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
        mHighPaint.setAntiAlias(antiAlias);
        mHourPaint.setAntiAlias(antiAlias);
        mLowPaint.setAntiAlias(antiAlias);
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
      mDatePaint.setTextSize(textSize / 2);
      mHighPaint.setTextSize(textSize * 2 / 3);
      mLowPaint.setTextSize(textSize * 2 / 3);
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

      if (getPeekCardPosition().isEmpty()) {
        // Draw date.
        int color = ContextCompat.getColor(getApplicationContext(), R.color.digital_date);
        mDatePaint.setColor(isInAmbientMode() ? Color.WHITE : color);
        String dateString = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.getDefault())
            .format(now)
            .toUpperCase();
        canvas
            .drawText(
                dateString,
                mXDistanceOffset,
                mYOffset + getResources().getDimension(R.dimen.line_height),
                mDatePaint);

        // Draw temperature high/low.
        if ((mHigh != null) && (mLow != null)) {
          canvas
              .drawText(
                  mHigh,
                  mXDistanceOffset,
                  mDatePaint.measureText(dateString),
                  mHighPaint);
          canvas
              .drawText(
                  mLow,
                  mXDistanceOffset + mHighPaint.measureText(mHigh) + mColonWidth / 2,
                  mDatePaint.measureText(dateString),
                  mLowPaint);
        }
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
        if ((mGoogleApiClient != null) && !mGoogleApiClient.isConnected()) {
          mGoogleApiClient.connect();
        }
        registerReceiver();
        mCalendar.setTimeZone(TimeZone.getDefault());
      } else {
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
          Wearable.DataApi.removeListener(mGoogleApiClient, this);
          mGoogleApiClient.disconnect();
        }
        unregisterReceiver();
      }

      updateTimer();
    }

    @Override
    public void onConnected(Bundle bundle) {
      Log.d(TAG, "Connection is succeeded");

      Wearable.DataApi.addListener(mGoogleApiClient, this);

      // Set data map.
      PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_INFO_REQUEST);
      DataMap map = putDataMapRequest.getDataMap();
      map.putString(KEY_TIMESTAMP, Long.toString(System.currentTimeMillis()));

      // Send data to handheld.
      PutDataRequest request = putDataMapRequest.asPutDataRequest();
      Wearable.DataApi
          .putDataItem(mGoogleApiClient, request)
          .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
              if (dataItemResult.getStatus().isSuccess()) {
                Log.d(TAG, "Putting data is succeeded");
              } else {
                Log.i(TAG, "Putting data is not succeeded");
              }
            }
          });
    }

    @Override
    public void onConnectionSuspended(int i) {
      Log.i(TAG, "Connection is suspended with: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
      Log.i(TAG, "Connection is failed by: " + connectionResult.toString());
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
      Log.d(TAG, "Data is changed");

      for (DataEvent dataEvent : dataEventBuffer) {
        if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
          DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
          String path = dataEvent.getDataItem().getUri().getPath();

          Log.i(TAG, "Path: " + path);

          if (path.equals(WEATHER_INFO)) {
            mHigh = dataMap.getString(KEY_HIGH).trim();
            mLow = dataMap.getString(KEY_LOW).trim();

            Log.i(TAG, "High: " + mHigh);
            Log.i(TAG, "Low: " + mLow);
            Log.i(TAG, "Weather ID: " + dataMap.getInt(KEY_WEATHER_ID));
          }
        }
      }
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
