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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

/**
 * This class handles conversion from PNG back to regular file. It writes
 * compressed data from the PNG to a temp file called SizerTemp and then
 * decompresses that into the original file.
 * @author Robert Keller, robin@waste-of-time-and-money.com
 *
 */
public class DecoderThread extends Thread{
    
    final JLabel status;
    final JProgressBar progress;
    
    final int BUFFER_SIZE = 32768;
    
    public DecoderThread(JLabel status, JProgressBar progress) {
        this.status = status;
        this.progress = progress;
    }
    
    public void run() {
        try {
            /* Check if a key file is being used */
            boolean usingkey = PNGFunnel.keybox.isSelected();
            if (usingkey && PNGFunnel.keypath.getText().equals("")) {
                JOptionPane.showMessageDialog(null,"\nERROR: No keyfile selected.");
                return;
            }
            
            status.setText("Preparing...");
            progress.setIndeterminate(true);
            
            /* Get the save path from the user */
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Where would you like to save the image?");
            jfc.setAcceptAllFileFilterUsed(true);
            File oldposition = new File(PNGFunnel.path.getText());
            if (oldposition.exists()) jfc.setSelectedFile(oldposition);
            int ret = jfc.showSaveDialog(null);
            if (ret != JFileChooser.APPROVE_OPTION) return;
            String outpath = jfc.getSelectedFile().getPath();
            
            /* Initialize variables */
            InputStream in = new FileInputStream(PNGFunnel.path.getText());
            FileOutputStream out = new FileOutputStream(outpath);
            FileOutputStream tempout = new FileOutputStream("SizerTemp");
            byte[] bytes = new byte[4];                      //Generic 4-byte buffer
            byte[] buf = null;                               //Buffer for reading/writing
            int width = 0, height = 0, depth = 8, ctype = 0; //Image properties
            int padding = -1;                                //Padding amount
            int filter;                                      //Stores the filter of the current line
            byte[] prevline, thisline;                       //Current and previous lines of the image
            boolean gotpadding = false;                      //If the padding has been read
            
            /* Read IHDR and check for valid formatting */
            status.setText("Checking file validity...");
            try {
                //Read past the PNG header (it's 8 bytes long)
                in.read(bytes);           //First 4 bytes of PNG header
                if (bytes[0] != -119 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G')
                    throw new Exception("File is not a PNG file.");
                in.read(bytes);           //Second 4 bytes of PNG header
                if (bytes[0] != 13 || bytes[1] != 10 || bytes[2] != 26 || bytes[3] != 10)
                    throw new Exception("File is not a PNG file.");
                in.read(bytes);           //Reads the size of the IHDR chunk, 13
                if (byteToInt(bytes) != 13)
                    throw new Exception("PNG header is corrupted.");
                in.read(bytes);           //Reads past "IHDR"
                if (bytes[0] != 'I' || bytes[1] != 'H' || bytes[2] != 'D' || bytes[3] != 'R')
                    throw new Exception("Image has no header.");
                in.read(bytes);           //The image width
                width = byteToInt(bytes);
                in.read(bytes);           //The image height
                height = byteToInt(bytes);
                if (width <=0 || height <= 0)
                    throw new Exception("Invalid dimensions");
                depth = in.read();        //The bit depth
                if (depth != 8) throw new Exception("Unsupported bit depth. For now, only 8-bit color is supported.");
                ctype = in.read();        //The color type
                if (ctype >> 2 == 1)
                    throw new Exception("Image uses transparency (alpha channel).");
                else if (ctype == 2 && !usingkey)
                    throw new Exception("A keyfile must be used to decode color images.");
                else if (ctype == 0 && usingkey)
                    throw new Exception("This image doesn't use a keyfile.");
                else if (!(ctype == 0 || ctype == 2 || ctype == 3))
                    throw new Exception("Unsupported color type. Only grayscale, RGB, and indexed are supported.");
                else if (ctype == 3)
                    usingkey = false;
                if (in.read() != 0) throw new Exception("Unsupported compression method.");
                if (in.read() != 0) throw new Exception("Unsupported filter method.");
                if (in.read() != 0) throw new Exception("Image is interlaced.");
                in.read(bytes);           //Skip the CRC
            }catch (Exception e) {
                status.setText("FILE IS NOT VALID: "+e.getMessage());
                return;
            }
            
            /* Get deflated file out of the IDAT chunks */
            status.setText("Getting compressed data from image...");
            progress.setIndeterminate(false);
            progress.setMaximum(width * height);
            progress.setValue(0);
            buf = new byte[BUFFER_SIZE];
            while (true) {                       //Each repetition reads a chunk
                in.read(bytes);                  //The length of this chunk
                int chunklen = byteToInt(bytes);
                in.read(bytes);                  //This chunk's 4-letter ID
                
                if (!gotpadding) {
                    if (bytes[0] == 'f' && bytes[1] == 'n' && bytes[2] == 'N' && bytes[3] == 'L') { //Padding chunk
                        in.read(bytes); //The padding amount
                        padding = byteToInt(bytes);
                        in.read(bytes); //Read past CRC
                    }else {
                        /* Get file padding amount from the user */
                        status.setText("File does not contain padding information.");
                        String padamount = (String) JOptionPane.showInputDialog(null, 
                                "How many bytes is the PNG padded?", "Specify Padding",JOptionPane.PLAIN_MESSAGE,
                                null,null,"0");
                        try {
                            padding = Integer.parseInt(padamount);
                        }catch (Exception e) {
                            status.setText("ERROR: Image not padded with positive integer.");
                            return;
                        }
                        System.out.println("Unrecognized chunk!");
                        for (int i = 0; i < chunklen; i++) //Read past this chunk
                            in.read();
                        in.read(bytes); //Read past CRC
                        status.setText("Gathering compressed data from image...");
                    }
                    gotpadding = true;
                }else if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == 'A' && bytes[3] == 'T') { //IDAT chunk
                    //Write the contents of this IDAT chunk to the temp file using the buffer
                    if (chunklen <= BUFFER_SIZE) { //Only one write required
                        int read = in.read(buf,0,chunklen);
                        if (read == -1) throw new UnsupportedEncodingException("EOF detected within IDAT chunk. Aborting.");
                        tempout.write(buf,0,read);
                        progress.setValue(progress.getValue()+read);
                    }else { //Multiple writes required
                        int totalread = 0;
                        while (totalread != chunklen) {
                            //Since chunklen > BUFFER_SIZE, we must switch what we have here depending on that.
                            int read = in.read(buf,0,chunklen-totalread<=BUFFER_SIZE?chunklen-totalread:buf.length);
                            if (read == -1) throw new UnsupportedEncodingException("EOF detected within IDAT chunk. Aborting.");
                            tempout.write(buf,0,read);
                            totalread += read;
                            progress.setValue(progress.getValue()+read);
                        }
                    }
                    in.read(bytes); //Read past CRC
                    
                }else if (bytes[0] == 'I' && bytes[1] == 'E' && bytes[2] == 'N' && bytes[3] == 'D') { //IEND chunk
                    break;
                    
                }else { //Unused chunk, it can be skipped
                    System.out.println("Unrecognized chunk!");
                    for (int i = 0; i < chunklen; i++) //Read past this chunk
                        in.read();
                    in.read(bytes); //Read past CRC
                }
            }
            //Free up memory
            tempout.flush();
            tempout.close();
            in.close();
            buf = null;
            tempout = null;
            in = null;
            System.gc();
            
            
            /* Decompress temp file */
            status.setText("Decompressing data to file...");
            progress.setValue(0);
            progress.setMaximum(height);
            InflaterInputStream inf = new InflaterInputStream(new FileInputStream("SizerTemp"),new Inflater(),BUFFER_SIZE);
            int bpp = 1;                 //Bytes per pixel. Should be changed based off of color type and bit depth.
            if (usingkey) {
                bpp = 3;
                width *= 3;
                padding *= 3;
            }
            prevline = new byte[width+1]; //Adding an extra byte first for filter convenience
            buf = new byte[width];       //Used for non-filtered lines, which must be read as bytes instead

            for (int h = 0; h < height-1; h++) { //Write all but the last line
                filter = inf.read();
                thisline = new byte[width+1];
                if (filter == 0) {       //No filter; we can just write the line in one step
                    int totalread = 0;
                    while (totalread != width) { //Read a whole line, not just what inf.read() returns
                        totalread += inf.read(buf,totalread,buf.length-totalread);
                    }
                    if (!usingkey)
                        out.write(buf);
                    else {
                        byte[] keyout = new byte[buf.length / 3];
                        int keypos = 0;
                        for (int i = 2; i < width; i+=3) {
                            keyout[keypos] = Keyreader.ints.get((int)((buf[i-2] << 16) + (buf[i-1] << 8) + buf[i]));
                            keypos++;
                        }
                        out.write(keyout);
                        keyout = null;
                    }
                    for (int w = 1; w <= width; w++) //Store this line in case the next one is filtered
                        thisline[w] = buf[w-1];
                    
                }else {      //Line has a filter; we must change the value of each pixel
                    int totalread = 1;
                    while (totalread != width+1) { //Read a whole line, not just what inf.read() returns
                        totalread += inf.read(thisline,totalread,thisline.length-totalread);
                    }
                    if (!usingkey) {
                        switch (filter) {
                        case 1 : { //SUB filter
                            System.out.println("Sub encoded line: "+h);
                            for (int w = 1; w <= width; w++)
                                thisline[w] = (byte)(thisline[w] + thisline[w-bpp]);
                            break;
                        }case 2 : { //UP filter
                            System.out.println("Up encoded line: "+h);
                            for (int w = 1; w <= width; w++)
                                thisline[w] = (byte)(thisline[w] + prevline[w]);
                            break;
                        }case 3 : { //AVERAGE filter
                            for (int w = 1; w <= width; w++)
                                thisline[w] = (byte)(thisline[w] + (thisline[w-bpp]+prevline[w])/2);
                            break;
                        }case 4 : { //PAETH filter
                            for (int w = 1; w <= width; w++)
                                thisline[w] = (byte)(thisline[w] + paethPredictor(thisline[w-bpp],prevline[w],prevline[w-bpp]));
                            break;
                        }
                        }
                        out.write(thisline,1,thisline.length-1);
                    }else {
                        byte[] keyout = new byte[width/3];
                        int keypos = 0;
                        switch (filter) {
                        case 1 : { //SUB filter
                            System.out.println("Sub encoded line: "+h);
                            for (int w = 1; w <= width; w+=3) {
                                thisline[w] = (byte)(thisline[w] + thisline[w-bpp]);
                                thisline[w+1] = (byte)(thisline[w+1] + thisline[w+1-bpp]);
                                thisline[w+2] = (byte)(thisline[w+2] + thisline[w+2-bpp]);
                                keyout[keypos] = Keyreader.ints.get((int)((thisline[w] << 16) + (thisline[w+1] << 8) + thisline[w+2]));
                                keypos++;
                            }
                            break;
                        }case 2 : { //UP filter
                            System.out.println("Up encoded line: "+h);
                            for (int w = 1; w <= width; w+=3) {
                                thisline[w] = (byte)(thisline[w] + prevline[w]);
                                thisline[w+1] = (byte)(thisline[w+1] + prevline[w+1]);
                                thisline[w+2] = (byte)(thisline[w+2] + prevline[w+2]);
                                keyout[keypos] = Keyreader.ints.get((int)((thisline[w] << 16) + (thisline[w+1] << 8) + thisline[w+2]));
                                keypos++;
                            }
                            break;
                        }case 3 : { //AVERAGE filter
                            System.out.println("Average encoded line: "+h);
                            for (int w = 1; w <= width; w+=3) {
                                thisline[w] = (byte)(thisline[w] + (thisline[w-bpp]+prevline[w])/2);
                                thisline[w+1] = (byte)(thisline[w+1] + (thisline[w+1-bpp]+prevline[w+1])/2);
                                thisline[w+2] = (byte)(thisline[w+2] + (thisline[w+2-bpp]+prevline[w+2])/2);
                                keyout[keypos] = Keyreader.ints.get((int)((thisline[w] << 16) + (thisline[w+1] << 8) + thisline[w+2]));
                                keypos++;
                            }
                            break;
                        }case 4 : { //PAETH filter
                            System.out.println("Paeth encoded line: "+h);
                            for (int w = 1; w <= width; w+=3) {
                                thisline[w] = (byte)(thisline[w] + paethPredictor(thisline[w-bpp],prevline[w],prevline[w-bpp]));
                                thisline[w+1] = (byte)(thisline[w+1] + paethPredictor(thisline[w+1-bpp],prevline[w+1],prevline[w+1-bpp]));
                                thisline[w+2] = (byte)(thisline[w+2] + paethPredictor(thisline[w+2-bpp],prevline[w+2],prevline[w+2-bpp]));
                                keyout[keypos] = Keyreader.ints.get((int)((thisline[w] << 16) + (thisline[w+1] << 8) + thisline[w+2]));
                                keypos++;
                            }
                            break;
                        }
                        }
                        out.write(keyout);
                    }
                    
                }
                prevline = thisline;
                progress.setValue(h+1);
            }
            
            //Now write the last line, skipping the padded bytes
            filter = inf.read();
            thisline = new byte[width+1];
            int totalread = 1;
            while (totalread != width+1-padding) { //Read a whole line, not just what inf.read() returns
                totalread += inf.read(thisline,totalread,thisline.length-totalread-padding);
            }
            if (!usingkey) {
                for (int w = 1; w <= width-padding; w++) {
                    switch (filter) {
                    case 0 : break;
                    case 1 : {
                        thisline[w] = (byte)(thisline[w] + thisline[w-bpp]);
                        break;
                    }case 2 : {
                        System.out.println("Up encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + prevline[w]);
                        break;
                    }case 3 : {
                        thisline[w] = (byte)(thisline[w] + (thisline[w-bpp]+prevline[w])/2);
                        break;
                    }case 4 : {
                        System.out.println("Paeth encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + paethPredictor(thisline[w-bpp],prevline[w],prevline[w-bpp]));
                        break;
                    }
                    }
                }
                out.write(thisline,1,thisline.length-1-padding);
            }else {
                byte[] keyout = new byte[(width - padding) / 3];
                int keypos = 0;
                for (int w = 1; w <= width - padding; w+=3) {
                    switch (filter) {
                    case 0 : break;
                    case 1 : {
                        System.out.println("Sub encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + thisline[w-bpp]);
                        thisline[w+1] = (byte)(thisline[w+1] + thisline[w+1-bpp]);
                        thisline[w+2] = (byte)(thisline[w+2] + thisline[w+2-bpp]);
                        break;
                    }case 2 : {
                        System.out.println("Up encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + prevline[w]);
                        thisline[w+1] = (byte)(thisline[w+1] + prevline[w+1]);
                        thisline[w+2] = (byte)(thisline[w+2] + prevline[w+2]);
                        break;
                    }case 3 : {
                        System.out.println("Average encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + (thisline[w-bpp]+prevline[w])/2);
                        thisline[w+1] = (byte)(thisline[w+1] + (thisline[w+1-bpp]+prevline[w+1])/2);
                        thisline[w+2] = (byte)(thisline[w+2] + (thisline[w+2-bpp]+prevline[w+2])/2);
                        break;
                    }case 4 : {
                        System.out.println("Paeth encoded pixel on last line");
                        thisline[w] = (byte)(thisline[w] + paethPredictor(thisline[w-bpp],prevline[w],prevline[w-bpp]));
                        thisline[w+1] = (byte)(thisline[w+1] + paethPredictor(thisline[w+1-bpp],prevline[w+1],prevline[w+1-bpp]));
                        thisline[w+2] = (byte)(thisline[w+2] + paethPredictor(thisline[w+2-bpp],prevline[w+2],prevline[w+2-bpp]));
                        break;
                    }
                    }
                    keyout[keypos] = Keyreader.ints.get((int)((thisline[w] << 16) + (thisline[w+1] << 8) + thisline[w+2]));
                    keypos++;
                }
                out.write(keyout);
            }
            progress.setValue(height);
            
            //Finally, free up memory and return
            out.flush();
            out.close();
            inf.close();
            buf = null;
            prevline = thisline = null;
            out = null;
            inf = null;
            System.gc();
            status.setText("File was decoded successfully!");
            
        }catch (NullPointerException e) {
            status.setText("ERROR: File and keyfile do not match.");
        }catch (IOException e) {
            JOptionPane.showMessageDialog(null,e.toString());
        }finally {
            try {
                new File("SizerTemp").delete();
            }catch (Exception ex) { }
            PNGFunnel.filechoose.setEnabled(true);
            PNGFunnel.keybutton.setEnabled(true);
            PNGFunnel.convertbutton.setEnabled(true);
            PNGFunnel.dimbutton.setEnabled(true);
            if (PNGFunnel.ispng) PNGFunnel.decodebutton.setEnabled(true);
        }
    }
    /**
     * byteToInt
     * Converts a 4-byte array to an integer.
     * @param bob The byte array to convert
     * @return int The integer representation of bob.
     */
    public static int byteToInt(byte[] bob) {
        int i = 0;
        int pos = 0;
        i += unsignedByteToInt(bob[pos++]) << 24;
        i += unsignedByteToInt(bob[pos++]) << 16;
        i += unsignedByteToInt(bob[pos++]) << 8;
        i += unsignedByteToInt(bob[pos++]) << 0;
        return i;
    }
    /**
     * unsignedByteToInt
     * Changes a single byte to an int
     * @param i The byte to change
     * @return int The integer representation
     */
    public static int unsignedByteToInt(int i) {
        return (int) i & 0xFF;
    }
    /**
     * paethPredictor
     * A function used to decode PNG filter type 4, the Paeth filter. It
     * calculates the closest of the 3 neighboring pixels.
     * @param a The pixel to the left of this one
     * @param b The pixel above this one
     * @param c The pixel up and to the left of this one
     * @return int The value of the closest pixel
     */
    public int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc)
            return a;
        else if (pb <= pc)
            return b;
        else
            return c;
    }
}
