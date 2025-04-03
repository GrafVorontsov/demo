package org.forever.demo;

import javafx.scene.control.TextArea;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;

public class FileDropHandler {
    public static void setupDragAndDrop(TextArea textArea, FileProcessor fileProcessor) {
        textArea.setOnDragOver(event -> {
            if (event.getGestureSource() != textArea && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        textArea.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                List<File> files = dragboard.getFiles();
                fileProcessor.process(files);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FunctionalInterface
    public interface FileProcessor {
        void process(List<File> files);
    }
}
