package io.lerk.gtasase;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();

    private WifiManager.MulticastLock multicastLock;
    private JmDNS jmdns;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    @SuppressLint("StaticFieldLeak")
    // The AsyncTask needs to (eg. will) complete before the activity.
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ListView serviceList = findViewById(R.id.serviceList);
        serviceList.setAdapter(new ArrayAdapter<ServiceInfo>(this, R.layout.layout_service) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ServiceInfo serviceInfo = getItem(position);
                if (serviceInfo != null) {
                    if(convertView != null) {
                        return initView(convertView, serviceInfo);
                    } else {
                        return initView(LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_service, parent, false), serviceInfo);
                    }
                } else {
                    return convertView;
                }
            }

            private View initView(View view, ServiceInfo serviceInfo) {
                String hostname = serviceInfo.getPropertyString("hostname");
                String serviceAddressString = serviceInfo.getInet4Addresses()[0].toString().replaceAll("/", "") + ":" + serviceInfo.getPort();
                String serviceNameString = serviceInfo.getName() + ((hostname == null || hostname.isEmpty()) ? "" : " (" + hostname + ")");

                TextView serviceName = view.findViewById(R.id.service_name);
                TextView serviceAddress = view.findViewById(R.id.service_address);
                Button connectButton = view.findViewById(R.id.connect_button);

                serviceName.setText(serviceNameString);
                serviceAddress.setText(serviceAddressString);
                connectButton.setOnClickListener(v -> {
                    final boolean permissionGranted = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

                    if (!permissionGranted) {
                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
                        }
                    } else {
                        Intent intent = new Intent(getApplicationContext(), ServiceActivity.class);
                        ArrayList<String> strings = new ArrayList<>(serviceInfo.getInet4Addresses().length);
                        for (int i = 0; i < serviceInfo.getInet4Addresses().length; i++) {
                            strings.add(i, serviceInfo.getInet4Addresses()[i].toString().replaceAll("/", ""));
                        }
                        intent.putStringArrayListExtra(ServiceActivity.SERVICE_ADDRESS_KEY, strings);
                        intent.putExtra(ServiceActivity.SERVICE_PORT_KEY, serviceInfo.getPort());
                        startActivity(intent);
                    }
                });

                return view;
            }
        });

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("gtaSaSeExtractorMulticastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AsyncTask<Void, Void, ServiceInfo[]>() {

                    private Snackbar snackbar;

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        ((ArrayAdapter<ServiceInfo>) serviceList.getAdapter()).clear();
                        snackbar = Snackbar.make(MainActivity.this.findViewById(R.id.mainContent), R.string.searching_services, Snackbar.LENGTH_INDEFINITE);
                        snackbar.show();
                        fab.setEnabled(false);
                    }

                    @Override
                    protected ServiceInfo[] doInBackground(Void... voids) {
                        try {
                            if (jmdns == null) {
                                jmdns = JmDNS.create(getDeviceIpAddress(wifi));
                            }
                            return jmdns.list("_gtasa-se._tcp.local.", 6000);
                        } catch (IOException e) {
                            Log.e(TAG, "Unable to start mDNS listener!", e);
                        } catch (ClassCastException e) {
                            Log.e(TAG, "Unable to cast listAdapter!", e);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(ServiceInfo[] list) {
                        if (list != null) {
                            ArrayAdapter<ServiceInfo> adapter = (ArrayAdapter<ServiceInfo>) serviceList.getAdapter();
                            for (int i = 0; i < list.length; i++) {
                                adapter.add(list[i]);
                            }
                            adapter.notifyDataSetChanged();
                        }
                        snackbar.dismiss();
                        fab.setEnabled(true);
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        new AsyncTask<Void, Void, ServiceInfo[]>() {

            private Snackbar snackbar;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                snackbar = Snackbar.make(MainActivity.this.findViewById(R.id.mainContent), R.string.searching_services, Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
            }

            @Override
            protected ServiceInfo[] doInBackground(Void... voids) {
                try {
                    if (jmdns == null) {
                        jmdns = JmDNS.create(getDeviceIpAddress(wifi));
                    }
                    return jmdns.list("_gtasa-se._tcp.local.", 6000);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start mDNS listener!", e);
                } catch (ClassCastException e) {
                    Log.e(TAG, "Unable to cast listAdapter!", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(ServiceInfo[] list) {
                if (list != null) {
                    ArrayAdapter<ServiceInfo> adapter = (ArrayAdapter<ServiceInfo>) serviceList.getAdapter();
                    for (int i = 0; i < list.length; i++) {
                        adapter.add(list[i]);
                    }
                    adapter.notifyDataSetChanged();
                }
                snackbar.dismiss();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (multicastLock != null) {
            multicastLock.release();
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
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private InetAddress getDeviceIpAddress(WifiManager wifi) {
        InetAddress result = null;
        try {
            // default to Android localhost
            result = InetAddress.getByName("10.0.0.2");

            // figure out our wifi address, otherwise bail
            WifiInfo wifiinfo = wifi.getConnectionInfo();
            int intaddr = wifiinfo.getIpAddress();
            byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
                    (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
            result = InetAddress.getByAddress(byteaddr);
        } catch (UnknownHostException ex) {
            Log.w(TAG, "Error resolving local address!", ex);
        }

        return result;
    }
}
