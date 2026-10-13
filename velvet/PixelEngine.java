package org.velvetatelier;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/**
 * Consistent-palette pixel stylizer and deterministic sprite animator.
 *
 * This is a frame-animation layer, not a video-diffusion model. Source images
 * can be created in Velvet Atelier's included local SD1.5 generator or imported.
 * All generated frames share dimensions, pivot and palette.
 */
public final class PixelEngine {
    public static final class Artwork {
        public final Bitmap master;
        public final int[] palette; // GIF palette. Index 0 is transparent.
        public final boolean hasTransparency;
        public Artwork(Bitmap image, int[] colors, boolean transparent) {
            master=image;
            palette=colors;
            hasTransparency=transparent;
        }
    }
    private PixelEngine() {}

    public static Artwork stylize(Bitmap source,int logicalSize,int colorCount) {
        if(source==null)throw new IllegalArgumentException("Import an image first");
        int size=Math.max(24,Math.min(192,logicalSize));
        int requested=Math.max(8,Math.min(128,colorCount));
        Bitmap small=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(small);
        canvas.drawColor(Color.TRANSPARENT,android.graphics.PorterDuff.Mode.CLEAR);
        Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
        float ratio=Math.min((float)size/source.getWidth(),(float)size/source.getHeight());
        float w=ratio*source.getWidth(),h=ratio*source.getHeight();
        RectF rect=new RectF((size-w)/2,(size-h)/2,(size+w)/2,(size+h)/2);
        canvas.drawBitmap(source,null,rect,paint);
        int[] pixels=new int[size*size];
        small.getPixels(pixels,0,size,0,0,size,size);
        boolean transparent=false;
        ArrayList<Integer> samples=new ArrayList<>();
        for(int i=0;i<pixels.length;i++) {
            int p=pixels[i];
            if(Color.alpha(p)<60) { transparent=true;continue; }
            // Exclude extreme repeats and retain a reproducible sample.
            if(samples.size()<4096 && ((i%3)==0 || samples.size()<requested))
                samples.add(p);
        }
        if(samples.isEmpty())samples.add(0xff000000);
        int clusters=Math.min(requested-1,samples.size());
        int[] palette=new int[256];
        palette[0]=0x000000;
        for(int i=1;i<=clusters;i++) {
            int index=(int)((i-1L)*samples.size()/clusters);
            palette[i]=samples.get(index)|0xff000000;
        }
        int[] assignments=new int[samples.size()];
        for(int iteration=0;iteration<7;iteration++) {
            long[] red=new long[clusters+1],green=new long[clusters+1],blue=new long[clusters+1];
            int[] count=new int[clusters+1];
            for(int i=0;i<samples.size();i++) {
                int p=samples.get(i);
                int best=nearest(p,palette,clusters);
                assignments[i]=best;
                red[best]+=Color.red(p);green[best]+=Color.green(p);blue[best]+=Color.blue(p);
                count[best]++;
            }
            for(int i=1;i<=clusters;i++)if(count[i]>0) {
                palette[i]=Color.rgb((int)(red[i]/count[i]),(int)(green[i]/count[i]),(int)(blue[i]/count[i]));
            }
        }
        for(int i=0;i<pixels.length;i++) {
            if(Color.alpha(pixels[i])<60) pixels[i]=Color.TRANSPARENT;
            else pixels[i]=palette[nearest(pixels[i],palette,clusters)];
        }
        small.setPixels(pixels,0,size,0,0,size,size);
        return new Artwork(small,palette,transparent);
    }

    private static int nearest(int color,int[] palette,int count) {
        int br=Color.red(color),bg=Color.green(color),bb=Color.blue(color),chosen=1;
        int min=Integer.MAX_VALUE;
        for(int i=1;i<=count;i++) {
            int q=palette[i];
            int r=br-Color.red(q),g=bg-Color.green(q),b=bb-Color.blue(q);
            int distance=3*r*r+5*g*g+2*b*b;
            if(distance<min) { min=distance;chosen=i; }
        }
        return chosen;
    }

    public static ArrayList<Bitmap> animate(Bitmap master,int count,String motion,int intensity) {
        int frames=Math.max(2,Math.min(24,count));
        int strength=Math.max(1,Math.min(6,intensity));
        ArrayList<Bitmap> result=new ArrayList<>(frames);
        int w=master.getWidth(),h=master.getHeight();
        int[] src=new int[w*h];
        master.getPixels(src,0,w,0,0,w,h);
        for(int k=0;k<frames;k++) {
            float phase=(float)(2*Math.PI*k/frames);
            float wave=(float)Math.sin(phase);
            float pulse=(float)Math.cos(phase);
            int[] dst=new int[w*h];
            for(int y=0;y<h;y++) {
                float upper=1f-(float)y/Math.max(1,h-1);
                float shiftX=0f,shiftY=0f,scaleX=1f,scaleY=1f;
                switch(motion) {
                    case "Breathing":
                        scaleX=1f+0.011f*strength*wave*upper;
                        scaleY=1f+0.007f*strength*wave*upper;
                        shiftY=-strength*0.14f*pulse*upper;
                        break;
                    case "Sway":
                        shiftX=wave*strength*upper*0.65f;
                        shiftY=-Math.abs(wave)*strength*0.18f;
                        break;
                    case "Bounce":
                        shiftY=-Math.max(0,wave)*strength*0.9f;
                        break;
                    case "Walk":
                        shiftX=(float)Math.sin(phase)*strength*0.32f;
                        // Approximate opposite leg motion by shifting bottom
                        // quarter in the opposite direction from upper body.
                        if(y>h*0.70f)shiftX+=Math.sin(phase*2)*strength*(y/(float)h-0.7f);
                        shiftY=-Math.abs(wave)*strength*0.25f;
                        break;
                    case "Float":
                        shiftX=wave*strength*0.27f*upper;
                        shiftY=-pulse*strength*0.55f;
                        break;
                    case "Pulse":
                        scaleX=1f+wave*strength*0.012f;
                        scaleY=1f+wave*strength*0.014f;
                        break;
                    default:
                        break;
                }
                for(int x=0;x<w;x++) {
                    int fromX=Math.round((x-w*0.5f-shiftX)/scaleX+w*0.5f);
                    int fromY=Math.round((y-h*0.78f-shiftY)/scaleY+h*0.78f);
                    if(fromX>=0&&fromX<w&&fromY>=0&&fromY<h)
                        dst[y*w+x]=src[fromY*w+fromX];
                }
            }
            Bitmap frame=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            frame.setPixels(dst,0,w,0,0,w,h);
            result.add(frame);
        }
        return result;
    }

    public static byte[] indexes(Bitmap image,int[] palette) {
        int w=image.getWidth(),h=image.getHeight();
        int[] px=new int[w*h];
        image.getPixels(px,0,w,0,0,w,h);
        byte[] result=new byte[px.length];
        int num=0;
        for(int i=1;i<palette.length;i++)if(palette[i]!=0)num=i;
        num=Math.max(1,num);
        for(int i=0;i<px.length;i++) {
            if(Color.alpha(px[i])<60) result[i]=0;
            else result[i]=(byte)nearest(px[i],palette,num);
        }
        return result;
    }

    public static Bitmap sheet(List<Bitmap> frames,int columns) {
        if(frames.isEmpty())throw new IllegalArgumentException("No frames to export");
        int w=frames.get(0).getWidth(),h=frames.get(0).getHeight();
        int cols=Math.max(1,Math.min(frames.size(),columns));
        int rows=(frames.size()+cols-1)/cols;
        Bitmap combined=Bitmap.createBitmap(w*cols,h*rows,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(combined);
        Paint p=new Paint();
        p.setFilterBitmap(false);
        for(int i=0;i<frames.size();i++)
            canvas.drawBitmap(frames.get(i),(i%cols)*w,(i/cols)*h,p);
        return combined;
    }

    public static Bitmap mirror(Bitmap image) {
        int w=image.getWidth(),h=image.getHeight();
        int[] src=new int[w*h],dst=new int[w*h];
        image.getPixels(src,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)dst[y*w+x]=src[y*w+(w-1-x)];
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        result.setPixels(dst,0,w,0,0,w,h);
        return result;
    }
}
