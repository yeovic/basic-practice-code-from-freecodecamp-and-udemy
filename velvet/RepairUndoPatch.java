package org.velvetatelier;

/**
 * Memory-bounded single-step undo of a localized diffusion repair. Stores only
 * the pre-repair region, never a second full-resolution source image.
 */
public final class RepairUndoPatch {
    public final FacePatchEngine.Region region;
    private final int[] originalPixels;
    public RepairUndoPatch(FacePatchEngine.Region region,int[] originalPixels){
        if(region==null||originalPixels==null||region.size<2||
            (long)region.size*region.size!=originalPixels.length)
            throw new IllegalArgumentException("Invalid repair undo patch");
        this.region=region;
        this.originalPixels=originalPixels.clone();
    }
    public int[] restorePixels(){
        return originalPixels.clone();
    }
    public int pixelCount(){return originalPixels.length;}
    public int bytesStored(){return originalPixels.length*4;}
}
