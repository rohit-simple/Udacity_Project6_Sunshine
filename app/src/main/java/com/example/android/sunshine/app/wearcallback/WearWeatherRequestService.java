package com.example.android.sunshine.app.wearcallback;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.WearableListenerService;

public class WearWeatherRequestService extends WearableListenerService {
    private WearGoogleClientHandler mWearGoogleClientHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mWearGoogleClientHandler = WearGoogleClientHandler.getInstance();
        mWearGoogleClientHandler.start(getApplicationContext());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWearGoogleClientHandler.tearDown();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);

        final String requestWeatherSyncPath = "/request_weather_sync";

        for(DataEvent dataEvent: dataEvents){
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                if(requestWeatherSyncPath.equals(dataEvent.getDataItem().getUri().getPath())){
                    Log.e("rohit_app", "weather has been requested from wearable");

                    PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather_update");
                    WearUtility.prepareWeatherDataForWear(this, putDataMapRequest);
                    if(putDataMapRequest.getDataMap().containsKey(WearUtility.KEY_WEATHER_ID) &&
                            putDataMapRequest.getDataMap().containsKey(WearUtility.KEY_HIGH_TEMPERATURE) &&
                            putDataMapRequest.getDataMap().containsKey(WearUtility.KEY_LOW_TEMPERATURE) &&
                            putDataMapRequest.getDataMap().containsKey(WearUtility.KEY_TIMESTAMP)){
                        mWearGoogleClientHandler.sendData(putDataMapRequest);
                    }
                }
            }
        }
    }
}