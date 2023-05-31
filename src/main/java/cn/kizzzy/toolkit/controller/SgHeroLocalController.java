package cn.kizzzy.toolkit.controller;

import cn.kizzzy.javafx.menu.MenuParameter;
import cn.kizzzy.javafx.plugin.PluginParameter;
import cn.kizzzy.javafx.viewer.ViewerExecutor;
import cn.kizzzy.javafx.viewer.executor.RdfViewerExecutor;

@MenuParameter(path = "文件浏览/三国豪侠传/解包器(本地)")
@PluginParameter(title = "文件浏览(SGH)")
public class SgHeroLocalController extends ExplorerView {
    
    @Override
    protected ViewerExecutor initialViewExecutor() {
        return new RdfViewerExecutor();
    }
}