package com.agosto.iotcoreprovisioning.device;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.OPTIONS;
import retrofit2.http.POST;
import retrofit2.http.Url;


public interface DeviceService {

    @GET
    Call<DeviceSettings> deviceSettings(@Url String url);

    @OPTIONS
    Call<ResponseBody> deviceOptions(@Url String url);

    @DELETE
    Call<ResponseBody> resetDeviceSettings(@Url String url);

    @POST
    Call<DeviceSettings> updateDeviceSettings(@Url String url, @Body DeviceSettings deviceSettings);
}
