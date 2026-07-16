package com.aevoreth.abcmm.domain.library;

/**
 * Minimal setlist identity for Library "Go to setlist" menus.
 */
public record SetlistRef(long id, String name) {

    public SetlistRef {
        name = name == null ? "" : name;
    }
}
