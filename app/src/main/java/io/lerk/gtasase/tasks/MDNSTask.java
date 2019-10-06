package io.lerk.gtasase.tasks;


import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import io.lerk.gtasase.MainActivity;
import io.lerk.gtasase.R;

public class MDNSTask extends AsyncTask<Void, Void, Void> {

    private static String TAG = MDNSTask.class.getCanonicalName();

    private final ArrayAdapter<ServiceInfo> serviceListAdapter;
    private final InetAddress ip;
    private final ActivityCallback callback;

    public MDNSTask(MainActivity activity, WifiManager wifi, ListView serviceList) {
        this.ip = activity.getDeviceIpAddress(wifi);
        this.callback = buildCallback(activity);

        //noinspection unchecked
        this.serviceListAdapter = (ArrayAdapter<ServiceInfo>) serviceList.getAdapter();
    }

    private ActivityCallback buildCallback(MainActivity activity) {
        return new ActivityCallback() {
            @Override
            public boolean appRunning() {
                return activity.isAppRunning();
            }

            @Override
            public void setLoading(boolean loading) {
                activity.runOnUiThread(() -> {
                    SwipeRefreshLayout swipeRefreshLayout = activity.findViewById(R.id.servicesRefreshLayout);
                    swipeRefreshLayout.setRefreshing(loading);
                });
            }

            @Override
            public void onMDNSAdded(ServiceEvent event) {
                activity.runOnUiThread(() -> {
                    if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("infoToasts", false)) {
                        Toast.makeText(activity.getApplicationContext(), R.string.service_found, Toast.LENGTH_SHORT).show();
                    }
                    serviceListAdapter.add(event.getInfo());
                });
            }

            @Override
            public void onMDNSRemoved(ServiceEvent event) {
                activity.runOnUiThread(() -> {
                    if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("infoToasts", false)) {
                        Toast.makeText(activity.getApplicationContext(), R.string.service_removed, Toast.LENGTH_SHORT).show();
                    }
                    for (int i = 0; i < serviceListAdapter.getCount(); i++) {
                        ServiceInfo item = serviceListAdapter.getItem(i);
                        if (item != null && (item.getName().equals(event.getName()) && item.getPort() == event.getInfo().getPort())) {
                            serviceListAdapter.remove(item);
                        }
                    }
                });
            }

            @Override
            public void onMDNSResolved(ServiceEvent event) {
                activity.runOnUiThread(() -> {
                    if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("infoToasts", false)) {
                        Toast.makeText(activity.getApplicationContext(), R.string.service_resolved, Toast.LENGTH_SHORT).show();
                    }
                    for (int i = 0; i < serviceListAdapter.getCount(); i++) {
                        ServiceInfo item = serviceListAdapter.getItem(i);
                        if (item != null && (item.getName().equals(event.getName()) || item.getPort() == event.getInfo().getPort())) {
                            serviceListAdapter.remove(item);
                            serviceListAdapter.add(event.getInfo());
                        }
                    }
                });
            }

            @Override
            public void onMDNSError(IOException e) {
                activity.onMDNSError(e);
            }
        };
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            JmDNS jmdns = JmDNS.create(ip, "gtaSaSavegameExtractor");
            jmdns.addServiceListener("_gtasa-se._tcp.local.", new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    callback.onMDNSAdded(event);
                    Log.i(TAG, "Found: '" + event.getType() + "', name: '" + event.getName() + "', port: " + event.getInfo().getPort());
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    callback.onMDNSRemoved(event);
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    callback.onMDNSResolved(event);
                }
            });
            while (callback.appRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "mDNS Thread interrupted!");
                }
                callback.setLoading(true);
                ServiceInfo[] list = jmdns.list("_gtasa-se._tcp.local.", 100);
                Log.i(TAG, "mDNS Thread running. " + list.length + " services found.");
                for (int i = 0; i < list.length; i++) {
                    ServiceInfo serviceInfo = list[i];
                    Log.d(TAG, "Services[" + i + "] " + ((serviceInfo.hasData()) ? "❗️" : "❓") + ", ip: '" + ((serviceInfo.getHostAddresses() != null && serviceInfo.getHostAddresses().length > 0) ? serviceInfo.getHostAddresses()[0] : "NA") + "', port: " + serviceInfo.getPort());
                }
                callback.setLoading(false);
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to create mDNS!", e);
            callback.onMDNSError(e);
        }
        return null;
    }

    private interface ActivityCallback {
        boolean appRunning();

        void setLoading(boolean loading);

        void onMDNSAdded(ServiceEvent event);

        void onMDNSRemoved(ServiceEvent event);

        void onMDNSResolved(ServiceEvent event);

        void onMDNSError(IOException e);
    }
}

