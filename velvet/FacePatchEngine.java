package org.velvetatelier;

/**
 * Pure Java localized image repair geometry and feathered compositing.
 * The patch itself is produced by the existing local SD1.5 img2img model,
 * not by this class. Preserve all original pixels outside the selected box.
 */
public final class FacePatchEngine {
    private FacePatchEngine(){}
    public static final class Region {
        public final int left,top,size;
        public Region(int left,int top,int size){this.left=left;this.top=top;this.size=size;}
        public String toString(){return left+","+top+" size "+size;}
    }
    public static Region region(int width,int height,float centerX,float centerY,float fraction) {
        if(width<32||height<32)throw new IllegalArgumentException("Image too small for local repair");
        if(!Float.isFinite(centerX)||!Float.isFinite(centerY) ||
           !Float.isFinite(fraction))throw new IllegalArgumentException("Invalid repair coordinates");
        float x=Math.max(0,Math.min(1,centerX));
        float y=Math.max(0,Math.min(1,centerY));
        float f=Math.max(0.12f,Math.min(0.8f,fraction));
        int side=Math.max(32,Math.min(Math.min(width,height),Math.round(Math.min(width,height)*f)));
        // Clamp the crop rather than leave black padding at image edges.
        int left=Math.max(0,Math.min(width-side,Math.round(width*x-side*0.5f)));
        int top=Math.max(0,Math.min(height-side,Math.round(height*y-side*0.5f)));
        return new Region(left,top,side);
    }
    public static int[] composite(int[] original,int width,int height,
                                  int[] patch,Region rect,int featherPx,float strength){
        if(original==null||patch==null||rect==null||width<1||height<1||
           (long)width*height!=original.length||
           (long)rect.size*rect.size!=patch.length||
           rect.left<0||rect.top<0||rect.left+rect.size>width||
           rect.top+rect.size>height)
            throw new IllegalArgumentException("Mismatched repair patch");
        int[] output=original.clone();
        int feather=Math.max(1,Math.min(rect.size/2,featherPx));
        float mix=Math.max(0,Math.min(1,strength));
        for(int y=0;y<rect.size;y++){
            for(int x=0;x<rect.size;x++){
                int border=Math.min(Math.min(x,rect.size-1-x),Math.min(y,rect.size-1-y));
                float t=Math.min(1f,Math.max(0f,border/(float)feather));
                float smooth=t*t*(3f-2f*t);
                float alpha=mix*smooth;
                if(alpha<=0f)continue;
                int dest=(rect.top+y)*width+rect.left+x;
                int src=y*rect.size+x;
                output[dest]=mixColor(original[dest],patch[src],alpha);
            }
        }
        return output;
    }
    private static int mixColor(int a,int b,float w){
        float ow=1-w;
        int aa=Math.round(((a>>>24)&255)*ow+((b>>>24)&255)*w);
        int r=Math.round(((a>>>16)&255)*ow+((b>>>16)&255)*w);
        int g=Math.round(((a>>>8)&255)*ow+((b>>>8)&255)*w);
        int blue=Math.round((a&255)*ow+(b&255)*w);
        return (aa<<24)|(r<<16)|(g<<8)|blue;
    }
}
