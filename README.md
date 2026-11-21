# EMC Interface for NeoForge

Connects ProjectE's learned items and EMC balance to adjacent item handlers.

## Installation

Java 21, Minecraft 1.21.1, and NeoForge. Tested with 21.1.265. Download `emc_interface-1.21.1.2.jar` from Releases. Install on both sides with EMC Precision 1.2.0 and its ProjectE companion. Sneak-use the block with an empty hand to toggle adjacent automation. The owner's account is available while that player is online. Tom's Simple Storage was tested.

将 ProjectE 的已学习物品和 EMC 接入存储网络，支持相邻设备自动取放。

## Build

Use JDK 21 and Python 3.11+. Create a text file with one absolute JAR path per line for the mapped Minecraft 1.21.1 and NeoForge 21.1.265 libraries, plus `emc_precision-1.2.0.jar`, `ProjectE-1.21.1-PE1.1.0-precision.jar`. Put `neoforge-21.1.265-client.jar` first in the classpath.

```sh
python build.py --classpath-file classpath.txt --output emc_interface-1.21.1.2.jar
```

## License and feedback

MIT. Independent NeoForge implementation inspired by the EMC Interface concept by OfekN. The original project is https://modrinth.com/mod/emc-interface.

Report issues here with reproduction steps and your Minecraft, NeoForge and mod versions.
