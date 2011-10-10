/*
PNGFunnel, copyright (C) 2008 Robert Keller, robin@waste-of-time-and-money.com

This file is part of PNGFunnel.

PNGFunnel is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

PNGFunnel is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PNGFunnel.  If not, see <http://www.gnu.org/licenses/>.
*/

package PNGFunnel;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

/**
 * This class handles the conversion from normal file to PNG. It writes a file
 * that uses 8-bit grayscale and no filters unless a keyfile is specified, in
 * which case it writes 8-but RGB with no filters. If the file to be converted
 * is over 30 MB, it creates a temp file called SizerTemp to avoid an
 * OutOfMemoryError.
 * @author Robert Keller, robin@waste-of-time-and-money.com
 *
 */
public class ConverterThread extends Thread {
    
    //Fields
    final int width, height;
    final JLabel status;
    final JProgressBar progress;
    
    //Defaults
    final int BUFFER_SIZE = 32768;
    
    public ConverterThread(long length, long width, JLabel status, JProgressBar progress) {
        this.width = (int) length;
        this.height = (int) width;
        this.status = status;
        this.progress = progress;
    }

    public void run() {
        try {
        /* Check if a key file is being used */
        boolean usingkey = PNGFunnel.keybox.isSelected();
        if (usingkey && PNGFunnel.keypath.getText().equals("")) {
            status.setText("ERROR: No keyfile selected.");
            return;
        }
        boolean usingpalette = usingkey && PNGFunnel.palettebox.isSelected();
        
        status.setText("Preparing...");
        progress.setIndeterminate(true);
            
        /* Get the save path from the user */
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Where would you like to save the image?");
        jfc.setFileFilter(new PNGFilter());
        File oldposition = new File(PNGFunnel.path.getText());
        if (oldposition.exists()) jfc.setSelectedFile(oldposition);
        int ret = jfc.showSaveDialog(null);
        if (ret != JFileChooser.APPROVE_OPTION) return;
        String outpath = jfc.getSelectedFile().getPath();
        if (!outpath.toLowerCase().endsWith(".png")) outpath += ".png";
        
        FileOutputStream fout = new FileOutputStream(outpath);
        InputStream in = new FileInputStream(PNGFunnel.path.getText());
        
        DataOutputStream out = new DataOutputStream(fout);
        
        byte[] PNGHeader = {-119,'P','N','G',13,10,26,10};
        out.write(PNGHeader);
        
        /* Write IHDR Chunk */
        status.setText("Writing PNG header...");
        Chunk IHDR = new Chunk((int)0x49484452); //"IHDR"
        IHDR.writeInt(width);   //Image width
        IHDR.writeInt(height);  //Image Height
        IHDR.writeByte(8);      //Bit depth
        if (usingpalette)       //Color type; 3 = indexed, 2 = RGB, 0 = gray
            IHDR.writeByte(3);
        else if (usingkey) 
            IHDR.writeByte(2);
        else
            IHDR.writeByte(0);
        IHDR.writeByte(0);      //Compression method
        IHDR.writeByte(0);      //Filter method
        IHDR.writeByte(0);      //Interlace method
        IHDR.writeTo(out);
        byte[] buf;
        
        /* Write the PNGFunnel-Specific padding chunk. */
        //This chunk must be first after IHDR. Since it is unsafe-to-copy, no programs should write above it.
        status.setText("Storing padding information...");
        Chunk fnNL = new Chunk((int)0x666E4E4C); //"fnNL"
        fnNL.writeInt(PNGFunnel.padded);
        fnNL.writeTo(out);
        
        /* Write a PLTE chunk if we're encoding the keyfile */
        if (usingpalette) {
            status.setText("Encoding keyfile...");
            Chunk PLTE = new Chunk((int)0x504C5445); //"PLTE"
            for (int i = 0; i < 256; i++)
                PLTE.write(Keyreader.colors[i]);
            PLTE.writeTo(out);
            usingkey = false;
        }
        
        progress.setIndeterminate(false);
        progress.setMinimum(0);
        
        /* Compress file first if it's larger than 30 MB */
        if (PNGFunnel.fsize > 30000000) {
            status.setText("Writing temp file...");
            progress.setMaximum(height);
            progress.setValue(0);
            FileOutputStream tempout = new FileOutputStream("SizerTemp");
            DeflaterOutputStream dout = new DeflaterOutputStream(tempout ,new Deflater(Deflater.BEST_COMPRESSION));
            buf = new byte[width];
            while (true) {
                int bytesread = in.read(buf);
                if (bytesread == -1) break;
                dout.write(0);  //The filter byte. 0 is none.
                if (!usingkey) {
                    dout.write(buf, 0, bytesread);
                }else {
                    byte[] towrite = new byte[bytesread*3];
                    int linepos = 0;
                    for (int i = 0; i < towrite.length; i+=3) {
                        towrite[i] = Keyreader.colors[buf[linepos] & 0xFF][0];
                        towrite[i+1] = Keyreader.colors[buf[linepos] & 0xFF][1];
                        towrite[i+2] = Keyreader.colors[buf[linepos] & 0xFF][2];
                        linepos++;
                    }
                    dout.write(towrite,0,towrite.length);
                }
                if (bytesread != width) { //This line needs padding (only happens on last line)
                    while (bytesread < width) {
                        dout.write(0); //Pad with all 0's
                        bytesread++;
                    }
                }
                progress.setValue(progress.getValue()+1);
            }
            //Free up all the memory we just used
            dout.finish();
            tempout.flush();
            tempout.close();
            dout = null;
            tempout = null;
            buf = null;
            System.gc();
            status.setText("Writing PNG data from temp file...");

            // Now write IDAT Chunks using temp file
            progress.setValue(0);
            progress.setMaximum((int)PNGFunnel.fsize / BUFFER_SIZE);
            InputStream tin;
            if (PNGFunnel.fsize > 30000000) tin = new FileInputStream("SizerTemp");
            else tin = in;
            buf = new byte[BUFFER_SIZE];
            for (;;) { // Writes a chunk each time the loop executes
                int bytesread = tin.read(buf);
                if (bytesread == -1) break;
                Chunk IDAT = new Chunk((int)0x49444154); //"IDAT"
                IDAT.write(buf,0,bytesread);
                IDAT.writeTo(out);
                
            }
        }else {
            status.setText("Writing PNG data...");
            progress.setMaximum(height);
            progress.setValue(0);
            /* Write single IDAT Chunk for smaller files */
            Chunk IDAT = new Chunk((int)0x49444154);  //"IDAT"
            DeflaterOutputStream def = new DeflaterOutputStream(IDAT, new Deflater(Deflater.BEST_COMPRESSION),BUFFER_SIZE);
            byte[] thisline = new byte[width];
            for (int linenum = 0; linenum < height; linenum++) {
                def.write(0);  //The filter byte. 0 is none.
                int bytesread = in.read(thisline);
                if (!usingkey) {
                    def.write(thisline, 0, bytesread);
                    if (bytesread != width) { //This line needs padding (only happens on last line)
                        while (bytesread < width) {
                            def.write(0); //Pad with all 0's
                            bytesread++;
                        }
                    }
                }else {
                    byte[] towrite = new byte[bytesread*3];
                    int linepos = 0;
                    for (int i = 0; i < towrite.length; i+=3) {
                        towrite[i] = Keyreader.colors[thisline[linepos] & 0xFF][0];
                        towrite[i+1] = Keyreader.colors[thisline[linepos] & 0xFF][1];
                        towrite[i+2] = Keyreader.colors[thisline[linepos] & 0xFF][2];
                        linepos++;
                    }
                    def.write(towrite,0,towrite.length);
                    if (bytesread != width) { //This line needs padding (only happens on last line)
                        byte[] onepad = {0,0,0};
                        while (bytesread < width) {
                            def.write(onepad); //Pad with all 0's
                            bytesread++;
                        }
                    }
                }
                progress.setValue(linenum+1);
            }
            def.finish();
            IDAT.writeTo(out);
        }
        
        /* Write IEND Chunk */
        progress.setIndeterminate(true);
        status.setText("Finishing up...");
        Chunk IEND = new Chunk((int)0x49454E44);  //"IEND"
        IEND.writeTo(out);  //No data in this chunk
        
        out.flush();
        fout.flush();
        out.close();
        fout.close();
        IEND = IHDR = null;
        buf = null;
        System.gc();
        if (PNGFunnel.fsize > 30000000) new File("SizerTemp").delete();
        status.setText("File was saved successfully!");
        progress.setIndeterminate(false);
        
        }catch (IOException e) {
            JOptionPane.showMessageDialog(null,e.getMessage());
        }finally {
            PNGFunnel.filechoose.setEnabled(true);
            PNGFunnel.keybutton.setEnabled(true);
            PNGFunnel.convertbutton.setEnabled(true);
            PNGFunnel.dimbutton.setEnabled(true);
            if (PNGFunnel.ispng) PNGFunnel.decodebutton.setEnabled(true);
        }
    }
    
    /**
     * This convienence class represents a PNG chunk, which has length, data,
     * and cyclic redundancy check (CRC) fields.
     *
     */
    static class Chunk extends DataOutputStream {
        final CRC32 crc;
        final ByteArrayOutputStream baos;
 
        Chunk(int chunkType) throws IOException {
            this(chunkType, new ByteArrayOutputStream(), new CRC32());
        }
        private Chunk(int chunkType, ByteArrayOutputStream baos, CRC32 crc) throws IOException {
            super(new CheckedOutputStream(baos, crc));
            this.crc = crc;
            this.baos = baos;
 
            writeInt(chunkType);
        }
 
        public void writeTo(DataOutputStream out) throws IOException {
            flush();
            out.writeInt(baos.size() - 4);
            baos.writeTo(out);
            out.writeInt((int)crc.getValue());
        }
    }
    /**
     * This file filter is used to allow saving as png files only.
     *
     */
    static class PNGFilter extends javax.swing.filechooser.FileFilter {
        public boolean accept(File f) {
            return (f.isDirectory() || (f.isFile() && f.getName().toLowerCase().endsWith(".png")));
        }

        public String getDescription() {
            return "PNG files";
        }
        
    }
}
