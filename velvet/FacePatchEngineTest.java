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
        System.out.println("PASS: selected face crop bounds, feather continuity and local-only compositor");
    }
}
