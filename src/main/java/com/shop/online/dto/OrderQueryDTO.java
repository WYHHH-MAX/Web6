package com.shop.online.dto;

import lombok.Data;

/**
 * 订单查询DTO
 */
@Data
public class OrderQueryDTO {
    
    /**
     * 订单状态
     */
    private String status;
    
    /**
     * 页码
     */
    private Integer page = 1;
    
    /**
     * 每页条数
     */
    private Integer size = 10;
    
    /**
     * 是否查询包含已退款商品的订单
     */
    private Boolean hasRefundedItems;
} 