StealthRealtor - A region use plugin for Bukkit
==================

Entirely command-based system allowing your players to buy plots or rent inn rooms using WorldGuard.

**Features:**

* Put plots on the market using only the WorldGuard commands you already know - pricing and status is all stored in the standard WG flags "price" and "buyable"
* Effectively control players' ability to sell/rent their own plots using the flag and command specific permissions within WorldGuard (particularly those ending in ".own")
* Lightweight command based transactions eliminate the need for plugin-related objects (like signs) in your world and allow easy integration with scripting such as Denizen so your players don't need to actually type commands
* Powerful zoning system for local buy/rent permissions and sales tax
* Easily split up the money -- revenue gets divided among all owners of a region if there are multiple
* Maintains a "legit" economy -- money is never created or destroyed, only transferred between players


Changelog
-------------------

**1.0.0**

* Initial commit.