package org.velvetatelier;

import java.util.Arrays;
import java.util.Random;

/** JVM regression tests for offline resizing and phone-safe size boundaries. */
public final class ImageResamplerTest {
    static int[] run(int[] src,int w,int h,int factor,int mode){
        int outW=w*factor,outH=h*factor;
        int[] result=new int[outW*outH];
        ImageResampler.resize(src,w,h,factor,mode,
            (y,row)->System.arraycopy(row,0,result,y*outW,outW),
            ()->false);
        return result;
    }
    static void check(boolean passed,String message){
        if(!passed)throw new AssertionError(message);
    }
    public static void main(String[] args){
        final int BLUE=0xff297bd2,RED=0xfff23452;
        int[] checker={BLUE,RED,RED,BLUE};
        for(int factor=2;factor<=4;factor++){
            int[] pixel=run(checker,2,2,factor,ImageResampler.PIXEL_PERFECT);
            int width=2*factor;
            for(int y=0;y<2*factor;y++){
                for(int x=0;x<2*factor;x++)
                    check(pixel[y*width+x]==checker[(y/factor)*2+x/factor],
                          "Nearest neighbor color mismatch: "+factor+"x/"+x+"/"+y);
            }
        }
        int[] flat=new int[7*5];
        Arrays.fill(flat,0xff64321a);
        for(int algorithm:new int[]{ImageResampler.FAST_SMOOTH,ImageResampler.HIGH_QUALITY}){
            int[] enlarged=run(flat,7,5,3,algorithm);
            for(int i=0;i<enlarged.length;i++)
                check(colorDistance(enlarged[i],0xff64321a)<=3,
                      "Flat RGB changed on resample: "+algorithm+" @"+i+" "+Integer.toHexString(enlarged[i]));
        }
        int[] alpha={0x00000000,0xffff8000,0xff00ff00,0x00000000};
        for(int algorithm:new int[]{ImageResampler.FAST_SMOOTH,ImageResampler.HIGH_QUALITY}){
            int[] large=run(alpha,2,2,4,algorithm);
            for(int c:large){
                int a=(c>>>24)&255;
                check(a>=0&&a<=255,"Alpha out of bounds");
                if(a==0)check((c&0xffffff)==0,"Dark transparency halo");
            }
        }
        boolean rejected=false;
        try{run(new int[4],2,2,20,ImageResampler.HIGH_QUALITY);}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"Unlimited scale accepted");
        rejected=false;
        try{run(new int[1025*1025],1025,1025,4,ImageResampler.PIXEL_PERFECT);}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"Oversized output accepted");
        boolean cancelled=false;
        try{ImageResampler.resize(new int[16],4,4,4,
            ImageResampler.HIGH_QUALITY,(y,row)->{},
            ()->true);}
        catch(IllegalStateException e){cancelled=true;}
        check(cancelled,"Cancellation failed");
        int[] test=new int[63*47];
        Random rng=new Random(123456L);
        for(int i=0;i<test.length;i++)
            test[i]=0xff000000|rng.nextInt(0x1000000);
        for(int alg=0;alg<=2;alg++){
            int[] r=run(test,63,47,2,alg);
            check(r.length==126*94,"Output pixel count mismatch");
        }
        System.out.println("PASS: nearest-neighbor 2-4x, Lanczos3/bilinear uniform colors, alpha, limits, cancellation, random test");
    }
    static int colorDistance(int a,int b){
        return Math.abs(((a>>16)&255)-((b>>16)&255))
            + Math.abs(((a>>8)&255)-((b>>8)&255))
            + Math.abs((a&255)-(b&255))
            + Math.abs(((a>>24)&255)-((b>>24)&255));
    }
}
