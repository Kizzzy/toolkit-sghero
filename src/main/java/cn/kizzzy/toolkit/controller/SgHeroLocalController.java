package cn.kizzzy.toolkit.controller;

import cn.kizzzy.display.Display;
import cn.kizzzy.display.DisplayContext;
import cn.kizzzy.display.DisplayHelper;
import cn.kizzzy.event.EventArgs;
import cn.kizzzy.helper.FileHelper;
import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.helper.StringHelper;
import cn.kizzzy.javafx.TreeItemCell;
import cn.kizzzy.javafx.TreeItemComparator;
import cn.kizzzy.javafx.common.JavafxHelper;
import cn.kizzzy.javafx.common.MenuItemArg;
import cn.kizzzy.javafx.display.DisplayTabView;
import cn.kizzzy.javafx.display.DisplayType;
import cn.kizzzy.javafx.setting.ISettingDialogFactory;
import cn.kizzzy.javafx.setting.SettingDialogFactory;
import cn.kizzzy.qqt.SgHeroConfig;
import cn.kizzzy.sghero.RdfFile;
import cn.kizzzy.sghero.RdfFileItem;
import cn.kizzzy.toolkit.extrator.PlayThisTask;
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
import cn.kizzzy.vfs.tree.RdfTreeBuilder;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.FileChooser;

import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    protected TreeView<Node<RdfFileItem>> tree_view;
    
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
public class SgHeroLocalController extends SgHeroViewBase implements DisplayContext, Initializable {
    
    protected static final String CONFIG_PATH = "sghero/local.config";
    
    protected static final TreeItemComparator comparator
        = new TreeItemComparator();
    
    protected IPackage userVfs;
    protected SgHeroConfig config;
    protected ISettingDialogFactory dialogFactory;
    
    protected IPackage vfs;
    protected ITree<RdfFileItem> tree;
    protected Map<String, File> loadedKvs = new HashMap<>();
    
    protected Display display = new Display();
    protected TreeItem<Node<RdfFileItem>> dummyTreeItem;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userVfs = new FilePackage(System.getProperty("user.home") + "/.user");
        userVfs.getHandlerKvs().put(SgHeroConfig.class, new JsonFileHandler<>(SgHeroConfig.class));
        
        config = userVfs.load(CONFIG_PATH, SgHeroConfig.class);
        config = config != null ? config : new SgHeroConfig();
        
        JavafxHelper.initContextMenu(tree_view, () -> stage.getScene().getWindow(), new MenuItemArg[]{
            new MenuItemArg(0, "设置", this::openSetting),
            new MenuItemArg(1, "加载RDF", this::loadPackage),
            new MenuItemArg(2, "导出/图片", this::exportImage),
            new MenuItemArg(3, "复制路径", this::copyPath),
        });
        
        addListener(DisplayType.TOAST_TIPS, this::toastTips);
        addListener(DisplayType.SHOW_TEXT, this::onDisplayEvent);
        addListener(DisplayType.SHOW_IMAGE, this::onDisplayEvent);
        addListener(DisplayType.SHOW_TABLE, this::onDisplayEvent);
        
        dummyTreeItem = new TreeItem<>();
        tree_view.setRoot(dummyTreeItem);
        tree_view.setShowRoot(false);
        tree_view.getSelectionModel().selectedItemProperty().addListener(this::onSelectItem);
        tree_view.setCellFactory(callback -> new TreeItemCell());
        
        lock_tab.selectedProperty().addListener((observable, oldValue, newValue) -> {
            display_tab.setPin(newValue);
        });
        
        DisplayHelper.load();
    }
    
    @Override
    public void stop() {
        play = false;
        if (playThisTask != null) {
            playThisTask.stop();
        }
        
        if (tree != null) {
            tree.stop();
        }
        
        userVfs.save(CONFIG_PATH, config);
        
        super.stop();
    }
    
    @Override
    public int provideIndex() {
        return show_choice.getSelectionModel().getSelectedIndex();
    }
    
    @Override
    public boolean isFilterColor() {
        return false;//image_filter.isSelected();
    }
    
    protected void toastTips(EventArgs args) {
        Platform.runLater(() -> tips.setText((String) args.getParams()));
    }
    
    protected void onDisplayEvent(final EventArgs args) {
        Platform.runLater(() -> {
            display_tab.show(args.getType(), args.getParams());
        });
    }
    
    protected void loadPackage(ActionEvent actionEvent) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择rdf文件");
        if (StringHelper.isNotNullAndEmpty(config.last_rdf)) {
            chooser.setInitialDirectory(new File(config.last_rdf));
        }
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RDF", "*.rdf")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null && file.getAbsolutePath().endsWith(".rdf")) {
            config.last_rdf = file.getParent();
            
            new Thread(() -> {
                IPackage iPackage = new FilePackage(file.getParent());
                iPackage.getHandlerKvs().put(RdfFile.class, new RdfFileHandler());
                
                RdfFile rdfFile = iPackage.load(FileHelper.getName(file.getAbsolutePath()), RdfFile.class);
                tree = new RdfTreeBuilder(rdfFile, new IdGenerator()).build();
                
                vfs = new RdfPackage(file.getParent(), tree);
                
                Platform.runLater(() -> {
                    dummyTreeItem.getChildren().clear();
                    
                    final List<Node<RdfFileItem>> nodes = tree.listNode(0);
                    for (Node<RdfFileItem> node : nodes) {
                        dummyTreeItem.getChildren().add(new TreeItem<>(node));
                    }
                });
                
                loadedKvs.put(file.getAbsolutePath(), file);
            }).start();
        }
    }
    
    protected Object leaf2file(String path, Type clazz) {
        if (vfs != null) {
            return vfs.load(path, clazz);
        }
        return null;
    }
    
    @Override
    public <T> T load(String path, Class<T> clazz) {
        if (vfs != null) {
            return vfs.load(path, clazz);
        }
        return null;
    }
    
    protected void onSelectItem(Observable observable, TreeItem<Node<RdfFileItem>> oldValue, TreeItem<Node<RdfFileItem>> newValue) {
        if (newValue != null) {
            Node<RdfFileItem> folder = newValue.getValue();
            Leaf<RdfFileItem> thumbs = null;
            
            if (folder.leaf) {
                thumbs = (Leaf<RdfFileItem>) folder;
            } else {
                newValue.getChildren().clear();
                
                Iterable<Node<RdfFileItem>> list = folder.children.values();
                for (Node<RdfFileItem> temp : list) {
                    TreeItem<Node<RdfFileItem>> child = new TreeItem<>(temp);
                    newValue.getChildren().add(child);
                }
                newValue.getChildren().sort(comparator);
            }
            
            if (thumbs != null) {
                if (display != null) {
                    display.stop();
                }
                display = DisplayHelper.newDisplay(this, thumbs.path);
                display.init();
            }
        }
    }
    
    protected void onChangeLayer(Observable observable, Number oldValue, Number newValue) {
        display.select(newValue.intValue());
    }
    
    @FXML
    protected void openSetting(ActionEvent actionEvent) {
        if (dialogFactory == null) {
            dialogFactory = new SettingDialogFactory(stage);
        }
        dialogFactory.show(config);
    }
    
    @FXML
    protected void showPrev(ActionEvent actionEvent) {
        display.prev();
    }
    
    @FXML
    protected void showNext(ActionEvent actionEvent) {
        display.next();
    }
    
    private boolean play;
    
    @FXML
    protected void play(ActionEvent actionEvent) {
        if (display != null) {
            play = !play;
            ((Button) actionEvent.getSource()).setText(play ? "暂停" : "播放");
            if (play) {
                new Thread(() -> {
                    while (play) {
                        try {
                            Platform.runLater(() -> display.play());
                            Thread.sleep(125);
                        } catch (InterruptedException e) {
                            LogHelper.error(null, e);
                        }
                    }
                }).start();
            }
        }
    }
    
    private boolean playThis;
    private PlayThisTask playThisTask;
    
    @FXML
    private void playThis(ActionEvent event) {
        playThis = !playThis;
        ((Button) event.getSource()).setText(playThis ? "暂停播放" : "连续播放");
        if (playThis) {
            TreeItem<Node<RdfFileItem>> selected = tree_view.getSelectionModel().getSelectedItem();
            
            List<Display> displays = new ArrayList<>();
            
            List<Leaf<RdfFileItem>> fileList = tree.listLeaf(selected.getValue());
            for (Leaf<RdfFileItem> file : fileList) {
                displays.add(DisplayHelper.newDisplay(this, file.path));
            }
            
            playThisTask = new PlayThisTask(displays);
            
            new Thread(playThisTask).start();
        } else {
            if (playThisTask != null) {
                playThisTask.stop();
            }
        }
    }
    
    @FXML
    protected void exportImage(ActionEvent event) {
        if (StringHelper.isNullOrEmpty(config.export_image_path)) {
            openSetting(null);
            return;
        }
        
        final TreeItem<Node<RdfFileItem>> selected = tree_view.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        
        IPackage target = null;
        Node<RdfFileItem> node = selected.getValue();
        
        List<Leaf<RdfFileItem>> list = tree.listLeaf(selected.getValue(), true);
        for (Leaf<RdfFileItem> leaf : list) {
            try {
                if (target == null) {
                    String pkgName = leaf.pack.replace(".rdf", "");
                    target = new FilePackage(config.export_image_path + "/" + pkgName);
                }
                
                if (leaf.path.contains(".png")) {
                    byte[] data = vfs.load(leaf.path, byte[].class);
                    if (data != null) {
                        target.save(leaf.path, data);
                    }
                }
            } catch (Exception e) {
                LogHelper.info(String.format("export image failed: %s", leaf.name), e);
            }
        }
    }
    
    protected void copyPath(ActionEvent actionEvent) {
        TreeItem<Node<RdfFileItem>> selected = tree_view.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Node<RdfFileItem> node = selected.getValue();
            if (node.leaf) {
                Leaf<RdfFileItem> leaf = (Leaf<RdfFileItem>) node;
                
                String path = leaf.path.replace("\\", "\\\\");
                StringSelection selection = new StringSelection(path);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(selection, selection);
            }
        }
    }
    
    private TreeItem<Node<RdfFileItem>> filterRoot;
    
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
            filterRoot = new TreeItem<>(new Node<>(0, "[Filter]"));
            dummyTreeItem.getChildren().add(filterRoot);
        }
        
        filterRoot.getChildren().clear();
        
        List<Node<RdfFileItem>> list = tree.listNodeByRegex(regex);
        for (Node<RdfFileItem> folder : list) {
            filterRoot.getChildren().add(new TreeItem<>(folder));
        }
        
        filterRoot.getChildren().sort(comparator);
    }
}