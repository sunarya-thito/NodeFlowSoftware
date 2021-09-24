package thito.nodeflow.java.transform;

import thito.nodeflow.java.*;

public class NumberToInt implements ObjectTransformation {
    @Override
    public Reference transform(Reference source) {
        return source.method("intValue").invoke();
    }
}
