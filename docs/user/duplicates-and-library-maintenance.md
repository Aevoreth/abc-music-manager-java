# Duplicates and library maintenance

---

## Duplicate review during scan

When **File → Scan Library…** finds duplicate songs or mirrored folder trees, a **Duplicate review** dialog opens.

The Java edition uses inventory-first peer groups (exact content hash and logical identity) plus **folder clusters**. Review happens in a batch tree:

1. Resolve **duplicated folders** first when present (pick which folder to keep; others can be unindexed or sent to the Recycle Bin)
2. Review remaining **individual duplicate groups** by match type
3. Apply a cleanup plan, or finish / cancel

Side-by-side metadata and diffs help you choose which copy to keep indexed.

There is no separate **Analyze duplicate folders…** menu item — folder analysis is part of the scan review flow.

---

## Set Export directory

The **Set Export directory** is intentionally **not scanned** so exported copies do not create duplicate library entries. See [Folder rules](settings/folder-rules.md).

---

## Re-scan after cleanup

Run **File → Scan Library…** again after moving, deleting, or unindexing files so the database stays accurate.

---

[← User Guide home](index.md) · [Library](library.md)
