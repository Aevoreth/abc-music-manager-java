package com.aevoreth.abcmm.domain.setplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set Play session state: item ids index into the ordered setlist; flags use {@code SetlistItem.id}.
 * Mirrors Python {@code set_play_state.SetPlaySessionState}.
 */
public final class SetPlaySessionState {

    private final List<Long> orderItemIds;
    private final Set<Long> playedItemIds = new HashSet<>();
    private Long currentItemId;
    private Long nextItemId;
    private final Set<Long> skippedItemIds = new HashSet<>();
    private int revision;

    public SetPlaySessionState(List<Long> orderItemIds) {
        this.orderItemIds = new ArrayList<>(Objects.requireNonNull(orderItemIds, "orderItemIds"));
    }

    public List<Long> orderItemIds() {
        return orderItemIds;
    }

    public Set<Long> playedItemIds() {
        return playedItemIds;
    }

    public Long currentItemId() {
        return currentItemId;
    }

    public void setCurrentItemId(Long currentItemId) {
        this.currentItemId = currentItemId;
    }

    public Long nextItemId() {
        return nextItemId;
    }

    public void setNextItemId(Long nextItemId) {
        this.nextItemId = nextItemId;
    }

    public Set<Long> skippedItemIds() {
        return skippedItemIds;
    }

    public int revision() {
        return revision;
    }

    public void bumpRevision() {
        revision++;
    }

    public int itemIndex(long itemId) {
        int idx = orderItemIds.indexOf(itemId);
        if (idx < 0) {
            throw new IllegalArgumentException("itemId not in order: " + itemId);
        }
        return idx;
    }
}
