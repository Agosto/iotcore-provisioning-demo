package com.agosto.iotcoreprovisioning.device;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * POJO for rest service
 */

public class DeviceSettings {
    @SerializedName("deviceId")
    @Expose
    public String deviceId = "";
    @SerializedName("encodedPublicKey")
    @Expose
    public String encodedPublicKey = "";
    @SerializedName("projectId")
    @Expose
    public String projectId = "";
    @SerializedName("registryId")
    @Expose
    public String registryId = "";

    public String url = "";
}
