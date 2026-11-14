import java.awt.Component;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;



public class MyIDE {

    // ===== Shared state =====
    // These are fields (not local variables) so that methods outside main, like openFile, can use them.

    // The row of tabs on the right side of the window
    static JTabbedPane tabs;

    // Remembers which file each tab is showing: tab (key) -> file (value)
    static HashMap<Component, File> openFiles = new HashMap<>();

    // ===== Program start =====
    public static void main(String[] args)
    {
        // Swing must build its window on its own thread, so everything below runs inside invokeLater
        SwingUtilities.invokeLater(() ->
        {
            // ----- Window -----
            JFrame frame = new JFrame("My IDE");

            // The popup that lets the user browse for a file or folder (shared by Open File and Open Folder)
            JFileChooser fileChooser = new JFileChooser();

            // ----- Menu bar -----
            // Created here, wired up with listeners below, then assembled into the bar
            JMenuBar menu = new JMenuBar();
            JMenu fileMenu = new JMenu("File");
            JMenuItem openMenu = new JMenuItem("Open File");
            JMenuItem saveMenu = new JMenuItem("Save");
            JMenuItem openFolderMenu = new JMenuItem("Open Folder");
            JMenuItem runMenu = new JMenuItem("Run");

            // ----- Tabs -----
            tabs = new JTabbedPane();

            //Console
            JTextArea consoleTextArea = new JTextArea();
            consoleTextArea.setEditable(false);

            // ----- File tree (left side) -----
            // Starts with a placeholder root; Open Folder replaces it with a real folder
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
            JTree tree = new JTree(root);

            // ===== What each menu item does =====

            // File -> Open File: pick one file and open it in a tab
            openMenu.addActionListener(e ->
            {
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION)
                {
                    File file = fileChooser.getSelectedFile();
                    openFile(file);
                }
            });

            // File -> Open Folder: pick a folder and show its contents in the tree
            openFolderMenu.addActionListener(e ->
            {
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION)
                {
                    File folder = fileChooser.getSelectedFile();

                    // Build a new tree from the folder, then swap it into the tree on screen
                    DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(folder.getName());
                    AddChildren(folder, newRoot);
                    tree.setModel(new DefaultTreeModel(newRoot));
                }
            });

            // File -> Save: write the text of the tab currently showing back to its file
            saveMenu.addActionListener(e -> saveCurrent());

            // File -> Run: run the code in the selected file 
            runMenu.addActionListener(e -> {
                Component selectedComponent = tabs.getSelectedComponent();
                if (selectedComponent == null){
                    return;
                }
                
                File file = openFiles.get(selectedComponent);
                
                if (!file.getName().endsWith(".java")){
                    JOptionPane.showMessageDialog(frame, "only run .java files");
                    return;
                }
                saveCurrent();

                consoleTextArea.setText("");
                consoleTextArea.append("Running: " + file.getAbsolutePath() + "\n");

                new Thread(() -> {
                    try{
                        ProcessBuilder pb = new ProcessBuilder("java", file.getAbsolutePath());
                        pb.redirectErrorStream(true);

                        Process process = pb.start();

                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        
                        String line;
                        while ((line = reader.readLine())!= null){
                            String text = line;
                            SwingUtilities.invokeLater(() -> consoleTextArea.append(text + "\n"));
                        }
                    }
                    catch (IOException ex){
                        SwingUtilities.invokeLater(() -> consoleTextArea.append("Could not run: " + ex.getMessage() + "\n"));
                    }
                }).start();;

            });
            // ===== Put the menu together (items go into the menu, the menu goes into the bar) =====
            fileMenu.add(openMenu);
            fileMenu.add(openFolderMenu);
            fileMenu.add(saveMenu);
            menu.add(fileMenu);
            menu.add(runMenu);

            // ===== Clicking a file in the tree opens it =====
            tree.addTreeSelectionListener(e ->
            {
                // Which tree item is selected? It's null if nothing is.
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (node != null)
                {
                    Object data = node.getUserObject();

                    // The placeholder root holds plain text, so only act on items that hold a FileItem
                    if (data instanceof FileItem)
                    {
                        File file = ((FileItem) data).file;

                        // Clicking a folder does nothing; only real files open
                        if (file.isFile())
                        {
                            openFile(file);
                        }
                    }
                }
            });

            // ===== Window layout: tree on the left, tabs on the right, with a draggable divider =====
            JSplitPane rightPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, new JScrollPane(consoleTextArea));
            
            // Give the tabs 80% of the vertical space, console 20%
            rightPane.setResizeWeight(0.8); 

            //Divides the window into two parts, left and right. Left is the file tree, right is the tabs and console.
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), rightPane);
            
            // Give the tree 10% of the horizontal space, tabs+console 90%
            splitPane.setResizeWeight(0.1); 

            frame.setJMenuBar(menu);
            frame.add(splitPane);
            frame.setSize(1000, 800);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Show the window last, once everything is added to it
            frame.setVisible(true);
        });
    }

    // ===== Builds the tree =====
    // Adds a node under "parent" for everything inside "folder".
    // It calls itself for each subfolder (recursion), so it goes as deep as the folders do.
    public static void AddChildren(File folder, DefaultMutableTreeNode parent)
    {
        // listFiles() returns null if the folder can't be read
        File[] files = folder.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new FileItem(file));
                parent.add(node);

                // If this is a folder, fill in its contents too
                if (file.isDirectory())
                {
                    AddChildren(file, node);
                }
            }
        }
    }

    // ===== Wraps a File so the tree shows just its name =====
    // The tree displays whatever toString() returns, and File's own toString() gives the full path.
    static class FileItem
    {
        File file;

        FileItem(File file)
        {
            this.file = file;
        }

        // @Override means "replace the toString() every object already has"
        @Override
        public String toString()
        {
            return file.getName();
        }
    }

    // ===== Opens a file in a new tab (or jumps to its tab if it's already open) =====
    static void openFile(File file)
    {
        // Step 1: is this file already open? If so, just select its tab and stop.
        for (Map.Entry<Component, File> entry : openFiles.entrySet())
        {
            if (entry.getValue().equals(file))
            {
                tabs.setSelectedComponent(entry.getKey());
                return;
            }
        }

        // Step 2: read the file's text into a new text area
        JTextArea fileTextArea = new JTextArea();

        try
        {
            fileTextArea.setText(Files.readString(file.toPath()));
        }
        catch (IOException ex)
        {
            // If the read failed, tell the user and don't make a tab
            JOptionPane.showMessageDialog(tabs, "Could not open file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 3: wrap the text area in a scroll pane (this is what goes in the tab)
        JScrollPane scrollPane = new JScrollPane(fileTextArea);

        // Step 4: add the tab, remember which file it belongs to, and switch to it
        tabs.addTab(file.getName(), scrollPane);
        openFiles.put(scrollPane, file);
        tabs.setSelectedComponent(scrollPane);
    }

    // ===== Saves the text of the currently selected tab back to its file =====
    static void saveCurrent(){

        // (Left over from before tabs: Save no longer uses the chooser, so this line does nothing now)
                

                // Which tab is on screen? It's null if no tabs are open.
                Component selectedComponent = tabs.getSelectedComponent();
                if (selectedComponent != null)
                {
                    // Look up the file for this tab
                    File file = openFiles.get(selectedComponent);

                    // A tab holds a scroll pane, and the text area is inside it
                    JScrollPane scrollPane = (JScrollPane) selectedComponent;
                    JTextArea area = (JTextArea) scrollPane.getViewport().getView();

                    try
                    {
                        Files.writeString(file.toPath(), area.getText());
                    }
                    catch (IOException ex)
                    {
                        JOptionPane.showMessageDialog(tabs, "Could not save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
    }
}