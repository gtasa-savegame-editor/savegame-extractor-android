package io.lerk.gtasase;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import io.lerk.gtasase.tasks.SavegamesFetchTask;

public class ServiceActivity extends AppCompatActivity {

    public static final String PROTO_VERSION = "1";

    public static final String SERVICE_ADDRESS_KEY = "serviceAddress";
    public static final String SERVICE_PORT_KEY = "servicePort";
    private static final String TAG = ServiceActivity.class.getCanonicalName();

    private boolean localView = true;
    private ListView savegameListView;
    private ArrayAdapter<File> localFileAdapter;
    private String serviceAddress;
    private Menu menu;
    private SavegamesFetchTask savegamesFetchTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);

        SwipeRefreshLayout refreshLayout = findViewById(R.id.savegameRefreshLayout);
        refreshLayout.setEnabled(false);
        refreshLayout.setColorSchemeResources(R.color.primaryLightColor, R.color.primaryColor, R.color.primaryDarkColor);

        Intent intent = getIntent();

        ArrayList<String> arrayList = intent.getStringArrayListExtra(SERVICE_ADDRESS_KEY);
        String[] serviceAdresses = new String[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            serviceAdresses[i] = arrayList.get(i);
        }

        if (serviceAdresses.length <= 0) {
            throw new IllegalStateException("No addresses for service");
        }
        int servicePort = intent.getIntExtra(SERVICE_PORT_KEY, 0);
        serviceAddress = serviceAdresses[0] + ":" + servicePort;

        savegameListView = findViewById(R.id.savegameListView);
        Log.i(TAG, "Using address: '" + serviceAddress + "'");

        localFileAdapter = new ArrayAdapter<File>(this, R.layout.layout_savegame) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                File item = getItem(position);
                if (item != null) {
                    if (convertView != null) {
                        return initView(convertView, item);
                    } else {
                        return initView(LayoutInflater.from(ServiceActivity.this).inflate(R.layout.layout_savegame, parent, false), item);
                    }
                }
                return convertView;
            }

            @SuppressLint("StaticFieldLeak")
            // The asyncTask has to (eg. will) finish before the activity does
            private View initView(View view, File item) {
                TextView savegameName = view.findViewById(R.id.savegame_name);
                Button uploadButton = view.findViewById(R.id.upload_button);

                savegameName.setText(item.getName());
                uploadButton.setOnClickListener(v -> new AsyncTask<Void, Void, Throwable>() {

                    private Snackbar snackbar;

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        snackbar = Snackbar.make(ServiceActivity.this.findViewById(R.id.serviceContent), R.string.uploading, Snackbar.LENGTH_INDEFINITE);
                        snackbar.show();
                    }

                    @Override
                    @SuppressLint("WrongThread") // wat
                    protected Throwable doInBackground(Void... voids) {
                        try {
                            String requestUrl = "http://" + Arrays.stream(serviceAdresses).filter(a -> a != null && !a.isEmpty()).findFirst().orElseThrow(IllegalStateException::new);
                            Log.d(TAG, "Using address: '" + requestUrl);
                            MultipartUtility multipart = new MultipartUtility(requestUrl + ":" + servicePort + "/add", "UTF-8");

                            multipart.addHeaderField("User-Agent", "GTASA Savegame Extractor (Android)");
                            multipart.addFormField("version", PROTO_VERSION);

                            multipart.addFilePart("savegame", item);

                            StringBuilder responseBuilder = new StringBuilder();
                            multipart.finish().forEach(responseBuilder::append);
                            Log.d(TAG, "Server Response:\n" + responseBuilder.toString());
                        } catch (Throwable ex) {
                            Log.e(TAG, "Unable to upload savegame!", ex);
                            return ex;
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Throwable ex) {
                        snackbar.dismiss();
                        if (ex == null) {
                            new AlertDialog.Builder(ServiceActivity.this).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                    .setTitle(R.string.upload_success_title)
                                    .setMessage(R.string.upload_success_message).show();
                        } else {
                            new AlertDialog.Builder(ServiceActivity.this).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                    .setTitle(R.string.upload_error_title)
                                    .setMessage(ex.getMessage()).show();
                        }
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));

                return view;
            }
        };
        savegameListView.setAdapter(localFileAdapter);

        updateTitlebarText();

        ArrayList<File> search = FileSearch.search();
        //noinspection unchecked
        search.forEach(f -> ((ArrayAdapter<File>) savegameListView.getAdapter()).add(f));
        //noinspection unchecked
        ((ArrayAdapter<File>) savegameListView.getAdapter()).notifyDataSetChanged();
    }

    private void updateTitlebarText() {
        String titleText = serviceAddress + ((localView) ?
                getString(R.string.view_mode_suffix_local) : getString(R.string.view_mode_suffix_remote));
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(titleText);
        }
        if (menu != null) {
            MenuItem item = menu.findItem(R.id.menu_main_setting);
            if (item != null) {
                item.setIcon(((localView) ? R.drawable.ic_cloud_upload_white_24dp : R.drawable.ic_cloud_download_white_24dp));
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (localView) {
            savegameListView.setAdapter(new ArrayAdapter<File>(this, R.layout.layout_savegame) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    File item = getItem(position);
                    if (item != null) {
                        if (convertView != null) {
                            return initView(convertView, item);
                        } else {
                            return initView(LayoutInflater.from(ServiceActivity.this).inflate(R.layout.layout_savegame, parent, false), item);
                        }
                    }
                    return convertView;
                }

                @SuppressLint("StaticFieldLeak")
                private View initView(View view, File item) {
                    TextView savegameName = view.findViewById(R.id.savegame_name);
                    Button downloadButton = view.findViewById(R.id.upload_button);
                    downloadButton.setText(R.string.download);

                    savegameName.setText(item.getName());
                    downloadButton.setOnClickListener(v -> new AsyncTask<Void, Void, Throwable>() {

                        private Snackbar snackbar;

                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            snackbar = Snackbar.make(ServiceActivity.this.findViewById(R.id.serviceContent), R.string.downloading, Snackbar.LENGTH_INDEFINITE);
                            snackbar.show();
                        }

                        @Override
                        protected Throwable doInBackground(Void... voids) {
                            DownloadManager downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            Uri uri = Uri.parse("http://" + serviceAddress + "/get/" + item.getName());
                            DownloadManager.Request request = new DownloadManager.Request(uri);
                            request.setTitle(item.getName());
                            request.setDescription(getString(R.string.downloading_message));
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.getName());
                            downloadmanager.enqueue(request);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Throwable ex) {
                            snackbar.dismiss();
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));

                    return view;
                }
            });

            savegamesFetchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            savegameListView.setAdapter(localFileAdapter);
        }
        localView = !localView;
        updateTitlebarText();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_service, menu);
        this.menu = menu;
        return true;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public ArrayAdapter<File> getLocalFileAdapter() {
        return localFileAdapter;
    }
}
