# GlowingEntities

![Maven Central](https://img.shields.io/maven-central/v/games.qore/GlowingEntities)

A lightweight utility to make entities (and blocks) glow per-player on modern Paper servers.

Originally by **SkytAsul**, modified and maintained by **qore games**.

- Paper-only `26.1+`
- No ProtocolLib dependency

![Glowing entities animation](demo.gif)

![Glowing blocks animation](demo-blocks.gif)

## Installation

This library is intended to be embedded in your plugin jar. You should shade and relocate it.

### Gradle

```kotlin
dependencies {
    implementation("games.qore:glowingentities:{VERSION}")
}
```

### Maven

```xml
<dependency>
  <groupId>games.qore</groupId>
  <artifactId>glowingentities</artifactId>
  <version>{VERSION}</version>
  <scope>compile</scope>
</dependency>
```

You must shade this library and relocate the package to avoid classpath conflicts.

Minimal Gradle Shadow setup:

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

tasks.shadowJar {
    relocate("games.qore.glowingentities", "com.your.plugin.glowingentities")
}
```

## Usage

### Basic example

```java
import games.qore.glowingentities.GlowingEntities;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    private GlowingEntities glowingEntities;

    @Override
    public void onEnable() {
        this.glowingEntities = new GlowingEntities(this);

        // Example placeholders: replace with your real entities/players.
        Item itemEntity = null;
        Player player = null;

        if (itemEntity != null && player != null) {
            this.glowingEntities.setGlowing(itemEntity, player);
        }
    }

    @Override
    public void onDisable() {
        if (this.glowingEntities != null) {
            this.glowingEntities.disable();
        }
    }
}
```

### Make entities glow

1. Create a single `GlowingEntities` instance with `new GlowingEntities(plugin)`.
2. Call `GlowingEntities#setGlowing(Entity entity, Player receiver, NamedTextColor color)`.
3. Reuse `setGlowing` with a different color to update it.
4. Call `GlowingEntities#unsetGlowing(Entity entity, Player receiver)` to remove the effect.
5. Call `GlowingEntities#disable()` when your plugin shuts down.

### Make blocks glow

Use `GlowingBlocks` in the same way.

## Attribution

- Original project and API design: SkytAsul
- This fork: package namespace migration to `games.qore.glowingentities`, Gradle/Paper-first tooling, and Paper `26.1+` support
