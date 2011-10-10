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

import javax.swing.JFileChooser;
import javax.swing.JTextField;

/**
 * This class lets the user choose a file, then sets a flag if the file was a
 * PNG file.
 * @author Robert Keller, robin@waste-of-time-and-money.com
 *
 */
public class FileChooser implements ActionListener{
    
    JTextField target;
    
    public FileChooser(JTextField target) {
        this.target = target;
    }
    
    public void actionPerformed(ActionEvent e) {
        PNGFunnel.convertbutton.setEnabled(false);
        PNGFunnel.dimbutton.setEnabled(false);
        PNGFunnel.decodebutton.setEnabled(false);
        PNGFunnel.keybutton.setEnabled(true);
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Choose a file to look at.");
        jfc.setAcceptAllFileFilterUsed(true);
        int ret = jfc.showOpenDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            target.setText(jfc.getSelectedFile().getPath());
            PNGFunnel.convertbutton.setEnabled(true);
            PNGFunnel.dimbutton.setEnabled(true);
            if (target.getText().toLowerCase().endsWith(".png")){
                PNGFunnel.ispng = true;
                PNGFunnel.decodebutton.setEnabled(true);
            } else PNGFunnel.ispng = false;
            PNGFunnel.gotdims = false;
        }else target.setText("");
        PNGFunnel.keybutton.setEnabled(true);
    }

}
