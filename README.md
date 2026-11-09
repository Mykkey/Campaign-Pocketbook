# D&D Campaign Notes

A whiteboard-style note app for planning a D&D campaign (JavaFX + Maven).

## Requirements
- 64-bit JDK 21 or newer (`java -version` must show it)
- Apache Maven 3.9+

## Run
```
mvn javafx:run
```
Run tests: `mvn test`. Projects are saved in `~/DnDCampaignNotes/<project name>/`
(`project.json`, `boards/*.json`, `images/`).

## Using it
- **Project tree** (left): `+ Board` / `+ Folder`, right-click to rename/delete, drag to move. Double-click a board to open it in a tab.
- **Add text**: double-click empty space (or use the Text tool). Double-click text to edit it, Ctrl+Enter / Esc / click away to finish.
- **Pan / zoom**: Hand tool, middle-mouse drag, or hold Space + drag; mouse wheel zooms. Ctrl+0 resets zoom.
- **Link**: select any object (text, sticky, image, shape...) then press **Link...** (Ctrl+L, or right-click). Double-click a linked object to jump to that board. Linked objects show a blue badge (red if the target was deleted).
- **Navigation**: Back/Forward buttons (Alt+Left/Right), clickable trail of visited boards, **Backlinks** menu shows what links to the current board.
- **Search**: box at the top right (Ctrl+F) searches all text and board names.
- Undo/redo Ctrl+Z / Ctrl+Y, copy/paste/duplicate Ctrl+C/V/D (works across tabs), Delete, Ctrl+A. Paste or drag-and-drop images straight onto a board.
- Everything autosaves.
