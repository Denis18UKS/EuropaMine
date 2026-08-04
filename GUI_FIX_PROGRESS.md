# GUI reactor correction (WIP)

This branch is limited to correcting the reactor and electrical-panel GUI introduced by the previous PR.

Current scope:
- prevent GUI upscaling above the native reference resolution;
- replace the stretched maintenance and technical-panel crops;
- rebuild the reactor screen against the supplied reference screenshots;
- keep reactor simulation, packets, commands, power network, repair and sabotage logic unchanged.

Do not merge until the visual comparison and GitHub Actions build are complete.
