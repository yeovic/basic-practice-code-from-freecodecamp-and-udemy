package org.velvetatelier;
public final class NativeImageTransportPolicyTest {
    private static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    private static void fail(Runnable action,String message){
        boolean caught=false;
        try{action.run();}catch(IllegalArgumentException ex){caught=true;}
        ok(caught,message);
    }
    private static byte[] header(int w,int h){
        byte[] p=new byte[33];
        byte[] signature={(byte)0x89,80,78,71,13,10,26,10};
        System.arraycopy(signature,0,p,0,8);
        p[11]=13;p[12]=73;p[13]=72;p[14]=68;p[15]=82;
        for(int k=0;k<4;k++){p[16+k]=(byte)(w>>>(24-k*8));p[20+k]=(byte)(h>>>(24-k*8));}
        return p;
    }
    public static void main(String[] ignored){
        byte[] png=header(512,768);
        NativeImageTransportPolicy.checkPng(png,512,768,512,768);
        fail(()->NativeImageTransportPolicy.checkPng(png,512,512,512,768),
            "Wrong requested shape accepted");
        fail(()->NativeImageTransportPolicy.checkPng(png,512,768,512,512),
            "Native metadata mismatch accepted");
        final byte[] badSignature=png.clone();badSignature[0]=0;
        fail(()->NativeImageTransportPolicy.checkPng(badSignature,512,768,512,768),
            "Corrupt signature accepted");
        final byte[] oversized=png.clone();oversized[16]=1;
        fail(()->NativeImageTransportPolicy.checkPng(oversized,512,768,512,768),
            "PNG IHDR dimension mismatch accepted");
        fail(()->NativeImageTransportPolicy.checkPng(new byte[10],512,768,512,768),
            "Tiny PNG accepted");
        fail(()->NativeImageTransportPolicy.checkPng(png,4096,4096,4096,4096),
            "Oversized output passed budget");
        System.out.println("PASS: lossless native PNG contract and bounds checks");
    }
}