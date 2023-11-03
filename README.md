# aeron-demos
A collection of Aeron demos

Normally you just need to do:
- cd to sub folder
- build: ```mvn clean package``` -> there will be FAT jar in target folder
- run it by bash script provided in ```resource```, please update the path to FAT jar beforehande

## Changes

- Switched to Gradle so we have centralized version control @ root package level
- To run a subproject, invoke ```gradle <subproject>:run```
- no fat jar, no bash script bs (heck, original author was mixing jdk16 in bash with jdk8 in mvn source compat).
- removed redundant ping pong demo with different network protocols
- original 2p1c submodule use case is not interesting ... rewrote the whole submodule
- ... maybe should've read more before i forked.
- ??? why is IOTA receiver not working