package io.lerk.gtasase;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.lerk.gtasase.adapters.ServiceInfoAdapter;
import io.lerk.gtasase.tasks.MDNSTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getCanonicalName();

    private WifiManager.MulticastLock multicastLock;
    private boolean appRunning = true;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,

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
        ServiceInfoAdapter adapter = new ServiceInfoAdapter(this, R.layout.layout_service);
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

        verifyStoragePermissions(this);
    }

    /**
     * Checks if permission to access files is granted.
     *
     * @param context the context
     */
    public static void verifyStoragePermissions(Activity context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
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
            showTutorial();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
            return true;
        }
        return false;
    }

    private void showTutorial() {
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
