package org.velvetatelier;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import java.io.File;

/**
 * Centralised model directory decision for Velvet Atelier.
 * Removable external application-specific storage is preferred when writable,
 * otherwise fall back to Android's internal app storage. No broad storage permission.
 *
 * Crucially, "external" can mean emulated internal storage on Android. Only a
 * SECONDARY, removable storage root counts as microSD for this method.
 */
public final class ModelStorage {
    private static final String TAG = "VelvetModelStorage";
    private static final long REQUIRED_BYTES = 6L * 1024 * 1024 * 1024;
    private ModelStorage() {}

    public static File modelsDir(Context context) {
        File internal = new File(context.getFilesDir(), "models");
        File[] roots;
        try {
            roots = context.getExternalFilesDirs(null);
        } catch (RuntimeException ex) {
            Log.w(TAG, "Cannot enumerate external file locations", ex);
            return ensure(internal);
        }
        if (roots != null) {
            for (int i = 1; i < roots.length; i++) {
                File root = roots[i];
                if (root == null || !root.exists() || !root.canWrite()) continue;
                try {
                    if (!Environment.isExternalStorageRemovable(root)) continue;
                    if (!Environment.MEDIA_MOUNTED.equals(
                            Environment.getExternalStorageState(root))) continue;
                    File candidate = new File(root, "models");
                    if (candidate.exists() && candidate.isDirectory() && candidate.canWrite()) {
                        Log.i(TAG, "Using existing model folder on removable SD card");
                        return candidate;
                    }
                    long available = new StatFs(root.getAbsolutePath()).getAvailableBytes();
                    if (available < REQUIRED_BYTES) {
                        Log.w(TAG, "SD card free space below model preparation requirement");
                        continue;
                    }
                    if (candidate.mkdirs() || candidate.isDirectory()) {
                        Log.i(TAG, "Using removable microSD for local model weights");
                        return candidate;
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "External storage unavailable, using internal storage", ex);
                }
            }
        }
        Log.i(TAG, "Using internal app storage for models (no writable microSD found)");
        return ensure(internal);
    }

    private static File ensure(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.w(TAG, "Failed to create model directory: " + directory);
        }
        return directory;
    }
}
