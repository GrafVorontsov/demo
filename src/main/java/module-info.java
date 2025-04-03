module org.forever.demo {
    requires javafx.fxml;

    // Apache POI dependencies
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.apache.commons.collections4;
    requires org.apache.commons.codec;
    requires org.apache.commons.io;

    requires org.apache.logging.log4j;
    requires org.apache.commons.compress;

    requires org.kordamp.ikonli.javafx;
    requires com.google.common;

    requires io.github.osobolev.jacob;
    requires de.jensd.fx.glyphs.fontawesome;
    requires javafx.controls;
    requires java.logging;

    exports org.forever.demo;
    opens org.forever.demo to javafx.fxml, org.apache.commons.compress;
}