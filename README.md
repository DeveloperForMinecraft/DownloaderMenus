
<p align="center">
</p>

<h1 align="center">DownloaderMenus</h1>
<h4 align="center">Minecraft 1.16.5 · Forge · client-side</h4>
<p align="center">Download any server's DeluxeMenus menu as a ready-to-use config — items and click logic included.</p>

<div align="center">
    <br>
    <img src="https://img.shields.io/github/last-commit/DeveloperForMinecraft/DownloaderMenus" alt="GitHub last commit"/>
    <img src="https://img.shields.io/github/commit-activity/w/DeveloperForMinecraft/DownloaderMenus" alt="GitHub commit activity"/>
    <img src="https://img.shields.io/github/contributors/DeveloperForMinecraft/DownloaderMenus?color=lyme" alt="GitHub contributors"/>
    <br>
    <img src="https://img.shields.io/github/languages/code-size/DeveloperForMinecraft/DownloaderMenus" alt="GitHub code size in bytes"/>
    <img src="https://img.shields.io/endpoint?url=https://ghloc.vercel.app/api/DeveloperForMinecraft/DownloaderMenus/badge?filter=.java$&label=lines%20of%20code&color=blue" alt="GitHub lines of code"/>
</div>

## Features
- **One-click export** — the "Скачать Меню" button on any chest GUI saves a ready-to-use DeluxeMenus YAML: names, lore, hex gradients, base64 heads, enchantments, potions, banners, map ids, item flags.
- **`/menulogic` recorder** — click through menus like a player; the mod reconstructs the logic: menu-to-menu transitions (`[openguimenu]`), closes (`[close]`), chat responses, `open_command` — as configs plus a `logic_graph.json`.
- **DeluxeMenus 1.14.1 verified** — legacy enchant/effect names, `item_flags`, `damage`: configs load with zero warnings.
- **Tiny & dependency-free** — ~50 KB jar, no server plugins required, works on vanilla / Spigot / Paper.

## Usage
1. Install Forge 1.16.5 and put [DownloaderMenus.jar](https://github.com/DeveloperForMinecraft/DownloaderMenus/releases/download/Release/DownloaderMenus.jar) into `.minecraft/mods/`.
2. Join a server and open any menu:
   - click **"Скачать Меню"** to save the menu, or
   - type **`/menulogic`**, interact with menus, type it again — get the menus **and** their logic.
3. Files appear in `.minecraft/Downloaded Menus/<server ip>/`.

## Building
Clone the repo and run `./gradlew build`.

## Bugs and Suggestions
Use the [issue tracker](https://github.com/DeveloperForMinecraft/DownloaderMenus/issues). The more details, the better.
