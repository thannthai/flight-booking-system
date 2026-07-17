package com.thanh.backend.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    /**
     * Chuyển đổi từ đối tượng Page của Spring Data sang PageResponse chuẩn hóa.
     */
    public static <T> PageResponse<T> of(Page<T> springPage) {
        return PageResponse.<T>builder()
                .content(springPage.getContent())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .last(springPage.isLast())
                .build();
    }
}
