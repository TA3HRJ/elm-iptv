# ELM IPTV branding

| File | Use |
|---|---|
| `elm-emblem.svg` | Master emblem (transparent background): open ring with a "live" dot, play button in the middle. |
| `elm-icon.svg` | App icon (emblem on the dark rounded square). |
| `elm-banner.svg` | Fire TV / Android TV launcher banner (16:9; rendered to 640×360, 960×540 and 1280×720 px for drawable-xhdpi/-xxhdpi/-xxxhdpi, so large TVs get a sharp tile). Wordmark uses the Inter typeface (SIL Open Font License). |

Colors: background `#0E1116`, accent `#4FA3FF`, ring gradient `#2F6BFF → #4FA3FF → #3EE0FF`,
disk gradient `#1E5BE0 → #4FA3FF`, live dot `#FF5A6E`.

The Android resources in `app/src/main/res` (`mipmap-*/ic_launcher.png`, `drawable-*/banner.png`,
`drawable-nodpi/elm_emblem.png`, `drawable-v26/ic_launcher_foreground.xml`) are generated from these files.
