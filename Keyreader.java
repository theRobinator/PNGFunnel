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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * This class allows the user to select a keyfile and then creates the
 * dictionary that the program will use immediately afterwards.
 * @author Robert Keller, robin@waste-of-time-and-money.com
 *
 */
public class Keyreader implements ActionListener {
    static byte[][] colors = null;
    static HashMap<Integer, Byte> ints = null;

    public void actionPerformed(ActionEvent arg0) {
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Choose a keyfile");
        jfc.setFileFilter(new KeyFilter());
        int ret = jfc.showOpenDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION && readKeys(jfc.getSelectedFile().getPath())) {
            PNGFunnel.keypath.setText(jfc.getSelectedFile().getPath());
        }else
            PNGFunnel.keypath.setText("");
    }
    
    /**
     * readKeys
     * Reads a keyfile and creates the dictionary for conversion.
     * @param path The complete path to the keyfile
     * @return boolean If the keyfile is valid
     */
    static boolean readKeys(String path) {
        try {
            Scanner in = new Scanner(new File(path));
            colors = new byte[256][3];
            ints = new HashMap<Integer,Byte>();
            for (int i = 0; i < 256; i++) {
                for (int j = 0; j < 3; j++)
                    colors[i][j] = (byte) in.nextInt();
                ints.put((int)((colors[i][0] << 16) + (colors[i][1] << 8) + colors[i][2]), (byte)i);
            }
            return true;
        }catch (IOException e) {
            JOptionPane.showMessageDialog(null,"ERROR: Invalid keyfile. A valid keyfile consists of 256 lines,\neach with an RGB integer color value.");
            return false;
        }
    }
    
    /**
     * Since keyfiles have no extension, this filter allows only files whose
     * size is in a believable range for a keyfile.
     *
     */
    static class KeyFilter extends javax.swing.filechooser.FileFilter {
        public boolean accept(File f) {
            return (f.isDirectory() || (f.isFile() && f.length() < 3329 && f.length() > 1534));
        }

        public String getDescription() {
            return "Key files";
        }
        
    }
}
