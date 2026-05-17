# World Plan v0.1

## Server Identity

XiceMCServer is a private-first Java Edition Minecraft server combining survival, building, and RPG progression. The default experience should feel close to vanilla survival, while selected worlds and regions provide RPG quests, dungeons, events, and structured combat.

The server is designed for friends first, with enough structure to support a future public opening.

## World List

| World | Persistence | Main Purpose | PVP | Reset Policy |
| --- | --- | --- | --- | --- |
| `main` | Permanent | Survival, homes, towns, long-term builds | Off by default | Never reset unless catastrophic |
| `main_nether` | Permanent | Long-term Nether infrastructure | Off by default | Avoid reset |
| `main_the_end` | Semi-permanent | Dragon, End builds, long-term End access | Off by default | Reset only after announcement |
| `resource` | Temporary | Mining, wood, sand, exploration resources | Off by default | Monthly or as needed |
| `resource_nether` | Temporary | Nether resources | Off by default | Monthly or tied to `resource` |
| `resource_end` | Temporary | Elytra, shulkers, End resources | Off by default | Monthly or event-based |
| `rpg` | Mixed | Quests, NPC areas, dungeons, bosses, story zones | Region-controlled | Reset per dungeon or season |
| `pvp` | Temporary or arena-based | Duels, arenas, events, battlegrounds | On in marked regions | Reset or rebuild freely |
| `build_test` | Temporary | Creative planning, build prototypes, staff testing | Off | Reset freely |

## Permanent World Rules

`main` is the heart of the server. It should preserve player history, bases, towns, roads, landmarks, farms, and public works.

Rules for permanent worlds:

1. No stealing.
2. No griefing.
3. No TNT, fire, lava, water, or entity-based destruction of other builds.
4. No PVP unless a clearly marked event or region enables it.
5. Public infrastructure should be protected with region permissions.
6. Major community builds should be documented before construction.

## Resource World Rules

Resource worlds exist so the permanent world can stay beautiful and stable.

Rules for resource worlds:

1. Players may mine aggressively and gather terrain-heavy resources.
2. No permanent homes or important builds should be placed here.
3. Reset dates must be announced in advance.
4. Valuable items left in resource worlds are the player's responsibility after reset announcements.
5. Reset cadence starts as monthly, then adjusts based on player activity.

## RPG Design

RPG gameplay should be added in layers, without overwhelming the vanilla survival base.

Initial RPG scope:

1. A protected spawn hub with lore, shops, portals, and notices.
2. Small quest areas near spawn or in the `rpg` world.
3. Repeatable dungeons with controlled loot.
4. Event bosses during scheduled sessions.
5. Region-specific PVP only when the event clearly requires it.

RPG progression should avoid invalidating vanilla survival. Rewards should be useful, cosmetic, collectible, or convenience-oriented before they become raw power.

## PVP Policy

PVP is disabled by default. PVP is allowed only in:

1. Marked arenas.
2. Scheduled events.
3. RPG regions that explicitly state combat rules.
4. Mutually agreed duels if a duel plugin is later added.

Unauthorized killing, trap abuse, spawn camping, or using PVP rules to steal items should be treated as rule abuse.

## Protection Strategy

Recommended protection layers:

1. Use whitelist and online-mode authentication.
2. Use LuckPerms for granular permissions.
3. Use CoreProtect for audit and rollback.
4. Use WorldGuard for spawn, public builds, RPG regions, and PVP zones.
5. Use a claims or land plugin for player builds if the group grows.

OP should not be used for normal administration. Administrative features should be granted through specific permissions.

## Reset and Announcement Policy

Permanent worlds are not reset.

Temporary worlds can reset only after:

1. The reset target is named clearly.
2. The reset date and time are announced.
3. Players are reminded which worlds are permanent and which are temporary.
4. A backup is created before reset.

Suggested initial schedule:

1. `resource` and `resource_nether`: reset monthly.
2. `resource_end`: reset monthly or after major End events.
3. `rpg` dungeon regions: reset per dungeon cycle.
4. `pvp`: reset whenever arenas or events change.

## Open Questions

1. Should player claims be available from day one, or only after the friend group grows?
2. Should the economy be purely player trade, or should shops and currency exist?
3. Should the `rpg` world use custom mobs and items, or stay close to vanilla at first?
4. Should the End be permanent, resource-based, or split into both?
5. What is the preferred maintenance window day and time?
