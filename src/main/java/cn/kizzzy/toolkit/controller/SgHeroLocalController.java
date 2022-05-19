package cn.kizzzy.toolkit.controller;

import cn.kizzzy.helper.FileHelper;
import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.helper.StringHelper;
import cn.kizzzy.javafx.StageHelper;
import cn.kizzzy.javafx.common.JavafxHelper;
import cn.kizzzy.javafx.common.MenuItemArg;
import cn.kizzzy.javafx.display.DisplayOperator;
import cn.kizzzy.javafx.display.DisplayTabView;
import cn.kizzzy.javafx.setting.SettingDialog;
import cn.kizzzy.sghero.RdfFile;
import cn.kizzzy.sghero.SgHeroConfig;
import cn.kizzzy.toolkit.view.AbstractView;
import cn.kizzzy.vfs.IPackage;
import cn.kizzzy.vfs.ITree;
import cn.kizzzy.vfs.handler.JsonFileHandler;
import cn.kizzzy.vfs.handler.RdfFileHandler;
import cn.kizzzy.vfs.pack.CombinePackage;
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
    
    private static final String CONFIG_PATH = "sghero/local.config";
    
    private static final Comparator<TreeItem<Node>> comparator
        = Comparator.comparing(TreeItem<Node>::getValue, new NodeComparator());
    
    private final StageHelper stageHelper
        = new StageHelper();
    
    private IPackage userVfs;
    private SgHeroConfig config;
    
    private CombinePackage vfs;
    private IdGenerator idGenerator;
    
    private DisplayOperator<IPackage> displayer;
    
    private TreeItem<Node> dummyRoot;
    private TreeItem<Node> filterRoot;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userVfs = new FilePackage(System.getProperty("user.home") + "/.user");
        userVfs.getHandlerKvs().put(SgHeroConfig.class, new JsonFileHandler<>(SgHeroConfig.class));
        
        config = userVfs.load(CONFIG_PATH, SgHeroConfig.class);
        config = config != null ? config : new SgHeroConfig();
        
        stageHelper.addFactory(SettingDialog::new, SettingDialog.class);
        
        JavafxHelper.initContextMenu(tree_view, () -> stage.getScene().getWindow(), new MenuItemArg[]{
            new MenuItemArg(0, "设置", this::openSetting),
            new MenuItemArg(1, "加载RDF", this::loadRdf),
            new MenuItemArg(2, "导出/文件", event -> exportFile(false)),
            new MenuItemArg(2, "导出/文件(递归)", event -> exportFile(true)),
            new MenuItemArg(2, "导出/打开路径", this::openExportFolder),
            new MenuItemArg(3, "复制路径", this::copyPath),
        });
        
        dummyRoot = new TreeItem<>();
        tree_view.setRoot(dummyRoot);
        tree_view.setShowRoot(false);
        tree_view.getSelectionModel().selectedItemProperty().addListener(this::onSelectItem);
        
        vfs = new CombinePackage();
        idGenerator = new IdGenerator();
        
        displayer = new DisplayOperator<>("cn.kizzzy.sghero.display", display_tab, IPackage.class);
        displayer.load();
        displayer.setContext(vfs);
    }
    
    @Override
    public void stop() {
        if (vfs != null) {
            vfs.stop();
        }
        
        userVfs.save(CONFIG_PATH, config);
        
        super.stop();
    }
    
    @FXML
    private void onFilter(ActionEvent event) {
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
            filterRoot = new TreeItem<>(new Node(-1, "[Filter]"));
            dummyRoot.getChildren().add(filterRoot);
        }
        
        filterRoot.getChildren().clear();
        
        List<Node> list = vfs.listNodeByRegex(regex);
        for (Node folder : list) {
            filterRoot.getChildren().add(new TreeItem<>(folder));
        }
        
        filterRoot.getChildren().sort(comparator);
    }
    
    private void onSelectItem(Observable observable, TreeItem<Node> oldValue, TreeItem<Node> newValue) {
        Node node = newValue == null ? null : newValue.getValue();
        if (node != null) {
            if (node.leaf) {
                Leaf leaf = (Leaf) node;
                
                displayer.display(leaf.path);
            } else {
                newValue.getChildren().clear();
                
                Iterable<Node> list = node.children.values();
                for (Node temp : list) {
                    TreeItem<Node> child = new TreeItem<>(temp);
                    newValue.getChildren().add(child);
                }
                newValue.getChildren().sort(comparator);
            }
        }
    }
    
    private void openSetting(ActionEvent actionEvent) {
        SettingDialog.Args args = new SettingDialog.Args();
        args.target = config;
        
        stageHelper.show(stage, args, SettingDialog.class);
    }
    
    private void loadRdf(ActionEvent actionEvent) {
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
        IPackage dataVfs = new FilePackage(file.getParent());
        dataVfs.getHandlerKvs().put(RdfFile.class, new RdfFileHandler());
        
        RdfFile rdfFile = dataVfs.load(FileHelper.getName(file.getAbsolutePath()), RdfFile.class);
        if (rdfFile == null) {
            return;
        }
        
        ITree tree = new RdfTreeBuilder(rdfFile, idGenerator).build();
        IPackage vfs = new RdfPackage(file.getParent(), tree);
        
        doAfterLoadVfs(vfs);
    }
    
    private void doAfterLoadVfs(IPackage _vfs) {
        vfs.addPackage(_vfs);
        
        Platform.runLater(() -> {
            dummyRoot.getChildren().clear();
            
            final List<Node> nodes = vfs.listNode(0);
            for (Node node : nodes) {
                TreeItem<Node> child = new TreeItem<>(node);
                dummyRoot.getChildren().add(child);
            }
            
            if (filterRoot != null) {
                dummyRoot.getChildren().add(filterRoot);
            }
        });
    }
    
    private void openExportFolder(ActionEvent actionEvent) {
        openFolderImpl(config.export_file_path);
    }
    
    private void openFolderImpl(String path) {
        new Thread(() -> {
            try {
                if (StringHelper.isNotNullAndEmpty(path)) {
                    Desktop.getDesktop().open(new File(path));
                }
            } catch (Exception e) {
                LogHelper.error(String.format("open folder error, %s", path), e);
            }
        }).start();
    }
    
    private void exportFile(boolean recursively) {
        if (StringHelper.isNullOrEmpty(config.export_file_path) || !new File(config.export_file_path).exists()) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择保存文件的文件夹");
            File file = chooser.showDialog(stage);
            if (file == null) {
                return;
            }
            config.export_file_path = file.getAbsolutePath();
        }
        
        final TreeItem<Node> selected = tree_view.getSelectionModel().getSelectedItem();
        Node node = selected == null ? null : selected.getValue();
        if (node == null) {
            return;
        }
        
        IPackage target = null;
        
        List<Leaf> list = vfs.listLeaf(node, recursively);
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
    
    private void copyPath(ActionEvent actionEvent) {
        TreeItem<Node> selected = tree_view.getSelectionModel().getSelectedItem();
        Node node = selected == null ? null : selected.getValue();
        if (node != null && node.leaf) {
            Leaf leaf = (Leaf) node;
            
            String path = leaf.path.replace("\\", "\\\\");
            StringSelection selection = new StringSelection(path);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
    }
}