# 📦 InventoryAutoSort

A lightweight, hotbar-safe inventory sorting plugin for **Minecraft Paper servers**.  
Sort and stack your entire backpack with a simple **double right-click** on any empty slot — no commands, no hassle.

---

## ✨ Features

- 🖱️ **Double right-click** any empty inventory slot to sort
- 📚 **Auto-stacks** duplicate items before sorting
- 🔒 **Hotbar preserved** — slots 0-8 are never touched
- 🎒 **Container support** — sorts chests, barrels, and other containers
- ⏱️ **Sort cooldown** — prevents spam sorting
- 🔕 **Toggle support** — players can disable sorting for themselves
- 🧺 **Bundle compatible** — right-clicking with a bundle works normally
- ⚡ **Lightweight** — no dependencies, no database, no config bloat

---

## 🎮 How To Use

1. Open your inventory
2. **Right-click** an empty slot once *(you'll hear a click sound)*
3. **Right-click** the same empty slot again quickly
4. ✅ Your backpack is now sorted and stacked!

> Your **hotbar is always safe** and will never be reordered.

---

## 🔧 Installation

1. Download the latest `.jar` from [Releases](../../releases)
2. Drop it into your server's `/plugins` folder
3. Restart your server
4. That's it — no configuration needed!

---

## 📋 Requirements
- Minecraft 1.21+
- Paper 1.21.11
- Java 21+

---

## 🔑 Permissions
Default
inventoryautosort.use

Allows a player to use inventory sorting
true

---

## 🗂️ Sorting Behavior

Scenario                    Behavior

- Player inventory:            Sorts backpack (slots 9-35) only
- Hotbar (slots 0-8):          Never touched
- Chest / Barrel / Container:  Entire container is sorted
- Duplicate item stacks:       Merged before sorting
- Named / enchanted items:     Only merged with identical copies
- Bundles on cursor:           Right-click works normally

---

## 🏗️ Building From Source

# Clone the repo
git clone https://github.com/ripsnortntear/InventoryAutoSort.git

# Navigate into the folder
cd InventoryAutoSort

# Build with Maven
mvn clean package

The compiled .jar will be in the /target folder.

---

🐛 Found a Bug?

Open an Issue and include:

    Your Paper version (/version)
    What you were doing when it happened
    Any relevant console errors

🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you'd like to change.
