package io.lerk.gtasase.search;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link FileSearch} implementation that uses the "find" command.
 *
 * @author Lukas Fülling (lukas@k40s.net)
 */
public class CommandLineSearch implements FileSearch {
    private static final String TAG = FileSearch.class.getCanonicalName();

    private static CommandLineSearch instance = null;

    private List<String> result = new ArrayList<>();

    private CommandLineSearch() {
    }

    /**
     * Searches for files starting at the given directory.
     * <p>
     * The directory needs to be as close to the <pre>Android/data</pre> folder,
     * because <pre>find</pre> stops being recursive when there is no read access to one folder.
     * Thus a search starting from <pre>/</pre> where the savegame files are in
     * <pre>/storage/emulated/0/**</pre> would stop working at <pre>/storage/emulated</pre>
     * because there is usually no read access to those folders.
     * </p>
     *
     * @param searchDirectory the directory to start the search from
     * @return a list of found savegames
     */
    public static ArrayList<Pair<Uri, File>> execute(File searchDirectory) {
        if (instance == null) {
            instance = new CommandLineSearch();
        }
        return instance.search(searchDirectory);
    }

    /**
     * Run a search for savegames starting inside all readable paths of the given directory.
     *
     * @param searchDirectory the directory to search in
     * @return a list of savegame files
     * @see #execute(File)
     */
    @Override
    public ArrayList<Pair<Uri, File>> search(File searchDirectory) {
        searchInternal(searchDirectory);
        return getFiles();
    }

    /**
     * Method that transforms the content of {@link #result} to Files.
     * Obviously this needs to be run after the actual search.
     *
     * @return an {@link ArrayList} of {@link File}s
     */
    private ArrayList<Pair<Uri, File>> getFiles() {
        ArrayList<Pair<Uri, File>> results = new ArrayList<>();
        result.forEach(s -> {
            if (!s.isEmpty()) {
                File file = new File(s);
                if (file.exists()) {
                    results.add(new Pair<>(Uri.fromFile(file), file));
                } else {
                    Log.d(TAG, "File does not exist: '" + file.getAbsolutePath() + "'");
                }
            }
        });
        return results;
    }

    /**
     * Method that does the actual search.
     *
     * @param directory the starting directory
     */
    private void searchInternal(File directory) {
        try {
            String absolutePath = directory.getAbsolutePath();
            Log.d(TAG, "Searching in '" + absolutePath + "'");

            String searchCommand = "\"/system/bin/find '" + absolutePath +
                    "' -type f -name 'GTASAsf*' 2> /dev/null\"";


            Process searchProcess = new ProcessBuilder("/system/bin/sh", "-c", searchCommand)
                    .directory(directory).start();

            int returnCode = searchProcess.waitFor();
            if (returnCode != 0) {
                Log.d(TAG, "Execution of 'find' returned " + returnCode + "!");
            }
            InputStreamReader reader = new InputStreamReader(searchProcess.getInputStream(), Charset.forName("UTF-8"));
            int data = reader.read();
            StringBuilder output = new StringBuilder();
            while (data != -1) {
                output.append((char) data);
                data = reader.read();
            }
            reader.close();
            String resString = result.toString();
            String[] resSplit = resString.split("\n");
            result = new ArrayList<>(Arrays.asList(resSplit));
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error running search!", e);
        }

    }
}
