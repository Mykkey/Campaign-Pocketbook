# D&D Campaign Notes

A whiteboard-style note-taking app for planning D&D campaigns. Drag the board around, double-click to drop text anywhere, draw, add sticky notes and images, and **link any object to another whiteboard** so a double-click jumps straight there. Organise boards into projects and folders, and keep several open at once in tabs.

Built with Java and JavaFX. Runs on Windows, macOS and Linux.

## Download and run

### 1. Get the app
Either download the zip: **[Download ZIP](https://github.com/Mykkey/DnD-Campaign-Notes/archive/refs/heads/main.zip)** and unzip it anywhere,

or clone it:
```
git clone https://github.com/Mykkey/DnD-Campaign-Notes.git
```

### 2. Install the two prerequisites (one time only)
| What | Version | Get it |
|------|---------|--------|
| Java JDK (64-bit) | 21 or newer | https://adoptium.net/ |
| Apache Maven | 3.9 or newer | https://maven.apache.org/download.cgi |

Make sure `java -version` and `mvn -v` both work in a new terminal. (JavaFX is downloaded automatically by Maven, so you don't install it separately.)

### 3. Start it
- **Windows:** double-click **`DnDCampaignNotes.bat`**. Tip: right-click it and choose *Send to > Desktop (create shortcut)* for a desktop icon.
- **Any OS:** open a terminal in the project folder and run:
  ```
  mvn javafx:run
  ```

The first launch takes a minute while Maven downloads dependencies. After that it starts quickly, and it rebuilds automatically whenever the source code has changed.

## Where your data lives
Projects are saved as plain files in `~/DnDCampaignNotes/<project name>/` (`project.json`, `boards/*.json`, `images/`). Everything autosaves. To back up or move a campaign, just copy its folder.

## Using it
| Do this | How |
|---------|-----|
| Open a board | Double-click it in the project tree (left). Right-click the tree to add, rename or delete; drag to move. |
| Add text | Double-click empty space (or the Text tool). Ctrl+Enter, Esc or clicking away finishes. |
| Pan / zoom | Hand tool, middle-mouse drag, or hold Space and drag. Mouse wheel zooms, Ctrl+0 resets. |
| Link to another board | Select an object, press **Link...** (Ctrl+L or right-click), pick a board. Double-click it to jump. Blue badge = linked, red = target was deleted. |
| Go back | Back/Forward buttons or Alt+Left / Alt+Right. The trail shows where you came from. |
| See what links here | **Backlinks** menu at the top. |
| Search | Box at the top right (Ctrl+F). |
| Images | **Image...** button, paste with Ctrl+V, or drag files onto the board. |
| Undo / redo | Ctrl+Z / Ctrl+Y |
| Copy / paste / duplicate | Ctrl+C / Ctrl+V / Ctrl+D (works between tabs) |

### Tool shortcuts
Press a number key to switch tools: **1** Select, **2** Hand, **3** Text, **4** Sticky, **5** Pen, **6** Eraser, **7** Rect, **8** Ellipse, **9** Line, **0** Arrow. The eraser only removes pen drawings.

## Name generator
Click **Names...** in the top bar (or press Ctrl+G), pick a race and optionally a gender, and it rolls first + last names. Select one and hit **Copy**, or **Add to board** (or double-click a name) to drop it onto the current whiteboard as text. Races: Human, Elf, Dwarf, Halfling, Gnome, Half-Elf, Half-Orc, Tiefling, Dragonborn, Orc, Goblin, Tabaxi, Aasimar, Genasi, Goliath, Kobold and Tortle.

## For developers
```
mvn test           # run the unit tests
mvn javafx:run     # run the app
```
Source is in `src/main/java/dnd/` (`model`, `storage`, `ui`, `nav`, `search`).
