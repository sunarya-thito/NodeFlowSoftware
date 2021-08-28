package thito.nodeflow.library.ui.handler;

import javafx.scene.control.*;
import org.jsoup.nodes.*;
import thito.nodeflow.library.ui.*;

public class ControlSkinHandler implements SkinHandler<Control> {
    @Override
    public void parse(SkinParser parser, Control node, Element element) {
        Element contextMenu = element.selectFirst("> contextmenu");
        if (contextMenu != null) {
            ContextMenu m = new ContextMenu();
            for (Element child : contextMenu.children()) {
                m.getItems().add(MenuBarSkinHandler.createMenuItem(parser, child));
            }
            node.setContextMenu(m);
        }
    }
}
