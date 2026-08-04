# Navigation terminal any-block binding fix

The `navigation_terminal` GUI is opened directly through `NavigationSystem.open(...)` for every block carrying this GUI binding.

No block-class check is performed. Native block handling remains only for GUI types that explicitly require their own block implementation.
