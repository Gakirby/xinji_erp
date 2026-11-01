package com.xinji.erp.common;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private List<T> list = new ArrayList<>();
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        PageResult<T> p = new PageResult<>();
        p.list = list;
        p.total = total;
        p.page = page;
        p.size = size;
        return p;
    }
}
