package com.agosto.iotcoreprovisioning.ui;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.agosto.iotcoreprovisioning.R;
import com.agosto.iotcoreprovisioning.device.DeviceSettings;
import com.google.api.services.cloudiot.v1.model.Device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DevicesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<DeviceListing> deviceListings;
    private DevicesListener devicesListener;

    public DevicesAdapter(List<Device> deviceList, DevicesListener devicesListener) {
        deviceListings = new ArrayList<>(deviceList.size());
        for(Device device : deviceList) {
            deviceListings.add(new DeviceListing(device.getId()));
        }
        this.devicesListener = devicesListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_row_item,parent,false);
        return  new DeviceHolder(view, devicesListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DeviceHolder deviceHolder = (DeviceHolder) holder;
        deviceHolder.setDevice(deviceListings.get(position));
    }

    @Override
    public int getItemCount() {
        return deviceListings.size();
    }

    /**
     * view model for list items and for passing to event handlers
     */
    public static class DeviceListing {
        public boolean provisioned = true;
        public boolean nearBy = false;
        public boolean inOtherRegistry = false;
        String deviceId = "";
        public DeviceSettings deviceSettings;

        DeviceListing(String deviceId) {
            this.deviceId = deviceId;
        }
    }

    public void setDeviceListings(List<Device> deviceList) {
        deviceListings = new ArrayList<>(deviceList.size());
        for(Device device : deviceList) {
            deviceListings.add(new DeviceListing(device.getId()));
        }
        notifyDataSetChanged();
    }

    private void sortListings() {
        Collections.sort(deviceListings, new Comparator<DeviceListing>() {
            @Override
            public int compare(DeviceListing first, DeviceListing second) {
                if(!first.nearBy && second.nearBy) {
                    return 1;
                } else if( first.nearBy && !second.nearBy) {
                    return -1;
                }
                return 0;
            }
        });
        notifyDataSetChanged();
    }

    public boolean addNearbyDevice(DeviceSettings deviceSettings) {
        // search existing devices
        for(int i=0, num = deviceListings.size(); i< num;i++) {
            DeviceListing deviceListing = deviceListings.get(i);
            if(deviceListing.deviceId.equals(deviceSettings.deviceId)) {
                deviceListing.nearBy = true;
                deviceListing.deviceSettings = deviceSettings;
                sortListings();
                return false;
            }
        }

        DeviceListing deviceListing = new DeviceListing(deviceSettings.deviceId);
        if(deviceSettings.registryId != null && !deviceSettings.registryId.isEmpty()) {
            deviceListing.provisioned = true;
            deviceListing.inOtherRegistry = true;
        } else {
            deviceListing.provisioned = false;
            deviceListing.inOtherRegistry = false;
        }
        deviceListing.nearBy = true;
        deviceListing.deviceSettings = deviceSettings;
        deviceListings.add(0,deviceListing);
        notifyItemInserted(0);
        return true;
    }

    /**
     * callback interface for when a device is selected in list
     */
    public interface DevicesListener {
        void onSelected(DeviceListing deviceListing, View view);
    }

    private static class DeviceHolder extends RecyclerView.ViewHolder {

        DeviceListing device;
        TextView deviceId;
        TextView extra;
        ImageView icon;
        DevicesListener mDevicesListener;
        ImageView menuIcon;
        DeviceHolder(View itemView, DevicesListener devicesListener) {
            super(itemView);
            deviceId = itemView.findViewById(R.id.deviceId);
            icon = itemView.findViewById(R.id.icon);
            extra = itemView.findViewById(R.id.extra);
            menuIcon = itemView.findViewById(R.id.device_menu);
            mDevicesListener = devicesListener;
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(mDevicesListener!=null) {
                        mDevicesListener.onSelected(device,menuIcon);
                    }
                }
            });
        }

        public void setDevice(DeviceListing device) {
            this.device = device;
            deviceId.setText(device.deviceId);
            if(!device.provisioned) {
                icon.setColorFilter(ContextCompat.getColor(icon.getContext(),R.color.colorAccent));
            } else if (device.inOtherRegistry) {
                icon.setColorFilter(ContextCompat.getColor(icon.getContext(),R.color.yellow));
            } else if(device.nearBy) {
                icon.setColorFilter(ContextCompat.getColor(icon.getContext(),R.color.green));
            } else {
                icon.setColorFilter(Color.GRAY);
            }
            if(device.deviceSettings == null) {
                extra.setVisibility(View.GONE);
                menuIcon.setVisibility(View.GONE);
            } else {
                menuIcon.setVisibility(View.VISIBLE);
                extra.setVisibility(View.VISIBLE);
                extra.setText(device.deviceSettings.url);
            }
        }
    }

}
