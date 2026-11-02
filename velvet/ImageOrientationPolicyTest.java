package org.velvetatelier;
import java.util.HashSet;
/** Tests all eight EXIF transforms without Android SDK. */
public final class ImageOrientationPolicyTest {
    private static void ok(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    public static void main(String[] args){
        final int width=5,height=3;
        for(int orientation=1;orientation<=8;orientation++){
            ImageOrientationPolicy.Affine t=ImageOrientationPolicy.transform(orientation);
            int ow=t.outWidth(width,height),oh=t.outHeight(width,height);
            ok(ow*oh==width*height,"Orientation changed pixel count");
            HashSet<Integer> visited=new HashSet<>();
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int[] xy=t.apply(x,y,width,height);
                ok(xy[0]>=0 && xy[0]<ow && xy[1]>=0 && xy[1]<oh,
                    "Out-of-bounds EXIF "+orientation);
                ok(visited.add(xy[1]*ow+xy[0]),
                    "Duplicate EXIF pixel mapping "+orientation);
            }
            ok(visited.size()==width*height,"Pixel loss EXIF "+orientation);
        }
        int[] clockwise=ImageOrientationPolicy.transform(6).apply(0,0,width,height);
        ok(clockwise[0]==height-1 && clockwise[1]==0,
            "EXIF 90 CW top-left mapped incorrectly");
        int[] transpose=ImageOrientationPolicy.transform(5).apply(3,1,width,height);
        ok(transpose[0]==1 && transpose[1]==3,"EXIF transpose wrong");
        int[] transverse=ImageOrientationPolicy.transform(7).apply(0,0,width,height);
        ok(transverse[0]==height-1 && transverse[1]==width-1,
            "EXIF transverse wrong");
        ok(ImageOrientationPolicy.isNormal(0)&&ImageOrientationPolicy.isNormal(1),
            "Undefined/normal should not allocate a second bitmap");
        System.out.println("PASS: EXIF 1-8 rotations, flips, bounds and image dimensions");
    }
}