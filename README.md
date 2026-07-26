# SurvivalCreativityMod

Client-side Fabric mod for Minecraft **26.2**. Enter a local creative-like imagination, save your build, then preview it as a hologram in survival while you place the real blocks.

## Controls

Defaults (changeable under **Options → Controls → Survival Creativity**):

| Key | Action |
|-----|--------|
| **I** | Toggle imagination edit (local creative). Exit snaps you back to your body. |
| **K** | Save current imagination and return to survival |
| **H** | Open saved imaginations — **click one to load its hologram** |
| **U** | Hide active hologram preview |

## How it works

1. Press **I** — local creative. Your body position is remembered.
2. Build as usual. Press **K** to save (or **I** to discard) — you snap back and the imagined blocks disappear.
3. Press **H** and **click an imagination** — hologram appears in survival.
4. Place real blocks to clear hologram pieces. Press **U** to hide.

Imaginations are stored under `survivalcreativitymod/imaginations/<world>/` in your game directory.

## Setup

```powershell
.\gradlew.bat clientClasses
```

Then run **Minecraft Client** from the debugger.

## License

This template is available under the CC0 license.
