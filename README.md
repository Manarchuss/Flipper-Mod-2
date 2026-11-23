# Sky Flipper HUD (Fabric, Minecraft 26.2)

A tiny corner HUD that shows the best **Bazaar** and **Auction House (BIN)** flips while you play Hypixel SkyBlock.
It only reads Hypixel's public API and draws text. It never clicks, sends chat, or automates anything.

```
Flips - Bazaar + AH
Bazaar (profit/hr)
Enchanted Lapis    +41.2k/h
...
Auction BIN (buy +profit, sells in)
Hyperion x1  4.20m +310k 12m
...
```
Bazaar rows show `buy order price > sell offer price`, already 0.1 coin better than the current top orders, with tax
applied. Green row = an auction deal that appeared since the last scan. `~` before a profit = estimated from listings only
(no exact recent sales of that variant yet). `x64` = stack, prices are for the whole stack.

## Keys (change in Options > Controls > Key Binds > "Sky Flipper HUD")
| Key | Action |
|-----|--------|
| U | show / hide the HUD |
| I | cycle Both -> Bazaar -> Auction |
| O | reload `config/skyflipper.json` after you edit it |

## Chat alerts
When a brand-new auction deal with at least `alertMinProfit` profit (default 200k, 0 = off) appears, you get a chat line like
`[Flip] Hyperion: buy 4.20m, resell ~4.55m, profit 310k, sells in ~12m. Find it: /ah Hyperion`. At most `alertMax` per scan.

## Settings
Edit `config/skyflipper.json` (created on first launch), then press O in game:
`hudX`/`hudY` (position), `rows`, `alertMinProfit`, `alertMax`, `saveSales`, `bzBudget`, `bzTax`, `bzMinVolume`, `ahBudget`, `ahMinProfit`, `ahMinPct`,
`ahSafetyPct`, `ahMinComps`, `ahMinSold`, `ahMaxHours`, `ahStrictReforge`, `ahIncludePets`, `ahRefreshSeconds`.

## Building the jar (no coding needed)
**Option A - GitHub does it for you**
1. Make a free GitHub account, create a new repository, and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab, wait for the green check on "build", open the run, and download the `skyflipper-mod` artifact.
3. Unzip it: the jar inside is your mod.

**Option B - on your PC**: install JDK 25 and Gradle 9.5, then run `gradle build`. The jar is in `build/libs/` (use the one *without* `-sources`).

If the build says a version doesn't exist, go to https://fabricmc.net/develop/template/ , pick 26.2, and copy the
versions it gives you into `gradle.properties`.

## Installing
Put the jar in your instance's `mods` folder (in the Modrinth app: your instance > Files > mods). Needs Fabric API,
which the Skyblocker modpack already includes. Restart the game.
The first AH scan decodes ~60k listings and takes a few seconds. The AH list needs real sales data to show anything,
but sales are now saved to `config/skyflipper-sales.json`, so the 10-15 minute warm-up only happens on the very first run.
That is intentional: it only lists items that are actually selling.

## Notes
* AH fees used: 1% listing under 10m / 2% under 100m / 2.5% above, plus 1% claim tax on sales of 1m+. Hypixel can change these.
* Always check the real listing in game before buying. The tool can't see everything about an item.
