package com.agosto.iotcoreprovisioning.device;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.api.services.cloudiot.v1.model.PublicKeyCredential;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Utility class for registering and managing devices in Cloud Iot Core
 */
public class Provisioning {
    private static final String TAG = "Provisioning";

    public static final String PROJECTS_UPDATED = "com.agosto.iotcoreprovisioning.PROJECTS_UPDATED";
    public static final String REGISTRIES_UPDATED = "com.agosto.iotcoreprovisioning.REGISTRIES_UPDATED";
    public static final String DEVICES_UPDATED = "com.agosto.iotcoreprovisioning.DEVICES_UPDATED";
    public static final String DEVICE_PROVISIONED = "com.agosto.iotcoreprovisioning.DEVICE_PROVISIONED";

    private static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private CloudIot mCloudIot;
    private CloudResourceManager mResourceManager;
    private List<Project> mProjects;
    private List<DeviceRegistry> mDeviceRegistries = new ArrayList<>();
    private String mProjectId = "";
    private DeviceRegistry mCurrentRegistry;
    private List<Device> mDevices = new ArrayList<>();
    private LocalBroadcastManager mBroadcastManager;

    public Provisioning() {
    }

    /**
     * launches an AsyncTask to create a CloudIot service which wraps around the Iot Core rest api.
     * @param context application content
     * @param account google account
     */
    public void buildCloudIotService(Context context, Account account) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        ArrayList<String> scopes = new ArrayList<>();
        scopes.add("https://www.googleapis.com/auth/cloudiot");
        scopes.add("https://www.googleapis.com/auth/cloud-platform.read-only");
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context,scopes);
        credential.setSelectedAccount(account);
        new BuildCloudResourceManageServiceTask(credential).execute();
        new BuildCloudIotServiceTask(credential).execute();
    }

    /**
     * Sets the Google Cloud projectId. Immediately fetches registries for project as well as the
     * devices for the top registry in the list.
     * @param projectId google cloud project id
     */
    public void setProjectId(String projectId) {
        if(projectId.equals(this.mProjectId)) {
            return;
        }
        this.mProjectId = projectId;
        if(mCloudIot!=null) {
            fetchRegistries();
        }
    }

    public String getProjectId() {
        return mProjectId;
    }

    /**
     * get GCP projects for current user
     * @return List of Project
     */
    public List<Project> getProjects() {
        return mProjects;
    }

    /**
     * set the current working registry
     * @param currentRegistry current registry
     */
    public void setCurrentRegistry(DeviceRegistry currentRegistry) {
        this.mCurrentRegistry = currentRegistry;
    }

    /**
     * set current registry by index in the registries listing.
     * @param index index of registry to use
     */
    public void setCurrentRegistry(int index) {
        if(index < 0 || index >= mDeviceRegistries.size()) {
            return;
        }
        DeviceRegistry deviceRegistry = mDeviceRegistries.get(index);
        if(deviceRegistry.equals(this.mCurrentRegistry)) {
            return;
        }
        this.mCurrentRegistry = mDeviceRegistries.get(index);
        fetchDevices();
    }

    /**
     * Gets list of devices.  Will be empty until after a DEVICES_UPDATED broadcast.
     * @return list of devices
     */
    public List<Device> getDevices() {
        return mDevices;
    }

    /**
     *Ggets list of registries. Will be empty until after a DEVICES_UPDATED broadcast.
     * @return list of registries
     */
    public List<DeviceRegistry> getDeviceRegistries() {
        return mDeviceRegistries;
    }

    /**
     * get current working registry or null if not set
     * @return current registry or null
     */
    @Nullable
    public DeviceRegistry getCurrentRegistry() {
        return mCurrentRegistry;
    }


    private class BuildCloudIotServiceTask extends AsyncTask<Void,Void,CloudIot> {


        private GoogleAccountCredential credential;

        BuildCloudIotServiceTask(GoogleAccountCredential credential) {
            this.credential = credential;
        }

        @Override
        protected CloudIot doInBackground(Void... voids) {
            return new CloudIot.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();
        }

        @Override
        protected void onPostExecute(CloudIot result) {
            if(result!=null) {
                mCloudIot = result;
                if(!mProjectId.isEmpty()) fetchRegistries();
            }
        }
    }

    private class BuildCloudResourceManageServiceTask extends AsyncTask<Void,Void,CloudResourceManager> {

        GoogleAccountCredential credential;

        BuildCloudResourceManageServiceTask(GoogleAccountCredential credential) {
            this.credential = credential;
        }

        @Override
        protected CloudResourceManager doInBackground(Void... voids) {
            return new CloudResourceManager.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).build();
        }

        @Override
        protected void onPostExecute(CloudResourceManager result) {
            if(result!=null) {
                mResourceManager = result;
                fetchProjects();
            }
        }
    }

    private void fetchProjects() {
        new FetchProjectsTask(mResourceManager).execute();
    }

    private class FetchProjectsTask extends AsyncTask<Void,Void, List<Project>> {

        CloudResourceManager resourceManager;

        FetchProjectsTask(CloudResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        @Override
        protected List<Project> doInBackground(Void... voids) {
            try {
                return resourceManager.projects().list().execute().getProjects();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Project> result) {
            if(result!=null) {
                mProjects = result;
                Log.d(TAG,result.get(0).getName());
                if(mProjectId.isEmpty() && !mProjects.isEmpty()) {
                    setProjectId(mProjects.get(0).getProjectId());
                }
            }
            mBroadcastManager.sendBroadcast(new Intent(PROJECTS_UPDATED));
        }
    }

    private void fetchRegistries() {
        mDevices.clear();
        mDeviceRegistries.clear();
        FetchRegistriesTask task = new FetchRegistriesTask(mCloudIot);
        task.execute();
    }

    private class FetchRegistriesTask extends AsyncTask<Void,Void, List<DeviceRegistry>> {

        CloudIot cloudIot;

        FetchRegistriesTask(CloudIot cloudIot) {
            this.cloudIot = cloudIot;
        }

        @Override
        protected List<DeviceRegistry> doInBackground(Void... voids) {
            try {
                return cloudIot.projects().locations().registries().list("projects/"+mProjectId+"/locations/us-central1").execute().getDeviceRegistries();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<DeviceRegistry> result) {
            if(result!=null) {
                mDeviceRegistries = result;
                if(mCurrentRegistry!=null && mDeviceRegistries.contains(mCurrentRegistry)) {
                    fetchDevices();
                } else if(!mDeviceRegistries.isEmpty()) {
                    mCurrentRegistry = mDeviceRegistries.get(0);
                    fetchDevices();
                }
            }
            // send event for empty reg
            mBroadcastManager.sendBroadcast(new Intent(REGISTRIES_UPDATED));
        }
    }

    /**
     * Fetches a new list of devices from current registry.  Will broadcast DEVICES_UPDATED when finished.
     */
    public void fetchDevices() {
        Log.d(TAG, "Getting devices for " + mCurrentRegistry.getName());
        mDevices.clear();
        FetchDevicesTask task = new FetchDevicesTask(mCloudIot,mCurrentRegistry.getName());
        task.execute();
    }

    private class FetchDevicesTask extends AsyncTask<Void,Void, List<Device>> {

        private final String path;
        CloudIot cloudIot;

        FetchDevicesTask(CloudIot cloudIot, String path) {
            this.cloudIot = cloudIot;
            this.path = path;
        }

        @Override
        protected List<Device> doInBackground(Void... voids) {
            try {
                return cloudIot.projects().locations().registries().devices().list(path).execute().getDevices();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Device> result) {
            mDevices = result == null ? new ArrayList<Device>() : result;
            mBroadcastManager.sendBroadcast(new Intent(DEVICES_UPDATED));
        }
    }

    /**
     * Creates a new device in current registry using deviceSettings.  Will broadcast
     * DEVICE_PROVISIONED when finished.  New device will have it's IoT Core device config updated
     * to turn off it's http config server.
     * @param deviceSettings device registration settings
     */
    public void createDevice(DeviceSettings deviceSettings) {
        CreateDeviceTask task = new CreateDeviceTask(mCloudIot,deviceSettings,mCurrentRegistry);
        task.execute();
    }

    private class CreateDeviceTask extends AsyncTask<Void,Void, Device> {

        CloudIot cloudIot;
        DeviceSettings deviceSettings;
        DeviceRegistry deviceRegistry;

        CreateDeviceTask(CloudIot cloudIot, DeviceSettings deviceSettings, DeviceRegistry deviceRegistry) {
            this.cloudIot = cloudIot;
            this.deviceSettings = deviceSettings;
            this.deviceRegistry = deviceRegistry;
        }

        @Override
        protected Device doInBackground(Void... voids) {
            Device device = new Device();
            device.setId(deviceSettings.deviceId);
            PublicKeyCredential publicKeyCredential = new PublicKeyCredential();
            String key = deviceSettings.encodedPublicKey.startsWith("-----BEGIN CERTIFICATE-----") ? deviceSettings.encodedPublicKey :
                    "-----BEGIN CERTIFICATE-----\n"+deviceSettings.encodedPublicKey+"-----END CERTIFICATE-----";
            Log.d(TAG,key);
            publicKeyCredential.setKey(key);
            publicKeyCredential.setFormat("RSA_X509_PEM");
            DeviceCredential credential = new DeviceCredential();
            credential.setPublicKey(publicKeyCredential);
            device.setCredentials(Arrays.asList(credential));
            try {
                return cloudIot.projects().locations().registries().devices().create(deviceRegistry.getName(), device).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Device result) {
            if(result!=null) {
                Log.d(TAG, result.getName());
                deviceSettings.registryId = deviceRegistry.getId();
                deviceSettings.projectId = mProjectId;
                updateDeviceSettings(deviceSettings);
                enabledConfigServer(false,deviceSettings.deviceId);
            }
        }
    }

    /*
    Updates device Iot Core config to enable/disable the device's config http server
     */
    public void enabledConfigServer(boolean enable, String deviceId) {
        String data = "{\"config_server_on\":"+enable+"}";
        new UpdateDeviceConfigTask(mCloudIot,deviceId,mCurrentRegistry,data).execute();
    }

    private class UpdateDeviceConfigTask extends AsyncTask<Void,Void, DeviceConfig> {

        CloudIot cloudIot;
        DeviceRegistry deviceRegistry;
        String deviceId;
        String configData;

        UpdateDeviceConfigTask(CloudIot cloudIot, String deviceId, DeviceRegistry deviceRegistry, String configData) {
            this.cloudIot = cloudIot;
            this.deviceId = deviceId;
            this.deviceRegistry = deviceRegistry;
            this.configData = configData;
        }

        @Override
        protected DeviceConfig doInBackground(Void... voids) {
            final String devicePath = deviceRegistry.getName() + "/devices/" + deviceId;
            ModifyCloudToDeviceConfigRequest request  =new ModifyCloudToDeviceConfigRequest();
            //DeviceConfigData data = new DeviceConfigData();
            //data.encodeBinaryData(configData.getBytes());
            request.setVersionToUpdate(0L);
            //request.setData(data);
            request.encodeBinaryData(configData.getBytes());
            try {
                return cloudIot.projects().locations().registries().devices().modifyCloudToDeviceConfig(devicePath,request).execute();

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(DeviceConfig result) {
            if(result!=null) {
                Log.d(TAG,result.toString());
            } else {
                Log.w(TAG,"Config update failed");
            }
        }
    }

    private void updateDeviceSettings(DeviceSettings deviceSettings) {
        DeviceService service = DeviceServiceGenerator.createService(DeviceService.class);
        Call<DeviceSettings> call = service.updateDeviceSettings(deviceSettings.url,deviceSettings);
        call.enqueue(new Callback<DeviceSettings>() {
            @Override
            public void onResponse(Call<DeviceSettings> call, Response<DeviceSettings> response) {
                if(response.code()==200) {
                    DeviceSettings deviceSettings = response.body();
                    Log.d(TAG,deviceSettings.registryId);
                    fetchDevices();
                    Intent intent = new Intent(DEVICE_PROVISIONED);
                    intent.putExtra("deviceId",deviceSettings.deviceId);
                    mBroadcastManager.sendBroadcast(intent);
                }
            }

            @Override
            public void onFailure(Call<DeviceSettings> call, Throwable t) {

            }
        });
    }

}
