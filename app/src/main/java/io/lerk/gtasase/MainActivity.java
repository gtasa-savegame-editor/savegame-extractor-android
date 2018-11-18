package io.lerk.gtasase;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();

    private WifiManager.MulticastLock multicastLock;
    private JmDNS jmdns;

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
                    TextView serviceName = convertView.findViewById(R.id.service_name);
                    TextView serviceAddress = convertView.findViewById(R.id.service_address);
                    Button connectButton = convertView.findViewById(R.id.connect_button);
                    serviceName.setText(serviceInfo.getName());
                    serviceAddress.setText(serviceInfo.getInet4Addresses()[0].toString());
                    connectButton.setOnClickListener(v ->
                            Toast.makeText(getContext(), "TODO: implement connecting.", LENGTH_LONG).show());
                }
                return convertView;
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AsyncTask<Void, Void, ServiceInfo[]>() {

                    private Snackbar snackbar;

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        snackbar = Snackbar.make(MainActivity.this.findViewById(R.id.mainContent), "Searching...", Snackbar.LENGTH_INDEFINITE);
                        snackbar.show();
                        fab.setEnabled(false);
                    }

                    @Override
                    protected ServiceInfo[] doInBackground(Void... voids) {
                        try {
                            InetAddress addr = InetAddress.getLocalHost();
                            String hostname = InetAddress.getByName(addr.getHostName()).toString();
                            if (jmdns == null) {
                                jmdns = JmDNS.create(addr, hostname);
                            }
                            return jmdns.list("_gtasa-se._tcp.local.", 10000);
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

        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("gtaSaSeExtractorMulticastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        new AsyncTask<Void, Void, ServiceInfo[]>() {

            private Snackbar snackbar;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                snackbar = Snackbar.make(MainActivity.this.findViewById(R.id.mainContent), "Searching...", Snackbar.LENGTH_INDEFINITE);
                snackbar.show();
            }

            @Override
            protected ServiceInfo[] doInBackground(Void... voids) {
                try {
                    InetAddress addr = InetAddress.getLocalHost();
                    String hostname = InetAddress.getByName(addr.getHostName()).toString();
                    if (jmdns == null) {
                        jmdns = JmDNS.create(addr, hostname);
                    }
                    return jmdns.list("_gtasa-se._tcp.local.", 10000);
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
}
