package org.forever.demo;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import java.util.List;

public class ChangeEntry {
    private final String version;
    private final List<String> changes;
    private final Color color;
    private final Image image;

    public ChangeEntry(String version, List<String> changes, Color color, Image image) {
        this.version = version;
        this.changes = changes;
        this.color = color;
        this.image = image;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getChanges() {
        return changes;
    }

    public Color getColor() {
        return color;
    }

    public Image getImage() {
        return image;
    }
}
