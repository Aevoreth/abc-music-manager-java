# Instrument optimizer: settings and examples

The optimizer changes which characters play existing parts. It preserves the song order, the instruments in the arrangement, and your characters' positions. It never grants a character an instrument they do not own.

Open **Optimizer help…** from Optimizer settings, part settings, or the optimization preview to return to this page.

## One settings window

Hover over a control, table cell, or column heading for a brief explanation. **Optimizer help…** opens a separate window that you can read, scroll, or close while continuing to use the optimizer or its settings.

Open **Optimizer settings…** from Bands or the set optimizer. **Goals** contains four on/off goals and the simultaneous-change target. Expand **Advanced tuning** for exact weights, the legacy band switch multiplier, and character switch multipliers. Existing saved values are retained.

**Character & instrument rules** edits all instrument roles and placement choices in one place. **Song exceptions** lets you choose a set and a song occurrence. Set song preservation separately from each part's **Automatic**, **Prefer character**, or **Pin to character** assignment. Expand **Advanced part preservation overrides** for part multipliers. Blank part values inherit the song value; blank song values inherit 1. Save commits changes across the window; Cancel discards them.

Search duration appears once, beside **Search**. It starts from the saved band default and remembers the last duration used for that band layout, including a custom duration.

## Keep loud instruments in the back row

In **Optimizer settings → Character & instrument rules**, choose an instrument such as bardic fiddle, sprightly fiddle, LM fiddle, or basic bassoon. Select your back-row characters by name:

- **Prefer**: try to give this instrument to one of these characters.
- **Avoid**: try not to give this instrument to this character.
- **No preference**: do not add a character-specific placement penalty.

If at least one character is marked Prefer, assigning the instrument outside that preferred group adds the placement penalty. Assigning it to an Avoid character also adds that penalty. These conditions do not charge twice for the same part. The penalty is shared by the instrument, then multiplied by **Instrument preferences**. Character preference strength and hard-requirement priority have separate columns; priority only applies to Always requirements.

Start with a placement penalty of **10** for loud fiddles. This is a strong preference, not a ban. If only a front-row character can cover the part, the optimizer can still use that character. Other competing goals can also outweigh this preference. A bassoon drone can use a smaller penalty when its position matters less.

Placement is edited only in this shared rules table. Character roles and instrument placement have separate columns because they express different preferences. These settings do not move characters around the layout.

## Prefer, Always take, Avoid, and Pin

| Setting | Meaning |
|---|---|
| Character prefers an instrument | Discourage giving that character a different instrument. An idle character adds no role penalty. |
| Instrument prefers characters | Discourage giving that instrument to anyone outside the preferred group. |
| Avoid this instrument | Discourage giving that instrument to that character; it remains allowed. |
| Always, when the song has it | A hard requirement to take that instrument when available. When several characters request fewer parts, priority decides; equal priority lets the search choose. |
| Pin this part | Assign this specific part of this song occurrence to this character. This is a hard constraint. Ownership still applies. |

Use **Song exceptions** for exceptions, or open **Optimizer settings for this part…** from an assignment. A **part preference** replaces band role and placement penalties for that part. A **pin** fixes the assignment even when a soft preference disagrees. Repeated appearances of the same song have separate settings. Hard requirements and pins cannot be overcome by increasing a weight.

## What the four weights do

Lower weighted scores are better. Weights are relative costs, not percentages. Zero turns a soft goal off.

### Smooth transitions

Discourages many people switching instruments at once. The preferred maximum is a target, not a hard limit.

With target **2** and smooth weight **4**:

| People switching at a transition | Smooth units | Points added |
|---|---:|---:|
| 0, 1, or 2 | 0 | 0 |
| 3 | 1 | 4 |
| 4 | 4 | 16 |
| 5 | 9 | 36 |

This grows faster than the total-switch cost, so a large spike is expensive. The reported worst transition is informative; it is not a separate weighted goal.

### Total switches

Discourages all instrument changes. One switch adds the total-switch weight times that character's switch multiplier. A per-character multiplier replaces the band default.

Only adjacent songs where a character plays different instruments in **both** songs count. **Fiddle → idle → harp adds zero switches.** The first song has no incoming switch. Instrument names that represent the same physical instrument, including Traveler/Traveller aliases, count as the same instrument.

### Instrument preferences

Multiplies instrument preferences, placement and Avoid penalties, and song part preferences. At role weight **2**, a placement penalty of **10** costs **20** points per affected part. At role weight **0**, all these soft preferences are ignored. Always requirements and pins remain hard.

### Existing assignments

Discourages changing assignments you have already rehearsed. One changed character slot costs this weight times its keep multiplier. Moving a part from one character to another can change two slots.

A part keep multiplier replaces the song keep multiplier, which replaces the default **1**. Blank override fields inherit the default. A multiplier of zero removes that preservation cost.

## Search, review, and apply

**Quick** uses 2 seconds, **Normal** 10 seconds, and **Thorough** 60 seconds. Longer searches may not improve the result. The search tries additional feasible starting assignments while continuing to compare preservation costs with the original assignments.

Cancel keeps the best feasible assignment found so far. Search does not write assignments. Review the transition counts, changed parts, and character burdens before **Apply**. Undo restores the previous assignments when no conflicting edits have intervened. The result is the best found during the search, not a proof of optimality.

## Starting with unassigned songs

Songs do not need existing layout assignments. Search fills every part using characters on the selected band layout who own its instrument. Unassigned parts are expected input, not a reason to refuse search.

A preference or pin naming a character outside the selected band layout can block search, for example after copying a set for another band. The **Optimize instruments** window shows a warning above the results as soon as the set loads. **Review and fix…** opens Song exceptions at the affected song and part. Review the warning and use **Clear rules for characters outside this layout**, then **Save**. This explicitly removes only those incompatible character references, preserving valid pins and preservation multipliers. Cancel discards the cleanup. A pin is never silently ignored during search.
