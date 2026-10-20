package org.velvetatelier;

public final class GenerationDimensionsTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        GenerationDimensions.Size portrait=GenerationDimensions.phoneSafe(512,768);
        check(portrait.width==512&&portrait.height==768&&!portrait.downscaled,
            "Portrait canvas lost");
        GenerationDimensions.Size landscape=GenerationDimensions.phoneSafe(768,512);
        check(landscape.width==768&&landscape.height==512,
            "Landscape canvas lost");
        GenerationDimensions.Size draft=GenerationDimensions.phoneSafe(384,384);
        check(draft.width==384&&draft.height==384,
            "Low-res draft canvas lost");
        GenerationDimensions.Size large=GenerationDimensions.phoneSafe(1024,1536);
        check(large.height>large.width,"Oversized portrait collapsed into square");
        check(large.width*large.height<=GenerationDimensions.MAX_PIXELS,
            "Unsafe memory budget accepted");
        check(large.downscaled,"Oversized input not marked as downscaled");
        GenerationDimensions.Size wide=GenerationDimensions.phoneSafe(1536,1024);
        check(wide.width>wide.height,"Oversized landscape collapsed");
        check(wide.width%64==0&&wide.height%64==0,"Latent alignment violated");
        boolean bad=false;
        try{GenerationDimensions.phoneSafe(0,768);}
        catch(IllegalArgumentException ex){bad=true;}
        check(bad,"Invalid zero width accepted");
        System.out.println("PASS: portrait, landscape, draft, downscale and 64px alignment");
    }
}