# Quartet Save Editor

Desktop save editor for **Quartet** built with **Java 17+, Maven**.

## Features

- Detects the default saves folder:
  - `~/Documents/Something Classic/Quartet/saves/`
- Lists available `slotXX` directories that contain `data.json`
- Loads `data.json` and shows up to 4 party members as tabs (in order)
- Edits `equippedItems[0..5]` using slot-specific dropdowns`
- Supports unknown currently-equipped IDs by keeping them selectable
- Saves with backup behavior:
  - create/overwrite `data.json.bak`
  - write updated pretty-printed `data.json

## Equipment Slot Mapping

- `0` = Weapon
- `1` = Accessory 1
- `2` = Helm
- `3` = Armor
- `4` = Accessory 2

## Safety warning

Always keep separate backups of your saves.

This tool **automatically creates `data.json.bak`** before every save, but you should still keep independent copies of important save data.
