package com.shop.online.controller;

import com.shop.online.dto.CreateOrderDTO;
import com.shop.online.dto.OrderQueryDTO;
import com.shop.online.service.OrderService;
import com.shop.online.vo.OrderVO;
import com.shop.online.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;

/**
 * 订单控制器
 */

/**
 * @RestController = @Controller + @ResponseBody
 */
@Slf4j
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;
    
    @Autowired
    private com.shop.online.service.UserService userService;

    /**
     * 获取订单列表
     */
    @GetMapping
    public Result getOrders(OrderQueryDTO queryDTO, HttpServletRequest request) {
        try {
            // 如果是退款状态，使用专门的退款订单接口
            if (queryDTO.getStatus() != null && "5".equals(queryDTO.getStatus())) {
                log.info("查询退款订单");
                // 使用专门方法查询退款订单
                Long userId = userService.getCurrentUser().getId();
                Object result = orderService.getRefundedOrders(userId, queryDTO.getPage(), queryDTO.getSize());
                return Result.success(result);
            }
            
            // 检查是否请求包含退款商品的订单（仍然保留此功能兼容性）
            boolean hasRefundedItems = queryDTO.getHasRefundedItems() != null && queryDTO.getHasRefundedItems();
            if (hasRefundedItems) {
                log.info("查询包含退款商品的订单");
                // 使用特殊方法查询包含退款商品的订单
                Object result = orderService.getOrdersWithRefundedItems(queryDTO);
                return Result.success(result);
            }
            
            // 常规订单查询
            Object result = orderService.getOrders(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("订单列表获取失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建订单
     */
    @PostMapping("/create")
    public Result createOrder(@RequestBody CreateOrderDTO createOrderDTO, HttpServletRequest request) {
//        log.info("========== 创建订单请求开始 ==========");
//        log.info("请求路径: {}", request.getRequestURI());
//        log.info("请求方法: {}", request.getMethod());
        
        // 打印请求头
        log.info("请求头信息:/");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
//            log.info("{}: {}", headerName, request.getHeader(headerName));
        }
        
        log.info("创建订单, 参数: {}", createOrderDTO);
        try {
            Object result = orderService.createOrder(createOrderDTO);
//            log.info("订单创建成功: {}", result);
//            log.info("========== 创建订单请求结束 ==========");
            return Result.success(result);
        } catch (Exception e) {
            log.error("订单创建失败", e);
            log.info("========== 创建订单请求失败 ==========");
            return Result.error(e.getMessage());
        }
    }

    /**
     * 支付订单
     */
    @PostMapping("/pay")
    public Result payOrder(@RequestBody Map<String, String> params, HttpServletRequest request) {
//        log.info("支付订单, 参数: {}", params);
//        log.info("请求路径: {}", request.getRequestURI());
        
        String orderNo = params.get("orderNo");
        if (orderNo == null) {
            log.warn("订单号为空");
            return Result.error("订单号不能为空");
        }
        
        String paymentMethod = params.get("paymentMethod");
        Integer paymentMethodInt = null;
        
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            try {
                paymentMethodInt = Integer.parseInt(paymentMethod);
            } catch (NumberFormatException e) {
                log.warn("支付方式格式不正确: {}", paymentMethod);
                return Result.error("支付方式格式不正确");
            }
        }
        
        try {
            orderService.payOrder(orderNo, paymentMethodInt);
//            log.info("订单支付成功: {}", orderNo);
            return Result.success();
        } catch (Exception e) {
            log.error("订单支付失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 取消订单
     */
    @PostMapping("/{orderNo}/cancel")
    public Result cancelOrder(@PathVariable("orderNo") String orderNo) {
//        log.info("取消订单, 订单号: {}", orderNo);
        try {
            orderService.cancelOrder(orderNo);
//            log.info("订单取消成功: {}", orderNo);
            return Result.success();
        } catch (Exception e) {
            log.error("订单取消失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 退款订单
     */
    @PostMapping("/{orderNo}/refund")
    public Result refundOrder(@PathVariable("orderNo") String orderNo) {
        log.info("申请退款, 订单号: {}", orderNo);
        try {
            orderService.refundOrder(orderNo);
            log.info("订单退款成功: {}", orderNo);
            return Result.success();
        } catch (Exception e) {
            log.error("订单退款失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 确认收货
     */
    @PostMapping("/{orderNo}/confirm")
    public Result confirmOrder(@PathVariable("orderNo") String orderNo) {
//        log.info("确认收货, 订单号: {}", orderNo);
        try {
            orderService.confirmOrder(orderNo);
//            log.info("确认收货成功: {}", orderNo);
            return Result.success();
        } catch (Exception e) {
            log.error("确认收货失败", e);
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取订单详情
     */
    @GetMapping("/{orderNo}")
    public Result getOrderDetail(@PathVariable("orderNo") String orderNo) {
//        log.info("获取订单详情, 订单号: {}", orderNo);
        try {
            OrderVO orderVO = orderService.getOrderDetail(orderNo);
//            log.info("获取订单详情成功: {}", orderNo);
            return Result.success(orderVO);
        } catch (Exception e) {
            log.error("获取订单详情失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 单个商品退款
     */
    @PostMapping("/refund-product")
    public Result refundProductItem(@RequestBody Map<String, Object> params) {
        log.info("申请商品退款, 参数: {}", params);
        try {
            String orderNo = params.get("orderNo").toString();
            Long productId = Long.parseLong(params.get("productId").toString());
            
            orderService.refundProductItem(orderNo, productId);
            log.info("商品退款成功: orderNo={}, productId={}", orderNo, productId);
            return Result.success();
        } catch (Exception e) {
            log.error("商品退款失败", e);
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取已退款订单列表
     */
    @GetMapping("/refunded")
    public Result getRefundedOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("获取已退款订单列表, page={}, size={}", page, size);
        try {
            // 获取当前用户ID
            Long userId = userService.getCurrentUser().getId();
            
            // 查询已退款订单
            Object result = orderService.getRefundedOrders(userId, page, size);
            log.info("获取已退款订单列表成功");
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取已退款订单列表失败", e);
            return Result.error(e.getMessage());
        }
    }
} 