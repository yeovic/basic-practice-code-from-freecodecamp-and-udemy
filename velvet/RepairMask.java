package org.velvetatelier;

/**
 * Generates a centered, smoothly feathered grayscale inpaint mask for the
 * native Local Dream SD1.5 `mask` request parameter. White = editable,
 * black = protected. The mask is purposefully smaller than the crop so
 * diffusion does not unnecessarily redraw its edges.
 */
public final class RepairMask {
    private RepairMask(){}

    /** RGBA-packed ARGB_8888 grayscale pixels for a square mask PNG. */
    public static int[] create(int side, int coveragePercent, int featherPercent) {
        if(side<32 || side>2048)
            throw new IllegalArgumentException("Invalid mask dimensions");
        if(coveragePercent<40 || coveragePercent>95)
            throw new IllegalArgumentException("Coverage must be 40–95 percent");
        if(featherPercent<3 || featherPercent>30)
            throw new IllegalArgumentException("Feather must be 3–30 percent");
        int[] pixels=new int[side*side];
        // Radius is relative to half the side. A coverage of 80% means
        // the white mask reaches 80% of the half-width before its fade.
        final float radius=coveragePercent/100f;
        final float fade=featherPercent/100f;
        final float half=side*0.5f;
        for(int y=0;y<side;y++){
            final float dy=((y+0.5f)-half)/half;
            for(int x=0;x<side;x++){
                float dx=((x+0.5f)-half)/half;
                // Elliptical center (the native engine samples gray levels).
                float distance=(float)Math.sqrt(dx*dx + dy*dy);
                float t=(radius-distance)/fade;
                t=Math.max(0f,Math.min(1f,t));
                float smooth=t*t*(3f-2f*t);
                int gray=Math.round(smooth*255f);
                pixels[y*side+x]=0xff000000|(gray<<16)|(gray<<8)|gray;
            }
        }
        return pixels;
    }
}
