package cn.kizzzy.toolkit.controller;

import cn.kizzzy.helper.FileHelper;
import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.helper.StringHelper;
import cn.kizzzy.javafx.common.JavafxHelper;
import cn.kizzzy.javafx.common.MenuItemArg;
import cn.kizzzy.javafx.display.DisplayOperator;
import cn.kizzzy.javafx.display.DisplayTabView;
import cn.kizzzy.javafx.setting.ISettingDialogFactory;
import cn.kizzzy.javafx.setting.SettingDialogFactory;
import cn.kizzzy.sghero.RdfFile;
import cn.kizzzy.sghero.SgHeroConfig;
import cn.kizzzy.toolkit.view.AbstractView;
import cn.kizzzy.vfs.IPackage;
import cn.kizzzy.vfs.ITree;
import cn.kizzzy.vfs.handler.JsonFileHandler;
import cn.kizzzy.vfs.handler.RdfFileHandler;
import cn.kizzzy.vfs.pack.FilePackage;
import cn.kizzzy.vfs.pack.RdfPackage;
import cn.kizzzy.vfs.tree.IdGenerator;
import cn.kizzzy.vfs.tree.Leaf;
import cn.kizzzy.vfs.tree.Node;
import cn.kizzzy.vfs.tree.NodeComparator;
import cn.kizzzy.vfs.tree.RdfTreeBuilder;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

abstract class SgHeroViewBase extends AbstractView {
    
    @FXML
    protected ChoiceBox<String> show_choice;
    
    @FXML
    protected TextField filterValue;
    
    @FXML
    protected CheckBox include_leaf;
    
    @FXML
    protected CheckBox lock_tab;
    
    @FXML
    protected TreeView<Node> tree_view;
    
    @FXML
    protected DisplayTabView display_tab;
    
    @FXML
    protected ProgressBar progress_bar;
    
    @FXML
    protected Label tips;
    
    @Override
    public String getName() {
        return "SgHeroDisplayer";
    }
}

@MenuParameter(path = "辅助/三国豪侠传/解包器(本地)")
@PluginParameter(url = "/fxml/toolkit/sghero_local_view.fxml", title = "三国豪侠传(解包)")
public class SgHeroLocalController extends SgHeroViewBase implements Initializable {
    
    protected static final String CONFIG_PATH = "sghero/local.config";
    
    protected static final Comparator<TreeItem<Node>> comparator
        = Comparator.comparing(TreeItem<Node>::getValue, new NodeComparator());
    
    protected IPackage userVfs;
    protected SgHeroConfig config;
    protected ISettingDialogFactory dialogFactory;
    
    protected IPackage vfs;
    protected ITree tree;
    
    protected DisplayOperator<IPackage> displayer;
    
    protected TreeItem<Node> dummyTreeItem;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userVfs = new FilePackage(System.getProperty("user.home") + "/.user");
        userVfs.getHandlerKvs().put(SgHeroConfig.class, new JsonFileHandler<>(SgHeroConfig.class));
        
        config = userVfs.load(CONFIG_PATH, SgHeroConfig.class);
        config = config != null ? config : new SgHeroConfig();
        
        JavafxHelper.initContextMenu(tree_view, () -> stage.getScene().getWindow(), new MenuItemArg[]{
            new MenuItemArg(0, "设置", this::openSetting),
            new MenuItemArg(1, "加载RDF", this::loadRdf),
            new MenuItemArg(2, "导出/文件", event -> exportFile(false)),
            new MenuItemArg(2, "导出/文件(递归)", event -> exportFile(true)),
            new MenuItemArg(2, "导出/打开路径", this::openExportFolder),
            new MenuItemArg(3, "复制路径", this::copyPath),
        });
        
        dummyTreeItem = new TreeItem<>();
        tree_view.setRoot(dummyTreeItem);
        tree_view.setShowRoot(false);
        tree_view.getSelectionModel().selectedItemProperty().addListener(this::onSelectItem);
        
        lock_tab.selectedProperty().addListener((observable, oldValue, newValue) -> {
            display_tab.setPin(newValue);
        });
        
        displayer = new DisplayOperator<>("cn.kizzzy.sghero.display", display_tab, IPackage.class);
        displayer.load();
    }
    
    @Override
    public void stop() {
        if (tree != null) {
            tree.stop();
        }
        
        userVfs.save(CONFIG_PATH, config);
        
        super.stop();
    }
    
    protected void onSelectItem(Observable observable, TreeItem<Node> oldValue, TreeItem<Node> newValue) {
        if (newValue != null) {
            Node folder = newValue.getValue();
            Leaf thumbs = null;
            
            if (folder.leaf) {
                thumbs = (Leaf) folder;
            } else {
                newValue.getChildren().clear();
                
                Iterable<Node> list = folder.children.values();
                for (Node temp : list) {
                    TreeItem<Node> child = new TreeItem<>(temp);
                    newValue.getChildren().add(child);
                }
                newValue.getChildren().sort(comparator);
            }
            
            if (thumbs != null) {
                displayer.display(thumbs.path);
            }
        }
    }
    
    @FXML
    protected void openSetting(ActionEvent actionEvent) {
        if (dialogFactory == null) {
            dialogFactory = new SettingDialogFactory(stage);
        }
        dialogFactory.show(config);
    }
    
    protected void loadRdf(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择rdf文件");
        if (StringHelper.isNotNullAndEmpty(config.last_rdf)) {
            File lastFolder = new File(config.last_rdf);
            if (lastFolder.exists()) {
                chooser.setInitialDirectory(lastFolder);
            }
        }
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RDF", "*.rdf")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null && file.getAbsolutePath().endsWith(".rdf")) {
            config.last_rdf = file.getParent();
            
            new Thread(() -> {
                try {
                    loadRdfImpl(file);
                } catch (Exception e) {
                    LogHelper.error("load rdf error", e);
                }
            }).start();
        }
    }
    
    private void loadRdfImpl(File file) {
        IPackage iPackage = new FilePackage(file.getParent());
        iPackage.getHandlerKvs().put(RdfFile.class, new RdfFileHandler());
        
        RdfFile rdfFile = iPackage.load(FileHelper.getName(file.getAbsolutePath()), RdfFile.class);
        tree = new RdfTreeBuilder(rdfFile, new IdGenerator()).build();
        
        vfs = new RdfPackage(file.getParent(), tree);
        
        displayer.setContext(vfs);
        
        Platform.runLater(() -> {
            dummyTreeItem.getChildren().clear();
            
            final List<Node> nodes = tree.listNode(0);
            for (Node node : nodes) {
                dummyTreeItem.getChildren().add(new TreeItem<>(node));
            }
        });
    }
    
    @FXML
    protected void exportFile(boolean recursively) {
        final TreeItem<Node> selected = tree_view.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        
        if (StringHelper.isNullOrEmpty(config.export_file_path) || !new File(config.export_file_path).exists()) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择保存文件的文件夹");
            File file = chooser.showDialog(stage);
            if (file == null) {
                return;
            }
            config.export_file_path = file.getAbsolutePath();
        }
        
        IPackage target = null;
        Node node = selected.getValue();
        
        List<Leaf> list = tree.listLeaf(selected.getValue(), recursively);
        for (Leaf leaf : list) {
            try {
                if (target == null) {
                    String pkgName = leaf.pack.replace(".rdf", "");
                    target = new FilePackage(config.export_file_path + "/" + pkgName);
                }
                
                byte[] data = vfs.load(leaf.path, byte[].class);
                if (data != null) {
                    target.save(leaf.path, data);
                }
            } catch (Exception e) {
                LogHelper.info(String.format("export image failed: %s", leaf.name), e);
            }
        }
    }
    
    protected void openExportFolder(ActionEvent actionEvent) {
        new Thread(() -> {
            try {
                if (StringHelper.isNotNullAndEmpty(config.export_file_path)) {
                    Desktop.getDesktop().open(new File(config.export_file_path));
                }
            } catch (Exception e) {
                LogHelper.error("open export folder error", e);
            }
        }).start();
    }
    
    protected void copyPath(ActionEvent actionEvent) {
        TreeItem<Node> selected = tree_view.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Node node = selected.getValue();
            if (node.leaf) {
                Leaf leaf = (Leaf) node;
                
                String path = leaf.path.replace("\\", "\\\\");
                StringSelection selection = new StringSelection(path);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(selection, selection);
            }
        }
    }
    
    private TreeItem<Node> filterRoot;
    
    @FXML
    protected void onFilter(ActionEvent event) {
        final String regex = filterValue.getText();
        if (StringHelper.isNullOrEmpty(regex)) {
            return;
        }
        
        try {
            Pattern.compile(regex);
        } catch (Exception e) {
            return;
        }
        
        if (filterRoot == null) {
            filterRoot = new TreeItem<>(new Node(0, "[Filter]"));
            dummyTreeItem.getChildren().add(filterRoot);
        }
        
        filterRoot.getChildren().clear();
        
        List<Node> list = tree.listNodeByRegex(regex);
        for (Node folder : list) {
            filterRoot.getChildren().add(new TreeItem<>(folder));
        }
        
        filterRoot.getChildren().sort(comparator);
    }
}