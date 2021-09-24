package thito.nodeflow.java.transform;

import thito.nodeflow.java.*;

public class StringToLong implements ObjectTransformation {
    @Override
    public Reference transform(Reference source) {
        return Java.Class(Long.class).method("parseLong", Java.Class(String.class)).invoke(source);
    }
}
