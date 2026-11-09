# D&D Campaign Notes

A whiteboard-style note-taking app for planning D&D campaigns. Drag the board around, double-click to drop text anywhere, draw, add sticky notes and images, and **link any object to another whiteboard** so a double-click jumps straight there. Organise boards into projects and folders, and keep several open at once in tabs.

Built with Java and JavaFX. Runs on Windows, macOS and Linux.

## Download and run

### 1. Get the app
**Recommended: clone it with [Git](https://git-scm.com/)**, so you can update with one click later:
```
git clone https://github.com/Mykkey/DnD-Campaign-Notes.git
```

Don't want Git? You can [download the ZIP](https://github.com/Mykkey/DnD-Campaign-Notes/archive/refs/heads/main.zip) and unzip it anywhere instead, but to update you'll have to download and unzip it again.

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

## Updating
If you cloned with Git, double-click **`update.bat`** (or run `git pull`) to get the latest version, then start the app as normal - it rebuilds itself automatically. Your campaigns are not affected by updates.

The app also checks GitHub when it starts (only needs an internet connection; it stays silent if offline). If a newer version number is published, a blue banner appears at the top with an **Update now** button (git copies) or a link to the download page (ZIP copies).

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
| Bold / italic words | While typing in a text or sticky note, **select some words** and press **Ctrl+B** / **Ctrl+I** (or the **B** / **I** toolbar buttons). Press again to remove. Outside of typing, the same buttons make the whole text or sticky note bold or italic. |
| Images | **Image...** button, paste with Ctrl+V, or drag files onto the board. |
| Undo / redo | Ctrl+Z / Ctrl+Y |
| Copy / paste / duplicate | Ctrl+C / Ctrl+V / Ctrl+D (works between tabs) |

Styled words are saved as simple markup inside the text: `**bold**`, `*italic*` and `***both***`. While you are typing you see those marks around the words; once you finish, the board shows real bold and italic. A literal `*` that would be mistaken for formatting can be typed as `\*`. Search matches the words, ignoring the marks.

### Tool shortcuts
Press a number key to switch tools: **1** Select, **2** Hand, **3** Text, **4** Sticky, **5** Pen, **6** Eraser, **7** Rect, **8** Ellipse, **9** Line, **0** Arrow. The eraser only removes pen drawings.

## Battlemaps
Battlemaps are a separate item type next to whiteboards, saved as `maps/<id>.battlemap` in your project folder.

- **Create one:** **+ Battlemap** in the project tree (or right-click > New battlemap). Choose **Squares** or **Hexagons**, the map size in cells, and the cell size.
- **Side panel of pieces:** 150+ detailed built-in vector pieces in seven categories: **Buildings** (houses, tavern, windmill, chapel, keep...), **Walls** (stone, gates, palisades, hedges, doors, interior walls), **Paths** (dirt and cobbled roads, bridges, stairs, rivers), **Camp** (tents, fires, wagons, boats, tools), **Nature** (trees, bushes, rocks, ponds, mushrooms, crystals), **Dungeon** (pillars, altars, traps, portals, summoning circles) and **Furniture** (beds, tables, rugs, fireplaces, bookshelves). Click a piece, then click the map to place it as often as you like (Esc to stop), or drag it onto the map. **Import SVG / PNG...** adds your own pieces; they are copied into the project's `assets` folder.
- **Tools** (number keys 1-7): Select, Hand, Terrain, Piece, Token, Ruler, Fog.
  - **Terrain:** paint grass, dirt, sand, stone, wood, water, snow or lava onto cells. Each is a textured ground type (grass blades, pebbly dirt, flagstones, wooden planks, rippling water, cracked lava...) that flows continuously across cells, with soft irregular edges where two types meet. Drag to paint, choose *Erase* to clear, change the brush size.
  - **Select:** move pieces (with **Snap** they line up with the grid), drag the round handle to rotate in 15 degree steps (hold Alt for free rotation), drag the square corner to resize (hold Shift to stretch). `[` / `]` rotate, `F` flips, right-click for front/back, copy, duplicate and delete.
  - **Selecting:** click a piece or token (clicking a transparent corner of a piece picks whatever is underneath). **Ctrl+click** (or Shift+click) adds or removes exactly that item from the selection; Ctrl+drag a box to add everything inside it; Ctrl+A selects all.
  - **Copy / paste:** Ctrl+C, Ctrl+X, Ctrl+V and Ctrl+D (duplicate) work on pieces and tokens, including between maps, and keep working when you have just used the toolbar.
  - **Token:** click a cell for a coloured token; double-click it to rename; right-click to set its size (medium to gargantuan).
  - **Ruler:** drag to measure distance in cells and feet (5 ft per cell).
- **Pop out for the players:** the **Pop out** button on a map opens it in its own window showing just the map (no handles or tool clutter). Drag that window to your playtable monitor and press **F11** for fullscreen. It updates live as you edit and has its own camera, so zooming it never moves your view. In that window: scroll to zoom, drag to pan, **F** or double-click to fit, **G** toggles the grid, **+/-** zoom. It remembers which monitor it was on, and closes when you leave the project or delete the map.
- **Preview on a whiteboard:** on a whiteboard click **Battlemap...** (or right-click > Insert battlemap preview). It shows a live thumbnail that updates when you edit the map; double-click it to open the map. Previews also show up in Backlinks.
- Map names and token labels are included in project search. Undo/redo, copy/paste and autosave work like on whiteboards.

## Cards (characters and monsters)
Cards are their own item type (`+ Card` in the project tree, saved as `cards/<id>.card`). Each is a 5e-style stat block: name, kind (player / NPC / monster), size, type, alignment, AC, HP and hit dice, speed, the six ability scores (with live modifiers), saves, skills, resistances, senses, languages, CR/XP, initiative bonus, a portrait, traits, **attacks** (to-hit, damage dice, type, reach/range, extra effect), other actions, reactions, legendary actions and notes. A parchment-style stat block updates live beside the form.

- **Start from a template:** a blank player character, a blank creature, or one of ten example creatures (goblin, kobold, skeleton, zombie, wolf, giant rat, orc, bandit, guard, commoner).
- **Duplicate** a card from the tree's right-click menu to make variants. Any whiteboard text or object can be **linked** to a card, so a double-click opens its stat block.

## Battle mode (initiative tracker)
On a battlemap, press **Battle** in the toolbar to dock the tracker on the right. Combat is tracked by hand: you roll the dice and type the results; the app keeps the state.

1. **+ Add...** picks cards from your project: tick each creature, set how many, which side (player / ally / enemy) and whether they **act as a group**. Each one becomes a token on the map, linked to its card (players clear fog around themselves by default).
2. Type each creature's **initiative**. (Optional: *Roll enemy initiative* rolls d20 + bonus for enemies and allies, one roll per group.) Ties go to the higher initiative bonus; the ^ / v buttons order tied creatures by hand.
3. **Start battle**, then press **Next >** to move through the turn order. Defeated creatures are skipped, and a new round starts after the last one. **< Back** steps backwards.
4. The **Turn** card shows the attacks of whoever is up (to-hit, reach/range, damage with average), plus their other traits, with a link to open the card. For a group it shows each distinct card once with how many are left.
5. **HP:** every creature has an HP bar and a box: type `-7` for damage, `+5` to heal, or a plain number to set HP. **Several hits at once** work too, e.g. `-5 -7 -3` when four goblins all hit the same fighter. Temporary HP soak damage first, reaching 0 marks a creature down (its token greys out with a cross), and healing brings it back. The `...` menu has rename, max HP, temp HP, AC, **conditions**, notes, find token, **place token** (puts a new token on the map if you deleted the old one) and remove.
6. The token whose turn it is gets a gold ring on the map and in the player window.

**Groups:** creatures that share a group take one turn together with one initiative (a pack of goblins). The group collapses to a single row with a one-line HP summary and expands to per-creature HP. You can also tick two or more creatures and press *Group*, or *Ungroup* later.

The **player window** (Pop out) shows a slim turn-order strip along the top: names only, never HP, and it leaves out hidden creatures and enemies still in the fog. Press **I** there to show or hide it.

## Fog of war
Tick **Fog** in a battlemap's toolbar to hide the map from the players until you reveal it. The player window shows unrevealed cells as solid black (so enemies and pieces under fog are not visible); in your editor the fog is shaded but see-through.

- **Fog tool** (key 7): choose *Reveal brush*, *Hide brush*, *Reveal rectangle* or *Hide rectangle* in the Fog list, then click or drag. The brush uses the same size box as terrain. *Reveal all* / *Hide all* act on the whole map.
- **Auto-reveal:** tokens set to reveal fog (players' tokens by default; right-click a token > *Reveals fog around it* to choose 3, 6, 9 or 12 cells) clear the fog around them as you place or move them. Explored cells stay revealed.
- **Hidden from players:** right-click a piece or token > *Hide from players* for traps, secret doors or an ambush. You still see it (ghosted); the players never do, even in revealed areas.
- Fog and reveals are part of undo (Ctrl+Z) and are saved with the map.

## Name generator
Click **Names...** in the top bar (or press Ctrl+G), pick a race and optionally a gender, and it rolls first + last names. Select one and hit **Copy**, or **Add to board** (or double-click a name) to drop it onto the current whiteboard as text. Races: Human, Elf, Dwarf, Halfling, Gnome, Half-Elf, Half-Orc, Tiefling, Dragonborn, Orc, Goblin, Tabaxi, Aasimar, Genasi, Goliath, Kobold and Tortle.

## Credits
The example creature cards use stat values from the System Reference Document 5.1 by Wizards of the Coast, licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). This app is not affiliated with or endorsed by Wizards of the Coast.

## For developers
```
mvn test           # run the unit tests
mvn javafx:run     # run the app
```
Source is in `src/main/java/dnd/` (`model`, `storage`, `ui`, `nav`, `search`, `map`, `names`, `update`).

The battlemap SVG pieces are generated by a small Python script, so they can be tweaked or extended in one place. Each piece is a short function in `tools/svg-assets/` (`buildings.py`, `walls.py`, `paths.py`, `camp.py`, `nature.py`, `dungeon.py`, `furniture.py`) built on shared helpers in `svglib.py` (shingles, stonework, planks, shadows, grain, gradients). To change or add pieces, edit those files and run:
```
cd tools/svg-assets
python build.py
```
That rewrites `src/main/resources/assets/` (the SVGs and `index.json`). Keep existing piece ids and sizes unchanged so older maps still load.
