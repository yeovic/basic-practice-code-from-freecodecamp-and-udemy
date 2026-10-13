package org.velvetatelier;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Small dependency-free GIF89a animator with one shared RGB palette.
 *
 * Uses deliberately bounded 9-bit LZW streams. Clear codes after at most
 * 180 literal pixel codes avoid GIF dictionary size transitions. A GIF is
 * slightly larger than dictionary-compressed alternatives, but decoding is
 * deterministic, works on older Android devices, and never flickers palettes.
 */
public final class GifWriter {
    private GifWriter() {}

    public static void write(
            OutputStream output,
            int width,
            int height,
            List<byte[]> frameIndexes,
            int[] palette,
            int fps,
            boolean transparent) throws IOException {
        if (width < 1 || height < 1 || width > 65535 || height > 65535)
            throw new IllegalArgumentException("Invalid GIF dimensions");
        if (frameIndexes == null || frameIndexes.isEmpty())
            throw new IllegalArgumentException("No GIF frames");
        if (palette == null || palette.length > 256)
            throw new IllegalArgumentException("Invalid GIF palette");
        output.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        word(output,width);
        word(output,height);
        output.write(0xF7); // GCT present, 8-bit RGB, 256 entries
        output.write(0);
        output.write(0);
        for (int i=0;i<256;i++) {
            int c = i < palette.length ? palette[i] : 0;
            output.write((c >>> 16) & 255);
            output.write((c >>> 8) & 255);
            output.write(c & 255);
        }
        // Infinite-loop application extension.
        output.write(new byte[] {33, (byte)255, 11});
        output.write("NETSCAPE2.0".getBytes(StandardCharsets.US_ASCII));
        output.write(new byte[] {3, 1, 0, 0, 0});
        int delay = Math.max(2,Math.min(65535,Math.round(100f/Math.max(1,fps))));
        for (byte[] pixels : frameIndexes) {
            if (pixels == null || pixels.length != width*height)
                throw new IllegalArgumentException("GIF frame pixel count mismatch");
            output.write(new byte[] {33, (byte)249, 4, (byte)(transparent ? 9 : 8)});
            word(output,delay);
            output.write(0); // transparent index, iff transparency enabled
            output.write(0);
            output.write(44);
            word(output,0); word(output,0);
            word(output,width); word(output,height);
            output.write(0); // no local color table
            output.write(8); // 8-bit minimum LZW code size
            byte[] bytes=lzw(pixels);
            for (int off=0;off<bytes.length;off+=255) {
                int n=Math.min(255,bytes.length-off);
                output.write(n);
                output.write(bytes,off,n);
            }
            output.write(0); // image data sub-block terminator
        }
        output.write(59); // GIF trailer
        output.flush();
    }

    private static void word(OutputStream out,int n) throws IOException {
        out.write(n & 255);
        out.write((n >>> 8) & 255);
    }

    private static byte[] lzw(byte[] pixels) throws IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        int buffer=0, bits=0, inRun=0;
        // Encode ONLY literal symbols and dictionary resets. A decoder may
        // accumulate ~180 dictionary entries per run, always below 512;
        // therefore every code is exactly 9 bits.
        int total=pixels.length+1+(pixels.length-1)/180+1;
        int[] codes=new int[total];
        int next=0;
        codes[next++]=256;
        for (int i=0;i<pixels.length;i++) {
            if (i!=0 && inRun>=180) {
                codes[next++]=256;
                inRun=0;
            }
            codes[next++]=pixels[i] & 255;
            inRun++;
        }
        codes[next++]=257;
        for (int i=0;i<next;i++) {
            buffer |= codes[i] << bits;
            bits += 9;
            while (bits>=8) {
                out.write(buffer & 255);
                buffer >>>= 8;
                bits -= 8;
            }
        }
        if (bits>0) out.write(buffer & 255);
        return out.toByteArray();
    }
}
