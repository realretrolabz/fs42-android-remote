# Skin Authoring

FS42 Remote skins are built inside the Android app. A skin is a background image plus tappable zones placed over that image.

## Basic Workflow

1. Open the app settings with the gear icon.
2. Go to `Themes`.
3. Turn on `Edit Mode`.
4. Choose `Image` to import a remote background, or use the bundled default.
5. Use `Add Element` to add buttons or the display text box.
6. Move and resize zones on top of the image.
7. Save with `Save Skin`.
8. Use `Installed Skins` to load or delete saved skins.

The default skin cannot be deleted. Custom installed skins can be deleted.

## Images And Scaling

Traditional tall remote images work especially well, and they are the easiest place to start. They are not the only option, though. A skin can be a photo of a real remote, a custom graphic, a novelty layout, a dashboard-style panel, or anything else you can make tappable.

The app does not draw the button graphics for you; it draws invisible or editable touch zones over whatever image you choose. Let the artwork be as practical or as strange as you want, then place zones where taps should happen.

The `Fill Screen` toggle controls how the image is displayed:

- On: fills the device screen and crops excess image area if needed.
- Off: fits the full image on screen and may show letterboxing.

The setting is saved into the skin JSON, so a skin reloads with the same scaling mode you saved.

Touch zones are stored as normalized coordinates relative to the displayed image. This keeps skins portable across different Android screen sizes.

## Touch Zone Shapes

When adding a button, the app asks which shape to use.

### Rectangle

Use rectangles for most normal remote buttons.

Rectangle zones are the easiest to place and resize. They work well for square, rectangular, and rounded-rectangle buttons.

### Circle

Use circles for round buttons or round-ish controls.

The hit area is an ellipse inside the zone bounds, so resize the bounding box until the visible hit area matches the button art.

### Polygon

Use polygons for unusual or angled buttons.

Polygon creation works like connect-the-dots:

1. Choose `Polygon`.
2. Tap around the button outline to add points.
3. Add at least 3 points.
4. Use up to 24 points.
5. Tap `Finish` when the outline matches the button.

In edit mode, polygon vertices appear as small draggable circles. Drag a vertex to fine tune the outline.

Polygon touch detection uses the actual polygon area, not the bounding rectangle.

## Display Text Box

The display text box is an optional live overlay. It is useful when the skin art includes a blank LCD/VFD area.

The app can show:

- channel input
- guide scroll
- now playing
- host temperature/load status
- errors or connection messages

Do not bake changing text into the background image. Leave the display area blank in the artwork and let the app render the live text.

## Guide Button Display Modes

The `GUIDE` button cycles through display modes:

1. Guide scroll from the guide bridge.
2. Now playing from FieldStation42 status.
3. Host temperature, CPU load, and memory load from the guide bridge.

The guide bridge is optional for normal remote controls, but guide/status display modes require it.

## Saving And Sharing

`Save Skin` installs the skin locally and exports a `.fs42skin` package to:

```text
Documents/FS42 Remotes
```

The `.fs42skin` file is a ZIP package containing `skin.json` and the background image asset.

## Tips

- Start with rectangle zones unless the artwork really needs another shape.
- Keep touch zones slightly larger than the visible button art for easier tapping.
- Use polygons sparingly; they are powerful but slower to edit than rectangles.
- Save after major layout changes.
- Use Undo/Redo in edit mode for quick layout corrections.
