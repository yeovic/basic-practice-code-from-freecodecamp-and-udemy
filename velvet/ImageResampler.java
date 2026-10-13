package org.velvetatelier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure Java and Android-independent image upscaler. Low allocation: keeps a
 * bounded number of source rows in a small cache, never a full-sized
 * floating-point intermediate image. This is mathematical resampling, NOT a
 * super-resolution neural network.
 *
 * Source and target use straight-alpha ARGB. All filtering uses premultiplied
 * colors to prevent dark edge halos around transparent sprites.
 */
public final class ImageResampler {
    public static final int PIXEL_PERFECT=0;
    public static final int FAST_SMOOTH=1;
    public static final int HIGH_QUALITY=2;
    private static final int MAX_AXIS=4096;
    private static final long MAX_OUTPUT_PIXELS=12_000_000L;
    private ImageResampler(){}

    public interface RowSink { void accept(int y,int[] row); }
    public interface AbortCheck { boolean shouldAbort(); }

    public static void resize(int[] input,int sourceWidth,int sourceHeight,
                              int scale,int method,RowSink output,AbortCheck abort){
        if(sourceWidth<1||sourceHeight<1||scale<2||scale>4)
            throw new IllegalArgumentException("Unsupported image size or scale");
        if(method<PIXEL_PERFECT||method>HIGH_QUALITY)
            throw new IllegalArgumentException("Unknown upscaling algorithm");
        long inputPixels=(long)sourceWidth*sourceHeight;
        if(input==null||inputPixels!=input.length)
            throw new IllegalArgumentException("Source pixels do not match dimensions");
        long ow=(long)sourceWidth*scale,oh=(long)sourceHeight*scale;
        if(ow>MAX_AXIS||oh>MAX_AXIS||ow*oh>MAX_OUTPUT_PIXELS)
            throw new IllegalArgumentException(
                "Output would exceed phone-safe 4096px/12MP limit. Choose 2x or a smaller input.");
        if(output==null)throw new IllegalArgumentException("Output sink missing");
        final int outWidth=(int)ow,outHeight=(int)oh;
        int[] resultRow=new int[outWidth];
        if(method==PIXEL_PERFECT){
            for(int y=0;y<outHeight;y++){
                if(abort!=null && abort.shouldAbort())
                    throw new IllegalStateException("Upscale cancelled");
                int sourceRow=(y/scale)*sourceWidth;
                for(int x=0;x<outWidth;x++)
                    resultRow[x]=input[sourceRow+x/scale];
                output.accept(y,resultRow);
            }
            return;
        }
        final int radius=method==FAST_SMOOTH?1:3;
        final AxisPlan xPlan=new AxisPlan(sourceWidth,outWidth,scale,radius);
        final AxisPlan yPlan=new AxisPlan(sourceHeight,outHeight,scale,radius);
        // 12 horizontal rows maximum in RAM. Keyed by original source row,
        // so repeated output rows reuse the same horizontal interpolation.
        final LinkedHashMap<Integer,float[]> cache=new LinkedHashMap<Integer,float[]>(16,0.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer,float[]> item) {
                return size()>12;
            }
        };
        for(int y=0;y<outHeight;y++) {
            if(abort!=null && abort.shouldAbort())
                throw new IllegalStateException("Upscale cancelled");
            int[] ys=yPlan.indices[y];
            float[] wy=yPlan.weights[y];
            float[][] rows=new float[ys.length][];
            for(int j=0;j<ys.length;j++){
                float[] resized=cache.get(ys[j]);
                if(resized==null){
                    resized=resizeHorizontalRow(input,sourceWidth,outWidth,ys[j],xPlan);
                    cache.put(ys[j],resized);
                }
                rows[j]=resized;
            }
            for(int x=0;x<outWidth;x++){
                int p=x*4;
                float a=0,r=0,g=0,b=0;
                for(int j=0;j<rows.length;j++){
                    float w=wy[j];
                    a+=rows[j][p]*w;
                    r+=rows[j][p+1]*w;
                    g+=rows[j][p+2]*w;
                    b+=rows[j][p+3]*w;
                }
                int alpha=toByte(a*255);
                int red=alpha==0?0:toByte(r/Math.max(a,0.000001f));
                int green=alpha==0?0:toByte(g/Math.max(a,0.000001f));
                int blue=alpha==0?0:toByte(b/Math.max(a,0.000001f));
                resultRow[x]=(alpha<<24)|(red<<16)|(green<<8)|blue;
            }
            output.accept(y,resultRow);
        }
    }

    private static float[] resizeHorizontalRow(int[] source,int sw,int outWidth,
                                                 int sourceY,AxisPlan plan){
        float[] row=new float[outWidth*4];
        int start=sourceY*sw;
        for(int x=0;x<outWidth;x++){
            float a=0,r=0,g=0,b=0;
            for(int j=0;j<plan.indices[x].length;j++){
                int c=source[start+plan.indices[x][j]];
                float w=plan.weights[x][j];
                float alpha=((c>>>24)&255)/255f;
                a+=alpha*w;
                r+=((c>>>16)&255)*alpha*w;
                g+=((c>>>8)&255)*alpha*w;
                b+=(c&255)*alpha*w;
            }
            int index=x*4;
            row[index]=a;
            row[index+1]=r/255f;
            row[index+2]=g/255f;
            row[index+3]=b/255f;
        }
        return row;
    }

    private static int toByte(float value){
        if(Float.isNaN(value))return 0;
        return Math.max(0,Math.min(255,Math.round(value)));
    }

    private static final class AxisPlan {
        final int[][] indices;
        final float[][] weights;
        AxisPlan(int src,int dest,int scale,int radius){
            indices=new int[dest][];
            weights=new float[dest][];
            for(int i=0;i<dest;i++){
                double center=((i+0.5d)/scale)-0.5d;
                // 2-tap linear or 6-tap Lanczos3.
                int start=radius==1?(int)Math.floor(center):
                    (int)Math.floor(center)-radius+1;
                int tapCount=radius*2;
                int[] ix=new int[tapCount];float[] w=new float[tapCount];
                float total=0;
                for(int t=0;t<tapCount;t++){
                    int original=start+t;
                    ix[t]=Math.max(0,Math.min(src-1,original));
                    double d=center-original;
                    float wt=radius==1 ? (float)Math.max(0,1-Math.abs(d)):
                        (float)lanczos(d,3);
                    w[t]=wt;total+=wt;
                }
                if(Math.abs(total)<1e-7)throw new IllegalStateException("Bad resampling weights");
                for(int t=0;t<tapCount;t++)w[t]/=total;
                indices[i]=ix;weights[i]=w;
            }
        }
        private static double lanczos(double x,int a){
            x=Math.abs(x);
            if(x<1e-8)return 1.0;
            if(x>=a)return 0.0;
            double piX=Math.PI*x;
            return (Math.sin(piX)/piX)*(Math.sin(piX/a)/(piX/a));
        }
    }
}
