package org.velvetatelier;

/** EXIF 1–8 image orientation; pure Java for deterministic host tests. */
public final class ImageOrientationPolicy {
    private ImageOrientationPolicy(){}
    public static final class Affine {
        public final int a,b,c,d;
        public final boolean rotated;
        Affine(int aa,int bb,int cc,int dd,boolean swap){
            a=aa;b=bb;c=cc;d=dd;rotated=swap;
        }
        public int outWidth(int w,int h){return rotated?h:w;}
        public int outHeight(int w,int h){return rotated?w:h;}
        /** Source pixel coordinates to oriented pixel coordinates. */
        public int[] apply(int x,int y,int width,int height){
            if(width<1||height<1||x<0||y<0||x>=width||y>=height)
                throw new IllegalArgumentException("Invalid image coordinate");
            int rawX=a*x+b*y,rawY=c*x+d*y;
            // Signed axes always map onto zero-based oriented bounds.
            int ox=(a<0?width-1:0)+(b<0?height-1:0);
            int oy=(c<0?width-1:0)+(d<0?height-1:0);
            return new int[]{rawX+ox,rawY+oy};
        }
    }
    public static Affine transform(int orientation){
        switch(orientation){
            case 2:return new Affine(-1,0,0,1,false);  // flip horizontal
            case 3:return new Affine(-1,0,0,-1,false); // rotate 180
            case 4:return new Affine(1,0,0,-1,false);  // flip vertical
            case 5:return new Affine(0,1,1,0,true);    // transpose
            case 6:return new Affine(0,-1,1,0,true);   // rotate 90 clockwise
            case 7:return new Affine(0,-1,-1,0,true);  // transverse
            case 8:return new Affine(0,1,-1,0,true);   // rotate 270
            default:return new Affine(1,0,0,1,false);
        }
    }
    public static boolean isNormal(int orientation){return orientation<=1||orientation>8;}
}
