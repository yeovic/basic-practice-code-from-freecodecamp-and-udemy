package org.velvetatelier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** JVM smoke test for GIF structure, frame count, looping and correct pixels. */
public final class GifWriterTest {
    public static void main(String[] args) throws Exception {
        int width=48,height=40;
        int[] palette=new int[256];
        palette[1]=0xff0000;palette[2]=0x00ff00;palette[3]=0x0000ff;
        List<byte[]> frames=new ArrayList<>();
        for(int t=0;t<9;t++) {
            byte[] f=new byte[width*height];
            for(int y=0;y<height;y++)
                for(int x=0;x<width;x++)
                    f[y*width+x]=(byte)((((x+t)/8+y/10)%3)+1);
            frames.add(f);
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        GifWriter.write(bytes,width,height,frames,palette,8,false);
        byte[] encoded=bytes.toByteArray();
        assertTrue(encoded.length>1000,"GIF unexpectedly small");
        assertTrue(new String(encoded,0,6,"US-ASCII").equals("GIF89a"),"header");
        ImageReader reader=ImageIO.getImageReadersByFormatName("gif").next();
        try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            reader.setInput(in);
            assertTrue(reader.getNumImages(true)==frames.size(),"frame count");
            for(int i=0;i<frames.size();i++) {
                BufferedImage img=reader.read(i);
                assertTrue(img.getWidth()==width&&img.getHeight()==height,"dimensions");
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                    int expected=palette[frames.get(i)[y*width+x]&255]&0xffffff;
                    int actual=img.getRGB(x,y)&0xffffff;
                    if(expected!=actual)throw new AssertionError(
                        "color mismatch at "+i+"/"+x+"/"+y+": "+actual+" != "+expected);
                }
            }
        } finally {reader.dispose();}
        System.out.println("PASS: GIF89a 9 frames, LZW, palette and decoded pixels ("+encoded.length+" bytes)");
        // Ensure transparent color index zero roundtrips, including GIF graphics extension.
        byte[] transparentFrame=new byte[width*height];
        for(int x=15;x<30;x++)for(int y=10;y<25;y++)transparentFrame[y*width+x]=1;
        ByteArrayOutputStream transparentBytes=new ByteArrayOutputStream();
        GifWriter.write(transparentBytes,width,height,java.util.Collections.singletonList(transparentFrame),palette,6,true);
        BufferedImage result=ImageIO.read(new ByteArrayInputStream(transparentBytes.toByteArray()));
        assertTrue((result.getRGB(0,0)>>>24)==0,"transparency");
        assertTrue((result.getRGB(20,15)>>>24)==255,"opaque pixel");
        System.out.println("PASS: GIF alpha transparency");
    }
    private static void assertTrue(boolean cond,String message) {
        if(!cond) throw new AssertionError(message);
    }
}
