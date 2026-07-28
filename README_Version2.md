# EaglerShop

EaglerShop is a Spigot/Paper plugin for EaglerCraft-compatible servers (1.8 - 1.14) providing:
- rules-based supply & demand shop that adjusts prices,
- enchant valuation applied after SD adjustments (positive for bonuses, negative for curses),
- auction house with fixed and timed auctions, listing fees, sale taxes, auto-delivery,
- Vault integration for economy and permissions (LuckPerms support via Vault),
- YAML persistence.

Installation
1. Build with Maven:
   mvn clean package
   The jar-with-dependencies will be in target/.
2. Drop the jar into your server `plugins/` folder.
3. Ensure Vault and an economy plugin (EssentialsX or similar) are installed and loaded.
4. Start the server. Edit config.yml as needed and use `/shopadmin reload` to apply changes.

Permissions (LuckPerms examples)
- Give base access:
  lp group default permission set eagler.shop.use true
  lp group default permission set eagler.ah.use true
- Allow selling/listing to doner:
  lp group doner permission set eagler.shop.sell true
  lp group doner permission set eagler.ah.list true
- Admins:
  lp group admin permission set eagler.shop.admin true
  lp group owner permission set eagler.ah.admin true

Config & tuning
- `config.yml` is commented. `sd.yml` and `auctions.yml` are created by the plugin for runtime data (counters, listings).
- Supply/demand formula:
  price = basePrice * clamp(1 + (demand - supply) * elasticity, minMultiplier, maxMultiplier)
- Enchant % values are applied after SD adjustments. Curses are negative in default config.

Commands
- /shop — open shop GUI; /shop price <material> /shop sell
- /shopadmin — admin actions (reload/save/additem/resetstats)
- /ah — auction GUI; create/list/bid/buy/claim/cancel
- /ahadmin — admin auction actions

Testing
- Buy/sell items and check /shop price to verify SD changes.
- Make auctions with /ah create while holding item; test bidding and buy-it-now behavior.
- Use /ah claim to get pending items/money.

If you want ongoing help (CI pipeline, prebuilt artifact upload, tweaks), tell me and I'll assist.