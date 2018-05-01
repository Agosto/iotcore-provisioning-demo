package com.agosto.iotcoreprovisioning.device;

import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.gson.Gson;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;

import java.util.ArrayList;
import java.util.Collection;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class DeviceScanner implements RangeNotifier {
    private static final String TAG = "DeviceScanner";
    public static final String NEW_NEARBY_DEVICE = "com.agosto.iotcoreprovisioning.NEW_NEARBY_DEVICE";
    private static final String DEVICE_JSON = "device_json";

    private final DeviceService mService;

    private BeaconManager mBeaconManager;
    private BeaconConsumer mBeaconConsumer;
    private ArrayList<String> mNearByDevices = new ArrayList<>();
    private Region mRegion = new Region("all-beacons-region", null, null, null);

    public DeviceScanner() {
        mService = DeviceServiceGenerator.createService(DeviceService.class);
    }

    /**
     * binds the scanner to a beacon consumer (often an activity)
     * @param beaconConsumer consumer to bind
     */
    public void bind(BeaconConsumer beaconConsumer) {
        mBeaconManager = BeaconManager.getInstanceForApplication(beaconConsumer.getApplicationContext());
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        mBeaconManager.bind(beaconConsumer);
        mBeaconConsumer = beaconConsumer;
    }

    /**
     * starts ranging for beacons
     */
    public void startScanning() {
        if(mBeaconManager==null) {
            return;
        }
        mBeaconManager.addRangeNotifier(this);

        try {
            mBeaconManager.startRangingBeaconsInRegion(mRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /*
    stops ranging for beacons
     */
    public void stopScanning() {
        if(mBeaconManager!=null) {
            mBeaconManager.removeAllRangeNotifiers();
            try {
                mBeaconManager.stopRangingBeaconsInRegion(mRegion);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mBeaconManager.unbind(mBeaconConsumer);
        }
    }

    /*
    resets list of already scanned devices so listener receives NEW_NEARBY_DEVICE broadcasts
     */
    public void clearScannedDevices() {
        mNearByDevices.clear();
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        for (Beacon beacon: beacons) {
            if (beacon.getServiceUuid() == 0xfeaa && beacon.getBeaconTypeCode() == 0x10) {
                String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                //Log.d(TAG,url);
                if(mNearByDevices.indexOf(url) == -1) {
                    Log.d(TAG,"New Device " + url);
                    mNearByDevices.add(url);
                    if(url.startsWith("http://device-")) {
                        DeviceSettings deviceSettings = new DeviceSettings();
                        deviceSettings.deviceId = url.replace("http://","");
                        sendBroadCast(deviceSettings);
                    } else {
                        getDeviceSettings(url  + ":8080");
                    }
                }
            }
        }
    }

    private void getDeviceSettings(final String url) {
        Call<DeviceSettings> call = mService.deviceSettings(url);
        call.enqueue(new Callback<DeviceSettings>() {
            @Override
            public void onResponse(Call<DeviceSettings> call, Response<DeviceSettings> response) {
                if(response.code()==200) {
                    DeviceSettings deviceSettings = response.body();
                    Log.d(TAG,deviceSettings.encodedPublicKey);
                    deviceSettings.url = url;
                    sendBroadCast(deviceSettings);
                }
            }

            @Override
            public void onFailure(Call<DeviceSettings> call, Throwable t) {

            }
        });
    }

    private void sendBroadCast(DeviceSettings deviceSettings) {
        Intent intent = new Intent(NEW_NEARBY_DEVICE);
        intent.putExtra(DEVICE_JSON, new Gson().toJson(deviceSettings));
        LocalBroadcastManager.getInstance(mBeaconConsumer.getApplicationContext()).sendBroadcast(intent);
    }

    @Nullable
    static public DeviceSettings getDeviceSetting(Intent intent) {
        String json = intent.getStringExtra(DEVICE_JSON);
        if(json!=null) {
            return new Gson().fromJson(json,DeviceSettings.class);
        }
        return null;
    }

}
