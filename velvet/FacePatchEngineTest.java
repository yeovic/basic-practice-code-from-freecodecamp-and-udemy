package org.velvetatelier;

public final class FacePatchEngineTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] ignored){
        FacePatchEngine.Region center=FacePatchEngine.region(512,768,.5f,.5f,.35f);
        check(center.size>=100&&center.size<=200,"Wrong crop size");
        for(float x:new float[]{0,0.05f,0.5f,0.97f,1f})
            for(float y:new float[]{0,0.12f,0.75f,1f}){
                FacePatchEngine.Region r=FacePatchEngine.region(640,480,x,y,.45f);
                check(r.left>=0&&r.left+r.size<=640,"Crop X out of bounds");
                check(r.top>=0&&r.top+r.size<=480,"Crop Y out of bounds");
            }
        int w=80,h=64;int[] image=new int[w*h];
        java.util.Arrays.fill(image,0xff202020);
        FacePatchEngine.Region rect=FacePatchEngine.region(w,h,.5f,.5f,.5f);
        int[] patch=new int[rect.size*rect.size];java.util.Arrays.fill(patch,0xffffffff);
        int[] output=FacePatchEngine.composite(image,w,h,patch,rect,6,.9f);
        check(output[0]==0xff202020,"Changed outside crop");
        check(output[(rect.top+rect.size/2)*w+rect.left+rect.size/2]!=0xff202020,
            "No actual repair at crop center");
        check(output[rect.top*w+rect.left]==0xff202020,"Unexpected hard seam on crop boundary");
        int[] disabled=FacePatchEngine.composite(image,w,h,patch,rect,6,0f);
        check(java.util.Arrays.equals(disabled,image),"Zero-strength blend changed input");
        check(image[(rect.top+rect.size/2)*w+rect.left+rect.size/2]==0xff202020,
            "Compositing mutated source");
        for(int target=0;target<RepairPromptTemplates.TARGETS.length;target++){
            for(int size=0;size<RepairPromptTemplates.ANATOMY_SIZES.length;size++){
                String pos=RepairPromptTemplates.positive(target,size);
                String neg=RepairPromptTemplates.negative(target,size);
                check(!pos.trim().isEmpty()&&!neg.trim().isEmpty(),"Empty repair guidance");
                check(!pos.contains("null")&&!neg.contains("null"),"Malformed repair prompt");
            }
        }
        check(RepairPromptTemplates.negative(3,1).contains("huge penis"),
            "Small anatomy repair missing oversized exclusions");
        check(!RepairPromptTemplates.negative(3,2).contains("huge penis"),
            "Large size preference contradicted by negative prompt");
        System.out.println("PASS: face/hand/body/anatomy repair prompts and size-aware negatives");
        int[] originalCrop=new int[rect.size*rect.size];
        java.util.Arrays.fill(originalCrop,0xff202020);
        int[] cropOnly=FacePatchEngine.compositePatch(originalCrop,patch,rect.size,6,.9f);
        for(int py=0;py<rect.size;py++){
            for(int px=0;px<rect.size;px++){
                int oldPixel=output[(rect.top+py)*w+rect.left+px];
                int cropPixel=cropOnly[py*rect.size+px];
                check(oldPixel==cropPixel,"Cropped compositor differs from old full-size blend");
            }
        }
        check(cropOnly[0]==originalCrop[0],"Crop feather did not protect edge");
        System.out.println("PASS: cropped-only blending matches previous full-image composite");
        System.out.println("PASS: selected face crop bounds, feather continuity and local-only compositor");
    }
}
