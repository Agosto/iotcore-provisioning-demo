package com.agosto.iotcoreprovisioning;

import android.Manifest;
import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;

import com.agosto.iotcoreprovisioning.device.DeviceScanner;
import com.agosto.iotcoreprovisioning.device.DeviceService;
import com.agosto.iotcoreprovisioning.device.DeviceServiceGenerator;
import com.agosto.iotcoreprovisioning.device.DeviceSettings;
import com.agosto.iotcoreprovisioning.device.Provisioning;
import com.agosto.iotcoreprovisioning.ui.DevicesAdapter;
import com.agosto.iotcoreprovisioning.ui.PermissionHandler;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudresourcemanager.model.Project;

import org.altbeacon.beacon.BeaconConsumer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, BeaconConsumer {
    private static final String TAG = "MainActivity";
    // Request codes
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_RECOVERABLE = 9002;
    private static final int RESCAN_DEFAULT_TIME = 1000*60;
    private GoogleApiClient mGoogleApiClient;
    private Account mAccount;
    GoogleSignInAccount mGoogleSignInAccount;
    SignInButton mSignInButton;

    Provisioning mProvisioning = new Provisioning();
    PermissionHandler mPermissionHandler = new PermissionHandler();
    DeviceService mDeviceService;
    DeviceScanner mDeviceScanner = new DeviceScanner();
    Handler mScanHandler = new Handler();

    Spinner mRegistryView;
    TextView mRegistryErrorView;
    TextView mEmailView;
    TextView mProjectView;
    RecyclerView mRecyclerView;
    View mProjectDetailsView;
    AutoCompleteTextView autoCompleteTextView;

    ArrayAdapter<String> registriesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String projectId = PreferenceManager.getDefaultSharedPreferences(this).getString("projectId","");
        mProvisioning.setProjectId(projectId);

        mDeviceService = DeviceServiceGenerator.createService(DeviceService.class);

        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope("https://www.googleapis.com/auth/cloudiot"))
                        .requestScopes(new Scope("https://www.googleapis.com/auth/cloud-platform.read-only"))
                        .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)

                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        mRegistryErrorView = findViewById(R.id.registryError);
        mRegistryView = findViewById(R.id.registry);
        registriesAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        registriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mRegistryView.setAdapter(registriesAdapter);
        mRegistryView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d(TAG,registriesAdapter.getItem(i));
                mProvisioning.setCurrentRegistry(i);
                setRecyclerView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        mEmailView = findViewById(R.id.email);
        mRecyclerView = findViewById(R.id.recyclerView);
        mProjectView = findViewById(R.id.projectId);

        mProjectView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleProjectEdit();
            }
        });

        autoCompleteTextView = findViewById(R.id.projectIdauto);
        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                setProjectId();
            }
        });

        mProjectDetailsView = findViewById(R.id.projectDetails);
        mSignInButton = findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                authorizeIotCoreAccess();
            }
        });
        setRecyclerView();
    }

    @Override
    public void onStart() {
        super.onStart();

        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            Log.d(TAG, "Got cached sign-in");
            GoogleSignInResult result = opr.get();
            handleSignInResult(result);
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            //showProgressDialog();
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(@NonNull GoogleSignInResult googleSignInResult) {
                    //hideProgressDialog();
                    handleSignInResult(googleSignInResult);
                }
            });
        }
        mPermissionHandler.run(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, R.string.rationale, new PermissionHandler.Callback() {
            @Override
            public void onPermission(boolean grantedNow) {
                mDeviceScanner.bind(MainActivity.this);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mScanHandler.removeCallbacksAndMessages(null);
        if(mDeviceScanner != null) {
            mDeviceScanner.stopScanning();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(onNewDeviceReceiver,new IntentFilter(DeviceScanner.NEW_NEARBY_DEVICE));
        broadcastManager.registerReceiver(onDevicesUpdate,new IntentFilter(Provisioning.DEVICES_UPDATED));
        broadcastManager.registerReceiver(onDeviceProvisioned,new IntentFilter(Provisioning.DEVICE_PROVISIONED));
        broadcastManager.registerReceiver(onRegistriesUpdate,new IntentFilter(Provisioning.REGISTRIES_UPDATED));
        broadcastManager.registerReceiver(onProjectsUpdate,new IntentFilter(Provisioning.PROJECTS_UPDATED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(onNewDeviceReceiver);
        broadcastManager.unregisterReceiver(onDevicesUpdate);
        broadcastManager.unregisterReceiver(onDeviceProvisioned);
        broadcastManager.unregisterReceiver(onRegistriesUpdate);
        broadcastManager.unregisterReceiver(onProjectsUpdate);
    }

    /* handler for new devices detected by ble */
    private BroadcastReceiver onNewDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DeviceSettings deviceSettings = DeviceScanner.getDeviceSetting(intent);
            DevicesAdapter adapter = (DevicesAdapter) mRecyclerView.getAdapter();
            if(adapter.addNearbyDevice(deviceSettings)) {
                mRecyclerView.scrollToPosition(0);
            };
        }
    };

    /* handler for when the registry list has been updated */
    private BroadcastReceiver onRegistriesUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setRegistrySpinner();
            setRecyclerView();
            boolean hasRegistries = mProvisioning.getCurrentRegistry() != null;
            mRegistryView.setVisibility(hasRegistries ? View.VISIBLE : View.GONE);
            mRegistryErrorView.setVisibility(hasRegistries ? View.GONE : View.VISIBLE);
        }
    };

    /* handler for when the project list has been updated */
    private BroadcastReceiver onProjectsUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //setProjectSpinner();
            setProjectAdapter();
        }
    };

    /* handler for when the devices list has been updated */
    private BroadcastReceiver onDevicesUpdate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setRecyclerView();
        }
    };

    /* handler for when a new device was provisioned */
    private BroadcastReceiver onDeviceProvisioned = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String deviceId = intent.getStringExtra("deviceId");
            Snackbar.make(mRecyclerView,"Device "+deviceId+" provisioned",Snackbar.LENGTH_LONG).show();
        }
    };

    void setRegistrySpinner() {
        List<DeviceRegistry> registries = mProvisioning.getDeviceRegistries();
        DeviceRegistry deviceRegistry = mProvisioning.getCurrentRegistry();
        if(registries.isEmpty() || deviceRegistry==null) {
            registriesAdapter.clear();
            registriesAdapter.notifyDataSetChanged();
        } else if(registries.size()!=mRegistryView.getCount()) {
            ArrayList<String> items = new ArrayList<>();
            for(DeviceRegistry registry : registries) {
                items.add(registry.getId());
            }
            registriesAdapter.clear();
            registriesAdapter.addAll(items);
            registriesAdapter.notifyDataSetChanged();
            int index = items.indexOf(deviceRegistry.getId());
            if(index>-1) {
                mRegistryView.setSelection(index);
            }
        } else {
            Log.d(TAG,"No registry spinner update");
        }
    }

    /**
     * sets project id from auto complete
     */
    private void setProjectId() {
        String projectId = autoCompleteTextView.getText().toString();
        PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit().putString("projectId",projectId).apply();
        mProvisioning.setProjectId(projectId);
        setRegistrySpinner();
        mProjectView.setText(mProvisioning.getProjectId());
        toggleProjectEdit();
        autoCompleteTextView.setText("");
    }

    void setProjectAdapter() {
        final List<Project> projects = mProvisioning.getProjects();
        if(projects==null) {
            return;
        }
        if(autoCompleteTextView.getAdapter()==null) {
            List<String> items = new ArrayList<>(projects.size());
            for(Project project : projects) {
                items.add(project.getProjectId());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items);
            //adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            autoCompleteTextView.setAdapter(adapter);
        }
        mProjectView.setText(mProvisioning.getProjectId());
    }

    void toggleProjectEdit() {
        if(mProjectView.getVisibility()==View.GONE) {
            mProjectView.setVisibility(View.VISIBLE);
            autoCompleteTextView.setVisibility(View.GONE);
        }  else {
            mProjectView.setVisibility(View.GONE);
            autoCompleteTextView.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_signout) {
            signOutDialog();
            return true;
        } else if(id==R.id.action_about) {
            aboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBeaconServiceConnect() {
        mDeviceScanner.startScanning();
        rescanForDevices(RESCAN_DEFAULT_TIME);
    }

    /* resets the list of found devices so they get rescanned and updated in the recyclerView */
    public void rescanForDevices(int ms) {
        mScanHandler.removeCallbacksAndMessages(null);
        mScanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mDeviceScanner.clearScannedDevices();
                rescanForDevices(RESCAN_DEFAULT_TIME);
            }
        },ms);
    }

    /* start google single to get access to iot core */
    private void authorizeIotCoreAccess() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    /* sign out of google */
    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        finish();
                    }
                });
        PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();
    }

    /* revokes permission to app and then signs out */
    private void revokeAccess() {
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        signOut();
                    }
                });
    }

    /* shows the device config dialog */
    protected void signOutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final boolean[] checked = {false};
        builder.setMultiChoiceItems(R.array.sign_out_options, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton(R.string.sign_out, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if(checked[0]) {
                            revokeAccess();
                        } else {
                            signOut();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        builder.create().show();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    /* handler for google sign in result */
    private void handleSignInResult(GoogleSignInResult result) {
        if (result.isSuccess()) {
            mGoogleSignInAccount = result.getSignInAccount();
            mAccount = mGoogleSignInAccount.getAccount();
            mProvisioning.buildCloudIotService(this,mAccount);
            mSignInButton.setVisibility(View.GONE);
            mProjectDetailsView.setVisibility(View.VISIBLE);
            if (mAccount != null) {
                mEmailView.setVisibility(View.VISIBLE);
                mEmailView.setText(mGoogleSignInAccount.getEmail());
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed:" + connectionResult);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mPermissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /* shows the context device menu when selected in the recyclerView */
    public void deviceMenu(final DevicesAdapter.DeviceListing deviceListing, View view) {
        if(!deviceListing.nearBy && !deviceListing.inOtherRegistry) {
            Log.d(TAG,"no menu options for devices not near by");
            return;
        } else if(deviceListing.inOtherRegistry && deviceListing.deviceSettings.url.isEmpty()) {
            Log.d(TAG,"no menu options for devices not in registry and not running config server");
            return;
        }
        boolean configServerOn = !deviceListing.deviceSettings.url.isEmpty();
        PopupMenu popupMenu = new PopupMenu(this,view, Gravity.END );
        popupMenu.inflate(R.menu.menu_device);
        // provision
        popupMenu.getMenu().getItem(0).setVisible(!deviceListing.provisioned);
        // configs
        popupMenu.getMenu().getItem(1).setVisible(deviceListing.provisioned);
        // led
        popupMenu.getMenu().getItem(2).setEnabled(configServerOn);
        // reset
        popupMenu.getMenu().getItem(3).setEnabled(configServerOn);

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                switch (id) {
                    case R.id.action_identify:
                        blinkDevice(deviceListing.deviceSettings);
                        break;
                    case R.id.action_register:
                        provisionDialog(deviceListing.deviceSettings);
                        break;
                    case R.id.action_reset:
                        resetDevice(deviceListing.deviceSettings);
                        break;
                    case R.id.action_config:
                        configDialog(deviceListing.deviceSettings);
                        break;
                }
                return false;
            }
        });
        Object menuHelper;
        Class[] argTypes;
        try {
            Field fMenuHelper = PopupMenu.class.getDeclaredField("mPopup");
            fMenuHelper.setAccessible(true);
            menuHelper = fMenuHelper.get(popupMenu);
            argTypes = new Class[]{boolean.class};
            menuHelper.getClass().getDeclaredMethod("setForceShowIcon", argTypes).invoke(menuHelper, true);
        } catch (Exception e) {

        }
        popupMenu.show();
    }

    /* blinks led on device using the device service api */
    void blinkDevice(DeviceSettings deviceSettings) {
        Call<ResponseBody> call = mDeviceService.deviceOptions(deviceSettings.url);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if(response.code()!=200) {
                    Snackbar.make(mRecyclerView,"Error Connected to Device",Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Snackbar.make(mRecyclerView,"Error Connected to Device",Snackbar.LENGTH_LONG).show();
                Log.w(TAG,t.toString());
            }
        });
    }

    /* resets device using the device service api */
    void resetDevice(DeviceSettings deviceSettings) {
        Call<ResponseBody> call = mDeviceService.resetDeviceSettings(deviceSettings.url);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                String msg = "Error Connected to Device";
                if(response.code()==200) {
                    msg = "Device Reset";
                    mProvisioning.fetchDevices();
                }
                Snackbar.make(mRecyclerView,msg,Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Snackbar.make(mRecyclerView,"Error Connected to Device",Snackbar.LENGTH_LONG).show();
                Log.w(TAG,t.toString());
            }
        });
    }

    /* shows the device config dialog */
    protected void configDialog(final DeviceSettings deviceSettings) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final boolean[] checked = {!deviceSettings.url.isEmpty()};
        builder.setTitle(R.string.update_config)
                .setIcon(R.drawable.settings)
                .setMultiChoiceItems(R.array.config_options, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mProvisioning.enabledConfigServer(checked[0],deviceSettings.deviceId);
                        rescanForDevices(3000); // quick rescan
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        builder.create().show();
    }

    /* prompt to provision a device into the current registry  */
    protected void provisionDialog(final DeviceSettings deviceSettings) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Provision " + deviceSettings.deviceId + " to registry " + mProvisioning.getCurrentRegistry().getId())
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mProvisioning.createDevice( deviceSettings);
                    }
                })
                .setNegativeButton(R.string.no, null);
        builder.create().show();
    }

    /* show about dialog */
    protected void aboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.app_name) + "\nVersion " + BuildConfig.VERSION_NAME + "\nBuild " + BuildConfig.VERSION_CODE).create().show();
    }

    /* resets the recyclerView with current devices list, and rescans for nearby devices */
    void setRecyclerView() {
        mRecyclerView.setVisibility(mProvisioning.getCurrentRegistry() == null ? View.GONE : View.VISIBLE);
        DevicesAdapter adapter = (DevicesAdapter) mRecyclerView.getAdapter();
        if(adapter==null) {
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
            mRecyclerView.setLayoutManager(linearLayoutManager);
            adapter = new DevicesAdapter(mProvisioning.getDevices(), new DevicesAdapter.DevicesListener() {
                @Override
                public void onSelected(DevicesAdapter.DeviceListing deviceListing, View view) {
                    deviceMenu(deviceListing,view);
                }
            });
            mRecyclerView.setAdapter(adapter);
        } else {
            adapter.setDeviceListings(mProvisioning.getDevices());
        }
        if(mDeviceScanner != null)
            mDeviceScanner.clearScannedDevices();
    }
}
