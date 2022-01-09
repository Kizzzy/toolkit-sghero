package cn.kizzzy.javafx;

import cn.kizzzy.helper.FileHelper;
import cn.kizzzy.sghero.RdfFileItem;
import cn.kizzzy.vfs.tree.Node;
import cn.kizzzy.vfs.tree.Root;
import javafx.scene.control.TreeCell;

public class TreeItemCell extends TreeCell<Node<RdfFileItem>> {
    
    @Override
    protected void updateItem(Node<RdfFileItem> item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        }
        if (item != null) {
            if (item instanceof Root) {
                setText(FileHelper.getName(item.name));
            } else {
                setText(item.name);
            }
        }
    }
}
