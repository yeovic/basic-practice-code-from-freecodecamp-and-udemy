package org.velvetatelier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import java.io.InputStream;

/**
 * Normalize camera/gallery EXIF orientation once during image import.
 * The repair selection, native inpaint and exported bitmap then share the
 * same upright pixel-coordinate system, including mirrored selfies.
 */
public final class ImageOrientationLoader {
    private ImageOrientationLoader(){}
    public static Bitmap apply(Context context,Uri uri,Bitmap decoded){
        if(decoded==null)return null;
        int orientation=1;
        if(Build.VERSION.SDK_INT>=24){
            try(InputStream in=context.getContentResolver().openInputStream(uri)){
                if(in!=null){
                    android.media.ExifInterface exif=new android.media.ExifInterface(in);
                    orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
                }
            }catch(Exception ignored){
                // A provider may omit EXIF; decoded pixels remain unchanged.
                orientation=1;
            }
        }
        if(ImageOrientationPolicy.isNormal(orientation))return decoded;
        ImageOrientationPolicy.Affine a=ImageOrientationPolicy.transform(orientation);
        Matrix matrix=new Matrix();
        matrix.setValues(new float[]{
            a.a,a.b,0f,
            a.c,a.d,0f,
            0f,0f,1f
        });
        Bitmap upright=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),
            decoded.getHeight(),matrix,false);
        if(upright!=decoded)decoded.recycle();
        return upright;
    }
}
