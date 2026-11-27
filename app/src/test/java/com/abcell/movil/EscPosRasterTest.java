package com.abcell.movil;

import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class EscPosRasterTest {
    private byte[] output(int width,int height,int pixel,boolean cut) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        EscPosRaster.write(out,width,height,(y,row)->Arrays.fill(row,pixel),cut,null);return out.toByteArray();
    }
    @Test public void correctHeaderAndBlackBits() throws Exception {
        byte[] bytes=output(8,1,0xff000000,false);
        assertArrayEquals(new byte[]{27,64,27,97,1,29,118,48,0,1,0,1,0,(byte)255,27,100,4},bytes);
    }
    @Test public void whiteAndTransparentPixelsStayBlank() throws Exception {
        assertEquals(0,output(8,1,0xffffffff,false)[13]);assertEquals(0,output(8,1,0x00000000,false)[13]);
    }
    @Test public void packsMostSignificantBitFirst() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        EscPosRaster.write(out,8,1,(y,row)->{Arrays.fill(row,0xffffffff);row[0]=0xff000000;row[7]=0xff000000;},false,null);
        assertEquals(0x81,out.toByteArray()[13]&255);
    }
    @Test public void bandsHaveNoExtraFeedAndLastPartialBandIsNotLost() throws Exception {
        byte[] b=output(384,65,0xff000000,false);
        assertEquals(5+8+48*64+8+48+3,b.length);
        int second=5+8+48*64;
        assertEquals(29,b[second]);assertEquals(48,b[second+4]);assertEquals(1,b[second+6]);
    }
    @Test public void bothPaperProfilesUseCorrectByteWidths() throws Exception {
        assertEquals(48,output(384,1,0xffffffff,false)[9]);assertEquals(72,output(576,1,0xffffffff,false)[9]);assertEquals(64,output(512,1,0xffffffff,false)[9]);
        assertEquals(58,PaperProfile.from("58").millimeters);assertEquals(80,PaperProfile.from("80").millimeters);assertEquals(512,PaperProfile.from("80-512").dots);
    }
    @Test public void cutIsExplicitAndOptional() throws Exception {
        byte[] b=output(8,1,0xff000000,true);assertArrayEquals(new byte[]{29,86,0},Arrays.copyOfRange(b,b.length-3,b.length));
        assertEquals(output(8,1,0xff000000,false).length+3,b.length);
    }
    @Test public void ioFailureStopsBeforeFeedOrCut() {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertThrows(IOException.class,()->EscPosRaster.write(out,8,128,(y,row)->Arrays.fill(row,0),true,rows->{throw new IOException("disconnected");}));
        assertEquals(5+8+64,out.size());
    }
    @Test public void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->output(383,1,0,false));assertThrows(IllegalArgumentException.class,()->output(384,0,0,false));
    }
}
