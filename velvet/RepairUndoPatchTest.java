package org.velvetatelier;

import java.util.Arrays;

public final class RepairUndoPatchTest {
    private static void ok(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }
    public static void main(String[] args){
        int size=12;
        int[] initial=new int[size*size];
        Arrays.fill(initial,0xff44aabb);
        FacePatchEngine.Region region=new FacePatchEngine.Region(40,20,size);
        RepairUndoPatch undo=new RepairUndoPatch(region,initial);
        Arrays.fill(initial,0xff102030);
        int[] restored=undo.restorePixels();
        ok(restored[0]==0xff44aabb,"Undo must own immutable original pixel data");
        Arrays.fill(restored,0xff112233);
        ok(undo.restorePixels()[0]==0xff44aabb,"Undo accessor leaked mutable storage");
        ok(undo.bytesStored()==size*size*4,"Undo size mismatch");
        boolean rejected=false;
        try{new RepairUndoPatch(region,new int[5]);}
        catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"Invalid region pixels accepted");
        System.out.println("PASS: crop-only undo restores original pixels, owns data, and checks shape");
    }
}
