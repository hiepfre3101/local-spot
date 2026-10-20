package com.localspot.dto.response;

import java.util.List;

/** openapi {@code CategoryNode} — cây tối đa 2 cấp (chốt 2026-10-06): gốc có {@code children}, con có danh sách rỗng. */
public record CategoryNode(Long id, String name, String slug, String icon, List<CategoryNode> children) {

    public CategoryNode {
        children = List.copyOf(children);
    }
}
