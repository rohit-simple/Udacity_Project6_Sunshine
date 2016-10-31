/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SunshineDigitalWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineDigitalWatchFace.Engine> mWeakReference;

        EngineHandler(SunshineDigitalWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineDigitalWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        Paint mTimeTextPaint;
        Paint mDayTextPaint;
        Paint mMaxTempTextPaint;
        Paint mMinTempTextPaint;

        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };
        float mXOffset;
        float mTimeYOffset;
        float mDayYOffset;
        float mWeatherIconYOffset;
        float mWeatherTempOffset;
        float mTempLeftMargin;

        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;
        Date mDate;

        Bitmap mWeatherBitmap;

        private GoogleApiClient mGoogleApiClient;

        private String mHighTenpStr = "20\u00B0";
        private String mLowTempStr = "18\u00B0";

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineDigitalWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineDigitalWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.RIGHT)
                    .build());
            Resources resources = SunshineDigitalWatchFace.this.getResources();

            mTempLeftMargin = resources.getDimension(R.dimen.weather_temp_left_margin);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimeTextPaint = createTextPaint(resources.getColor(R.color.text_primary));
            mDayTextPaint = createTextPaint(resources.getColor(R.color.text_secondary));
            mMaxTempTextPaint = createTextPaint(resources.getColor(R.color.text_primary));
            mMinTempTextPaint = createTextPaint(resources.getColor(R.color.text_secondary));

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mWeatherBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clear);

            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            if(mGoogleApiClient != null && mGoogleApiClient.isConnected()){
                mGoogleApiClient.disconnect();
            }

            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineDigitalWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineDigitalWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineDigitalWatchFace.this.getResources();

            if(insets.isRound()){
                mXOffset = resources.getDimension(R.dimen.x_offset_round);

                mTimeTextPaint.setTextSize(resources.getDimension(R.dimen.text_time_size_round));
                mDayTextPaint.setTextSize(resources.getDimension(R.dimen.text_day_size_round));
                mMaxTempTextPaint.setTextSize(resources.getDimension(R.dimen.text_max_temp_size_round));
                mMinTempTextPaint.setTextSize(resources.getDimension(R.dimen.text_min_temp_size_round));

                mTimeYOffset = resources.getDimension(R.dimen.time_top_margin_round);
                mDayYOffset = resources.getDimension(R.dimen.day_top_margin_round);
                mWeatherIconYOffset = resources.getDimension(R.dimen.weather_icon_top_margin_round);
                mWeatherTempOffset = resources.getDimension(R.dimen.weather_temp_top_margin_round);
            }else{
                mXOffset = resources.getDimension(R.dimen.x_offset);

                mTimeTextPaint.setTextSize(resources.getDimension(R.dimen.text_time_size));
                mDayTextPaint.setTextSize(resources.getDimension(R.dimen.text_day_size));
                mMaxTempTextPaint.setTextSize(resources.getDimension(R.dimen.text_max_temp_size));
                mMinTempTextPaint.setTextSize(resources.getDimension(R.dimen.text_min_temp_size));

                mTimeYOffset = resources.getDimension(R.dimen.time_top_margin);
                mDayYOffset = resources.getDimension(R.dimen.day_top_margin);
                mWeatherIconYOffset = resources.getDimension(R.dimen.weather_icon_top_margin);
                mWeatherTempOffset = resources.getDimension(R.dimen.weather_temp_top_margin);
            }

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDayTextPaint.setAntiAlias(!inAmbientMode);
                    mMaxTempTextPaint.setAntiAlias(!inAmbientMode);
                    mMinTempTextPaint.setAntiAlias(!inAmbientMode);
                }
                Resources resources = getResources();
                if(mAmbient){
                    mTimeTextPaint.setColor(resources.getColor(R.color.white));
                    mDayTextPaint.setColor(resources.getColor(R.color.white));
                    mMaxTempTextPaint.setColor(resources.getColor(R.color.white));
                    mMinTempTextPaint.setColor(resources.getColor(R.color.white));
                }else{
                    mTimeTextPaint.setColor(resources.getColor(R.color.text_primary));
                    mDayTextPaint.setColor(resources.getColor(R.color.text_secondary));
                    mMaxTempTextPaint.setColor(resources.getColor(R.color.text_primary));
                    mMinTempTextPaint.setColor(resources.getColor(R.color.text_secondary));
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            if(mAmbient){
                canvas.drawText(String.format(Locale.getDefault(), "%d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE)), mXOffset, mTimeYOffset, mTimeTextPaint);
            }else{
                final boolean shouldDrawColons = (System.currentTimeMillis() % 1000) < 500;
                float timeX = mXOffset;
                final String colon = ":";

                int hour = mCalendar.get(Calendar.HOUR);
                if(hour == 0){
                    hour = 12;
                }
                String hourStr = hour + "";
                canvas.drawText(hourStr, timeX, mTimeYOffset, mTimeTextPaint);
                timeX += mTimeTextPaint.measureText(hourStr);

                if(shouldDrawColons){
                    canvas.drawText(colon, timeX, mTimeYOffset, mTimeTextPaint);
                }
                timeX += mTimeTextPaint.measureText(colon);

                String minuteStr = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.MINUTE));
                canvas.drawText(minuteStr, timeX, mTimeYOffset, mTimeTextPaint);
                timeX += mTimeTextPaint.measureText(minuteStr);

                if(shouldDrawColons){
                    canvas.drawText(colon, timeX, mTimeYOffset, mTimeTextPaint);
                }
                timeX += mTimeTextPaint.measureText(colon);

                String secondStr = String.format(Locale.getDefault(), "%02d", mCalendar.get(Calendar.SECOND));
                canvas.drawText(secondStr, timeX, mTimeYOffset, mTimeTextPaint);
            }

            String dayText = mDayOfWeekFormat.format(mDate).toUpperCase() + ", " + mDateFormat.format(mDate);
            canvas.drawText(dayText, mXOffset, mDayYOffset, mDayTextPaint);

            float x = mXOffset;

            if(!mAmbient){
                canvas.drawBitmap(mWeatherBitmap, x, mWeatherIconYOffset, new Paint());
                x += mWeatherBitmap.getWidth() + mTempLeftMargin;
            }

            canvas.drawText(mHighTenpStr, x, mWeatherTempOffset, mMaxTempTextPaint);
            x += mMaxTempTextPaint.measureText(mHighTenpStr);

            canvas.drawText(mLowTempStr, x, mWeatherTempOffset, mMinTempTextPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void initFormats(){
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);

            mDateFormat = DateFormat.getDateFormat(SunshineDigitalWatchFace.this);
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/request_weather_sync");
            putDataMapRequest.getDataMap().putLong("time", new Date().getTime());

            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            final String weatherUpdatePath = "/weather_update";

            for(DataEvent dataEvent: dataEventBuffer){
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    if(weatherUpdatePath.equals(dataEvent.getDataItem().getUri().getPath())){
                        Log.e("rohit_wear", "weather update received from phone");

                        DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                        if(dataMap.containsKey("weather_id")){
                            int resId = Utility.getIconResourceForWeatherCondition(dataMap.getInt("weather_id"));
                            if(resId != -1){
                                mWeatherBitmap = BitmapFactory.decodeResource(getResources(), resId);
                            }
                        }
                        if(dataMap.containsKey("high_temp") && dataMap.containsKey("low_temp")){
                            mHighTenpStr = dataMap.getString("high_temp");
                            mLowTempStr = dataMap.getString("low_temp");
                        }
                    }
                }
            }
        }
    }
}
