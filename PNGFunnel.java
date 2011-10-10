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

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

/**
 * This class builds the GUI and then listens for clicks of the buttons in the
 * second control panel. It executes the dimension calculations, but starts
 * new threads for the conversion operations.
 * @author Robert Keller, robin@waste-of-time-and-money.com
 *
 */
public class PNGFunnel implements ActionListener{
    
    //GUI Elements
    static JTextField path = null;
    static JButton filechoose = null;
    static JButton convertbutton = null;
    static JButton dimbutton = null;
    static JButton decodebutton = null;
    static JCheckBox keybox = null;
    static JTextField keypath = null;
    static JButton keybutton = null;
    static JCheckBox palettebox = null;
    static JProgressBar progress = null;
    static JLabel curraction = null;
    
    //Fields
    private static long length = 1, width = 1;
    static long fsize = 0;
    static int padded = 0;
    static boolean gotdims = false, ispng = false;
    
    /*
     * The main method builds the GUI only.
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("PNG Funnel");
        //frame.setResizable(false);
        Container pane = frame.getContentPane();
        
        path = new JTextField("");
        path.setPreferredSize(new Dimension(400,20));
        path.setEditable(false);
        
        filechoose = new JButton("Choose file...");
        filechoose.addActionListener(new FileChooser(path));
        
        keybox = new JCheckBox("Use keyfile");
        
        keypath = new JTextField("");
        keypath.setPreferredSize(new Dimension(350,20));
        
        keybutton = new JButton("Choose file...");
        keybutton.addActionListener(new Keyreader());
        
        palettebox = new JCheckBox("Encode keyfile in image (loses security but reduces conversion time and file size)");
        
        JPanel choosepanel = new JPanel();
        choosepanel.setLayout(new FlowLayout());
        choosepanel.setBorder(new TitledBorder("1. Select your file and an optional keyfile"));
        choosepanel.add(new JLabel("Current file: "));
        choosepanel.add(path);
        choosepanel.add(filechoose);
        choosepanel.add(keybox);
        choosepanel.add(keypath);
        choosepanel.add(keybutton);
        choosepanel.add(palettebox);
        choosepanel.setPreferredSize(new Dimension(630,130));
        
        pane.add(choosepanel,BorderLayout.NORTH);
        
        JPanel status = new JPanel();
        status.setBorder(new TitledBorder("3. Read status here"));
        status.setLayout(new GridLayout(3,1));
        
        /* The label for the progress bar */
        curraction = new JLabel("  Idle.");
        curraction.setHorizontalTextPosition(JLabel.CENTER);
        status.add(curraction);
        
        /* The progress bar */
        progress = new JProgressBar(0,1);
        progress.setPreferredSize(new Dimension(610,20));
        progress.setValue(0);
        status.add(progress);
        
        /* Add the buttons */
        JPanel buttons = new JPanel();
        buttons.setLayout(new GridLayout(1,3));
        buttons.setBorder(new TitledBorder("2. Choose a button"));
        
        //Button to calculate dimensions of new file
        dimbutton = new JButton("Calculate dimensions");
        dimbutton.setEnabled(false);
        dimbutton.addActionListener(new PNGFunnel());
        
        //Button to convert from file to png
        convertbutton = new JButton("Create PNG");
        convertbutton.setEnabled(false);
        convertbutton.addActionListener(new PNGFunnel());
        
        //Button to convert from png to file
        decodebutton = new JButton("Decode PNG");
        decodebutton.setEnabled(false);
        decodebutton.addActionListener(new PNGFunnel());
        
        buttons.add(dimbutton);
        buttons.add(convertbutton);
        buttons.add(decodebutton);
        
        /* Add everything to the GUI */
        pane.add(status, BorderLayout.SOUTH);
        pane.add(buttons, BorderLayout.CENTER);
        frame.pack();
        //frame.setSize(new Dimension(700,700));
        frame.setVisible(true);
    }

    /*
     * The actionPerformed method of PNGFunnel handles all the buttons in the
     * second control panel, used for conversion.
     */
    public void actionPerformed(ActionEvent arg0) {
        //Disable buttons so we don't have conflicting calls
        filechoose.setEnabled(false);
        convertbutton.setEnabled(false);
        dimbutton.setEnabled(false);
        decodebutton.setEnabled(false);
        keybutton.setEnabled(false);
        
        //The "Create PNG" button
        if (arg0.getSource().equals(convertbutton)) {
            if (!gotdims) {
                getDims();
                if (!gotdims) {
                    filechoose.setEnabled(true);
                    convertbutton.setEnabled(true);
                    dimbutton.setEnabled(true);
                    if (ispng) decodebutton.setEnabled(true);
                    keybutton.setEnabled(true);
                    return;
                }
            }
            ConverterThread cthread = new ConverterThread(length, width, curraction, progress);
            cthread.start();
            
        //The "Decode PNG" button
        }else if (arg0.getSource().equals(decodebutton)) {
            DecoderThread dthread = new DecoderThread(curraction,progress);
            dthread.start();
            
        //The "Calculate dimensions" button
        }else {
            getDims();
            filechoose.setEnabled(true);
            convertbutton.setEnabled(true);
            dimbutton.setEnabled(true);
            if (ispng) decodebutton.setEnabled(true);
            keybutton.setEnabled(true);
        }
    }
    
    /**
     * getDims()
     * Sets the static length and width fields of PNGFunnel. To maximize
     * compatibility, dimensions must be at most 30000 x 30000. Since some
     * files can't fit this constraint, this method continually pads the file
     * in question until it does. 
     */
    private void getDims() {
        try {
            fsize = new RandomAccessFile(path.getText(),"r").length();
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(null,"ERROR OPENING FILE: Java doesn't believe this file exists. Try changing the file name.\n\n");
            return;
        }catch (IOException e) {
            System.err.println("Error opening file: "+e);
            return;
        }
        if (fsize > 900000000) {
            JOptionPane.showMessageDialog(null, "File too large. Only files up to 900,000,000 bytes are supported.");
            return;
        }
        curraction.setText("Calculating...");
        progress.setIndeterminate(true);
        int wrong = 0;
        padded = 0;
        for (long i = fsize; i <= 900000000; i++) {
            if (wrong == 350) { //I won't explain it, but this means there won't be a correct dimension for quite some time
                long diff = 29999 - (i % 30000); //The next multiple of 30000
                padded += diff + 1;
                i += diff;
                wrong = 0;
                continue;
            }
            if (isPrime(i)) {
                length = i;
                width = 1;
                if (length <= 30000) {
                    curraction.setText("File size: "+fsize+" bytes. Dimensions: "+length + " x " + width + " (padded "+padded+" bytes)");
                    progress.setIndeterminate(false);
                    gotdims = true;
                    return;
                }else wrong++;
                padded++;
                continue;
            }
            long s = (long) Math.sqrt(i);
            for (long j = s+1; j > 0; j--) {
                if (i % j == 0) {
                    width = j;
                    length = i / j;
                    if (length <= 30000) {
                        curraction.setText("File size: "+fsize+" bytes. Dimensions: "+length + " x " + width + " (padded "+padded+" bytes)");
                        progress.setIndeterminate(false);
                        gotdims = true;
                        return;
                    }else wrong++;
                    break;
                }
            }
            padded++;
        }
    }
    
    /**
     * isPrime()
     * Determines if a number is prime.
     * Adopted from an AppleScript by Nigel Garvey, Arthur Knapp, and Michael
     * Sullivan at http://bbs.macscripter.net/viewtopic.php?id=15030
     * @param n The number to test
     * @return boolean If n is prime
     */
    private boolean isPrime(long n) {
        if (n < 37)
            return (n == 2 || n == 3 || n == 5 || n == 7 || n == 11 ||
                    n == 13 || n == 17 || n == 19 || n == 23 || n == 29 || n == 31);
        else if (n % 2 == 0)
            return false;
        else if ((n%3)*(n%5) == 0)
            return false;
        else if ((n%7)*(n%11)*(n%13)*(n%17)*(n%19)*(n%23)*(n%29) == 0)
            return false;
        else {
            int limit = (int) Math.sqrt(n);
            for (int i = 30; i <= limit; i += 30) {
                if ((n%(i+1))*(n%(i+7))*(n%(i+11))*(n%(i+13))*(n%(i+17))*(n%(i+19))*(n%(i+23))*(n%(i+29)) == 0)
                    return false;
            }
            return true;
        }
    }
}
