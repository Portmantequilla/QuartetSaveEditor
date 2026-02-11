# Quartet Save Editor

Desktop save editor for **Quartet** built with **Java 17+, JavaFX, and Jackson**.

## Features

- Detects the default saves folder:
  - `~/Documents/Something Classic/Quartet/saves/`
- Lists available `slotXX` directories that contain `data.json`
- Loads `data.json` and shows up to 8 party members as tabs (in order)
- Edits `equippedItems[0..5]` using slot-specific dropdowns
- Loads dropdown options from bundled resource:
  - `src/main/resources/quartet_item_list_alpha2_originals_only.json`
- Supports unknown currently-equipped IDs by keeping them selectable
- Saves with backup behavior:
  - create/overwrite `data.json.bak`
  - write updated pretty-printed `data.json`
- Reload button discards in-UI edits and reloads from disk

## Equipment Slot Mapping

- `0` = Weapon
- `1` = Accessory 1
- `2` = Helm
- `3` = Armor
- `4` = Accessory 2
- `5` = Accessory 3

## Run

### Prerequisites

- JDK 17+

### Start app

```bash
./gradlew run
```

If you do not have a Gradle wrapper in your environment, you can run with local Gradle:

```bash
gradle run
```

## Save format expectations

- Slot save file path: `<slotFolder>/data.json`
- Characters: `party.characters[]`
- Character tab title source:
  - from `stringAttributes[]` where `key == "characterName"`
  - fallback to `Character N`

## Safety warning

Always keep separate backups of your saves.

This tool **automatically creates `data.json.bak`** before every save, but you should still keep independent copies of important save data.

## Acceptance checklist

- [x] Detects default saves folder and lists `slotXX`
- [x] Loads `<slot>/data.json`
- [x] Displays up to 8 characters as tabs, in order
- [x] Each tab edits `equippedItems[0..5]` with correct slot mapping
- [x] Dropdowns populate from `quartet_item_list_alpha2_originals_only.json`
- [x] Save creates `<slot>/data.json.bak` then writes `<slot>/data.json`
- [x] Reload works
- [x] Handles unknown item IDs and JSON issues gracefully
