# Instrument optimizer: settings and examples

The optimizer changes which characters play existing parts. It preserves the song order, the instruments in the arrangement, and your characters' positions. It never grants a character an instrument they do not own.

Open **Optimizer help…** from the band optimizer rules, song optimizer settings, or optimization preview to return to this page.

## Keep loud instruments in the back row

In **Bands → Optimizer rules → Instrument placement…**, choose an instrument such as bardic fiddle, sprightly fiddle, LM fiddle, or basic bassoon. Select your back-row characters by name:

- **Prefer**: try to give this instrument to one of these characters.
- **Avoid**: try not to give this instrument to this character.
- **No preference**: do not add a character-specific placement penalty.

If at least one character is marked Prefer, assigning the instrument outside that preferred group adds the placement penalty. Assigning it to an Avoid character also adds that penalty. These conditions do not charge twice for the same part. The penalty is shared by the instrument, then multiplied by **Role preservation**.

Start with a placement penalty of **10** for loud fiddles. This is a strong preference, not a ban. If only a front-row character can cover the part, the optimizer can still use that character. Other competing goals can also outweigh this preference. A bassoon drone can use a smaller penalty when its position matters less.

You can also select **Avoid** from an individual character's **Instrument preferences…** window. Prefer and Avoid there are mutually exclusive. These settings do not move characters around the layout.

## Prefer, Always take, Avoid, and Pin

| Setting | Meaning |
|---|---|
| Character prefers an instrument | Discourage giving that character a different instrument. An idle character adds no role penalty. |
| Instrument prefers characters | Discourage giving that instrument to anyone outside the preferred group. |
| Avoid this instrument | Discourage giving that instrument to that character; it remains allowed. |
| Always, when the song has it | A hard requirement to take that instrument when available. When several characters request fewer parts, priority decides; equal priority lets the search choose. |
| Pin this part | Assign this specific part of this song occurrence to this character. This is a hard constraint. Ownership still applies. |

Use the song's optimizer settings for exceptions. A **part preference** replaces band role and placement penalties for that part. A **pin** fixes the assignment even when a soft preference disagrees. Repeated appearances of the same song have separate settings. Hard requirements and pins cannot be overcome by increasing a weight.

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

### Role preservation

Multiplies instrument preferences, placement and Avoid penalties, and song part preferences. At role weight **2**, a placement penalty of **10** costs **20** points per affected part. At role weight **0**, all these soft preferences are ignored. Always requirements and pins remain hard.

### Existing assignments

Discourages changing assignments you have already rehearsed. One changed character slot costs this weight times its keep multiplier. Moving a part from one character to another can change two slots.

A part keep multiplier replaces the song keep multiplier, which replaces the default **1**. Blank override fields inherit the default. A multiplier of zero removes that preservation cost.

## Search, review, and apply

**Quick** uses 2 seconds, **Normal** 10 seconds, and **Thorough** 60 seconds. Longer searches may not improve the result. The search tries additional feasible starting assignments while continuing to compare preservation costs with the original assignments.

Cancel keeps the best feasible assignment found so far. Search does not write assignments. Review the transition counts, changed parts, and character burdens before **Apply**. Undo restores the previous assignments when no conflicting edits have intervened. The result is the best found during the search, not a proof of optimality.
