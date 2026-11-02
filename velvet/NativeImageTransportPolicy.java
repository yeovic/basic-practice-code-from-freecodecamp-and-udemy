package org.velvetatelier;

/** Validates untrusted native-local PNG output before allocating a Bitmap. */
public final class NativeImageTransportPolicy {
    private NativeImageTransportPolicy(){}
    public static final String OUTPUT_FORMAT="png";
    private static final byte[] SIGNATURE={
        (byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a
    };
    private static int read32(byte[] a,int i){
        return ((a[i]&255)<<24)|((a[i+1]&255)<<16)|((a[i+2]&255)<<8)|(a[i+3]&255);
    }
    public static void checkPng(byte[] a,int expectedW,int expectedH,
                                int claimedW,int claimedH){
        if(expectedW<16||expectedH<16 || (long)expectedW*expectedH>2_000_000)
            throw new IllegalArgumentException("Unsafe expected image dimensions");
        if(a==null||a.length<33||a.length>12*1024*1024)
            throw new IllegalArgumentException("Unexpected PNG size");
        for(int i=0;i<SIGNATURE.length;i++)
            if(a[i]!=SIGNATURE[i])throw new IllegalArgumentException("PNG signature mismatch");
        if(read32(a,8)!=13 ||a[12]!='I'||a[13]!='H'||a[14]!='D'||a[15]!='R')
            throw new IllegalArgumentException("Invalid PNG IHDR");
        int imageW=read32(a,16),imageH=read32(a,20);
        if(imageW!=expectedW||imageH!=expectedH||
           claimedW!=expectedW||claimedH!=expectedH)
            throw new IllegalArgumentException("Native output dimensions do not match request");
    }
}
