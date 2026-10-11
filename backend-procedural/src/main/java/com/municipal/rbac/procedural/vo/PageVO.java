package com.municipal.rbac.procedural.vo;

import java.util.List;

/** VO（View Object）：分页响应，页码从 1 开始。 */
public record PageVO<T>(List<T> items, int page, int pageSize, long total) {
}
