package thito.nodeflow.plugin.base.blueprint;

import thito.nodeflow.engine.node.*;
import thito.nodeflow.engine.node.handler.*;
import thito.nodeflow.internal.language.*;
import thito.nodeflow.plugin.base.blueprint.state.*;

public interface NodeProvider {
    String getId();
    I18n displayNameProperty();
    Node createNode();
    NodeHandler createHandler(Node node, BlueprintNodeState handlerState);
}
