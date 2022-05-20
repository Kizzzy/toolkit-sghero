package cn.kizzzy.toolkit.controller;

import cn.kizzzy.javafx.viewer.ViewerExecutor;
import cn.kizzzy.javafx.viewer.executor.RdfViewerExecutor;

@MenuParameter(path = "文件浏览/三国豪侠传/解包器(本地)")
@PluginParameter(url = "/fxml/explorer_view.fxml", title = "文件浏览(SGH)")
public class SgHeroLocalController extends ExplorerView {
    
    @Override
    public String getName() {
        return "SgHero Display";
    }
    
    @Override
    protected ViewerExecutor initialViewExecutor() {
        return new RdfViewerExecutor();
    }
}