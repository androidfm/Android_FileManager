package com.qtraceex.gui.plugin;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.io.File;

/*
* Script Main Class
* */
public class FileManager {
    public static final String APP_TITLE = "ADB FileManager";
    private int ROW_ICON_PADDING = 6;

    JFrame frame;
    private JPanel gui;

    private FileSystemView fileSystemView;
    private JComboBox comboBoxPath;
    private JTree deviceTree;
    private DefaultTreeModel treeModel;
    private FileTable deviceTable;
    private JProgressBar progressBar;

    private JButton btnSetFovar; //Button to Set Favorite Path
    ImageIcon  iconFavorOn = null;
    ImageIcon  iconFavorOff = null;

    private JButton btnShellMenu; //Button used to invoke Shell menu

    private JTextField localPath;
    private FileTable localTable;

    FileManagerStatusBar statusBar ;
    private JLabel  statusLabel;

    String  lastCommand = null;
    String lastAdbCommand = null;

    static DecimalFormat df = new DecimalFormat("#,###");
    PluginHandler mHandler = null;
    boolean  bFavorPath = false;
    private FileEx currentFile;
    private boolean cellSizesSet = false;
    private Desktop desktop;

    FileManager(PluginHandler handler) {
        mHandler = handler;
    }

    FileManager() {
    }

    /*creat main mGui*/
    public Container getGui() {
        if (gui==null) {
            gui = new JPanel(new BorderLayout(3,3));
            gui.setBorder(new EmptyBorder(5,5,5,5));
            loadConfigure();
            fileSystemView = FileSystemView.getFileSystemView();
            desktop = Desktop.getDesktop();

            /*Device Panel Init*/
            JPanel devicePanel = new JPanel(new BorderLayout(3,3));

            JPanel pathPanel = new JPanel(new BorderLayout(3,3));
            pathPanel.add(new JLabel("Device:"), BorderLayout.WEST);
            comboBoxPath = new JComboBox();
            comboBoxPath.setEditable(true);
            pathPanel.add(comboBoxPath, BorderLayout.CENTER);
            devicePanel.add(pathPanel, BorderLayout.NORTH);
            updateFavor();

            /*Device File Table & Device combobox Path Init*/
            comboBoxPath.getEditor().getEditorComponent().addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                        gotoPath((String) comboBoxPath.getEditor().getItem());
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });

            comboBoxPath.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if(e.getActionCommand().compareTo("comboBoxChanged") == 0
                        && comboBoxPath.getEditor().getEditorComponent().hasFocus()){
                        gotoPath((String) comboBoxPath.getEditor().getItem());
                    }
                }
            });
            JButton buttonDeviceGo = new JButton("Go");
            buttonDeviceGo.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    gotoPath((String) comboBoxPath.getEditor().getItem());
                }
            })
            pathPanel.add(buttonDeviceGo, BorderLayout.EAST);

            deviceTable = new FileTable(true);
            deviceTable.setShowVerticalLines(false);
            deviceTable.addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount() == 2){
                        int row = deviceTable.getSelectionModel().getLeadSelectionIndex();
                        FileEx file = ((FileTableModel)deviceTable.getModel()).getFile(row);
                        if(file.getName().compareTo("..") == 0){
                            gotoPath(new ADBFile(deviceTable.mCurrentPath).getParent());
                        }else if(file.isDirectory()){
                            gotoPath(file.getPath());
                            //showChildren(file);
                        }
                    }else if(e.getButton() == 1){
                        int row = deviceTable.getSelectionModel().getLeadSelectionIndex();
                        if(row > 0){
                            setFileDetails( ((FileTableModel)deviceTable.getModel()).getFile(row) );
                        }
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showDeviceMenu((Component)e.getSource(),
                                e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {

                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {

                }

                @Override
                public void mouseExited(MouseEvent e) {

                }
            });

            deviceTable.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if(e.getKeyCode() == KeyEvent.VK_ENTER){
                        int row = deviceTable.getSelectionModel().getLeadSelectionIndex();
                        FileEx file = ((FileTableModel)deviceTable.getModel()).getFile(row);
                        if(file.getName().compareTo("..") == 0){
                            gotoPath(new ADBFile(deviceTable.mCurrentPath).getParent());
                        }else if(file.isDirectory()){
                            gotoPath(file.getPath());
                        }
                        e.consume();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });

            DropTargetAdapter dropTargetAdapter = new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                            List<File> list = (List<File>) (dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                            for (File file : list) {
                                mHandler.device.runAdb("push " + file.getPath() + " " + comboBoxPath.getEditor().getItem());
                                dtde.dropComplete(true);
                            }
                        } else {
                            dtde.rejectDrop();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    gotoPath((String) comboBoxPath.getEditor().getItem());
                }
            };

            DropTarget  dropTarget = new DropTarget(deviceTable, DnDConstants.ACTION_COPY_OR_MOVE, dropTargetAdapter);

            deviceTable.setDropTarget(dropTarget);


            JScrollPane tableScroll = new JScrollPane(deviceTable);
            Dimension d = tableScroll.getPreferredSize();
            tableScroll.setPreferredSize(new Dimension((int)d.getWidth(), (int)(d.getHeight()/2)));
            devicePanel.add(tableScroll, BorderLayout.CENTER);


            /* Device File Tree Init*/
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            treeModel = new DefaultTreeModel(root);

            TreeSelectionListener treeSelectionListener = new TreeSelectionListener() {
                public void valueChanged(TreeSelectionEvent tse){
                    DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode)tse.getPath().getLastPathComponent();
                    showChildren(node);
                    setFileDetails((FileEx)node.getUserObject());
                }
            };

            runInThread(new Runnable() {
                @Override
                void run() {
                    ArrayList<ADBFile> roots = new ADBFile("/").listDir();
                    for (ADBFile fileSystemRoot : roots) {
                        DefaultMutableTreeNode node = new DefaultMutableTreeNode(fileSystemRoot);
                        if(fileSystemRoot.isDirectory()){
                            root.add( node );
                        }
                   }
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            deviceTree.setModel(treeModel);
                            deviceTree.setCellRenderer(new FileTreeCellRenderer());
                        }
                    });
                }
            });
            // show the file system roots.


            deviceTree = new JTree();
            deviceTree.setRootVisible(false);
            deviceTree.addTreeSelectionListener(treeSelectionListener);
            //tree.expandRow(0);
            JScrollPane treeScroll = new JScrollPane(deviceTree);

            // as per trashgod tip
            deviceTree.setVisibleRowCount(15);

            Dimension preferredSize = treeScroll.getPreferredSize();
            Dimension widePreferred = new Dimension(
                200,
                (int)preferredSize.getHeight());
            treeScroll.setPreferredSize( widePreferred );

            JToolBar toolBar = new JToolBar();
            toolBar.setFloatable(false);

            /* Icon & Button init*/
            String favorIconPath = mHandler.getWorkPath() + "favor.png";
            iconFavorOn = new ImageIcon(favorIconPath);
            iconFavorOff = new ImageIcon(mHandler.getWorkPath() + "favor_off.png");
            btnSetFovar = new JButton();
            btnSetFovar.setIcon(iconFavorOff);
            btnSetFovar.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent ae) {
                    if(bFavorPath){
                        removeFavor((String) comboBoxPath.getEditor().getItem());
                    }else{
                        setFavor((String) comboBoxPath.getEditor().getItem());
                    }

                }
            });
            toolBar.add(btnSetFovar);

            toolBar.addSeparator();

            toolBar.addSeparator();
            btnShellMenu = new JButton("...");
            //Invoke menu when click btnShellMenu
            btnShellMenu.setComponentPopupMenu(createShellMenu());
            btnShellMenu.addMouseListener(new MouseAdapter(){
                public void mouseReleased(MouseEvent event) {
                    if (event.getButton() == 1 && btnShellMenu.isEnabled())
                        btnShellMenu.getComponentPopupMenu().show(event.getComponent(), 0,
                                0);
                }
            });

            toolBar.add(btnShellMenu);

            toolBar.addSeparator();

            JPanel fileView = new JPanel(new BorderLayout(3,3));

            fileView.add(toolBar,BorderLayout.NORTH);

            devicePanel.add(fileView, BorderLayout.SOUTH);

            JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                treeScroll, devicePanel);

            /*Device File Table Init*/
            JLabel localPathLabel = new JLabel("Local:");
            localPath = new JTextField();
            localPath.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {

                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                        gotoLocalPath(localPath.getText());
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });
            JPanel localPathPanel = new JPanel(new BorderLayout(2,1));
            localPathPanel.add(localPathLabel, BorderLayout.WEST);
            localPathPanel.add(localPath, BorderLayout.CENTER);
            buttonDeviceGo = new JButton("Go");
            buttonDeviceGo.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    gotoLocalPath(localPath.getText());
                }
            });
            localPathPanel.add(buttonDeviceGo, BorderLayout.EAST);

            /*Local File Table Init*/
            localTable = new FileTable(false);
            localTable.setShowVerticalLines(false);
            localTable.addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(e.getClickCount() == 2){
                        int row = localTable.getSelectionModel().getLeadSelectionIndex();
                        FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                        if(file.getName().compareTo("..") == 0){
                            gotoLocalPath(new ADBFile(localTable.mCurrentPath).getParent());
                        }else if(file.isDirectory()){
                            gotoLocalPath(file.getPath());
                            //showChildren(file);
                        }
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showLocalMenu((Component)e.getSource(),
                                e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {

                }

                @Override
                public void mouseEntered(MouseEvent e) {

                }

                @Override
                public void mouseExited(MouseEvent e) {

                }
            });

            localTable.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {

                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if(e.getKeyCode() == KeyEvent.VK_ENTER){
                        int row = localTable.getSelectionModel().getLeadSelectionIndex();
                        FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                        if(file.getName().compareTo("..") == 0){
                            gotoLocalPath(new ADBFile(localTable.mCurrentPath).getParent());
                        }else if(file.isDirectory()){
                            gotoLocalPath(file.getPath());
                            //showChildren(file);
                        }
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });

            dropTargetAdapter = new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                            List<File> list = (List<File>) (dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                            for (File file : list) {
                                if(file.isDirectory()){
                                    gotoLocalPath(file.getPath());
                                }else{
                                    gotoLocalPath(file.getParent());
                                    localTable.selectByFile(file.getName());
                                }
                            }
                        } else {
                            dtde.rejectDrop();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            dropTarget = new DropTarget(localTable, DnDConstants.ACTION_COPY_OR_MOVE, dropTargetAdapter);

            localTable.setDropTarget(dropTarget);

            JScrollPane scrollPane = new JScrollPane(localTable);
            //scrollPane.add(localTable);
            JPanel localView = new JPanel(new BorderLayout(1,2));

            d = scrollPane.getPreferredSize();
            scrollPane.setPreferredSize(new Dimension((int)d.getWidth(), (int)(d.getHeight())));
            //scrollPane.add(localTable, BorderLayout.CENTER);
            localView.add(localPathPanel, BorderLayout.NORTH);
            localView.add(scrollPane, BorderLayout.CENTER);
            //String path = mHandler.getConfigure("filemanager_localpath");
            if(mLocalPath == null){
                mLocalPath = mHandler.getCurrentProjectSource();
                if(mLocalPath == null){
                    mLocalPath = "/";
                }
            }
            gotoLocalPath(mLocalPath);

            JSplitPane contentPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, localView);
            gui.add(contentPanel, BorderLayout.CENTER);

            /*StatusBar & ProgressBar Init*/
            JPanel simpleOutput = new JPanel(new BorderLayout(3,3));
            progressBar = new JProgressBar();
            simpleOutput.add(progressBar, BorderLayout.EAST);
            progressBar.setVisible(false);

            gui.add(simpleOutput, BorderLayout.SOUTH);

            statusBar = new FileManagerStatusBar();
            statusLabel = new JLabel();
            statusBar.add(statusLabel,BorderLayout.WEST);
            gui.add(statusBar, BorderLayout.SOUTH);
        }
        return gui;
    }

    /**
     * Run Local Shell Command by gnome-terminal
     * @param bashCmd command script
     * @param bWait wait the task
     */
    private void runShellCommand(String bashCmd, boolean  bWait){
        SwingWorker<Void, FileEx> worker = new SwingWorker<Void, FileEx>() {
            @Override
            public Void doInBackground() {
                if(bWait){
                    mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";read\"");
                }else{
                    mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";\"");
                }
            }

            @Override
            protected void process(List<FileEx> chunks) {
            }

            @Override
            protected void done() {
            }
        };
        worker.execute();
    }

    /*
    * Menu for Shell Button, Some useful shell command
    * */
    private JPopupMenu createShellMenu() {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem item;

        item = new JMenuItem("Shell");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String bashCmd = "echo 'Path:" + comboBoxPath.getEditor().getItem() + "';adb shell"
                runShellCommand(bashCmd,false);
                //mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";\"")
            }
        });
        popup.add(item);

        item = new JMenuItem("Adb root");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String bashCmd = "adb root ";
                runShellCommand(bashCmd, true);
            }
        });
        popup.add(item);

        item = new JMenuItem("Adb Remount");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String bashCmd = "adb remount ";
                runShellCommand(bashCmd, true);
            }
        });
        popup.add(item);

        item = new JMenuItem("Kill Process");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pid = mHandler.device.selectProcess(frame);
                if(pid != null){
                    String bashCmd = "adb shell kill " + pid;
                    runShellCommand(bashCmd, true);
                    //mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";read\"")
                }
            }
        });
        popup.add(item);


        popup.addSeparator();

        item = new JMenuItem("Memo Info");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pid = mHandler.device.selectProcess(frame);
                if(pid != null){
                    String bashCmd = "adb shell dumpsys meminfo " + pid;
                    runShellCommand(bashCmd, true);
                    //mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";read\"")
                }
            }
        });
        popup.add(item);

        item = new JMenuItem("Current Activity");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               String bashCmd = "adb shell dumpsys window w | grep \\/ | grep name= | cut -d = -f 3 | cut -d \\) -f 1";
               runShellCommand(bashCmd, true);
            }
        });
        popup.add(item);

        if(File.separator.equals("/")){
            item = new JMenuItem("Dumpsys");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dumpSys();
                }
            });
            popup.add(item);

            item = new JMenuItem("Device Info");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String bashCmd = "./plugin/info.sh";
                    runShellCommand(bashCmd, false);
                }
            });
            popup.add(item);
        }

        return popup;
    }

    /**
     * Call Android Dump System Services
     */
    void dumpSys(){
        String serviceList = mHandler.device.runAdb("shell service list");
        ArrayList<String> matchList = new ArrayList<String>();
        try {
            Pattern regex = Pattern.compile("\\d+\\s+([^:]+):\\s*\\[([^\\[\\]]*)\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL | Pattern.MULTILINE);
            Matcher regexMatcher = regex.matcher(serviceList);
            while (regexMatcher.find()) {
                matchList.add(regexMatcher.group(1));
            }
        } catch (PatternSyntaxException ex) {
            // Syntax error in the regular expression
        }
        if(matchList.size() > 0){
            matchList.sort(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o1.toLowerCase().compareTo(o2.toLowerCase());
                }
            } );
            matchList.add(0, "*");
            matchList = mHandler.ui.selectList("Select Services", matchList);
        }

        String dumpSysResult = "";
        File file = new File("__dumpsys.txt");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if(file.exists()){
                    file.delete();
                }
                if(matchList == null || matchList.size() == 0 || matchList.get(0).equals("*")){
                    String bashCmd = "adb shell dumpsys >> __dumpsys.txt";
                    mHandler.runSystemCommand(bashCmd);
                    java.awt.Desktop.getDesktop().open(file);
                }else{
                    for(String service: matchList){
                        mHandler.runSystemCommand("echo ::adb shell dumpsys "+service+" >> __dumpsys.txt");
                        String bashCmd = "adb shell dumpsys "+service+" >> __dumpsys.txt";
                        mHandler.runSystemCommand(bashCmd);
                        mHandler.runSystemCommand("echo  >> __dumpsys.txt");
                    }
                    java.awt.Desktop.getDesktop().open(file);
                }
            }
        });
    }

    /*
    * run task in thread
    * */
    void runInThread(Runnable runnable){
        new Thread(runnable).start();
    }

    /*
    * Show Context Menu fot Device Table
    * */
    private void showDeviceMenu(Component invoker, int x, int y) {
        FileTable table = (FileTable) invoker;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem item;
        FileEx fileSel = ((FileTableModel)table.getModel()).getFile(table.getSelectedRow());

        item = new JMenuItem("Refresh");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gotoPath(comboBoxPath.getEditor().getItem());;
            }
        });
        popup.add(item);

        /*
        item = new JMenuItem("Go ..");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gotoPath(new FileEx(comboBoxPath.getEditor().getItem()).getParent());
            }
        });
        popup.add(item);
        */

        item = new JMenuItem("Sync Tree");
        item.addActionListener(new ActionListener() {
            @Override
            void actionPerformed(ActionEvent e) {
                DefaultMutableTreeNode treeNode = selectTreeNode(new FileEx(comboBoxPath.getEditor().getItem()));
                deviceTree.setSelectionPath(new TreePath(treeNode.getPath()));
                deviceTree.scrollPathToVisible(new TreePath(treeNode.getPath()));
            }
        });
        popup.add(item);

        popup.addSeparator();

        if(fileSel != null && fileSel.isFile()){
            item = new JMenuItem("View");
            item.addActionListener(new ActionListener() {
                @Override
                void actionPerformed(ActionEvent e) {
                    runInThread(new Runnable() {
                        @Override
                        void run() {
                            String bashCmd = "adb shell cat " + fileSel.getPath() + " | gedit &";
                            mHandler.runSystemCommand(bashCmd);
                        }
                    });
                    //mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";read\"")
                }
            });
            popup.add(item);
        }

        item = new JMenuItem("Download");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                runWork(new Runnable() {
                    @Override
                    void run() {
                        int [] rows = table.getSelectedRows();
                        String strLocalPath = localPath.getText();
                        for(int row: rows){
                            FileEx file = ((FileTableModel)table.getModel()).getFile(row);
                            statusLabel.setText("pulling " + file.getName());
                            String saveFolder = strLocalPath;
                            if(file.isDirectory()){
                                saveFolder = saveFolder + File.separator + file.getName();
                                new File(saveFolder).mkdirs();
                            }
                            String ret = mHandler.device.runAdb("pull " + file.getPath() + " " + saveFolder);
                            statusLabel.setText("pull " + file.getName() + "--finished");
                        }
                        gotoLocalPath(strLocalPath);

                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            void run() {
                                for(int row: rows) {
                                    FileEx file = ((FileTableModel)table.getModel()).getFile(row);
                                    localTable.selectByFile(file.getName());
                                }
                            }
                        });
                    }
                })
            }
        });
        popup.add(item);

        item = new JMenuItem("Create Folder");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String folder = JOptionPane.showInputDialog("Folder:");
                if(folder != null){
                    runWork(new Runnable() {
                        @Override
                        void run() {
                            mHandler.device.runAdb("shell mkdir " + comboBoxPath.getEditor().getItem() + "/" + folder);
                            gotoPath(comboBoxPath.getEditor().getItem());
                            table.selectByFile(folder);
                        }
                    });
                }
            }
        });
        popup.add(item);

        item = new JMenuItem("Delete");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirmRet = JOptionPane.showConfirmDialog(null,"Delete file or folder  ?");
                if(confirmRet == JOptionPane.YES_OPTION){
                    runWork(new Runnable() {
                    @Override
                    public void run() {
                            int [] rows = table.getSelectedRows();
                            String folder = localPath.getText();
                            for(int row: rows){
                                FileEx file = ((FileTableModel)table.getModel()).getFile(row);
                                String ret = mHandler.device.runAdb("shell rm -r " + file.getPath());
                            }
                            gotoPath(comboBoxPath.getEditor().getItem());
                        }
                    });
                }
            }
        });
        popup.add(item);

        popup.addSeparator();

        item = new JMenuItem("Proc");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pid = mHandler.device.selectProcess(frame);
                if(pid != null){
                    gotoPath("/proc/" + pid);
                }
            }
        });
        popup.add(item);

        item = new JMenuItem("command");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String command = "adb shell cat %f";
                if(lastAdbCommand != null){
                    command = lastAdbCommand;
                }
                command = JOptionPane.showInputDialog("Command(%f:file %p:folder %pid:process):", command);
                if(command != null){
                    lastAdbCommand = command;
                    int row = table.getSelectedRow();
                    FileEx file = ((FileTableModel)table.getModel()).getFile(row);
                    if(command.indexOf("%pid") >= 0){
                        String pid = mHandler.device.selectProcess(frame);
                        if(pid != null){
                            command = command.replace("%pid", pid);
                        }
                    }
                    String bashCmd = command.replace("%f", file.getPath());
                    bashCmd = bashCmd.replace("%p", file.getParent());
                    runShellCommand(bashCmd, true);
                }
                //mHandler.runSystemCommand("gnome-terminal -t \"ADB-Shell\" -x bash -c \""+bashCmd+ ";read\"")
            }
        });
        popup.add(item);

        popup.show(invoker,(int)(x + popup.getWidth()/2), (int)(y + popup.getHeight()/2));
    }

    /**
     *Show Context Menu for Local File Table
     */
    private void showLocalMenu(Component invoker, int x, int y) {
        FileTable localTable = (FileTable) invoker;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem item;

        FileEx fileSel = ((FileTableModel)localTable.getModel()).getFile(localTable.getSelectedRow());

        item = new JMenuItem("Refresh");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gotoLocalPath(localPath.getText());;
            }
        });
        popup.add(item);

        popup.addSeparator();

        item = new JMenuItem("UpLoad");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runWork(new Runnable() {
                    @Override
                    public void run() {
                        int [] rows = localTable.getSelectedRows();
                        String ret = mHandler.device.runAdb("remount " );
                        String remotePath = comboBoxPath.getEditor().getItem();
                        for(int row: rows){
                            FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                            statusLabel.setText("Pushing " + file.getName());
                            saveFolder = remotePath;
                            if(file.isDirectory()){
                                saveFolder = saveFolder + "/" + file.getName();
                                mHandler.device.runAdb("mkdir " + saveFolder);
                            }
                            ret = mHandler.device.runAdb("push " + file.getPath() + " " + saveFolder);
                            statusLabel.setText("Push " + file.getName() + "--finished");
                        }

                        gotoPath(remotePath);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            void run() {
                                for(int row: rows) {
                                    FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                                    deviceTable.selectByFile(file.getName());
                                }
                            }
                        });
                    }
                });
            }
        });
        popup.add(item);

        item = new JMenuItem("Create Folder");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String folder = JOptionPane.showInputDialog("Folder:");
                if(folder != null){
                    mHandler.runSystemCommand("mkdir " + localPath.getText() + "/" + folder);
                    gotoLocalPath(localPath.getText());
                    localTable.selectByFile(folder);
                }

            }
        });
        popup.add(item);

        popup.addSeparator();

        item = new JMenuItem("Delete");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirmRet = JOptionPane.showConfirmDialog(null,"Delete file or folder?");
                if(confirmRet == JOptionPane.YES_OPTION){
                    int [] rows = localTable.getSelectedRows();
                    for(int row: rows){
                        FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                        mHandler.runSystemCommand("rm -r " + file.getPath());
                    }
                    gotoLocalPath(localPath.getText());
                }
            }
        });
        popup.add(item);


        item = new JMenuItem("Open");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    int row = localTable.getSelectedRow();
                    FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                    //desktop.open(file);
                    //desktop.open(file.getParentFile());
                    java.awt.Desktop.getDesktop().browse(file.toURI());
                } catch(Throwable t) {
                }
            }
        });
        popup.add(item);

        if(fileSel.isFile()){
            item = new JMenuItem("Edit");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        desktop.edit(fileSel);
                    } catch(Throwable t) {
                    }
                }
            });
            popup.add(item);
        }

        item = new JMenuItem("command");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String command = "cat %f";
                if(lastCommand != null){
                    command = lastCommand;
                }
                String lastAdbCommand = null;
                command = JOptionPane.showInputDialog("Command(%f:file %p:folder):", command);
                if(command != null) {
                    lastCommand = command;
                    int row = localTable.getSelectedRow();
                    FileEx file = ((FileTableModel) localTable.getModel()).getFile(row);
                    String bashCmd = command.replace("%f", file.getPath());
                    bashCmd = bashCmd.replace("%p", file.getParent());
                    runShellCommand(bashCmd, true);
                }
            }
        });
        popup.add(item);


        item = new JMenuItem("Explorer");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String saveFolder = comboBoxPath.getEditor().getItem();
                int row = localTable.getSelectedRow();
                FileEx file = ((FileTableModel)localTable.getModel()).getFile(row);
                mHandler.runSystemCommand("nautilus " + file.getPath());
            }
        });
        popup.add(item);

        popup.show(invoker,(int)(x + popup.getWidth()/2), (int)(y + popup.getHeight()/2));
    }

    String  saveFolder = "";

    /**
    static class FileManagerConfigure implements Serializable{
        ArrayList<String> favorList = null;
        String mLocalPath = null;
    }
    The Class in the Groovy Script can't be Serialize. Because the class is dynamically loaded.
     HashMap<String, Object> can be Serialize,use this for instead.
     */
    HashMap<String, Object> mConfigure = new HashMap<String, Object>();
    ArrayList<String> favorList = null;
    String mLocalPath = null;

    void setFavor(String path){
        if(favorList == null){
            favorList = new ArrayList<String>();
        }
        if(!favorList.contains(path)){
            favorList.add(path);
            saveConfigure();
            updateFavor();
        }
        bFavorPath = true;
        btnSetFovar.setIcon(iconFavorOn);
    }

    void removeFavor(String path){
        if(favorList == null){
            favorList = new ArrayList<String>();
        }
        if(favorList.contains(path)){
            favorList.remove(path);
            saveConfigure();
            updateFavor();
        }
        bFavorPath = false;
        btnSetFovar.setIcon(iconFavorOff);
    }

    /*
    * Update Favor Button by current comboBoxPath
    * */
    void updateFavor(){
        comboBoxPath.removeAllItems();
        for(String item: favorList){
            comboBoxPath.addItem(item);
        }
    }

    /*
    * Save Configure to file, include favor,localPath
    * */
    void saveConfigure(){
        FileOutputStream fs = null;
        mConfigure.put("favor", favorList);
        mConfigure.put("localPath", localPath.getText());
        try {
            String configurePath = mHandler.getScriptDataPath() + "filemanager.dat";
            fs = new FileOutputStream(configurePath);
            ObjectOutputStream os = null;
            os = new ObjectOutputStream(fs);
            os.writeObject(mConfigure);
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    * Load Configure from file
    * */
    void loadConfigure(){
        try {
            String configurePath = mHandler.getScriptDataPath() + "filemanager.dat";
            if(!new File(configurePath).exists()){
                return ;
            }
            FileInputStream fs = new FileInputStream(configurePath);
            ObjectInputStream  s = new  ObjectInputStream (fs);
            mConfigure = (HashMap<String, Object>)s.readObject();
            favorList = mConfigure.get("favor");
            mLocalPath = mConfigure.get("localPath");
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    void gotoLocalPath(String path){
        if(path == null){
            return;
        }

        FileEx file = new FileEx(path);
        if (!file.exists() || !file.isDirectory()) {

            path = FileSystemView.getFileSystemView().getHomeDirectory().getPath();
            file = new FileEx(path);
        }

        if (file.exists() && file.isDirectory()) {
            ArrayList<FileEx> files = file.listDir();
            localTable.setTableData(files, path);
        }
        localPath.setText(file.getPath());
        saveConfigure();
        //mHandler.setConfigure("filemanager_localpath", path);
    }

    /*
    * Device browser path
    * */
    void gotoPath(String path){
        ADBFile file = new ADBFile(path);

        if (file.isDirectory()) {
            ArrayList<ADBFile> files = file.listDir();
            for(ADBFile adbFile:files){
                if(adbFile.isDirectory()){
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(adbFile);
                }
            }
            deviceTable.setTableData(files, path);
        }

        if(favorList == null) {
            bFavorPath = false;
        }else{
            bFavorPath = favorList.contains(file.getPath());
        }

        if(bFavorPath){
            btnSetFovar.setIcon(iconFavorOn);
        }else{
            btnSetFovar.setIcon(iconFavorOff);
        }
        comboBoxPath.getEditor().setItem(file.getPath());
    }

    /*
    * Device table goto root
    * */
    public void showRootFile() {
        gotoPath("/");
    }

    /*
    * Locate file in Device Tree
    * */
    private DefaultMutableTreeNode locateTreeNode(FileEx find){
        if(find.getPath() == "/"){
            return treeModel.getRoot();
        }
        DefaultMutableTreeNode parent = locateTreeNode(find.getParentFile());
        if(parent.getChildCount() <= 0){
            FileEx file = (FileEx) parent.getUserObject();
            ArrayList<FileEx> files = file.listDir(); //!!
            for (FileEx child : files) {
                if (child.isDirectory()) {
                    parent.add(new DefaultMutableTreeNode(child));
                }
            }
        }
        for(int i = 0 ; i < parent.getChildCount(); i++){
            DefaultMutableTreeNode check = parent.getChildAt(i);
            if(((FileEx)check.getUserObject()).getPath().compareTo(find.getPath()) == 0){
                return check;
            }
        }
        return null;
    }

    /*
    * Search and select the node of device Tree
    * */
    private DefaultMutableTreeNode selectTreeNode(FileEx find){
        DefaultMutableTreeNode ret= null;
        if(find.getPath() == "/"){
            ret = (DefaultMutableTreeNode)treeModel.getRoot();
            deviceTree.collapsePath(new TreePath(ret.getPath()));
            return treeModel.getRoot();
        }
        DefaultMutableTreeNode parent = locateTreeNode(find.getParentFile());
        if(parent == null){
           return ret;
        }
        if(parent.getChildCount() == 0){
            FileEx parentFile = (FileEx)parent.getUserObject();
            ArrayList<FileEx> files = parentFile.listDir();
            for(FileEx check: files){
                if(check.isDirectory()){
                    parent.add(new DefaultMutableTreeNode(check));
                }
            }
        }
        for(int i = 0 ; i < parent.getChildCount(); i++){
            DefaultMutableTreeNode check = parent.getChildAt(i);
            deviceTree.collapsePath(new TreePath(check.getPath()));
            if(check.getUserObject() == find){
                deviceTree.expandPath(new TreePath(check.getPath()));
                ret = check;
            }
        }

        return  ret;
    }


    /**
     * Add the files that are contained within the directory of this node.
     *  */
    private void showChildren(final DefaultMutableTreeNode node) {
        deviceTree.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, FileEx> worker = new SwingWorker<Void, FileEx>() {
            @Override
            public Void doInBackground() {
                FileEx file = (FileEx) node.getUserObject();
                if (file.isDirectory()) {
                    ArrayList<FileEx> files = file.listDir(); //!!
                    if (node.isLeaf()) {
                        for (FileEx child : files) {
                            if (child.isDirectory()) {
                                publish(child);
                            }
                        }
                    }
                    comboBoxPath.getEditor().setItem(file.getPath());
                    deviceTable.setTableData(files, file.getPath());
                }
                return null;
            }

            @Override
            protected void process(List<FileEx> chunks) {
                for (FileEx child : chunks) {
                    node.add(new DefaultMutableTreeNode(child));
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                deviceTree.setEnabled(true);
            }
        };
        worker.execute();
    }

    /*
    * run work task by SwingWorker
    * */
    private void runWork(Runnable runnable) {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        SwingWorker<Void, FileEx> worker = new SwingWorker<Void, FileEx>() {
            @Override
            public Void doInBackground() {
                runnable.run();
            }

            @Override
            protected void process(List<FileEx> chunks) {
             }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
            }
        };
        worker.execute();
    }

    /** Update the File details view with the details of this File. */
    private void setFileDetails(FileEx file) {
        if(file == null){
            return;
        }
        currentFile = file;

        frame = (JFrame)gui.getTopLevelAncestor();
        if (frame!=null) {
            frame.setTitle(
                APP_TITLE +
                " :: " +
                file.getName());
        }

        gui.repaint();
    }

    /**
     * The Main Entry of Plugin
     * @param handler Plugin handler, used to inter
     * @return
     */
    String run(PluginHandler handler) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame f = new JFrame(APP_TITLE);

                FileManager fileManager = new FileManager(handler);
                f.setContentPane(fileManager.getGui());

                f.pack();
                f.pack();
                f.setMinimumSize(f.getSize());

                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                screenSize.height = screenSize.height / 2;
                screenSize.width = screenSize.width / 2;
                f.setPreferredSize(screenSize);
                f.setLocationRelativeTo(null);
                f.setExtendedState(JFrame.MAXIMIZED_BOTH);

                f.setVisible(true);

                setFrameIcon(f, handler.getWorkPath() + File.separator + "filemanager.png");
                fileManager.showRootFile();
            }
        });
        return null;
    }


    /*
    * set frame icon
    * */
    static private void setFrameIcon(JFrame frame, String iconPath){
        URI urlIcon = new File(iconPath).toURI();
        ArrayList<Image> images = new ArrayList<Image>();
        images.add(ImageIO.read(urlIcon.toURL()));
        images.add(ImageIO.read(urlIcon.toURL()));
        frame.setIconImages(images);
    }

    /** A TableModel to hold FileEx[]. */
    class FileTableModel extends AbstractTableModel {
        FileEx upFile = new FileEx("..");
        private ArrayList<FileEx> files;
        boolean showState = true;
        //private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
        private String[] columns = [
                "Icon",
                "File",
                "Size",
                "Last Modified",
                "State",
        ];

        FileTableModel() {
            this(new ArrayList<FileEx>());
        }

        FileTableModel(ArrayList<FileEx> files, boolean showState) {
            this.files = files;
            this.showState = showState
        }

        FileTableModel(boolean showState) {
            this.showState = showState
        }

        public Object getValueAt(int row, int column) {
            FileEx file;
            if(row == 0){
                file = upFile;
                switch (column) {
                    case 0:
                        return file.getIcon();
                    case 1:
                        return file.getName();
                    case 2:
                        return "";
                    case 3:
                        return "";
                    case 4:
                        return "";
                    default:
                        break;
                }
            }else {
                file = files.get(row - 1);
                switch (column) {
                    case 0:
                        return file.getIcon();
                    case 1:
                        return file.getName();
                    case 2:
                        return df.format(file.length());
                    case 3:
                        return file.getDate();
                    case 4:
                        return file.getState();
                    default:
                        break;;
                }
           }
            return "";
        }

        public int getColumnCount() {
            if(!showState){
                return columns.length - 1;
            }
            return columns.length;
        }

        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0:
                    return ImageIcon.class;
            }
            return String.class;
        }

        public String getColumnName(int column) {
            return columns[column];
        }

        public int getRowCount() {
            return files.size() + 1;
        }

        public FileEx getFile(int row) {
            if(row < 0){
                return null;
            }
            if(row == 0){
                return upFile;
            }
            return files.get(row -1);
        }

        public void setFiles(ArrayList<FileEx> files) {
            this.files = files;
            fireTableDataChanged();
        }
    }

    /** A TreeCellRenderer for File. */
    class FileTreeCellRenderer extends DefaultTreeCellRenderer {

        private FileSystemView fileSystemView;

        private JLabel label;

        FileTreeCellRenderer() {
            label = new JLabel();
            label.setOpaque(true);
            fileSystemView = FileSystemView.getFileSystemView();
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {

            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            FileEx file = (FileEx)node.getUserObject();
            label.setIcon(file.getIcon());
            label.setText(file.getName());
            label.setToolTipText(file.getPath());

            if (selected) {
                label.setBackground(backgroundSelectionColor);
                label.setForeground(textSelectionColor);
            } else {
                label.setBackground(backgroundNonSelectionColor);
                label.setForeground(textNonSelectionColor);
            }

            return label;
        }
    }

    /**
     * FileEx extends from File,
     * use for FileTableModel handling data
     */
    class FileEx extends File {
        static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        FileEx(String pathname) {
            super(pathname)
        }

        ArrayList<FileEx> listDir(){
            ArrayList<FileEx> ret = new ArrayList<FileEx>();
            File [] files  = this.listFiles();
            for(File file:files){
                ret.add(new FileEx(file.getPath()));
            }
            ret.sort(new Comparator<FileEx>() {
                @Override
                public int compare(FileEx o1, FileEx o2) {
                    if(o1.isDirectory() != o2.isDirectory()){
                        return o1.isDirectory()?-1:1;
                    }
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return ret;
        }

        public FileEx getParentFile() {
            return new FileEx(getParent());
        }

        Icon getIcon(){
            return UIManager.getIcon(this.isFile() ? "FileView.fileIcon": "FileView.directoryIcon");
        }

        String getState(){
            return "";
        }

        String getDate(){
            return simpleDateFormat.format(new Date(lastModified()));

        }
    }

    /*
    * Proxy of Device File
    * */
    class ADBFile extends FileEx{
        boolean  mBeFile = false;
        long length = 0;
        String mState = "";
        Date mDate = null;
        ADBFile(String pathname) {
            super(pathname)
        }

        ArrayList<ADBFile> listDir(){
            ArrayList<ADBFile> ret = new ArrayList<ADBFile>();
            //ret.add(new ADBFile(".."));
            mHandler.device.runAdb("shell ls -a -l " + this.getAbsolutePath(), new ActionListener() {
                @Override
                void actionPerformed(ActionEvent e) {
                    String line = e.getSource();
                    if(line.indexOf("Permission denied") >= 0){
                    }else{
                        ADBFile file = parseLine(line);
                        if(file != null
                          && file.getName().compareTo(".") != 0
                          && file.getName().compareTo("..") != 0){
                            ret.add(file);
                        }
                    }
                }
            });
            ret.sort(new Comparator<FileEx>() {
                @Override
                int compare(FileEx o1, FileEx o2) {
                    if(o1.isDirectory() != o2.isDirectory()){
                        return o1.isDirectory()?-1:1;
                    }
                    return o1.getName().compareTo(o2.getName());
                }
            });
            return ret;
        }

        //([\w-]+)\s+[\w\d\s]*?([\d]*)\s([\d-]*\s[\d:]*)\s*([^\s]+)[\r\n]
        Pattern regex = Pattern.compile("([\\w-]+)\\s+[\\w\\d\\s]*?([\\d]*)\\s([\\d-]*\\s[\\d:]*)\\s*([^\\s]+)[\\r\\n]");
        ADBFile parseLine(String line){
            //System.out.println(line);
            line += "\n";
            try {
                Matcher regexMatcher = regex.matcher(line);
                if (regexMatcher.find()) {
                    String fileName = regexMatcher.group(4);
                    boolean  bFolder = false;
                    if(line.indexOf("->") > 0){
                        String link = line.substring(line.lastIndexOf("->") + 2);
                        link = link.trim();
                        if(link.startsWith("/")){
                            bFolder = true;
                        }
                    }
                    ADBFile ret = new ADBFile(this.getAbsolutePath() + "/" + fileName);
                    String strLeng = regexMatcher.group(2);
                    if(strLeng == null || strLeng.isEmpty()){
                        ret.length = 0;
                    }else{
                        ret.length = Long.valueOf(strLeng);
                    }

                    ret.mBeFile = !bFolder;
                    ret.mState = regexMatcher.group(1);
                    if(ret.mState.startsWith("-")){
                        ret.mBeFile = true;
                    }else{
                        ret.mBeFile = false;
                    }
                    String time = regexMatcher.group(3);
                    if(time.length() - time.replace("\\:", "").length() < 3){
                        time += ":00";
                    }

                    try{
                        ret.mDate = simpleDateFormat.parse(time);
                    }catch (NumberFormatException e){
                        ret.mDate = null;
                        System.out.println("Parse Date Error!");
                    }

                    return ret;
                }
            } catch (PatternSyntaxException ex) {
                // Syntax error in the regular expression
            }
            return null;
        }

        public ADBFile getParentFile() {
            return new ADBFile(getParent());
        }

        @Override
        boolean isDirectory() {
            return !mBeFile;
        }

        @Override
        boolean isFile() {
            return mBeFile;
        }

        @Override
        long length() {
            return length;
        }

        Icon getIcon(){
            return UIManager.getIcon(mBeFile ? "FileView.fileIcon": "FileView.directoryIcon");
        }

        String getState(){
            return mState;
        }

        String getDate(){
            if(mDate == null){
                return "";
            }
            return simpleDateFormat.format(mDate);
        }
    }


    /*
    * Table used to display files
    * */
    class FileTable extends JTable{
        private FileTableModel fileTableModel;
        String mCurrentPath = "";

        boolean  mDeviceFileTable = true;
        FileTable(boolean  deviceFileTable) {
            mDeviceFileTable = deviceFileTable;
        }

        /*
        * setup Table Data
        * */
        public void setTableData(final ArrayList<FileEx> files, String currentPath) {
            mCurrentPath = currentPath;
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (fileTableModel==null) {
                        fileTableModel = new FileTableModel(mDeviceFileTable);
                        setModel(fileTableModel);
                    }

                    DefaultTableCellRenderer d = new DefaultTableCellRenderer();

                    //设置表格单元格的对齐方式为居中对齐方式
                    setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                    fileTableModel.setFiles(files);
                    if (!cellSizesSet && files.size() > 0) {
                        Icon icon = files.get(0).getIcon();

                        // size adjustment to better account for icons
                        setRowHeight( icon.getIconHeight()+ ROW_ICON_PADDING);
                        d.setHorizontalAlignment(JLabel.RIGHT);
                        TableColumn col = getColumn(getColumnName(2));
                        col.setCellRenderer(d);
                        /*
                        setColumnWidth(0,-1);
                        setColumnWidth(2,60);
                        getColumnModel().getColumn(2).setMaxWidth(120);
                        setColumnWidth(3,-1);
                        setColumnWidth(4,80);
                        */
                        cellSizesSet = true;
                    }

                    if(FileTable.this.getModel().getRowCount()){
                        FileTable.this.setRowSelectionInterval(0, 0);
                    }
                }
            });
        }

        private void setColumnWidth(int column, int width) {
            TableColumn tableColumn = getColumnModel().getColumn(column);
            if (width<0) {
                // use the preferred width of the header..
                JLabel label = new JLabel( (String)tableColumn.getHeaderValue() );
                Dimension preferred = label.getPreferredSize();
                // altered 10->14 as per camickr comment.
                width = (int)preferred.getWidth()+14;
            }
            tableColumn.setPreferredWidth(width);
            tableColumn.setMaxWidth(width);
            tableColumn.setMinWidth(width);
        }

        public void selectByFile(String item){
            int iRowCount = fileTableModel.getRowCount();
            for(int i = 0 ;i < iRowCount; i++){
                FileEx file = fileTableModel.getFile(i);
                if(file.getName().compareTo(item) == 0){
                    changeSelection(i, 1, false, false);
                    Rectangle rectTable =  this.getVisibleRect();
                    Rectangle rectCell = getCellRect(i,0,true);

                    Rectangle rectangle = new Rectangle((int)rectCell.x, (int)(rectCell.y - rectTable.getHeight() / 2), (int)rectTable.width, (int)rectTable.height);
                    this.scrollRectToVisible(rectangle);
                }
            }
        }


        public String getToolTipText(MouseEvent e) {
            int row= rowAtPoint(e.getPoint());
            int col= columnAtPoint(e.getPoint());
            String tiptextString=null;

            if(row>-1 && col>-1){
                Object value= getValueAt(row, col);
                if(null!=value && !"".equals(value))
                    tiptextString=value.toString();//悬浮显示单元格内容
            }
            if(tiptextString != null){
                tiptextString = tiptextString.replace("\n", " ");
            }
            return tiptextString;
        }
    }
}


/*
* Status bar
* */
public class FileManagerStatusBar extends JPanel{
    public  JLabel statusBarText = null;
    public  JLabel statusBarLogText = null;
    private static int STARUSBAR_HEIGHT = 23;

    public FileManagerStatusBar() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(10, STARUSBAR_HEIGHT));

        statusBarText = new JLabel(" ");
        statusBarText.setPreferredSize(new java.awt.Dimension(400, STARUSBAR_HEIGHT));
        add(statusBarText, BorderLayout.WEST);
        statusBarLogText = new JLabel("");
        statusBarLogText.setPreferredSize(new java.awt.Dimension(400, STARUSBAR_HEIGHT));
        add(statusBarLogText, BorderLayout.CENTER);
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    public void updateStatus(String statusInfo) {
        statusBarLogText.setText(statusInfo);
    }
}
