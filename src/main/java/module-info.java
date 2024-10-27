module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires eu.hansolo.tilesfx;
    requires java.management;
    requires jdk.attach;
    requires java.rmi;
    requires java.instrument;
    requires java.desktop;
    requires java.compiler;

    opens org.example.threadsvisualiser to javafx.fxml;
    exports org.example.threadsvisualiser;
}