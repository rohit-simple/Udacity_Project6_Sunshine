package com.example.android.sunshine.app.wearcallback;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;

import java.util.Date;

public class WearUtility {
    public static final String KEY_WEATHER_ID = "weather_id";
    public static final String KEY_HIGH_TEMPERATURE = "high_temp";
    public static final String KEY_LOW_TEMPERATURE = "low_temp";
    public static final String KEY_TIMESTAMP = "timestamp";

    public static void prepareWeatherDataForWear(Context context, PutDataMapRequest putDataMapRequest){
        final String[] WEAR_NOTIFY_WEATHER_PROJECTION = new String[] {
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
        };
        final int WEATHER_ID_POS = 0;
        final int WEATHER_ID_MAX_TEMP = 1;
        final int WEATHER_ID_MIN_TEMP = 2;

        String locationQuery = Utility.getPreferredLocation(context);
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
        Cursor cursor = context.getContentResolver().query(weatherUri, WEAR_NOTIFY_WEATHER_PROJECTION, null, null, null);

        if(cursor != null){
            if(cursor.moveToFirst()){
                DataMap dataMap = putDataMapRequest.getDataMap();
                dataMap.putInt(KEY_WEATHER_ID, cursor.getInt(WEATHER_ID_POS));
                dataMap.putString(KEY_HIGH_TEMPERATURE, Utility.formatTemperature(context, cursor.getDouble(WEATHER_ID_MAX_TEMP)));
                dataMap.putString(KEY_LOW_TEMPERATURE, Utility.formatTemperature(context, cursor.getDouble(WEATHER_ID_MIN_TEMP)));
                dataMap.putLong(KEY_TIMESTAMP, new Date().getTime());
            }
            cursor.close();
        }
    }
}
