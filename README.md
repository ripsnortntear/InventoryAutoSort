# 📦 InventoryAutoSort

A modern, lightweight, hotbar-safe inventory auto-sorting plugin for **Minecraft Paper 1.21.x** servers.  
Sort and stack your entire backpack or containers with a simple **double right-click** on any empty slot — no commands, no hassle.

---

## ✨ Features

- 🖱️ **Double right-click** — any empty slot to sort and stack.
- 🔒 **Hotbar preserved** — slots 0–8 are protected and never reordered.
- 📚 **Smart auto-stacking** — merges duplicate items before sorting, respecting max stack sizes.
- 🎒 **Container support** — sorts chests, double chests, barrels, shulker boxes, and ender chests.
- 🛡️ **Virtual Vault & GUI protection** — protects virtual vaults and custom menus (e.g. **PlayerVaultZ**, DeluxeMenus, ChestCommands) from having navigation items corrupted or sorted.
- 🚫 **Configurable blacklists** — easily blacklist specific inventory titles or plugin holder classes.
- ⏱️ **Sort cooldown & debounce** — configurable cooldown prevents spam sorting.
- 💾 **Persistent player preferences** — players can toggle sorting on or off (`/isort`), persisting across restarts.
- 🧺 **Vanilla-friendly** — placing items with right-click while holding items on the cursor works normally without interference.
- 🌎 **Map safe** — maps are never merged or corrupted.
- ⚡ **Lightweight & fast** — zero external runtime dependencies, asynchronous preference saving.

---

## 🎮 How To Use

1. Open your inventory or container.
2. **Right-click** an empty slot once *(you will hear a subtle tick sound)*.
3. **Right-click** an empty slot on the same inventory again within 500ms.
4. ✅ Your inventory or container is instantly stacked and sorted!

> **Note:** Clicking in your own backpack sorts your backpack. Clicking in a container sorts that container. Your **hotbar (slots 0–8) is always safe**.

---

## 💻 Commands & Aliases

Commands: `/inventorysort`, `/isort`, `/autosort`

| Command | Permission | Description |
|---|---|---|
| `/isort` or `/isort toggle` | `inventoryautosort.use` | Toggle auto-sorting on/off for yourself |
| `/isort on` | `inventoryautosort.use` | Explicitly enable auto-sorting |
| `/isort off` | `inventoryautosort.use` | Explicitly disable auto-sorting |
| `/isort reload` | `inventoryautosort.reload` | Reload `config.yml` |
| `/isort help` | None | Show command help |

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `inventoryautosort.use` | `true` | Allows players to use the auto-sort feature |
| `inventoryautosort.reload` | `op` | Allows reloading plugin configuration |
| `inventoryautosort.admin` | `op` | Full administrative control (includes reload and use) |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Time window in milliseconds between the two right-clicks to trigger sorting.
double-click-window: 500

# Cooldown in milliseconds between successful sorts.
sort-cooldown: 2000

# Whether to show the action bar message after a successful sort.
show-action-bar: true
action-bar-message: "&a✔ Inventory sorted & stacked! Hotbar safe ✓"
cooldown-message: "&cPlease wait &e{time}s &cbefore sorting again!"

# Whether to play subtle sound feedback.
play-sounds: true

# Keep slots 0-8 untouched when sorting player backpack.
preserve-hotbar: true

containers:
  # Enable sorting of external containers (chests, barrels, etc.).
  enabled: true

  # Protect virtual inventories (PlayerVaultZ, plugin GUIs). Set to FALSE (default)
  # to prevent sorting virtual vaults and plugin menus.
  sort-virtual-inventories: false

  # Allow sorting Ender Chests.
  sort-ender-chests: true

  # Allowed inventory types for container sorting.
  allowed-types:
    - CHEST
    - BARREL
    - SHULKER_BOX
    - ENDER_CHEST
    - DISPENSER
    - DROPPER
    - HOPPER

  # Titles of inventories to never sort (case-insensitive substring or regex).
  blacklisted-titles:
    - "Vault"
    - "PlayerVault"
    - "Menu"
    - "Shop"
    - "Trade"
    - "Warps"
    - "Crate"
    - "Auction"
    - "Bank"
    - "Backpack"

  # InventoryHolder classes to never sort (case-insensitive substring match).
  blacklisted-holders:
    - "playervault"
    - "playervaultz"
    - "menu"
    - "gui"

debug: false
```

---

## 🏗️ Building From Source

### Requirements
- **Java 21+**
- **Maven 3.8+**

```bash
# Clone the repository
git clone https://github.com/ripsnortntear/InventoryAutoSort.git
cd InventoryAutoSort

# Compile and package
mvn clean package
```

The compiled shaded jar will be located in the `target/` directory:
```
target/InventoryAutoSort-1.3.0.jar
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
