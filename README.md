# Circuit

Circuit is a Fabric mod for planning redstone circuits before building them in Minecraft.

The mod adds a top-down editor where you can place gates, wires, clocks, memory parts, inputs, outputs, encoders, decoders, and your own saved modules. When the plan looks right, Circuit can sync it into the world using Minecraft commands.

I built this because larger redstone projects get messy fast. Being able to sketch the circuit first, move pieces around, save reusable modules, and only then place the blocks makes experimentation a lot less painful.

## What It Can Do

- Open an in-game circuit editor with a keybind
- Place bundled redstone components from a categorized palette
- Switch between a simple labeled view and a detailed block view
- Draw grouped redstone dust lines and straight observer lines
- Work across multiple editor planes for layered builds
- Save, name, load, import, and delete `.bcdi` plans
- Turn selected parts into reusable modules
- Edit modules by opening them back into their parts
- Generate encoder and decoder structures as an experimental feature
- Sync the current plan into the Minecraft world
- Set up a redstone-friendly test world
- Install bundled `.litematic` files for Litematica users

## Requirements

- Minecraft 1.21
- Fabric Loader 0.19.2 or newer
- Fabric API
- Java 21

## Building

```powershell
.\gradlew.bat build
```

The mod jar will be in:

```text
build/libs/circuit-1.0.1.jar
```

## Notes

Circuit is mainly a client-side planning tool. Syncing a plan into the world uses commands, so you need cheats or permission to run commands on the world/server.

The encoder and decoder generator is intentionally marked experimental in the UI. The bundled hand-made components are the safer choice when you need predictable behavior.

## Project Status

This is a small personal project by cirby. Issues, bug reports, ideas, and pull requests are welcome.

## License

CC BY-NC-ND 4.0
