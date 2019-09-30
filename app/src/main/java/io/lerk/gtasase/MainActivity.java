package io.lerk.gtasase;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.ServiceInfoImpl;

import io.lerk.gtasase.tasks.MDNSTask;

import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();

    private WifiManager.MulticastLock multicastLock;
    private boolean appRunning = true;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.servicesRefreshLayout);
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setColorSchemeResources(R.color.primaryDarkColor, R.color.primaryColor, R.color.primaryLightColor);

        ListView serviceList = findViewById(R.id.serviceList);
        ArrayAdapter<ServiceInfo> adapter = new ArrayAdapter<ServiceInfo>(this, R.layout.layout_service) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                ServiceInfo serviceInfo = getItem(position);
                if (serviceInfo != null) {
                    if (convertView != null) {
                        return initView(convertView, serviceInfo);
                    } else {
                        return initView(LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_service, parent, false), serviceInfo);
                    }
                } else {
                    return convertView;
                }
            }

            private View initView(View view, ServiceInfo serviceInfo) {
                String serviceAddressString;
                String hostname = serviceInfo.getPropertyString("hostname");
                String propertyIp = serviceInfo.getPropertyString("ip");
                TextView serviceName = view.findViewById(R.id.service_name);
                TextView serviceAddress = view.findViewById(R.id.service_address);
                Button connectButton = view.findViewById(R.id.connect_button);

                try {
                    serviceAddressString = getFullServiceAddressString(serviceInfo, propertyIp);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to determine address for service.", e);
                    serviceAddressString = getString(R.string.address_error_message);
                    connectButton.setEnabled(false);
                    serviceAddress.setTextColor(getColor(R.color.errorTextColor));
                }
                String serviceNameString = serviceInfo.getName() + ((hostname == null || hostname.isEmpty()) ? "" : " (" + hostname + ")");

                serviceName.setText(serviceNameString);
                serviceAddress.setText(serviceAddressString);
                final String finalServiceAddressString = serviceAddressString;
                connectButton.setOnClickListener(v -> {
                    final boolean permissionGranted = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

                    if (!permissionGranted) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                        }
                        Toast.makeText(MainActivity.this, R.string.permission_hook_try_again, LENGTH_LONG).show();
                    } else {
                        Intent intent = new Intent(getApplicationContext(), ServiceActivity.class);
                        ArrayList<String> strings = new ArrayList<>();
                        Arrays.asList(serviceInfo.getInetAddresses()).forEach(s -> strings.add(s.toString().replaceAll("/", "")));
                        if (strings.size() <= 0) {
                            strings.add(finalServiceAddressString);
                        }
                        intent.putStringArrayListExtra(ServiceActivity.SERVICE_ADDRESS_KEY, strings);
                        intent.putExtra(ServiceActivity.SERVICE_PORT_KEY, serviceInfo.getPort());
                        startActivity(intent);
                    }
                });

                return view;
            }

            private String getFullServiceAddressString(ServiceInfo serviceInfo, String propertyIp) throws Exception {
                String portPropertyString = serviceInfo.getPropertyString("port");
                String portServiceInfoString = String.valueOf(serviceInfo.getPort());
                if (!portServiceInfoString.equals("") && !portServiceInfoString.equals("0")) {
                    return getHostAddressString(serviceInfo, propertyIp) + ":" + portServiceInfoString;
                } else if (portPropertyString != null && !portPropertyString.equals("0")) {
                    return getHostAddressString(serviceInfo, propertyIp) + ":" + portPropertyString;
                } else {
                    throw new Exception("No port found for service.");
                }
            }

            private String getHostAddressString(ServiceInfo serviceInfo, String propertyIp) {
                if (serviceInfo.getInetAddresses().length > 0 &&
                        !(serviceInfo.getInetAddresses()[0].toString().toLowerCase().startsWith("0.") ||
                                serviceInfo.getInetAddresses()[0].toString().toLowerCase().contains("0:"))) {
                    return serviceInfo.getInetAddresses()[0].toString().replaceAll("/", "");
                } else if (propertyIp != null) {
                    return propertyIp;
                } else {
                    return ((ServiceInfoImpl) serviceInfo).getDns().getLocalHost().getInetAddress().getHostAddress();
                }
            }
        };

        adapter.setNotifyOnChange(true); // !!!!
        serviceList.setAdapter(adapter);

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("gtaSaSeExtractorMulticastLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            MDNSTask mdnsTask = new MDNSTask(this, wifi, serviceList);
            mdnsTask.executeOnExecutor(MDNSTask.THREAD_POOL_EXECUTOR);
        } else {
            onMDNSError(new IOException("WifiManager is null!"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (multicastLock != null) {
            multicastLock.release();
        }
        appRunning = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_tutorial) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.tutorial_title)
                    .setMessage(R.string.tutorial_message_1)
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.next, (dialog, which) ->
                            new AlertDialog.Builder(MainActivity.this)
                                    .setNegativeButton(R.string.cancel, (d, w) -> d.cancel())
                                    .setTitle(R.string.tutorial_title)
                                    .setMessage(R.string.tutorial_message_2)
                                    .setPositiveButton(R.string.next, (dial0g, wh1ch) ->
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setTitle(R.string.tutorial_title)
                                                    .setMessage(R.string.tutorial_message_3)
                                                    .setNeutralButton(R.string.okay, (d, w) -> d.dismiss()).show()).show()).show();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    public InetAddress getDeviceIpAddress(WifiManager wifi) {
        InetAddress result = null;
        try {
            // default to Android localhost
            result = InetAddress.getByName("10.0.0.2");

            // figure out our wifi address, otherwise bail
            WifiInfo wifiinfo = wifi.getConnectionInfo();
            int intaddr = wifiinfo.getIpAddress();
            byte[] byteaddr = new byte[]{(byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
                    (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff)};
            result = InetAddress.getByAddress(byteaddr);
        } catch (UnknownHostException ex) {
            Log.w(TAG, "Error resolving local address!", ex);
        }

        return result;
    }

    public boolean isAppRunning() {
        return appRunning;
    }

    public void onMDNSError(IOException e) {
        new AlertDialog.Builder(getApplicationContext())
                .setTitle(R.string.mdns_error_title)
                .setMessage(R.string.mdns_error_message)
                .setNeutralButton(R.string.okay, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                }).show();
        Log.e(TAG, "mDNS Error occurred!", e);
    }
}
