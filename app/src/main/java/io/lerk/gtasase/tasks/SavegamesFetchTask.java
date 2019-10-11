package io.lerk.gtasase.tasks;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import io.lerk.gtasase.R;
import io.lerk.gtasase.ServiceActivity;

public class SavegamesFetchTask extends AsyncTask<Void, Void, ArrayList<Pair<Uri, File>>> {

    private static final String TAG = SavegamesFetchTask.class.getCanonicalName();

    private final ActivityCallback activityCallback;

    public SavegamesFetchTask(ServiceActivity activity) {
        this.activityCallback = generateActivityCallback(activity);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        activityCallback.setLoading(true);
    }

    @Override
    protected ArrayList<Pair<Uri, File>> doInBackground(Void... voids) {
        try {
            URL url = new URL("http://" + activityCallback.getServiceAddress() + "/list");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            Integer timeout = activityCallback.getRequestTimeout();
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setRequestMethod("GET");
            Log.d(TAG, "Fetch available savegames: " + con.getResponseCode());
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            con.disconnect();
            ArrayList<Pair<Uri, File>> res = new ArrayList<>();
            @SuppressWarnings("unchecked") ArrayList<HashMap<String, String>> results = new ObjectMapper().readValue(content.toString(), ArrayList.class);
            results.forEach(m -> {
                String name = m.get("name");
                if (name == null) {
                    name = activityCallback.getGenericSavegameName();
                }
                res.add(new Pair<>(Uri.parse(name), new File(name)));
            });
            return res;
        } catch (IOException e) {
            activityCallback.onFetchErrorToast(e);
            Log.e(TAG, "Unable to fetch available savegames!", e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(ArrayList<Pair<Uri, File>> files) {
        activityCallback.setLoading(false);
        if (files != null) {
            if (files.size() > 0) {
                activityCallback.onFetchSuccessToast();
                ArrayAdapter<Pair<Uri, File>> adapter = activityCallback.getListAdapter();
                files.forEach(adapter::add);
                adapter.notifyDataSetChanged();
            } else {
                activityCallback.onNoFilesDialog();
            }
        } else {
            activityCallback.onErrorDialog();
        }
    }

    private ActivityCallback generateActivityCallback(ServiceActivity activity) {
        return new ActivityCallback() {
            @Override
            public void setLoading(boolean loading) {
                activity.runOnUiThread(() -> {
                    SwipeRefreshLayout refreshLayout = activity.findViewById(R.id.savegameRefreshLayout);
                    refreshLayout.setRefreshing(loading);
                });
            }

            @Override
            public String getServiceAddress() {
                return activity.getServiceAddress();
            }

            @Override
            public void onFetchErrorToast(IOException e) {
                activity.runOnUiThread(() -> {
                    if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("infoToasts", false)) {
                        Toast.makeText(activity, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFetchSuccessToast() {
                activity.runOnUiThread(() -> {
                    if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("infoToasts", false)) {
                        Toast.makeText(activity, R.string.fetching_success_message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onNoFilesDialog() {
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                        .setTitle(R.string.fetching_error_title)
                        .setMessage(R.string.fetching_error_message_no_files).show());
            }

            @Override
            public void onErrorDialog() {
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                        .setTitle(R.string.fetching_error_title)
                        .setMessage(R.string.fetching_error_message_error).show());
            }

            @Override
            public ArrayAdapter<Pair<Uri, File>> getListAdapter() {
                return activity.getLocalFileAdapter();
            }

            @Override
            public String getGenericSavegameName() {
                return activity.getString(R.string.default_savegame_name);
            }

            @Override
            public Integer getRequestTimeout() {
                return PreferenceManager.getDefaultSharedPreferences(
                        activity.getApplicationContext()).getInt("fetchTimeout",
                        Integer.parseInt(activity.getString(R.string.setting_fetch_timeout_default)));
            }
        };
    }

    private interface ActivityCallback {
        void setLoading(boolean loading);

        String getServiceAddress();

        void onFetchErrorToast(IOException e);

        void onFetchSuccessToast();

        void onNoFilesDialog();

        void onErrorDialog();

        ArrayAdapter<Pair<Uri, File>> getListAdapter();

        String getGenericSavegameName();

        Integer getRequestTimeout();
    }

}
