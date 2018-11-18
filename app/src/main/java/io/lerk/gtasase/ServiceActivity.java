package io.lerk.gtasase;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import javax.jmdns.ServiceInfo;

public class ServiceActivity extends Activity {

    public static final String PROTO_VERSION = "1";

    public static final String SERVICE_ADDRESS_KEY = "serviceAddress";
    public static final String SERVICE_PORT_KEY = "servicePort";
    private static final String TAG = ServiceActivity.class.getCanonicalName();

    private String[] serviceAdresses;
    private int servicePort;

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

        ListView savegameListView = findViewById(R.id.savegameListView);
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
        });

        TextView titleView = findViewById(R.id.serverAddress);
        String titleText = serviceAdresses[0] + ":" + servicePort;
        titleView.setText(titleText);

        ArrayList<File> search = FileSearch.search();
        search.forEach(f -> ((ArrayAdapter<File>) savegameListView.getAdapter()).add(f));
        ((ArrayAdapter<File>) savegameListView.getAdapter()).notifyDataSetChanged();
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
                    MultipartUtility multipart = new MultipartUtility(requestUrl + ":" +  servicePort + "/add", "UTF-8");

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
}
