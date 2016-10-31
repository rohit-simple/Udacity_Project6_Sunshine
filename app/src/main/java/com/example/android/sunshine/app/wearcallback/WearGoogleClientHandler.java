package com.example.android.sunshine.app.wearcallback;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearGoogleClientHandler implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{
    private static WearGoogleClientHandler instance = new WearGoogleClientHandler();
    private GoogleApiClient mGoogleApiClient;

    private WearGoogleClientHandler() {}

    public static WearGoogleClientHandler getInstance(){
        return instance;
    }

    public void start(Context context){
        instance.mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(instance)
                .addOnConnectionFailedListener(instance)
                .build();
        instance.mGoogleApiClient.connect();
    }

    public void tearDown(){
        if(mGoogleApiClient != null){
            if(mGoogleApiClient.isConnected()){
                mGoogleApiClient.disconnect();
            }
            mGoogleApiClient = null;
        }
    }

    public void sendData(PutDataMapRequest putDataMapRequest){
        if(mGoogleApiClient.isConnected()){
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
