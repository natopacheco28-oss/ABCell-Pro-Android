package com.abcell.movil;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;

/** ESC/POS GS v 0 raster bands. No printer font/codepage dependency; all accents and borders are pixels. */
public final class EscPosRaster {
    public interface PixelRows { void read(int y,int[] argb); }
    public interface Checkpoint { void afterBand(int rowsSent) throws IOException; }
    private static final int[][] BAYER={{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}};
    public static final int BAND_ROWS=64;
    private EscPosRaster() {}
    public static void write(OutputStream out,int width,int height,PixelRows source,boolean cut,Checkpoint checkpoint) throws IOException {
        if(width<=0 || width>832 || width%8!=0 || height<=0 || height>16000)throw new IllegalArgumentException("Dimensiones de impresión no válidas");
        int bytes=width/8;int[] pixels=new int[width];
        interrupted();out.write(new byte[]{27,64,27,97,1}); // initialize, center
        for(int start=0;start<height;start+=BAND_ROWS) {
            interrupted();int rows=Math.min(BAND_ROWS,height-start);byte[] band=new byte[bytes*rows];
            for(int r=0;r<rows;r++) {
                source.read(start+r,pixels);
                for(int x=0;x<width;x++) {
                    int p=pixels[x],a=p>>>24;
                    int l=(77*((p>>16)&255)+150*((p>>8)&255)+29*(p&255))>>8;
                    l=(l*a+255*(255-a))/255; // transparent logos print white, not black
                    int threshold=BAYER[(start+r)&3][x&3]*16+8;
                    if(l<threshold)band[r*bytes+x/8]|=(byte)(0x80>>(x&7));
                }
            }
            out.write(new byte[]{29,118,48,0,(byte)(bytes&255),(byte)(bytes>>8),(byte)(rows&255),(byte)(rows>>8)});
            out.write(band);out.flush();if(checkpoint!=null)checkpoint.afterBand(start+rows);
        }
        interrupted();out.write(new byte[]{27,100,4}); // feed after the complete image, never between bands
        if(cut)out.write(new byte[]{29,86,0});
        out.flush();
    }
    private static void interrupted() throws InterruptedIOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Impresión cancelada");
    }
}
