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
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public class ServiceActivity extends AppCompatActivity {

    public static final String PROTO_VERSION = "1";

    public static final String SERVICE_ADDRESS_KEY = "serviceAddress";
    public static final String SERVICE_PORT_KEY = "servicePort";
    private static final String TAG = ServiceActivity.class.getCanonicalName();

    private String[] serviceAdresses;
    private int servicePort;

    private boolean localView = true;
    private ListView savegameListView;
    private ArrayAdapter<File> localFileAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);

        Intent intent = getIntent();

        ArrayList<String> arrayList = intent.getStringArrayListExtra(SERVICE_ADDRESS_KEY);
        serviceAdresses = new String[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            serviceAdresses[i] = arrayList.get(i);
        }

        servicePort = intent.getIntExtra(SERVICE_PORT_KEY, 0);

        savegameListView = findViewById(R.id.savegameListView);

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
                        snackbar = Snackbar.make(ServiceActivity.this.findViewById(R.id.serviceContent), "Uploading...", Snackbar.LENGTH_INDEFINITE);
                        snackbar.show();
                    }

                    @Override
                    protected Throwable doInBackground(Void... voids) {
                        try {
                            String requestUrl = "http://";
                            for (int i = 0; i < serviceAdresses.length; i++) {
                                if (serviceAdresses[i] != null && !serviceAdresses[i].isEmpty()) {
                                    requestUrl += serviceAdresses[i];
                                    Log.d(TAG, "Using address: '" + serviceAdresses[i]);
                                    break;
                                }
                            }
                            MultipartUtility multipart = new MultipartUtility(requestUrl + ":" + servicePort + "/add", "UTF-8");

                            multipart.addHeaderField("User-Agent", "GTASA Savegame Extractor (Android)");
                            multipart.addFormField("version", PROTO_VERSION);

                            multipart.addFilePart("savegame", item);

                            StringBuilder resposeBuilder = new StringBuilder();
                            multipart.finish().forEach(resposeBuilder::append);
                            Log.d(TAG, "Server Response:\n" + resposeBuilder.toString());
                        } catch (IOException ex) {
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
        search.forEach(f -> ((ArrayAdapter<File>) savegameListView.getAdapter()).add(f));
        ((ArrayAdapter<File>) savegameListView.getAdapter()).notifyDataSetChanged();
    }

    private void updateTitlebarText() {
        String titleText = serviceAdresses[0] + ":" + servicePort + ((localView) ? " (local)" : " (remote)");
        ActionBar toolbar = getSupportActionBar();
        if (toolbar != null) {
            toolbar.setTitle(titleText);
        }
    }

    @Override
    @SuppressLint("StaticFieldLeak") // the AsyncTask should (eg. will) finish before the activity
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
                // The asyncTask has to (eg. will) finish before the activity does
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
                            snackbar = Snackbar.make(ServiceActivity.this.findViewById(R.id.serviceContent), "Downloading...", Snackbar.LENGTH_INDEFINITE);
                            snackbar.show();
                        }

                        @Override
                        protected Throwable doInBackground(Void... voids) {
                            DownloadManager downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            Uri uri = Uri.parse("http://" + serviceAdresses[0] + ":" + servicePort + "/get/" + item.getName());
                            DownloadManager.Request request = new DownloadManager.Request(uri);
                            request.setTitle(item.getName());
                            request.setDescription("Downloading Savegame...");
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
            new AsyncTask<Void, Void, ArrayList<File>>() {

                private Snackbar snackbar;

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    snackbar = Snackbar.make(ServiceActivity.this.findViewById(R.id.serviceContent), "Fetching Savegames...", Snackbar.LENGTH_INDEFINITE);
                    snackbar.show();
                }

                @Override
                protected ArrayList<File> doInBackground(Void... voids) {
                    try {
                        URL url = new URL("http://" + serviceAdresses[0] + ":" + servicePort + "/list");
                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("GET");
                        Log.d(TAG, "Fetch available savegames: " + con.getResponseCode());
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(con.getInputStream()));
                        String inputLine;
                        StringBuffer content = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        in.close();
                        con.disconnect();
                        ArrayList<File> res = new ArrayList<>();
                        ArrayList<HashMap<String, String>> results = new ObjectMapper().readValue(content.toString(), ArrayList.class);
                        results.forEach(m -> res.add(new File(m.get("name"))));
                        return res;
                    } catch (ProtocolException e) {
                        ServiceActivity.this.runOnUiThread(() -> Toast.makeText(ServiceActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
                        Log.e(TAG, "Unable to fetch available savegames!", e);
                    } catch (MalformedURLException e) {
                        ServiceActivity.this.runOnUiThread(() -> Toast.makeText(ServiceActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
                        Log.e(TAG, "Unable to fetch available savegames!", e);
                    } catch (IOException e) {
                        ServiceActivity.this.runOnUiThread(() -> Toast.makeText(ServiceActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
                        Log.e(TAG, "Unable to fetch available savegames!", e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(ArrayList<File> files) {
                    snackbar.dismiss();
                    if (files != null) {
                        if (files.size() > 0) {
                            Toast.makeText(ServiceActivity.this, R.string.fetching_success_message, Toast.LENGTH_LONG).show();
                            @SuppressWarnings("unchecked") ArrayAdapter<File> adapter = (ArrayAdapter<File>) savegameListView.getAdapter();
                            files.forEach(adapter::add);
                            adapter.notifyDataSetChanged();
                        } else {
                            new AlertDialog.Builder(ServiceActivity.this).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                    .setTitle(R.string.fetching_error_title)
                                    .setMessage(R.string.fetching_error_message_no_files).show();
                        }
                    } else {
                        new AlertDialog.Builder(ServiceActivity.this).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                .setTitle(R.string.fetching_error_title)
                                .setMessage(R.string.fetching_error_message_error).show();
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        return true;
    }
}
