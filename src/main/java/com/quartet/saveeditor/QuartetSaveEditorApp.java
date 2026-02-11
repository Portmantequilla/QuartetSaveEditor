package com.quartet.saveeditor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class QuartetSaveEditorApp extends Application {
    private static final Pattern SLOT_PATTERN = Pattern.compile("slot\\d+");
    private static final String SAVE_FILE_NAME = "data.json";
    private static final int MAX_CHARACTERS = 8;

    private final ObjectMapper mapper = new ObjectMapper();

    private final ListView<Path> slotListView = new ListView<>();
    private final TabPane tabPane = new TabPane();
    private final Label statusLabel = new Label("No slot loaded.");

    private final Map<String, List<String>> itemOptionsByCategory = new LinkedHashMap<>();

    private Path currentSavesRoot;
    private Path currentSlotFolder;
    private ObjectNode currentDataRoot;

    @Override
    public void start(Stage stage) {
        loadItemOptions();
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        ToolBar toolBar = buildToolbar(stage);
        root.setTop(toolBar);

        slotListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
        slotListView.getSelectionModel().selectedItemProperty().addListener((_, _, selectedPath) -> {
            if (selectedPath != null) {
                loadSlot(selectedPath);
            }
        });

        VBox leftPanel = new VBox(8, new Label("Slots"), slotListView);
        leftPanel.setPadding(new Insets(0, 10, 0, 0));
        leftPanel.setPrefWidth(180);
        VBox.setVgrow(slotListView, Priority.ALWAYS);

        root.setLeft(leftPanel);
        root.setCenter(tabPane);

        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(8, 0, 0, 0));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 980, 650);
        stage.setTitle("Quartet Save Editor");
        stage.setScene(scene);
        stage.show();

        useDefaultSavesFolder();
    }

    private ToolBar buildToolbar(Stage stage) {
        Button openButton = new Button("Open Slot Folder...");
        Button defaultButton = new Button("Use Default Saves Folder");
        Button saveButton = new Button("Save");
        Button reloadButton = new Button("Reload");

        openButton.setOnAction(_ -> chooseFolder(stage));
        defaultButton.setOnAction(_ -> useDefaultSavesFolder());
        saveButton.setOnAction(_ -> saveCurrentSlot());
        reloadButton.setOnAction(_ -> reloadCurrentSlot());

        return new ToolBar(openButton, defaultButton, new Separator(), saveButton, reloadButton);
    }

    private void loadItemOptions() {
        try (InputStream in = getClass().getResourceAsStream("/quartet_item_list_alpha2_originals_only.json")) {
            if (in == null) {
                throw new IOException("Missing bundled item list resource.");
            }
            JsonNode root = mapper.readTree(in);
            itemOptionsByCategory.put("Weapons", readStringArray(root.path("Weapons")));
            itemOptionsByCategory.put("Armor", readStringArray(root.path("Armor")));
            itemOptionsByCategory.put("Helms", readStringArray(root.path("Helms")));
            itemOptionsByCategory.put("Accessories", readStringArray(root.path("Accessories")));
            itemOptionsByCategory.put("Items", readStringArray(root.path("Items")));
        } catch (IOException e) {
            showError("Failed to load item list", e.getMessage());
        }
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode entry : node) {
                if (entry.isTextual()) {
                    values.add(entry.asText());
                }
            }
        }
        return values;
    }

    private void chooseFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Quartet Saves Root or Slot Folder");
        if (currentSavesRoot != null && Files.isDirectory(currentSavesRoot)) {
            chooser.setInitialDirectory(currentSavesRoot.toFile());
        }

        java.io.File selected = chooser.showDialog(stage);
        if (selected == null) {
            return;
        }

        Path selectedPath = selected.toPath();
        if (!Files.isDirectory(selectedPath)) {
            showError("Invalid selection", "Please select a directory.");
            return;
        }

        if (isSlotDirectory(selectedPath)) {
            currentSavesRoot = selectedPath.getParent();
            slotListView.setItems(FXCollections.observableArrayList(selectedPath));
            slotListView.getSelectionModel().selectFirst();
            return;
        }

        loadSlotsFromRoot(selectedPath);
    }

    private void useDefaultSavesFolder() {
        Path defaultRoot = Paths.get(
            System.getProperty("user.home"),
            "Documents",
            "Something Classic",
            "Quartet",
            "saves"
        );

        if (!Files.isDirectory(defaultRoot)) {
            showError("Default saves folder not found", "No directory at: " + defaultRoot);
            return;
        }
        loadSlotsFromRoot(defaultRoot);
    }

    private void loadSlotsFromRoot(Path rootFolder) {
        try {
            List<Path> slots = Files.list(rootFolder)
                .filter(Files::isDirectory)
                .filter(this::isSlotDirectory)
                .filter(path -> Files.exists(path.resolve(SAVE_FILE_NAME)))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();

            if (slots.isEmpty()) {
                showError("No valid slots found", "No slotXX folders with data.json in: " + rootFolder);
                return;
            }

            currentSavesRoot = rootFolder;
            slotListView.setItems(FXCollections.observableArrayList(slots));
            slotListView.getSelectionModel().selectFirst();
        } catch (IOException e) {
            showError("Failed to scan saves folder", e.getMessage());
        }
    }

    private boolean isSlotDirectory(Path path) {
        return SLOT_PATTERN.matcher(path.getFileName().toString()).matches();
    }

    private void loadSlot(Path slotFolder) {
        Path dataPath = slotFolder.resolve(SAVE_FILE_NAME);
        if (!Files.exists(dataPath)) {
            showError("Missing save file", "Expected file not found: " + dataPath);
            return;
        }

        try {
            JsonNode rootNode = mapper.readTree(dataPath.toFile());
            if (!(rootNode instanceof ObjectNode objectNode)) {
                showError("Invalid save format", "Root JSON node must be an object.");
                return;
            }
            currentDataRoot = objectNode;
            currentSlotFolder = slotFolder;
            rebuildCharacterTabs();
            statusLabel.setText("Loaded " + slotFolder.getFileName() + "/" + SAVE_FILE_NAME);
        } catch (IOException e) {
            showError("Invalid JSON", "Could not parse file: " + dataPath + "\n" + e.getMessage());
        }
    }

    private void rebuildCharacterTabs() {
        tabPane.getTabs().clear();

        ArrayNode characters = getCharactersArray();
        int characterCount = Math.min(characters.size(), MAX_CHARACTERS);
        for (int i = 0; i < characterCount; i++) {
            JsonNode character = characters.get(i);
            if (!(character instanceof ObjectNode characterNode)) {
                continue;
            }

            String name = readCharacterName(characterNode, i);
            Tab tab = new Tab(name);
            tab.setClosable(false);
            tab.setContent(buildEquipmentEditor(characterNode));
            tabPane.getTabs().add(tab);
        }

        if (tabPane.getTabs().isEmpty()) {
            tabPane.getTabs().add(new Tab("No Characters", new Label("No party.characters entries found.")));
            tabPane.getTabs().get(0).setClosable(false);
        }
    }

    private ArrayNode getCharactersArray() {
        JsonNode partyNode = currentDataRoot.path("party");
        JsonNode charactersNode = partyNode.path("characters");
        if (charactersNode instanceof ArrayNode arrayNode) {
            return arrayNode;
        }

        ObjectNode rootParty = currentDataRoot.with("party");
        return rootParty.putArray("characters");
    }

    private String readCharacterName(ObjectNode characterNode, int index) {
        JsonNode attributes = characterNode.path("stringAttributes");
        if (attributes.isArray()) {
            for (JsonNode attr : attributes) {
                if ("characterName".equals(attr.path("key").asText())) {
                    String value = attr.path("value").asText();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "Character " + (index + 1);
    }

    private GridPane buildEquipmentEditor(ObjectNode characterNode) {
        ArrayNode equippedItems = ensureEquippedItems(characterNode);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        addEquipRow(grid, 0, "Weapon", equippedItems, 0, itemOptionsByCategory.getOrDefault("Weapons", List.of()));
        addEquipRow(grid, 1, "Accessory 1", equippedItems, 1, itemOptionsByCategory.getOrDefault("Accessories", List.of()));
        addEquipRow(grid, 2, "Helm", equippedItems, 2, itemOptionsByCategory.getOrDefault("Helms", List.of()));
        addEquipRow(grid, 3, "Armor", equippedItems, 3, itemOptionsByCategory.getOrDefault("Armor", List.of()));
        addEquipRow(grid, 4, "Accessory 2", equippedItems, 4, itemOptionsByCategory.getOrDefault("Accessories", List.of()));
        addEquipRow(grid, 5, "Accessory 3", equippedItems, 5, itemOptionsByCategory.getOrDefault("Accessories", List.of()));

        return grid;
    }

    private ArrayNode ensureEquippedItems(ObjectNode characterNode) {
        JsonNode equipped = characterNode.path("equippedItems");
        ArrayNode equippedItems;
        if (equipped instanceof ArrayNode array) {
            equippedItems = array;
        } else {
            equippedItems = characterNode.putArray("equippedItems");
        }

        while (equippedItems.size() < 6) {
            equippedItems.add("");
        }
        return equippedItems;
    }

    private void addEquipRow(GridPane grid, int row, String label, ArrayNode equippedItems, int equipIndex, List<String> baseOptions) {
        Label slotLabel = new Label(label + ":");
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setMaxWidth(Double.MAX_VALUE);

        String currentValue = equippedItems.path(equipIndex).asText("");
        Set<String> options = new LinkedHashSet<>();
        options.add("");
        options.addAll(baseOptions);
        if (!currentValue.isBlank() && !options.contains(currentValue)) {
            options.add(currentValue + " (unknown)");
        }

        List<String> displayedOptions = new ArrayList<>(options);
        comboBox.setItems(FXCollections.observableArrayList(displayedOptions));
        comboBox.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || item.isEmpty() ? "(empty)" : item);
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null || item.isEmpty() ? "(empty)" : item);
            }
        });

        String selectedValue = currentValue;
        if (!currentValue.isBlank() && !baseOptions.contains(currentValue)) {
            selectedValue = currentValue + " (unknown)";
        }
        comboBox.setValue(selectedValue);

        comboBox.valueProperty().addListener((_, _, newValue) -> {
            String valueToWrite = newValue == null ? "" : newValue;
            if (valueToWrite.endsWith(" (unknown)")) {
                valueToWrite = valueToWrite.substring(0, valueToWrite.length() - " (unknown)".length());
            }
            equippedItems.set(equipIndex, mapper.getNodeFactory().textNode(valueToWrite));
            statusLabel.setText("Modified " + currentSlotFolder.getFileName() + "/" + SAVE_FILE_NAME + " (unsaved)");
        });

        grid.add(slotLabel, 0, row);
        grid.add(comboBox, 1, row);
        GridPane.setHgrow(comboBox, Priority.ALWAYS);
    }

    private void saveCurrentSlot() {
        if (currentSlotFolder == null || currentDataRoot == null) {
            showError("Nothing to save", "Please load a slot first.");
            return;
        }

        Path dataPath = currentSlotFolder.resolve(SAVE_FILE_NAME);
        Path backupPath = currentSlotFolder.resolve(SAVE_FILE_NAME + ".bak");
        try {
            Files.copy(dataPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            mapper.writerWithDefaultPrettyPrinter().writeValue(dataPath.toFile(), currentDataRoot);
            statusLabel.setText("Saved + backup created: " + backupPath.getFileName());
        } catch (IOException e) {
            showError("Save failed", e.getMessage());
        }
    }

    private void reloadCurrentSlot() {
        if (currentSlotFolder == null) {
            showError("Nothing to reload", "Please load a slot first.");
            return;
        }
        loadSlot(currentSlotFolder);
        statusLabel.setText("Reloaded " + currentSlotFolder.getFileName() + "/" + SAVE_FILE_NAME);
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Quartet Save Editor");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
