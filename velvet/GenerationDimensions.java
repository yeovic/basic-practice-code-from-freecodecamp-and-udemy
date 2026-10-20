package org.velvetatelier;

/**
 * Preserves SD1.5 guided aspect ratio while bounding Android image inference.
 *
 * Rounds to latent-friendly 64px increments. It never silently swaps portrait
 * for square, but may downscale oversized requests to a phone-safe canvas.
 */
public final class GenerationDimensions {
    private GenerationDimensions(){}
    public static final int MAX_SIDE=768, MAX_PIXELS=512*768;
    public static final class Size {
        public final int width,height;
        public final boolean downscaled;
        Size(int w,int h,boolean reduced){
            width=w;height=h;downscaled=reduced;
        }
    }
    public static Size phoneSafe(int requestedWidth,int requestedHeight){
        if(requestedWidth<1 || requestedHeight<1
            || requestedWidth>8192 || requestedHeight>8192)
            throw new IllegalArgumentException("Invalid image dimensions");
        double factor=Math.min(1.0,Math.min(
            (double)MAX_SIDE/Math.max(requestedWidth,requestedHeight),
            Math.sqrt((double)MAX_PIXELS/((double)requestedWidth*requestedHeight))));
        int w=Math.max(256,((int)Math.floor(requestedWidth*factor)/64)*64);
        int h=Math.max(256,((int)Math.floor(requestedHeight*factor)/64)*64);
        // Min bound 256 could push a narrow image past the budget.
        while(w*h>MAX_PIXELS || w>MAX_SIDE || h>MAX_SIDE){
            if(w>=h && w>256)w-=64;
            else if(h>256)h-=64;
            else throw new IllegalArgumentException("Cannot fit selected aspect ratio");
        }
        return new Size(w,h,w!=requestedWidth||h!=requestedHeight);
    }
}
