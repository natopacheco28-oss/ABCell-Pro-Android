package com.abcell.movil;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit, cancellable SPP connection per job. Caller must hold BLUETOOTH_CONNECT.
 * Timeouts close the socket to unblock connect/write; no reflection or blind automatic retries.
 */
public final class BluetoothPrintJob {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile BluetoothSocket socket;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicBoolean timedOut=new AtomicBoolean();
    public interface Progress { void update(int rows,int total); }
    @SuppressLint("MissingPermission") // Checked by BluetoothPrinterActivity before starting the job.
    public void send(BluetoothDevice device,Bitmap bitmap,boolean insecure,boolean cut,Progress progress) throws IOException {
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        try {
            if(cancelled.get())throw new IOException("Cancelado");
            BluetoothSocket connection=insecure?device.createInsecureRfcommSocketToServiceRecord(SPP):device.createRfcommSocketToServiceRecord(SPP);
            socket=connection;
            if(cancelled.get())throw new IOException("Cancelado");
            ScheduledFuture<?> connectTimeout=timer.schedule(()->{timedOut.set(true);closeSocket();},20,TimeUnit.SECONDS);
            connection.connect();connectTimeout.cancel(false);
            if(cancelled.get() || timedOut.get())throw new IOException("Conexión interrumpida");
            timer.schedule(()->{timedOut.set(true);closeSocket();},120,TimeUnit.SECONDS);
            EscPosRaster.write(connection.getOutputStream(),bitmap.getWidth(),bitmap.getHeight(),
                    (y,row)->bitmap.getPixels(row,0,bitmap.getWidth(),0,y,bitmap.getWidth(),1),cut,
                    rows->{
                        if(cancelled.get())throw new IOException("Cancelado");
                        progress.update(rows,bitmap.getHeight());
                        try{Thread.sleep(30);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Cancelado",e);}
                    });
            if(cancelled.get() || timedOut.get())throw new IOException("Envío interrumpido");
            // Give small printer buffers time to drain before closing RFCOMM.
            try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Cancelado",e);}
            if(cancelled.get() || timedOut.get())throw new IOException("Envío interrumpido");
        } catch(IOException e) {
            if(cancelled.get())throw new IOException("Impresión cancelada. La nota podría haber salido parcialmente; revisa el papel antes de reintentar.",e);
            if(timedOut.get())throw new IOException("La impresora no respondió a tiempo. Revisa alimentación, alcance y papel. Comprueba si la nota salió parcialmente antes de reintentar.",e);
            throw new IOException("No se pudo enviar la nota. Comprueba que la impresora esté vinculada, encendida, libre y admita Bluetooth clásico SPP + ESC/POS raster. Revisa si salió parcialmente antes de repetir. Puedes probar la conexión alternativa en los ajustes.",e);
        } finally {timer.shutdownNow();closeSocket();}
    }
    public void cancel(){cancelled.set(true);closeSocket();}
    private void closeSocket(){BluetoothSocket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
