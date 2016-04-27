package com.example.android.sunshine.app.wear;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearableListenerService extends WearableListenerService {
  private static final String TAG = SunshineWearableListenerService.class.getSimpleName();

  // Wear.
  private static final String WEATHER_INFO_REQUEST = "/weather-info-request";

  @Override
  public void onDataChanged(DataEventBuffer dataEvents) {
    Log.d(TAG, "Data is changed");

    for (DataEvent dataEvent : dataEvents) {
      if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
        String path = dataEvent.getDataItem().getUri().getPath();

        Log.i(TAG, "Path: " + path);

        if (path.equals(WEATHER_INFO_REQUEST)) {
          SunshineSyncAdapter.syncImmediately(this);
        }
      }
    }
  }
}
