package cn.kizzzy.javafx.viewer.executor;

import cn.kizzzy.helper.FileHelper;
import cn.kizzzy.helper.LogHelper;
import cn.kizzzy.helper.StringHelper;
import cn.kizzzy.javafx.common.MenuItemArg;
import cn.kizzzy.javafx.display.DisplayOperator;
import cn.kizzzy.javafx.display.DisplayTabView;
import cn.kizzzy.javafx.viewer.ViewerExecutorArgs;
import cn.kizzzy.javafx.viewer.ViewerExecutorAttribute;
import cn.kizzzy.javafx.viewer.ViewerExecutorBinder;
import cn.kizzzy.sghero.RdfFile;
import cn.kizzzy.sghero.SgHeroConfig;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@ViewerExecutorAttribute
public class RdfViewerExecutor extends AbstractViewerExecutor {
    
    private static final String CONFIG_PATH = "sghero/local.config";
    
    private SgHeroConfig config;
    
    @Override
    public void initialize(ViewerExecutorArgs args) {
        IPackage userVfs = args.getUserVfs();
        userVfs.getHandlerKvs().put(SgHeroConfig.class, new JsonFileHandler<>(SgHeroConfig.class));
        
        config = userVfs.load(CONFIG_PATH, SgHeroConfig.class);
        config = config != null ? config : new SgHeroConfig();
    }
    
    @Override
    public void stop(ViewerExecutorArgs args) {
        IPackage userVfs = args.getUserVfs();
        userVfs.save(CONFIG_PATH, config);
    }
    
    @Override
    public void initOperator(DisplayTabView tabView, IPackage vfs) {
        displayer = new DisplayOperator<>("cn.kizzzy.sghero.display", tabView, IPackage.class);
        displayer.load();
        displayer.setContext(vfs);
    }
    
    @Override
    public Iterable<MenuItemArg> showContext(ViewerExecutorArgs args, Node selected) {
        List<MenuItemArg> list = new ArrayList<>();
        list.add(new MenuItemArg(1, "加载/Rdf(SgHero)", event -> loadFile(args)));
        list.add(new MenuItemArg(1, "加载/目录(SgHero)", event -> loadFolder(args)));
        if (selected != null) {
            list.add(new MenuItemArg(0, "设置", event -> openSetting(args, config)));
            list.add(new MenuItemArg(2, "打开/文件目录", event -> openExportFolder(args)));
            list.add(new MenuItemArg(3, "导出/文件", event -> exportFile(args, selected, false)));
            list.add(new MenuItemArg(3, "导出/文件(递归)", event -> exportFile(args, selected, true)));
            if (selected.leaf) {
                list.add(new MenuItemArg(9, "复制路径", event -> copyPath(selected)));
            }
        }
        return list;
    }
    
    private void loadFile(ViewerExecutorArgs args) {
        Stage stage = args.getStage();
        
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择rdf文件");
        if (StringHelper.isNotNullAndEmpty(config.last_rdf)) {
            File lastFolder = new File(config.last_rdf);
            if (lastFolder.exists()) {
                chooser.setInitialDirectory(lastFolder);
            }
        }
        
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("RDF", "*.rdf"),
            new FileChooser.ExtensionFilter("ALL", "*.*")
        );
        
        File file = chooser.showOpenDialog(stage);
        if (file != null && file.getAbsolutePath().endsWith(".rdf")) {
            config.last_rdf = file.getParent();
            
            loadRdfImpl(args, file);
        }
    }
    
    private void loadRdfImpl(ViewerExecutorArgs args, File file) {
        IdGenerator idGenerator = args.getIdGenerator();
        
        IPackage rootVfs = new FilePackage(file.getParent());
        rootVfs.getHandlerKvs().put(RdfFile.class, new RdfFileHandler());
        
        String path = FileHelper.getName(file.getAbsolutePath());
        RdfFile rdfFile = rootVfs.load(path, RdfFile.class);
        if (rdfFile == null) {
            return;
        }
        
        ITree tree = new RdfTreeBuilder(rdfFile, idGenerator).build();
        IPackage rdfVfs = new RdfPackage(file.getParent(), tree);
        
        args.getObservable().setValue(new ViewerExecutorBinder(rdfVfs, this));
    }
    
    private void loadFolder(ViewerExecutorArgs args) {
        // todo
    }
    
    private void openExportFolder(ViewerExecutorArgs args) {
        openFolderImpl(config.export_file_path);
    }
    
    private void exportFile(ViewerExecutorArgs args, Node selected, boolean recursively) {
        Stage stage = args.getStage();
        IPackage vfs = args.getVfs();
        
        if (StringHelper.isNullOrEmpty(config.export_file_path) || !new File(config.export_file_path).exists()) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择保存文件的文件夹");
            File file = chooser.showDialog(stage);
            if (file == null) {
                return;
            }
            config.export_file_path = file.getAbsolutePath();
        }
        
        if (selected == null) {
            return;
        }
        
        IPackage target = null;
        
        List<Leaf> list = vfs.listLeaf(selected, recursively);
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
                LogHelper.info(String.format("export file failed: %s", leaf.name), e);
            }
        }
    }
}
