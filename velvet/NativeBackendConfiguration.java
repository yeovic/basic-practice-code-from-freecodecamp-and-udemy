package org.velvetatelier;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The upstream Local Dream service reads app_prefs.use_img2img when starting
 * its native server, adding --no_img2img when the flag is false. Masked
 * inpainting and multi-pass img2img need the VAE encoder available.
 */
public final class NativeBackendConfiguration {
    private NativeBackendConfiguration(){}

    /**
     * Enable the explicit user-invoked img2img workflow. Returns true only
     * when a live backend must be restarted to pick up the changed flag.
     */
    public static boolean enableImg2Img(Context context) {
        SharedPreferences prefs=context.getSharedPreferences("app_prefs",Context.MODE_PRIVATE);
        if(prefs.getBoolean("use_img2img",true))return false;
        if(!prefs.edit().putBoolean("use_img2img",true).commit())
            throw new IllegalStateException("Could not enable local image-to-image backend");
        return true;
    }

    public static final String ACTION_RESTART =
        "io.github.xororz.localdream.RESTART_BACKEND";
}
