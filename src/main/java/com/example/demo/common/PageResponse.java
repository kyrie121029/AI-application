package com.example.demo.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 统一分页响应格式 —— 替代直接序列化 PageImpl
 * <p>
 * 为什么需要这个类？
 * Spring Data 的 PageImpl 直接序列化为 JSON 时结构不稳定，
 * 而且暴露了大量内部字段（pageable、sort、empty 等）。
 * 这个类只返回前端真正需要的字段。
 *
 * @param <T> 列表元素类型
 */
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "数据列表")
    private List<T> content;

    @Schema(description = "当前页码（从0开始）", example = "0")
    private int page;

    @Schema(description = "每页条数", example = "10")
    private int size;

    @Schema(description = "总条数", example = "25")
    private long totalElements;

    @Schema(description = "总页数", example = "3")
    private int totalPages;

    @Schema(description = "是否最后一页", example = "false")
    private boolean last;

    // ==================== 构造方法 ====================

    private PageResponse() {}

    /**
     * 从 Spring Data Page 对象转换为 PageResponse
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.content = page.getContent();
        response.page = page.getNumber();
        response.size = page.getSize();
        response.totalElements = page.getTotalElements();
        response.totalPages = page.getTotalPages();
        response.last = page.isLast();
        return response;
    }

    // ==================== Getter（只读，无 Setter） ====================

    public List<T> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean isLast() { return last; }
}
