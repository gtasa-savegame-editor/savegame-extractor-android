package io.lerk.gtasase;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;

import io.lerk.gtasase.adapters.SavegameFileAdapter;
import io.lerk.gtasase.tasks.FileSearchTask;
import io.lerk.gtasase.tasks.SavegamesFetchTask;

public class ServiceActivity extends AppCompatActivity {

    public static final String PROTO_VERSION = "1";

    public static final String SERVICE_ADDRESS_KEY = "serviceAddress";
    public static final String SERVICE_PORT_KEY = "servicePort";
    private static final String TAG = ServiceActivity.class.getCanonicalName();
    private static final int SELECT_SAVE_REQUEST_CODE = 42;
    private static final String[] possibleSaves = {"GTASAsf1.b", "GTASAsf2.b", "GTASAsf3.b",
            "GTASAsf4.b", "GTASAsf5.b", "GTASAsf6.b", "GTASAsf7.b", "GTASAsf8.b"};

    private boolean localView = true;
    private ListView savegameListView;
    private ArrayAdapter<File> localFileAdapter;
    private String serviceAddress;
    private Menu menu;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private SwipeRefreshLayout refreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);

        refreshLayout = findViewById(R.id.savegameRefreshLayout);
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

        MainActivity.verifyStoragePermissions(this);

        localFileAdapter = new SavegameFileAdapter(this, R.layout.layout_savegame, serviceAddress, servicePort);
        savegameListView.setAdapter(localFileAdapter);

        updateTitlebarText();

        final boolean permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        if (!permissionGranted) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_needed)
                    .setMessage(R.string.permissions_needed_message)
                    .setNeutralButton(R.string.okay, (dialog, which) ->
                            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE))
                    .create().show();
        }

        String searchMethod = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("searchMethod", getString(R.string.search_method_manual));
        if (searchMethod.equals(getString(R.string.search_method_manual))) {
            Intent searchIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(searchIntent, SELECT_SAVE_REQUEST_CODE);
        } else if (searchMethod.equals(getString(R.string.search_method_command_line))) {
            runFileSearch(getStoragePath(), false);
        } else if (searchMethod.equals(getString(R.string.search_method_file_api))) {
            runFileSearch(getStoragePath(), true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_SAVE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            ArrayList<File> files = new ArrayList<>();

            if (data != null) {
                for (String possibleSave : possibleSaves) {
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(data.getData(), possibleSave);
                    if (uri.getPath() != null) {
                        File savegame = new File(uri.getPath());
                        files.add(savegame);
                    }
                }
            }

            files.forEach(f -> ((ArrayAdapter<File>) savegameListView.getAdapter()).add(f));
            //noinspection unchecked
            ((ArrayAdapter<File>) savegameListView.getAdapter()).notifyDataSetChanged();
            refreshLayout.setRefreshing(false);
        }
    }

    /**
     * This method tries to find the primary storage path by using this app as a reference.
     *
     * @return a path similar to <pre>/storage/emulated/0</pre> or null if nothing was found.
     */
    @Nullable
    private String getStoragePath() {
        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir == null) {
            externalFilesDir = getFilesDir();
        }
        String[] split = externalFilesDir.getAbsolutePath().split(File.separator);
        for (int i = 0; i < split.length; i++) {
            if (split[i].equals("data")) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    sb.append(split[j]).append(File.separator);
                }
                return sb.toString();
            }
        }
        return null;
    }

    private void runFileSearch(String searchDirectory, boolean useFileApi) {
        File directory = new File(searchDirectory);
        if (!directory.exists()) {
            Log.w(TAG, "Directory does not exist: '" + directory.getAbsolutePath() + "'");
        }
        refreshLayout.setRefreshing(true);
        FileSearchTask fileSearchTask = new FileSearchTask(directory, useFileApi, files -> {
            //noinspection unchecked
            files.forEach(f -> ((ArrayAdapter<File>) savegameListView.getAdapter()).add(f));
            //noinspection unchecked
            ((ArrayAdapter<File>) savegameListView.getAdapter()).notifyDataSetChanged();
            refreshLayout.setRefreshing(false);
        });
        fileSearchTask.execute();
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

            new SavegamesFetchTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
