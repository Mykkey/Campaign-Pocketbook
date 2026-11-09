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
| Search | Box at the top right (Ctrl+F). Finds text on whiteboards and map pins, and inside cards (traits, actions, attacks, notes, relationship notes) and encounters (names and notes). Press Enter to search again; the results update as you type. |
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
- **Tools** (number keys 1-9 and 0): Select, Hand, Terrain, Piece, Token, Ruler, Fog, Area, Pin, Light. The toolbar only shows the settings of the tool you have selected (terrain and brush for Terrain, the fog controls for Fog, shape and size for Area, light kind and Dark map for Light, and so on), so it stays tidy; Grid, Snap and the buttons on the right are always there.
  - **Terrain:** paint grass, dirt, sand, stone, wood, water, snow or lava onto cells. Each is a textured ground type (grass blades, pebbly dirt, flagstones, wooden planks, rippling water, cracked lava...) that flows continuously across cells, with soft irregular edges where two types meet. Drag to paint, choose *Erase* to clear, change the brush size.
  - **Select:** move pieces (with **Snap** they line up with the grid), drag the round handle to rotate in 15 degree steps (hold Alt for free rotation), drag the square corner to resize (hold Shift to stretch). `[` / `]` rotate, `F` flips, right-click for front/back, copy, duplicate and delete.
  - **Selecting:** click a piece or token (clicking a transparent corner of a piece picks whatever is underneath). **Ctrl+click** (or Shift+click) adds or removes exactly that item from the selection; Ctrl+drag a box to add everything inside it; Ctrl+A selects all.
  - **Copy / paste:** Ctrl+C, Ctrl+X, Ctrl+V and Ctrl+D (duplicate) work on pieces and tokens, including between maps, and keep working when you have just used the toolbar.
  - **Token:** click a cell for a coloured token; double-click it to rename; right-click to set its size (medium to gargantuan).
  - **Ruler:** drag to measure distance in cells and feet (5 ft per cell). The measurement stays on the map until you measure again, press Esc or press Clear. Tick **Show players** to draw it in the player window too.
- **Pop out for the players:** the **Pop out** button on a map opens it in its own window showing just the map (no handles or tool clutter). Drag that window to your playtable monitor and press **F11** for fullscreen. It updates live as you edit and has its own camera, so zooming it never moves your view. In that window: scroll to zoom, drag to pan, **F** or double-click to fit, **G** toggles the grid, **+/-** zoom. It remembers which monitor it was on, and closes when you leave the project or delete the map.
- **Preview on a whiteboard:** on a whiteboard click **Battlemap...** (or right-click > Insert battlemap preview). It shows a live thumbnail that updates when you edit the map; double-click it to open the map. Previews also show up in Backlinks.
  - **Area** (key 8): lay a spell or breath area over the map. Pick **Circle (burst)**, **Cone**, **Line** or **Cube** and a size in feet in the toolbar, click where it starts and drag to aim it (15 degree steps, hold Alt for free aim; a circle follows the pointer). The cells it covers are tinted and counted ("30 ft cone: 19 cells"). A cell counts when its centre is inside the shape, on square and hex grids. Tick **Show players** to draw it in the player window too. **Clear area** or Esc removes it. It is a temporary overlay and is not saved.
  - **Pin** (key 9): numbered notes for you alone, never shown to the players: a trap, a secret door, what a monster does. Click the map to add one, click a pin to move it, double-click (or right-click > Edit) to change its note, and optionally **link it to a whiteboard, card or encounter** that you can open from the pin. Hover a pin to read its note. Pins are included in project search and in undo.
  - **Light** (key 0) and **Dark map:** tick **Dark map** and the map is dark: cells that no light reaches are black-blue in the player window, dim-lit cells are shaded, and bright cells are clear (you see a lighter version so you can still edit). Place light sources with the Light tool (Candle 5/10 ft, Lamp 15/30, Torch 20/40, Campfire 20/40, Lantern 30/60, Daylight spell 60/120: bright light, then dim light). Drag a light to move it, right-click to remove it. Right-click a token for **Carries a light** (it moves with the token) and **Sees in the dark (darkvision)**: darkvision treats darkness as dim light and dim light as bright light within its range. With fog on, a token's darkvision and carried light also clear the fog around it as it moves. There are no walls for light to stop at: it is a plain radius.
  - **Hidden pieces:** right-click a piece or token > *Hide from players* keeps it (a trap, an ambusher) out of the player window; it shows ghosted for you.
- Map names, token labels and pin notes are included in project search. Undo/redo, copy/paste and autosave work like on whiteboards.

## Relationships and family trees
Every card has a **Relationships** section at the bottom. Add a line for each person it is connected to: **parent of, child of, spouse or partner of, sibling of, mentor of, student of, employer of, works for, ally of, rival or enemy of, or connected to** (with a note such as "owes him a favour"). You only have to enter a relationship on one of the two cards: the other card shows it too ("Child of Dan - set on their card"), and entering it on both counts once. Press **Relations...** in the top bar to see them as pictures:

- **Family tree:** parents, partners, siblings and children laid out in generations, with partners side by side and children centred under their parents. Several separate families sit side by side.
- **Web of relationships:** allies, rivals, mentors (arrows from mentor to student), employers and other connections, drawn as coloured, labelled lines.
- **Show** one card to see just the family or web it belongs to. Click anyone to open their card. Relationship notes are included in project search.
- Duplicating a card does not copy its relationships, since the copy is a different person.

## Spell cards
Spells are their own item type (**+ Spell** in the project tree, saved as `spells/<id>.spell`), so you can write your own homebrew spells and keep spells you use often in the project. **Range / target**, casting time and duration are dropdowns of the usual choices (Self, Self (15-foot cone), Touch, Sight, 30 feet, 60 feet... for range; 1 action, 1 bonus action, 1 minute... for casting time; Instantaneous, 1 minute, 1 hour... for duration) that also take anything you type, so a custom value is always possible. A spell card has name, level (cantrip to 9th), school, casting time, range, components (V, S, M and what the material is), duration, concentration, ritual, classes, description, "At higher levels" and your own notes, with a spell card that updates live beside the form. Spells imported from the SRD (see the Library below) become spell cards with **Add as spell card**; duplicate one to make a homebrew variant. Spell cards can be linked and previewed like any other item and are found by project search (name, text, class, school, material, notes).

## Magic items
Magic items are their own item type (**+ Item** in the project tree, saved as `items/<id>.item`), so a homebrew item or a piece of loot can be written once and linked from anywhere. An item has a name, type (wondrous item, weapon, armor, potion, ring, wand...), rarity (common to artifact), whether it needs attunement (and by whom), a description, charges and when they come back, a curse or drawback, its value in gold and your own notes. The card beside the form updates live and takes its accent colour from the rarity. Items can be linked and previewed on hover like any other item, are offered by name hints when their name appears in text, and are found by project search (name, description, curse, notes).

## Quests
Quests are their own item type (**+ Quest** in the project tree, saved as `quests/<id>.quest`) for plot threads you want to keep track of. A quest has a name, a status (open, active, on hold, done, failed), who gave it, the reward, a summary, a checklist of **steps and clues** to tick off as the party does or finds them, the characters, maps, whiteboards and items it **involves** (add them, double-click to open), and your own notes. A summary beside the form updates live. Press **Quests...** in the top bar for the **quest board**: every quest in columns by status with its progress ("1 of 3 steps") and giver, a filter box, double-click to open and right-click to move a quest to another status. Quests can be linked and previewed on hover like any other item and are found by project search (name, summary, steps, giver, reward, notes).

## Sessions and the timeline
Each game session can have its own page (**+ Session** in the project tree, saved as `sessions/<id>.session`). A session has a title, a number, the real date you played, the date in the game world (as you write it, for example "14th of Mirtul, 1492") and an optional **campaign day** number so the app can show time passing, then a **recap for next time**, what happened, next steps, and the characters, quests and maps that came up. A new session starts with the next number, today's date and the previous session's campaign day. When you open a session, a box at the top shows **"Last time on..."**: the recap of the session before it, with how many days later this one is, a **Copy** button and a button to open that session. Press **Timeline...** in the top bar to see every session in order with its dates, the days that passed between them and its recap; click one to open it. Sessions can be linked and previewed on hover like any other item and are found by project search (title, recap, what happened, next steps).

## Links and notes in text
On a whiteboard, links and notes work on **selected words**, not just whole text blocks:

- **Link a few words:** double-click a text block or sticky note to edit it, select the words (a character name in the middle of a paragraph, say), then press **Link...** (or Ctrl+L) and pick the target. Only those words become a link: blue and underlined. **Hover** a link and a small preview of what it points to appears next to the pointer: a character or monster stat block, a spell card, a battlemap thumbnail, an encounter's creatures and difficulty, or the first lines of a whiteboard. **Double-click** it to open the target. Untick **Link previews** in the whiteboard toolbar if you would rather not see previews; the choice is remembered. In the text you will see the words wrapped like `[Mira]{1}` while you edit (just as bold shows as `**bold**`); the target is kept with the note, so deleting the brackets undoes it.
- **A note on a few words:** for things that have no sheet of their own (a rumour, what the innkeeper is hiding, a house rule), select words and press **Note...**, then type your own text. It can use `**bold**` and `*italic*` and run over several lines. The words turn green and underlined; **hover** them and the note appears in a small green-edged box. To change it later, **double-click** the words (or right-click them and choose **Edit note on these words...**). Right-click also has **Remove note from these words**.
- **Both on the same words:** a link and a note can share a selection. They show blue with a small green dot; hovering shows the note above the preview, and double-click still opens the link (edit the note from the right-click menu or with **Note...** while typing).
- **Whole objects:** with an object selected (not typing), **Link...** and **Note...** apply to the whole thing, for text blocks, pictures and shapes; a note shows as a green "i" badge next to the blue link badge. Right-click an object for the same options (**Edit note...**, **Remove note**). Double-click a shape or picture that only has a note to edit it.
- **Remove:** select the words (or the object) and press **Unlink** or **No note**.
- Whiteboards that link to an item show up in that item's **Backlinks** (notes are not links, so they do not). Project search also finds text inside notes.

### Name hints
When the name of one of your cards or spell cards appears in a block of text, it gets a faint dotted underline. Hover it and a small box offers **Link** (the words become a link to that card, which then previews it on hover) or **Dismiss** (stop underlining that name in that text block; other blocks are unaffected). Names are matched as whole words, ignoring case, longest name first (so "Mira Stonehelm" wins over "Mira"), and card names under three letters are ignored (aliases can be two). **Aliases:** a card's name is matched in full, so "Jasper" alone does not match a card called "Jasper Egan". Give the card **Also known as** names (for example `Jasper, Jas, Captain Egan`, separated by commas, at least two letters each, so initials like JE work) in its Identity section, and any of those words now offers the same link. The hint box shows which card it means ("Jasper -> Jasper Egan"). A card's real name always wins if an alias clashes with another card's name, and a duplicate card does not copy the aliases. Aliases are also found by project search. Words that already have a link or note are left alone. Untick **Name hints** in the whiteboard toolbar to switch the underlines off; the choice is remembered.

## Cards (characters and monsters)
Cards are their own item type (`+ Card` in the project tree, saved as `cards/<id>.card`). Each is a 5e-style stat block: name, kind (player / NPC / monster), size, type, alignment, AC, HP and hit dice, speed, the six ability scores (with live modifiers), saves, skills, resistances, senses, languages, CR/XP, initiative bonus, a portrait, traits, **attacks** (to-hit, damage dice, type, reach/range, extra effect), other actions, reactions, legendary actions and notes. A parchment-style stat block updates live beside the form.

- **Start from a template:** a blank player character, a blank creature, or one of ten example creatures (goblin, kobold, skeleton, zombie, wolf, giant rat, orc, bandit, guard, commoner).
- **Duplicate** a card from the tree's right-click menu to make variants. Any whiteboard text or object can be **linked** to a card, so a double-click opens its stat block.

## Encounters
An encounter is a saved fight, its own item type next to whiteboards, battlemaps and cards (`encounters/<id>.encounter` in your project folder).

- **Create one:** **+ Encounter** in the project tree (or right-click > New encounter). Duplicate it from the right-click menu to make variants.
- **Build it:** pick a **common enemy** (goblin, orc, wolf...) from the dropdown, or use **+ From my cards...** for player characters, NPCs and your own custom cards. For each one set how many, which side (enemy / ally / player) and whether they act as a group.
- **Common enemies:** a shelf of creatures shared by every project, so you do not need a card in the project to use a goblin. **Edit common enemies...** opens the library: change any creature's stats, remove ones you never use, add homebrew creatures (blank, duplicated, or copied from one of your project's cards), or restore the starter creatures. They are also listed in the battle's **+ Add creatures...** dialog. The first time one is used in a project a card for it is made in a "Common Enemies" folder, and it is refreshed from the library each time it is used (to change a creature for one project only, duplicate its card). The library is stored in `Common Enemies` next to your projects.
- **Difficulty:** set your party (number of characters and their level; it is remembered with the project) and the encounter shows **Trivial / Easy / Medium / Hard / Deadly**, using the 5e Dungeon Master's Guide method (monster XP, times the multiplier for how many enemies there are, against the party's XP budget). Only enemies count. XP comes from each card's XP, or from its challenge rating if XP is blank.
- **Run it:** on a battlemap press **Battle**, then **Load encounter...** in the setup screen to add all its creatures (and their tokens) in one go, then enter initiative and Start.
- Like any item, an encounter can be linked from a whiteboard object (it opens on double-click).

## Battle mode
On a battlemap, press **Battle** in the toolbar to open the battle screen. The map stays in the middle; creature cards go on the right and the turn order runs along the bottom. Combat is tracked by hand: you roll the dice and type the results; the app keeps the state. A slate banner at the top says **Battle setup**; once the fight starts it turns amber and reads **BATTLE - Round N** with whose turn it is.

**1. Set up (once).** **+ Add creatures...** picks cards from your project: tick each creature, set how many, which side (player / ally / enemy) and whether they **act as a group**. Type each creature's initiative, or press its **d20** button; **Roll enemies and allies** rolls everyone who has none yet (one roll per group). Players roll their own, so type their results in. **Start battle** stays locked until everyone has an initiative, and initiative is never rolled again afterwards.

**2. Run it.** The **initiative strip** along the bottom lists everyone in order. The creature whose turn it is gets a large amber chip and the strip scrolls to it. Press **Next >** (or **N**) to advance, **< Back** (or **P**) to step back. Defeated creatures are skipped and a new round starts after the last one. The active creature's **large card** is on the right with its attacks (to-hit, reach/range, damage with average), traits and full stat block.

**3. Plan ahead.** Click any chip in the strip, or any token on the map, to show that creature in a second card under the active one, without changing whose turn it is. **Back to current turn** closes it. Late arrivals can be added mid-battle with **+ Add creatures...** and are asked for their initiative.

**HP:** every card has an HP bar and a box: type `-7` for damage, `+5` to heal, or a plain number to set HP. **Several hits at once** work too, e.g. `-5 -7 -3`. Temporary HP soak damage first, reaching 0 marks a creature down (its token greys out with a cross), and healing brings it back. The `...` menu has rename, max HP, temp HP, AC, **conditions**, notes, find token, **place token** (if you deleted the old one) and remove. **Conditions** show as a coloured ring around the creature's token, split into one slice per condition (Poisoned is green, Stunned yellow, Frightened purple, and so on), on your map and in the player window, and as matching dots on its chip in the initiative strip. **Map tools** in the banner brings the normal toolbar back for moving tokens and revealing fog; **Exit** returns to the normal editor and keeps the battle.

**Quick map tools in battle:** a **Map:** strip under the banner (during setup and during the fight) has **Move**, **Ruler**, **Area** and **Pin** buttons, so you can measure a distance, lay a cone or circle over the map, or drop a private note without opening the map toolbar. Choosing Area shows its shape and size right there. One switch, **Show ruler and area to players**, draws whatever you have laid on the map in the player window too (off by default, so you can plan privately), and **Clear** takes the ruler and area off the map. Pins are never shown to the players.

**Groups:** creatures that share a group take one turn together with one initiative (a pack of goblins). Tick two or more rows in setup and press *Group*, or *Ungroup* later. The card lists each member with its own HP.

The **player window** (Pop out) shows a slim turn-order strip along the top: names only, never HP, and it leaves out hidden creatures and enemies still in the fog. Press **I** there to show or hide it. The token whose turn it is gets a gold ring on the map and in the player window.

## Fog of war
Tick **Fog** in a battlemap's toolbar to hide the map from the players until you reveal it. The player window shows unrevealed cells as solid black (so enemies and pieces under fog are not visible); in your editor the fog is shaded but see-through.

- **Fog tool** (key 7): choose *Reveal brush*, *Hide brush*, *Reveal rectangle* or *Hide rectangle* in the Fog list, then click or drag. The brush uses the same size box as terrain. *Reveal all* / *Hide all* act on the whole map.
- **Auto-reveal:** tokens set to reveal fog (players' tokens by default; right-click a token > *Reveals fog around it* to choose 3, 6, 9 or 12 cells) clear the fog around them as you place or move them. Explored cells stay revealed.
- **Hidden from players:** right-click a piece or token > *Hide from players* for traps, secret doors or an ambush. You still see it (ghosted); the players never do, even in revealed areas.
- Fog and reveals are part of undo (Ctrl+Z) and are saved with the map.

## Name generator
Click **Names...** in the top bar (or press Ctrl+G), pick a race and optionally a gender, and it rolls first + last names. Select one and hit **Copy**, or **Add to board** (or double-click a name) to drop it onto the current whiteboard as text. Races: Human, Elf, Dwarf, Halfling, Gnome, Half-Elf, Half-Orc, Tiefling, Dragonborn, Orc, Goblin, Tabaxi, Aasimar, Genasi, Goliath, Kobold and Tortle.

## Monster and spell library
Press **Library...** in the top bar to browse the 5e SRD monsters and spells. The data is not bundled with the app: download [5e-SRD-Monsters.json](https://raw.githubusercontent.com/5e-bits/5e-database/main/src/2014/en/5e-SRD-Monsters.json) and [5e-SRD-Spells.json](https://raw.githubusercontent.com/5e-bits/5e-database/main/src/2014/en/5e-SRD-Spells.json) once from the open [5e-database](https://github.com/5e-bits/5e-database/tree/main/src/2014/en) project (the library window has links too; your browser shows the text, so press Ctrl+S to save it) and press **Import SRD file...** in the library window (you can pick both files at once). The imported data is kept next to your projects in `SRD Library`, so every project can use it.

- **Monsters:** search by name and filter by type or challenge rating. The full stat block is shown. **Add to project as a card** makes an ordinary, editable card in an "SRD Monsters" folder (its attacks are filled in, so it works in battles straight away). **Add to common enemies** puts it in the dropdown used by the encounter builder and the battle's Add dialog, in every project.
- **Spells:** search by name and filter by level, class and school. **Add as spell card** turns the spell into an editable spell card in your project (in a "Spells" folder; if you already made one from that spell it opens that one instead). **Add to board** puts the spell on the open whiteboard as a text note, and **Copy** copies it.
- Importing a file again replaces what was imported before for that kind (monsters or spells).

## Loot generator
Press **Loot...** in the top bar (or **Ctrl+L**) for random treasure. Pick a **tier** (the challenge rating of what the party beat: 0-4, 5-10, 11-16, 17+) and either **a creature's pockets** (coins and the odd keepsake; set how many creatures and the coins are added together) or **a treasure hoard** (coins, gems, art objects, magic items of rarities that suit the tier, and a few curios). It changes as you change the options, **Roll** rolls again, **Copy** copies it and **Add to board** puts it on the open whiteboard as a text note. The amounts are rough guidance in the spirit of the usual treasure tiers, not a published table, so change anything you like.

## Dark mode
Press **Dark mode** in the top bar (or on the project chooser) to switch the whole app to a dark theme, and **Light mode** to switch back. It is remembered the next time you start the app (in `settings.properties` next to your projects) and applies to every window and dialog. On whiteboards the board turns dark and dark text and lines are drawn lighter so they stay readable; the colours stored in your notes are never changed, and sticky notes, images and stat blocks keep their own colours. The player window is always dark.

## NPC generator
When the party talks to someone you had not planned, press **NPC...** in the top bar (or **Ctrl+Shift+N**). Pin whatever you care about, leave the rest on **Random**, and press **Roll**: race, gender, **category and profession** (over 60 built in, from farmers and innkeepers to guards, priests, nobles and thieves), age group and attitude to the party (friendly, neutral, wary or hostile). You get a name that fits the race, an age, two details of appearance, two personality traits (which lean warm or cold with the attitude), a mannerism, a voice, what they want, a secret, what they open with, and a couple of things a person in that job might know or offer, which is handy for improvising the conversation.

- **Re-roll one detail:** the small button beside each line rolls only that line and keeps the rest (and keeps what you pinned).
- **You choose what happens to them.** Rolling only shows the NPC. **Add to board** puts the description on the open whiteboard as a note, **Save as NPC card** makes a card in an "NPCs" folder (stats borrowed from a matching creature, e.g. a guard gets guard stats, with the description in its notes, ready for a battle), and **Copy** copies the text. Each does only its own thing, so you can add one to the board without creating a card.
- **Your own professions:** **Edit professions...** lets you add professions (name, category, which creature's stats suit them, and what they might know). They appear next to the built-in ones in every project; the built-in ones cannot be changed. They are stored in `npc-professions.json` next to your projects.

## Organising your project
- **Icons:** the project tree and the tabs show a small drawn icon for each kind of item: a folder (it opens when expanded), a whiteboard, a battlemap, a card and an encounter (crossed swords). Folders show how many items are inside them, for example `NPCs (7)`.
- **Colours:** right-click any folder or item and choose **Set colour** to pick from ten colours (or **Default colour** to go back). The icon takes the colour in the tree and on the item's tab, so you can spot your NPCs, your maps or the current session's folder at a glance.
- **Keyboard:** with the project tree selected, **F2** renames the selected item and **Delete** deletes it (with the usual confirmation). Delete only touches the tree item here, never the open whiteboard.
- **Tabs:** right-click a tab for **Close**, **Close others**, **Close all** and **Reveal in tree**.
- **Recent:** the **Recent** button in the top bar lists the last ten items you opened, newest first, for jumping back quickly.

## Credits
The example creature cards, and any monsters and spells you import into the library, use content from the System Reference Document 5.1 by Wizards of the Coast, licensed under [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/). This app is not affiliated with or endorsed by Wizards of the Coast.

## For developers
**Versions** are `1.y.z`. The `1` only changes if there is ever a second generation of the app; `y` goes up for every new feature (and `z` resets to 0); `z` goes up for a fix or small change. The version lives in `pom.xml`.

**Branches.** The app's update banner reads the version in `pom.xml` on `main`, so everything that is pushed to `main` is a release. Day-to-day work happens on the `dev` branch (push it as often as you like, nobody is told about it); when a set of changes is ready, merge `dev` into `main` and push, and that is when users see the banner:
```
git checkout main
git merge dev
git push
git checkout dev
```

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
