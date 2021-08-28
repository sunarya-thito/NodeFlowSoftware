package thito.nodeflow.library.ui.form;

import javafx.beans.binding.*;
import javafx.beans.value.*;
import javafx.collections.*;
import javafx.css.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import thito.nodeflow.library.binding.*;
import thito.nodeflow.library.language.*;

import java.lang.reflect.*;

public class FormPane extends VBox {
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private Form form;

    private ObservableList<FormProperty<?>> formPropertyList = FXCollections.observableArrayList();

    public FormPane(Form form) {
        this.form = form;
        MappedListBinding.bind(getChildren(), formPropertyList, FormField::new);
        for (Field field : form.getClass().getDeclaredFields()) {
            try {
                if (field.getType() == FormProperty.class) {
                    field.setAccessible(true);
                    FormProperty<?> formProperty = (FormProperty<?>) field.get(form);
                    formPropertyList.add(formProperty);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public Form getForm() {
        return form;
    }

    public class FormField extends VBox {
        private FormProperty<?> formProperty;
        private BorderPane content = new BorderPane();

        public FormField(FormProperty<?> formProperty) {
            this.formProperty = formProperty;

            getStyleClass().add("form-field");
            content.getStyleClass().add("form-field-container");

            getChildren().addAll(new FormName(), content);
            content.setCenter(formProperty.getFormNode().getNode());
        }

        public FormProperty<?> formProperty() {
            return formProperty;
        }

        public class FormName extends HBox implements ChangeListener<Object> {
            private Label fieldName = new Label();
            private Label invalid = new Label();

            public FormName() {
                getStyleClass().add("form-field-information");
                getChildren().addAll(fieldName, invalid);
                fieldName.getStyleClass().add("form-field-name");
                invalid.getStyleClass().add("form-field-invalid");

                formProperty.addListener(new WeakChangeListener<>(this));
            }

            @Override
            public void changed(ObservableValue<?> observableValue, Object old, Object val) {
                StringExpression invalid = null;
                for (Validator validator : formProperty.getValidatorList()) {
                    I18n inv = validator.validate(val);
                    if (inv != null) {
                        if (invalid == null) {
                            invalid = inv;
                        } else {
                            invalid = invalid.concat("\n").concat(inv);
                        }
                    }
                }
                if (invalid == null) {
                    this.invalid.textProperty().unbind();
                    this.invalid.textProperty().set(null);
                    this.invalid.pseudoClassStateChanged(INVALID, false);
                    formProperty.validProperty().set(true);
                } else {
                    this.invalid.textProperty().bind(invalid);
                    this.invalid.pseudoClassStateChanged(INVALID, true);
                    formProperty.validProperty().set(false);
                }
            }
        }
    }
}
